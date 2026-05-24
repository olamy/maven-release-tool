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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreReleaseChecksStepTest {

    @TempDir
    Path tempDir;

    private List<String> logOutput;
    private Map<String, String> commandOutputs;
    private boolean promptYesNoResponse;
    private PreReleaseChecksStep step;

    @BeforeEach
    void setUp() {
        logOutput = new ArrayList<>();
        commandOutputs = new HashMap<>();
        promptYesNoResponse = false;

        CommandRunner runner = new CommandRunner(logOutput::add) {
            @Override
            public String getOutput(Path workingDir, List<String> args) {
                String key = String.join(" ", args);
                return commandOutputs.getOrDefault(key, "");
            }

            @Override
            public boolean promptYesNo(String prompt) {
                logOutput.add(prompt);
                return promptYesNoResponse;
            }
        };

        step = new PreReleaseChecksStep(runner);
    }

    private ReleaseState newState() {
        return ReleaseState.create(
                "maven-test-plugin", "org.apache.maven.plugins", "1.0.0-SNAPSHOT", ComponentType.PLUGIN, tempDir);
    }

    private ReleaseState stateWithNullGroupId() {
        return ReleaseState.create("maven-test-plugin", null, "1.0.0-SNAPSHOT", ComponentType.PLUGIN, tempDir);
    }

    @Test
    void allChecksPassed() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        // No SNAPSHOT deps at all — clean project
        commandOutputs.put(
                "mvn dependency:list --no-transfer-progress",
                "[INFO]    org.apache.commons:commons-lang3:jar:3.12.0:compile\n");

        StepResult result = step.execute(newState(), List.of());

        assertTrue(result.succeeded());
        assertTrue(logContains("\u2713"), "Expected success tick in output");
        long successCount = logOutput.stream().filter(l -> l.contains("\u2713")).count();
        assertTrue(successCount == 3, "Expected 3 success ticks but got " + successCount);
    }

    @Test
    void gpgKeyMissing() {
        commandOutputs.put("gpg --list-secret-keys", "");

        StepResult result = step.execute(newState(), List.of());

        assertFalse(result.succeeded());
        assertTrue(logContains("\u2717"), "Expected failure cross in output");
        assertTrue(logContains("GPG"), "Expected GPG mentioned in failure");
        assertFalse(logContains("\u2713"), "No success ticks should appear before GPG failure");
    }

    @Test
    void gitDirty() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "M pom.xml");

        StepResult result = step.execute(newState(), List.of());

        assertFalse(result.succeeded());
        assertTrue(logContains("\u2713"), "GPG check should have passed");
        assertTrue(logContains("\u2717"), "Git check should show failure");
        long successCount = logOutput.stream().filter(l -> l.contains("\u2713")).count();
        assertTrue(successCount == 1, "Only GPG tick should appear, got " + successCount);
    }

    @Test
    void snapshotDependencyUserAccepts() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        // Any SNAPSHOT dep — user is prompted and accepts
        commandOutputs.put(
                "mvn dependency:list --no-transfer-progress",
                "[INFO]    com.example:snapshot-lib:jar:2.0-SNAPSHOT:compile\n");
        promptYesNoResponse = true;

        StepResult result = step.execute(newState(), List.of());

        assertTrue(result.succeeded(), "Step should succeed when user accepts SNAPSHOT deps");
        assertTrue(logContains("SNAPSHOT dependencies found"), "Should warn about SNAPSHOT deps");
        assertTrue(logContains("snapshot-lib"), "Should name the artifact");
        assertTrue(logContains("\u2713"), "Should show accepted tick");
    }

    @Test
    void snapshotDependencyUserRejects() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        commandOutputs.put(
                "mvn dependency:list --no-transfer-progress",
                "[INFO]    com.example:snapshot-lib:jar:2.0-SNAPSHOT:compile\n");
        promptYesNoResponse = false;

        StepResult result = step.execute(newState(), List.of());

        assertFalse(result.succeeded(), "Step should fail when user rejects SNAPSHOT deps");
        assertTrue(logContains("\u2717"), "Should show failure tick");
        assertTrue(result.message().contains("snapshot-lib"), "Failure message should name the artifact");
    }

    @Test
    void internalSnapshotLabeledAsInterModule() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        // Same groupId — labelled (inter-module) in the output
        commandOutputs.put(
                "mvn dependency:list --no-transfer-progress",
                "[INFO]    org.apache.maven.plugins:plugin-a:jar:1.0.0-SNAPSHOT:compile\n");
        promptYesNoResponse = true;

        StepResult result = step.execute(newState(), List.of());

        assertTrue(result.succeeded());
        assertTrue(logContains("inter-module"), "Same-groupId SNAPSHOT should be labelled as inter-module");
    }

    @Test
    void classifierCoordinateHandledCorrectly() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        // 6-part coordinate with classifier: groupId:artifactId:type:classifier:version:scope
        commandOutputs.put(
                "mvn dependency:list --no-transfer-progress",
                "[INFO]    org.apache.maven.plugins:maven-surefire-plugin:zip:site-source:3.5.6-SNAPSHOT:provided\n");
        promptYesNoResponse = true;

        StepResult result = step.execute(newState(), List.of());

        assertTrue(result.succeeded(), "Classifier coordinate should be handled and prompted");
        assertTrue(logContains("maven-surefire-plugin"), "Artifact name should appear in warning");
        assertTrue(logContains("3.5.6-SNAPSHOT"), "SNAPSHOT version should appear in warning");
    }

    @Test
    void nullGroupIdPromptsForAllSnapshots() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        commandOutputs.put(
                "mvn dependency:list --no-transfer-progress",
                "[INFO]    some.group:some-lib:jar:1.0-SNAPSHOT:compile\n");
        promptYesNoResponse = true;

        StepResult result = step.execute(stateWithNullGroupId(), List.of());

        assertTrue(result.succeeded(), "Should succeed when user accepts and groupId is unknown");
    }

    private boolean logContains(String substring) {
        return logOutput.stream().anyMatch(l -> l.contains(substring));
    }
}
