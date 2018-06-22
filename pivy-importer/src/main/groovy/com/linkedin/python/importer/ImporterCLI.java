/*
 * Copyright 2016 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.python.importer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.linkedin.python.importer.deps.SdistDownloader;
import com.linkedin.python.importer.deps.WheelsDownloader;
import com.linkedin.python.importer.deps.DependencyDownloader;
import com.linkedin.python.importer.deps.DependencySubstitution;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ImporterCLI {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ImporterCLI.class);

    private ImporterCLI() {
        //private constructor for "util" class
    }

    public static void main(String[] args) throws Exception {

        Options options = createOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(options, args);

        if (line.hasOption("quiet")) {
            Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.setLevel(Level.WARN);
        }

        if (!line.hasOption("repo")) {
            throw new RuntimeException("Unable to continue, no repository location given on the command line (use the --repo switch)");
        }
        final File repoPath = new File(line.getOptionValue("repo"));

        repoPath.mkdirs();

        if (!repoPath.exists() || !repoPath.isDirectory()) {
            throw new RuntimeException("Unable to continue, " + repoPath.getAbsolutePath() + " does not exist, or is not a directory");
        }

        importPackages(line, repoPath);
        logger.info("Execution Finished!");
    }

    private static void importPackages(CommandLine line, File repoPath) {
        final DependencySubstitution replacements = new DependencySubstitution(buildSubstitutionMap(line), buildForceMap(line));
        for (String dependency : line.getArgList()) {
            DependencyDownloader artifactDownloader;

            if (dependency.split(":").length == 2) {
                artifactDownloader = new SdistDownloader(dependency, repoPath, replacements, line.hasOption("latest"),
                    line.hasOption("pre"), line.hasOption("lenient"));
            } else if (dependency.split(":").length == 3) {
                artifactDownloader = new WheelsDownloader(dependency, repoPath, replacements, line.hasOption("latest"),
                    line.hasOption("pre"), line.hasOption("lenient"));
            } else {
                String errMsg = "Unable to parse the dependency "
                    + dependency
                    + ".\nThe format of dependency should be either <module>:<revision> for source distribution "
                    + "or <module>:<revision>:<classifier> for Wheels.";

                if (line.hasOption("lenient")) {
                    logger.error(errMsg);
                    continue;
                }
                throw new IllegalArgumentException(errMsg);
            }

            pullDownPackageAndDependencies(artifactDownloader);
        }
    }

    public static void pullDownPackageAndDependencies(DependencyDownloader artifactDownloader) {
        Set<String> processedDependencies = new HashSet<>();
        artifactDownloader.getProcessedDependencies().addAll(processedDependencies);
        artifactDownloader.download();
        processedDependencies.addAll(artifactDownloader.getProcessedDependencies());
    }

    private static Map<String, String> buildForceMap(CommandLine line) {
        Map<String, String> sub = new LinkedHashMap<>();
        if (line.hasOption("force")) {
            for (String it : Arrays.asList(line.getOptionValues("force"))) {
                System.out.println(it);
                String[] split = it.split(":");
                sub.put(split[0], split[1]);
            }
        }

        return sub;
    }

    private static Options createOptions() {
        Option repo = Option.builder()
            .longOpt("repo")
            .numberOfArgs(1)
            .argName("file")
            .desc("location of the ivy repo")
            .build();

        Option replacement = Option.builder()
            .longOpt("replace")
            .numberOfArgs(Option.UNLIMITED_VALUES)
            .argName("replacement")
            .desc("Noted like orig:version=replace:version")
            .valueSeparator(',')
            .build();

        Option quiet = Option.builder()
            .longOpt("quiet")
            .numberOfArgs(0)
            .desc("Sets logging level to WARN")
            .build();

        Option force = Option.builder()
            .longOpt("force")
            .numberOfArgs(Option.UNLIMITED_VALUES)
            .desc("Noted like name:version")
            .valueSeparator(',')
            .build();

        Option latest = Option.builder()
            .longOpt("latest")
            .numberOfArgs(0)
            .desc("Gets latest versions of dependencies")
            .build();

        Option pre = Option.builder()
            .longOpt("pre")
            .numberOfArgs(0)
            .desc("Allows pre-releases (alpha, beta, release candidates)")
            .build();

        Option lenient = Option.builder()
            .longOpt("lenient")
            .numberOfArgs(0)
            .desc("Allows to import all available dependencies with logging missed")
            .build();

        Options options = new Options();
        options.addOption(replacement);
        options.addOption(repo);
        options.addOption(quiet);
        options.addOption(force);
        options.addOption(latest);
        options.addOption(pre);
        options.addOption(lenient);

        return options;
    }

    public static Map<String, String> buildSubstitutionMap(CommandLine line) {
        Map<String, String> sub = new LinkedHashMap<>();
        if (line.hasOption("replace")) {
            for (String it : Arrays.asList(line.getOptionValues("replace"))) {
                System.out.println(it);
                String[] split = it.split("=");
                sub.put(split[0], split[1]);
            }
        }
        return sub;
    }
}
