package com.linkedin.python.importer.distribution

import com.linkedin.python.importer.deps.DependencySubstitution
import com.linkedin.python.importer.pypi.PypiApiCache
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

@Slf4j
class WheelsPackage extends PythonPackage {

    WheelsPackage(File packageFile,
                  PypiApiCache pypiApiCache,
                  DependencySubstitution dependencySubstitution,
                  boolean latestVersions,
                  boolean allowPreReleases) {

        super(packageFile, pypiApiCache, dependencySubstitution, latestVersions, allowPreReleases)
    }

    /**
     * Get all the dependencies from package metadata. Json metadata is preferred since Version 2.0 of metadata
     * is migrated to JSON representation. See details at https://legacy.python.org/dev/peps/pep-0426/#abstract.
     * @return a Map whose key is configuration and value is dependencies List
     */
    @Override
    Map<String, List<String>> getDependencies() {
        Map<String, List<String>> dependenciesMap

        try {
            dependenciesMap = parseRuntimeRequiresFromMetadataJson(runtimeRequiresFromMetadataJson)
        } catch(Exception e) {
            log.info("Failed to parse Json Metadata for package ${packageFile.name}: ${e.message}. " +
                "Parsing METADATA text file instead.")
            dependenciesMap = parseDistRequiresFromMetadataText(metadataText)
        }

        return dependenciesMap
    }

    private Map<String, List<String>> parseRuntimeRequiresFromMetadataJson(Map<String, List<String>> requires) {
        def dependenciesMap = [:]
        log.debug("requires: {}", requires)

        requires.each { key, value ->
            String configuration = key
            List<String> requiresList = value

            if (dependenciesMap.containsKey(configuration)) {
                dependenciesMap[configuration] = []
            }

            for (String require : requiresList) {
                require = require.replaceAll(/[()]/, "")
                String dependency = parseDependencyFromRequire(require)
                dependenciesMap[configuration] << dependency
            }
        }

        return dependenciesMap
    }

    /**
     * Get a Map of runtime requires which includes run_requires and meta_requires dependencies from Json metadata.
     * The kep is configuration (extra), and value is runtime requires List.
     * @param jsonMetadata
     * @return
     */
    private Map<String, List<String>> getRuntimeRequiresFromMetadataJson() {
        def jsonMetadata = getJsonMetadata()

        if (jsonMetadata == null) {
            throw new RuntimeException("There is no metadata Json file in package ${packageFile.name}.")
        }

        Map<String, List<String>> runtimeRequiresMap = [:]

        def run_requires = jsonMetadata["run_requires"]
        def meta_requires = jsonMetadata["meta_requires"]

        addRuntimeRequiresFromRequiresMap(runtimeRequiresMap, run_requires)
        addRuntimeRequiresFromRequiresMap(runtimeRequiresMap, meta_requires)
    }

    /**
     * Get package metadata in Json format from the package.
     * @return Json package metadata, otherwise empty String if not found Json metadata
     */
    private getJsonMetadata() {
        String jsonMetadataEntry = moduleName + ".dist-info/metadata.json"
        String metadata = explodeZipForTargetEntry(jsonMetadataEntry)
        if (metadata == "") {
            return null
        }

        def jsonSlurper = new JsonSlurper()
        def jsonMetadata = jsonSlurper.parseText(metadata)
        log.debug("Json metadata: $jsonMetadata")

        return jsonMetadata
    }

    /**
     * Add all the runtime requires dependencies from requires List.
     * @param runtimeRequires
     * @param requires
     */
    private static void addRuntimeRequiresFromRequiresMap(Map<String, List<String>> runtimeRequiresMap, def requires) {
        for (def require_map : requires) {
            String configuration = require_map["extra"] ? require_map["extra"] : "default"
            if (runtimeRequiresMap[configuration] == null) {
                runtimeRequiresMap[configuration] = []
            }
            for (def requiresList : require_map["requires"]) {
                runtimeRequiresMap[configuration].addAll((List) requiresList)
            }
        }
    }

    private Map<String, List<String>> parseDistRequiresFromMetadataText(String requires) {
        def dependenciesMap = [:]
        log.debug("requires: {}", requires)
        def config = 'default'
        dependenciesMap[config] = []
        requires.eachLine { line ->
            if (line.startsWith("Requires-Dist:")) {
                line = line.replaceAll(/[()]/, "")

                // there is package named extras, see https://pypi.org/project/extras/
                config = line.substring(line.lastIndexOf("extra") + "extra".length())
                    .replaceAll(/['=\s]/, "")

                if (!dependenciesMap.containsKey(config)) {
                    dependenciesMap[config] = []
                }

                dependenciesMap[config] << parseDependencyFromRequire(line)
            }
        }

        return dependenciesMap
    }

    private String getMetadataText() {
        String metadataTextEntry = moduleName + ".dist-info/METADATA"
        return explodeZipForTargetEntry(metadataTextEntry)
    }
}
