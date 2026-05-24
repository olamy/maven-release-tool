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
import org.apache.maven.release.tool.config.GlobalConfig;
import org.apache.maven.release.tool.config.ProjectConfig;
import org.apache.maven.release.tool.eta.EtaHistory;
import org.apache.maven.release.tool.eta.EtaTracker;
import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.exec.TeeOutputCapture;
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
import org.apache.maven.release.tool.ui.ReleaseSelector;
import org.apache.maven.release.tool.ui.StepOutputView;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.completer.FileNameCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "maven-release-tool",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "TUI-based release supervisor for Apache Maven components",
        footer = {
            "",
            "Data is stored under: ~/.m2/maven-release-tool/",
            "  releases/<id>/          release state, vote/announcement emails, step logs",
            "  projects/<name>/        per-project command overrides",
            "  history.json            ETA timing from past releases",
            "  config.json             user preferences"
        },
        subcommands = {
            MavenReleaseTool.StartCommand.class,
            MavenReleaseTool.ResumeCommand.class,
            MavenReleaseTool.ListCommand.class,
            MavenReleaseTool.ManageCommand.class,
            MavenReleaseTool.CleanCommand.class,
            MavenReleaseTool.StatsCommand.class
        })
public class MavenReleaseTool {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MavenReleaseTool()).execute(args);
        System.exit(exitCode);
    }

    @Command(name = "start", description = "Start a new release from current directory")
    static class StartCommand implements Runnable {

        @Option(
                names = {"-h", "--help"},
                usageHelp = true,
                description = "Show this help message and exit.")
        boolean helpRequested;

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
                description = "Project directory (prompts with current dir as default when omitted)")
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
                if (projectDir == null) {
                    projectDir = promptProjectDir();
                }
                StateStore stateStore = new StateStore();
                TeeOutputCapture capture = new TeeOutputCapture();
                CommandRunner runner = new CommandRunner(capture);
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

                ReleaseState state = ReleaseState.create(
                        component, detectGroupId(runner, absProjectDir), version, type, absProjectDir);
                state.setReleaseTag(releaseTag);
                state.setNextVersion(nextVersion);
                state.setDryRun(dryRun);

                String gitUrl = runner.getOutput(absProjectDir, List.of("git", "remote", "get-url", "origin"));
                state.setGitRemoteUrl(gitUrl);

                CommandOverrideStore overrideStore = new CommandOverrideStore(stateStore.getBaseDir());
                ProjectConfig projectConfig = overrideStore.load(gitUrl);
                GlobalConfig globalConfig = overrideStore.loadGlobal();

                PipelineBuilder pipelineBuilder = new PipelineBuilder(runner, stateStore);
                List<Step> steps = pipelineBuilder.buildPipeline(type);

                ReleasePipeline pipeline =
                        new ReleasePipeline(steps, state, stateStore, overrideStore, projectConfig, globalConfig);

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

                runPipeline(pipeline, etaTracker, etaHistory, overrideStore, projectConfig, globalConfig, capture);

            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }

        private Path promptProjectDir() throws IOException {
            Path defaultDir = Path.of("").toAbsolutePath();
            try (Terminal terminal =
                    TerminalBuilder.builder().system(true).jansi(true).build()) {
                LineReader reader = LineReaderBuilder.builder()
                        .terminal(terminal)
                        .completer(new FileNameCompleter())
                        .build();
                String input = reader.readLine("Project directory [" + defaultDir + "]: ");
                if (input == null || input.isBlank()) {
                    return defaultDir;
                }
                Path typed = Path.of(input.trim());
                return typed.isAbsolute() ? typed : defaultDir.resolve(typed).normalize();
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

        private String detectGroupId(CommandRunner runner, Path dir) {
            String output = runner.getOutput(
                    dir, List.of("mvn", "help:evaluate", "-Dexpression=project.groupId", "-q", "-DforceStdout"));
            return output.isBlank() ? null : output.trim();
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

        @Option(
                names = {"-h", "--help"},
                usageHelp = true,
                description = "Show this help message and exit.")
        boolean helpRequested;

        @Option(names = "--component", description = "Artifact ID (optional when --dashboard is used)")
        String component;

        @Option(names = "--version", description = "Release version (optional when --dashboard is used)")
        String version;

        @Option(names = "--dashboard", description = "Open interactive dashboard to select a release")
        boolean dashboard;

        @Override
        public void run() {
            try {
                if (dashboard || component == null || version == null) {
                    StateStore stateStore = new StateStore();
                    ReleaseState selected = selectFromDashboard(stateStore);
                    if (selected == null) {
                        return;
                    }
                    resumeRelease(stateStore, selected.getArtifactId(), selected.getVersion());
                } else {
                    resumeRelease(new StateStore(), component, version);
                }
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "list", description = "List in-progress releases")
    static class ListCommand implements Runnable {

        @Option(
                names = {"-h", "--help"},
                usageHelp = true,
                description = "Show this help message and exit.")
        boolean helpRequested;

        @Option(names = "--dashboard", description = "Open interactive dashboard to select a release")
        boolean dashboard;

        @Override
        public void run() {
            try {
                StateStore stateStore = new StateStore();
                if (dashboard) {
                    ReleaseState selected = selectFromDashboard(stateStore);
                    if (selected == null) {
                        return;
                    }
                    resumeRelease(stateStore, selected.getArtifactId(), selected.getVersion());
                } else {
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
                }
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "manage", description = "Interactively manage in-progress releases (resume or delete)")
    static class ManageCommand implements Runnable {

        @Option(
                names = {"-h", "--help"},
                usageHelp = true,
                description = "Show this help message and exit.")
        boolean helpRequested;

        @Override
        public void run() {
            try {
                StateStore stateStore = new StateStore();
                ReleaseSelector selector = new ReleaseSelector();

                while (true) {
                    List<ReleaseState> releases = stateStore.listAll();
                    if (releases.isEmpty()) {
                        System.out.println("No in-progress releases.");
                        return;
                    }

                    ReleaseSelector.ManageResult result = selector.manage(releases);

                    if (result == null || result.action() == ReleaseSelector.ManageAction.QUIT) {
                        return;
                    }

                    if (result.action() == ReleaseSelector.ManageAction.RESUME) {
                        ReleaseState selected = result.release();
                        resumeRelease(stateStore, selected.getArtifactId(), selected.getVersion());
                        return;
                    }

                    if (result.action() == ReleaseSelector.ManageAction.DELETE) {
                        ReleaseState selected = result.release();
                        System.out.print("Delete " + selected.getReleaseId() + "? [y/N] ");
                        System.out.flush();
                        int ch = System.in.read();
                        System.out.println();
                        if (ch == 'y' || ch == 'Y') {
                            stateStore.delete(selected.getReleaseId());
                            System.out.println("Deleted " + selected.getReleaseId() + ".");
                        }
                        // loop back to show updated list
                    }
                }
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @Command(name = "clean", description = "Clean up a completed/abandoned release")
    static class CleanCommand implements Runnable {

        @Option(
                names = {"-h", "--help"},
                usageHelp = true,
                description = "Show this help message and exit.")
        boolean helpRequested;

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

        @Option(
                names = {"-h", "--help"},
                usageHelp = true,
                description = "Show this help message and exit.")
        boolean helpRequested;

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

    private static ReleaseState selectFromDashboard(StateStore stateStore) throws IOException {
        List<ReleaseState> releases = stateStore.listAll();
        return new ReleaseSelector().select(releases);
    }

    private static void resumeRelease(StateStore stateStore, String component, String version) throws IOException {
        ReleaseState state = stateStore.load(component, version);
        if (state == null) {
            System.err.println("No in-progress release found for " + component + " " + version);
            return;
        }

        TeeOutputCapture capture = new TeeOutputCapture();
        CommandRunner runner = new CommandRunner(capture);
        CommandOverrideStore overrideStore = new CommandOverrideStore(stateStore.getBaseDir());
        ProjectConfig projectConfig = overrideStore.load(state.getGitRemoteUrl());
        GlobalConfig globalConfig = overrideStore.loadGlobal();

        PipelineBuilder pipelineBuilder = new PipelineBuilder(runner, stateStore);
        List<Step> steps = pipelineBuilder.buildPipeline(state.getComponentType());

        ReleasePipeline pipeline =
                new ReleasePipeline(steps, state, stateStore, overrideStore, projectConfig, globalConfig);

        EtaHistory etaHistory = new EtaHistory(stateStore.getBaseDir());
        etaHistory.load();
        EtaTracker etaTracker = new EtaTracker(etaHistory);

        System.out.println("=== Resuming Release ===");
        System.out.println("Component: " + component + " " + version);
        System.out.println("Resuming from step " + (state.getCurrentStepIndex() + 1) + "/" + steps.size());
        System.out.println();

        StepState currentStep = state.getCurrentStep();
        if (currentStep != null && currentStep.getStatus() == StepStatus.WAITING) {
            System.out.println("Previous step '" + currentStep.getName() + "' was waiting.");
            currentStep.markCompleted();
            state.advanceToNextStep();
            stateStore.save(state);
        }

        runPipeline(pipeline, etaTracker, etaHistory, overrideStore, projectConfig, globalConfig, capture);
    }

    private static void runPipeline(
            ReleasePipeline pipeline,
            EtaTracker etaTracker,
            EtaHistory etaHistory,
            CommandOverrideStore overrideStore,
            ProjectConfig projectConfig,
            GlobalConfig globalConfig,
            TeeOutputCapture capture)
            throws IOException {

        CommandConfirmView confirmView = new CommandConfirmView();
        ReleaseDashboard dashboard = new ReleaseDashboard(pipeline.getState(), pipeline.getSteps(), etaTracker);
        StepOutputView outputView = new StepOutputView();

        dashboard.clearAndRender();

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
                    dashboard.clearAndRender();
                    continue;
                }
                case GO_BACK -> {
                    boolean moved = pipeline.goBackToPreviousStep();
                    if (moved) {
                        dashboard.clearAndRender();
                    } else {
                        System.out.println("Already at the first step.");
                    }
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
                    confirmView.promptSaveOverride(
                            commandsToRun, step.name(), overrideStore, projectConfig, globalConfig);
                }
                case ACCEPT -> {
                    // fall through to execute
                }
                default -> {
                    continue;
                }
            }

            capture.reset();
            StepResult result = pipeline.executeCurrentStep(commandsToRun);

            if (result.message() != null) {
                capture.buffer(result.message());
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
                if (result.fullScreen()) {
                    outputView.showFullScreen(dashboard, capture.getLines(), step.name());
                } else {
                    dashboard.clearAndRender();
                    outputView.show(dashboard, capture.getLines(), step.name());
                }
            } else {
                dashboard.clearAndRender();
                outputView.show(dashboard, capture.getLines(), step.name());

                boolean isReleaseStep = step.name().startsWith("maven-release-");

                CommandConfirmView.FailureAction failureAction =
                        confirmView.promptOnFailure(step.name(), isReleaseStep);

                switch (failureAction) {
                    case RETRY -> {
                        stepState.setStatus(StepStatus.PENDING);
                        pipeline.save();
                        dashboard.clearAndRender();
                    }
                    case IGNORE -> {
                        stepState.markSkipped();
                        pipeline.getState().advanceToNextStep();
                        pipeline.save();
                        dashboard.clearAndRender();
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
        dashboard.clearAndRender();
        System.out.println("\n=== Release complete! ===");
    }
}
