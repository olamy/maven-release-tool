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
package org.apache.maven.release.tool.steps;

import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;

public class PreReleaseChecksStep extends AbstractStep {

    private static final int WIDTH = 80;

    public PreReleaseChecksStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "pre-release-checks";
    }

    @Override
    public String describe() {
        return "Verify GPG key, JDK version, git status, and SNAPSHOT dependencies";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of(
                "gpg --list-secret-keys",
                "java -version",
                "git status --porcelain",
                "mvn dependency:list --no-transfer-progress");
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        // Check GPG key
        String gpgOutput = runner.getOutput(projectDir(state), List.of("gpg", "--list-secret-keys"));
        if (gpgOutput.isBlank()) {
            logFailure("No GPG secret keys found");
            return StepResult.failure("No GPG secret keys found. Configure GPG signing before releasing.");
        }
        logSuccess("GPG signing key found");

        // Check git is clean
        String gitStatus = runner.getOutput(projectDir(state), List.of("git", "status", "--porcelain"));
        if (!gitStatus.isBlank()) {
            logFailure("Working directory is not clean");
            return StepResult.failure("Working directory is not clean:\n" + gitStatus);
        }
        logSuccess("Working directory clean");

        // Check for SNAPSHOT dependencies via Maven dependency resolution.
        // This catches deps inherited from parent POMs, BOMs, and property-resolved versions
        // that a plain pom.xml grep would miss.
        String depList =
                runner.getOutput(projectDir(state), List.of("mvn", "dependency:list", "--no-transfer-progress"));
        // Coordinates may be 5-part (groupId:artifactId:type:version:scope)
        // or 6-part with classifier (groupId:artifactId:type:classifier:version:scope).
        // The regex matches both by allowing the SNAPSHOT token to appear anywhere after the type.
        List<String> snapshotLines = depList.lines()
                .filter(line -> line.contains("SNAPSHOT"))
                .filter(line -> line.matches("^\\[INFO]\\s{4}\\S+:\\S+:\\S+:.*SNAPSHOT.*"))
                .toList();

        if (!snapshotLines.isEmpty()) {
            String projectGroupId = state.getGroupId();
            String artifactList = snapshotLines.stream()
                    .map(line -> {
                        String[] parts = line.replaceFirst("^\\[INFO]\\s+", "").split(":");
                        // Find the version token (the one containing SNAPSHOT)
                        String version = java.util.Arrays.stream(parts)
                                .filter(p -> p.contains("SNAPSHOT"))
                                .findFirst()
                                .orElse("?-SNAPSHOT");
                        String label =
                                projectGroupId != null && parts[0].equals(projectGroupId) ? " (inter-module)" : "";
                        return parts[0] + ":" + parts[1] + ":" + version + label;
                    })
                    .distinct()
                    .reduce("", (a, b) -> a + "\n  - " + b);
            runner.log("  \u26a0 SNAPSHOT dependencies found:" + artifactList);
            boolean accepted = runner.promptYesNo("  Continue with the release anyway?");
            if (!accepted) {
                logFailure("Release blocked by SNAPSHOT dependencies");
                return StepResult.failure("Release blocked: SNAPSHOT dependencies not accepted:" + artifactList);
            }
            logSuccess("SNAPSHOT dependencies accepted by user");
        } else {
            logSuccess("No SNAPSHOT dependencies");
        }

        // Detect and store the most recent git tag (previous release tag)
        String previousTag =
                runner.getOutput(projectDir(state), List.of("git", "describe", "--abbrev=0", "--tags", "HEAD"));
        if (!previousTag.isBlank()) {
            state.setPreviousTag(previousTag.trim());
            logSuccess("Previous release tag detected: " + previousTag.trim());
        }

        return StepResult.ok("All pre-release checks passed.");
    }

    private void logSuccess(String message) {
        runner.log(styledCheck("  ✓ ", Color.GREEN, message));
    }

    private void logFailure(String message) {
        runner.log(styledCheck("  ✗ ", Color.RED, message));
    }

    private static String styledCheck(String icon, Color iconColor, String message) {
        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(Span.styled(icon, Style.EMPTY.fg(iconColor)), Span.raw(message))))
                .build()
                .render(buf.area(), buf);
        return buf.toAnsiStringTrimmed();
    }
}
