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
    private PreReleaseChecksStep step;

    @BeforeEach
    void setUp() {
        logOutput = new ArrayList<>();
        commandOutputs = new HashMap<>();

        CommandRunner runner = new CommandRunner(logOutput::add) {
            @Override
            public String getOutput(Path workingDir, List<String> args) {
                String key = String.join(" ", args);
                return commandOutputs.getOrDefault(key, "");
            }
        };

        step = new PreReleaseChecksStep(runner);
    }

    private ReleaseState newState() {
        return ReleaseState.create(
                "maven-test-plugin", "org.apache.maven.plugins", "1.0.0-SNAPSHOT", ComponentType.PLUGIN, tempDir);
    }

    @Test
    void allChecksPassed() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        commandOutputs.put("grep -r SNAPSHOT pom.xml", "<version>1.0.0-SNAPSHOT</version>");

        StepResult result = step.execute(newState(), List.of());

        assertTrue(result.succeeded());
        assertTrue(logContains("✓"), "Expected success tick in output");
        long successCount = logOutput.stream().filter(l -> l.contains("✓")).count();
        assertTrue(successCount == 3, "Expected 3 success ticks but got " + successCount);
    }

    @Test
    void gpgKeyMissing() {
        commandOutputs.put("gpg --list-secret-keys", "");

        StepResult result = step.execute(newState(), List.of());

        assertFalse(result.succeeded());
        assertTrue(logContains("✗"), "Expected failure cross in output");
        assertTrue(logContains("GPG"), "Expected GPG mentioned in failure");
        assertFalse(logContains("✓"), "No success ticks should appear before GPG failure");
    }

    @Test
    void gitDirty() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "M pom.xml");

        StepResult result = step.execute(newState(), List.of());

        assertFalse(result.succeeded());
        assertTrue(logContains("✓"), "GPG check should have passed");
        assertTrue(logContains("✗"), "Git check should show failure");
        long successCount = logOutput.stream().filter(l -> l.contains("✓")).count();
        assertTrue(successCount == 1, "Only GPG tick should appear, got " + successCount);
    }

    @Test
    void externalSnapshotDependency() {
        commandOutputs.put("gpg --list-secret-keys", "sec rsa4096 2024-01-01");
        commandOutputs.put("git status --porcelain", "");
        commandOutputs.put(
                "grep -r SNAPSHOT pom.xml", "<version>1.0.0-SNAPSHOT</version>\n<version>2.0.0-SNAPSHOT</version>");

        StepResult result = step.execute(newState(), List.of());

        assertFalse(result.succeeded());
        long successCount = logOutput.stream().filter(l -> l.contains("✓")).count();
        assertTrue(successCount == 2, "GPG and git ticks should appear, got " + successCount);
        assertTrue(logContains("✗"), "SNAPSHOT check should show failure");
    }

    private boolean logContains(String substring) {
        return logOutput.stream().anyMatch(l -> l.contains(substring));
    }
}
