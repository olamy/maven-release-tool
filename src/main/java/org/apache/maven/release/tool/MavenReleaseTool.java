/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.release.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.maven.release.tool.config.CommandOverrideStore;
import org.apache.maven.release.tool.config.CommandResolver;
import org.apache.maven.release.tool.config.ProjectConfig;
import org.apache.maven.release.tool.eta.EtaHistory;
import org.apache.maven.release.tool.eta.EtaTracker;
import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;
import org.apache.maven.release.tool.model.StepState;
import org.apache.maven.release.tool.model.StepStatus;
import org.apache.maven.release.tool.persistence.StateStore;
import org.apache.maven.release.tool.pipeline.PipelineBuilder;
import org.apache.maven.release.tool.pipeline.ReleasePipeline;
import org.apache.maven.release.tool.steps.Step;
import org.apache.maven.release.tool.ui.CommandConfirmView;
import org.apache.maven.release.tool.ui.ReleaseDashboard;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "maven-release-tool",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "TUI-based release supervisor for Apache Maven components",
        subcommands = {
            MavenReleaseTool.StartCommand.class,
            MavenReleaseTool.ResumeCommand.class,
            MavenReleaseTool.ListCommand.class,
            MavenReleaseTool.CleanCommand.class,
            MavenReleaseTool.StatsCommand.class
        })
public class MavenReleaseTool {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MavenReleaseTool()).execute(args);
        System.exit(exitCode);
    }

    @Command(name = "start", description = "Start a new release")
    static class StartCommand implements Runnable {

        @Option(
                names = "--version",
                description = "Release version (optional, release plugin detects from pom.xml if omitted)")
        String version;

        @Option(names = "--component", description = "Artifact ID (auto-detected from pom.xml if omitted)")
        String component;

        @Option(names = "--type", description = "Component type: CORE, PLUGIN, SHARED, PARENT_POM, SKIN, EXTENSION")
        ComponentType type;

        @Option(
                names = "--project-dir",
                description = "Project directory (defaults to current dir)",
                defaultValue = ".")
        Path projectDir;

        @Option(
                names = "--next-version",
                description = "Next development version (optional, release plugin will prompt if omitted)")
        String nextVersion;

        @Option(names = "--dry-run", description = "Show commands without executing")
        boolean dryRun;

        @Override
        public void run() {
            try {
                StateStore stateStore = new StateStore();
                CommandRunner runner = new CommandRunner();
                Path absProjectDir = projectDir.toAbsolutePath();

                if (component == null) {
                    component = detectArtifactId(runner, absProjectDir);
                }
                if (type == null) {
                    type = detectComponentType(runner, absProjectDir);
                }

                if (version == null) {
                    version = detectVersion(runner, absProjectDir);
                }

                String releaseTag = version != null ? component + "-" + version : null;

                ReleaseState state = ReleaseState.create(component, null, version, type, absProjectDir);
                state.setReleaseTag(releaseTag);
                state.setNextVersion(nextVersion);
                state.setDryRun(dryRun);

                String gitUrl = runner.getOutput(absProjectDir, List.of("git", "remote", "get-url", "origin"));
                state.setGitRemoteUrl(gitUrl);

                CommandOverrideStore overrideStore = new CommandOverrideStore(stateStore.getBaseDir());
                ProjectConfig projectConfig = overrideStore.load(gitUrl);

                PipelineBuilder pipelineBuilder = new PipelineBuilder(runner, stateStore);
                List<Step> steps = pipelineBuilder.buildPipeline(type);

                ReleasePipeline pipeline = new ReleasePipeline(steps, state, stateStore, overrideStore, projectConfig);

                EtaHistory etaHistory = new EtaHistory(stateStore.getBaseDir());
                etaHistory.load();
                EtaTracker etaTracker = new EtaTracker(etaHistory);

                System.out.println("=== Maven Release Tool ===");
                System.out.println(
                        "Component: " + component + (version != null ? " " + version : " (version from pom.xml)"));
                System.out.println("Type: " + type);
                System.out.println("Project: " + absProjectDir);
                System.out.println("Dry-run: " + dryRun);

                Duration eta = etaTracker.estimateRemaining(state, steps);
                if (!eta.isZero()) {
                    System.out.println("ETA: " + etaTracker.formatDuration(eta));
                }

                System.out.println("Steps: " + steps.size());
                System.out.println();

                runPipeline(pipeline, etaTracker, etaHistory, stateStore, overrideStore, projectConfig);

            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        private String detectArtifactId(CommandRunner runner, Path dir) {
            String output = runner.getOutput(
                    dir, List.of("mvn", "help:evaluate", "-Dexpression=project.artifactId", "-q", "-DforceStdout"));
            return output.isBlank() ? dir.getFileName().toString() : output.trim();
        }

        private ComponentType detectComponentType(CommandRunner runner, Path dir) {
            String parentArtifactId = runner.getOutput(
                    dir,
                    List.of("mvn", "help:evaluate", "-Dexpression=project.parent.artifactId", "-q", "-DforceStdout"));
            return ComponentType.fromParentArtifactId(parentArtifactId.trim());
        }

        private String detectVersion(CommandRunner runner, Path dir) {
            String pomVersion = runner.getOutput(
                    dir, List.of("mvn", "help:evaluate", "-Dexpression=project.version", "-q", "-DforceStdout"));
            if (!pomVersion.isBlank() && pomVersion.endsWith("-SNAPSHOT")) {
                return pomVersion.replace("-SNAPSHOT", "");
            }
            return pomVersion.isBlank() ? null : pomVersion;
        }
    }

    @Command(name = "resume", description = "Resume an in-progress release")
    static class ResumeCommand implements Runnable {

        @Option(names = "--component", required = true, description = "Artifact ID")
        String component;

        @Option(names = "--version", required = true, description = "Release version")
        String version;

        @Override
        public void run() {
            try {
                StateStore stateStore = new StateStore();
                ReleaseState state = stateStore.load(component, version);
                if (state == null) {
                    System.err.println("No in-progress release found for " + component + " " + version);
                    return;
                }

                CommandRunner runner = new CommandRunner();
                CommandOverrideStore overrideStore = new CommandOverrideStore(stateStore.getBaseDir());
                ProjectConfig projectConfig = overrideStore.load(state.getGitRemoteUrl());

                PipelineBuilder pipelineBuilder = new PipelineBuilder(runner, stateStore);
                List<Step> steps = pipelineBuilder.buildPipeline(state.getComponentType());

                ReleasePipeline pipeline = new ReleasePipeline(steps, state, stateStore, overrideStore, projectConfig);

                EtaHistory etaHistory = new EtaHistory(stateStore.getBaseDir());
                etaHistory.load();
                EtaTracker etaTracker = new EtaTracker(etaHistory);

                System.out.println("=== Resuming Release ===");
                System.out.println("Component: " + component + " " + version);
                System.out.println("Resuming from step " + (state.getCurrentStepIndex() + 1) + "/" + steps.size());
                System.out.println();

                // If current step is waiting (vote), handle it
                StepState currentStep = state.getCurrentStep();
                if (currentStep != null && currentStep.getStatus() == StepStatus.WAITING) {
                    System.out.println("Previous step '" + currentStep.getName() + "' was waiting.");
                    currentStep.markCompleted();
                    state.advanceToNextStep();
                    stateStore.save(state);
                }

                runPipeline(pipeline, etaTracker, etaHistory, stateStore, overrideStore, projectConfig);

            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "list", description = "List in-progress releases")
    static class ListCommand implements Runnable {
        @Override
        public void run() {
            try {
                StateStore stateStore = new StateStore();
                List<ReleaseState> releases = stateStore.listAll();
                if (releases.isEmpty()) {
                    System.out.println("No in-progress releases.");
                    return;
                }
                System.out.println("In-progress releases:");
                for (ReleaseState r : releases) {
                    System.out.printf(
                            "  %-40s  step %d/%d  %s%n",
                            r.getReleaseId(),
                            r.getCurrentStepIndex() + 1,
                            r.getSteps().size(),
                            r.getCurrentStep() != null ? r.getCurrentStep().getStatus() : "");
                }
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "clean", description = "Clean up a completed/abandoned release")
    static class CleanCommand implements Runnable {

        @Option(names = "--component", required = true, description = "Artifact ID")
        String component;

        @Option(names = "--version", required = true, description = "Release version")
        String version;

        @Override
        public void run() {
            try {
                StateStore stateStore = new StateStore();
                stateStore.delete(component + "-" + version);
                System.out.println("Cleaned up release: " + component + "-" + version);
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "stats", description = "Show ETA history and statistics")
    static class StatsCommand implements Runnable {

        @Option(names = "--type", description = "Filter by component type")
        ComponentType type;

        @Override
        public void run() {
            try {
                StateStore stateStore = new StateStore();
                EtaHistory history = new EtaHistory(stateStore.getBaseDir());
                history.load();

                var data = history.getData();
                if (data.isEmpty()) {
                    System.out.println("No ETA history recorded yet.");
                    return;
                }

                for (var entry : data.entrySet()) {
                    if (type != null && !entry.getKey().equals(type.name())) {
                        continue;
                    }
                    System.out.println(entry.getKey() + ":");
                    for (var stepEntry : entry.getValue().entrySet()) {
                        var timing = stepEntry.getValue();
                        System.out.printf(
                                "  %-30s  median: %ds  (%d samples)%n",
                                stepEntry.getKey(), timing.medianSeconds(), timing.samples());
                    }
                    System.out.println();
                }
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private static void runPipeline(
            ReleasePipeline pipeline,
            EtaTracker etaTracker,
            EtaHistory etaHistory,
            StateStore stateStore,
            CommandOverrideStore overrideStore,
            ProjectConfig projectConfig)
            throws IOException {

        CommandConfirmView confirmView = new CommandConfirmView();
        ReleaseDashboard dashboard = new ReleaseDashboard(pipeline.getState(), pipeline.getSteps(), etaTracker);

        dashboard.render();

        while (pipeline.hasMoreSteps()) {
            Step step = pipeline.getCurrentStep();
            StepState stepState = pipeline.getCurrentStepState();
            if (step == null || stepState == null) {
                break;
            }

            if (stepState.getStatus() == StepStatus.COMPLETED || stepState.getStatus() == StepStatus.SKIPPED) {
                pipeline.getState().advanceToNextStep();
                continue;
            }

            CommandResolver.ResolvedCommands resolved = pipeline.resolveCurrentCommands();

            CommandConfirmView.ConfirmResult confirm = confirmView.confirm(step.name(), step.describe(), resolved);

            if (confirm == null) {
                continue;
            }

            List<String> commandsToRun = confirm.commands();

            switch (confirm.action()) {
                case QUIT -> {
                    pipeline.save();
                    etaHistory.save();
                    System.out.println("Release state saved. Exiting.");
                    return;
                }
                case SKIP -> {
                    pipeline.skipCurrentStep();
                    System.out.println("Skipped: " + step.name());
                    continue;
                }
                case DRY_RUN -> {
                    StepResult dryResult = pipeline.dryRunCurrentStep();
                    if (dryResult.message() != null) {
                        System.out.println(dryResult.message());
                    }
                    continue;
                }
                case EDITED -> {
                    confirmView.promptSaveOverride(commandsToRun, step.name(), overrideStore, projectConfig);
                }
                case ACCEPT -> {
                    // fall through to execute
                }
                default -> {
                    continue;
                }
            }

            StepResult result = pipeline.executeCurrentStep(commandsToRun);

            if (result.message() != null) {
                System.out.println(result.message());
            }

            if (result.succeeded()) {
                if (result.suggestedAction() == StepResult.Action.PAUSE) {
                    etaHistory.save();
                    System.out.println("Release state saved. Exiting.");
                    return;
                }
                if (stepState.getDurationSeconds() != null) {
                    etaTracker.recordCompletedStep(pipeline.getState(), stepState);
                }
            } else {
                System.out.println("Step failed: " + result.message());
                boolean isReleaseStep = step.name().startsWith("maven-release-");

                CommandConfirmView.FailureAction failureAction =
                        confirmView.promptOnFailure(step.name(), isReleaseStep);

                switch (failureAction) {
                    case RETRY -> {
                        stepState.setStatus(StepStatus.PENDING);
                        pipeline.save();
                        continue;
                    }
                    case IGNORE -> {
                        System.out.println("Ignoring failure, continuing to next step.");
                        stepState.markSkipped();
                        pipeline.getState().advanceToNextStep();
                        pipeline.save();
                        continue;
                    }
                    case ROLLBACK -> {
                        System.out.println("Running mvn release:rollback...");
                        CommandRunner rollbackRunner = new CommandRunner(System.out::println);
                        rollbackRunner.exec(
                                Path.of(pipeline.getState().getProjectDir()), List.of("mvn", "release:rollback"));
                        System.out.println("Running mvn release:clean...");
                        rollbackRunner.exec(
                                Path.of(pipeline.getState().getProjectDir()), List.of("mvn", "release:clean"));
                        System.out.println("Rollback complete. Release state saved.");
                        etaHistory.save();
                        return;
                    }
                    default -> {
                        System.out.println("Release state saved. Fix the issue and resume.");
                        etaHistory.save();
                        return;
                    }
                }
            }
        }

        etaHistory.save();
        dashboard.render();
        System.out.println("\n=== Release complete! ===");
    }
}
