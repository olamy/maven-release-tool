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
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenPrepareAndPerformStepTest {

    private final MavenPrepareAndPerformStep step = new MavenPrepareAndPerformStep(new CommandRunner());

    @Test
    void nameIsCorrect() {
        assertEquals("maven-release-prepare-and-perform", step.name());
    }

    @Test
    void defaultCommandsContainsBothPrepareAndPerform() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "3.0.0", ComponentType.PLUGIN, Path.of("/tmp"));

        List<String> commands = step.defaultCommands(state);

        assertEquals(2, commands.size());
        assertTrue(commands.get(0).contains("release:prepare"), "first command should be release:prepare");
        assertTrue(commands.get(1).contains("release:perform"), "second command should be release:perform");
    }

    @Test
    void defaultCommandsIncludeVersionArguments() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "3.0.0", ComponentType.PLUGIN, Path.of("/tmp"));
        state.setNextVersion("3.0.1-SNAPSHOT");
        state.setReleaseTag("my-plugin-3.0.0");

        List<String> commands = step.defaultCommands(state);

        String prepareCmd = commands.get(0);
        assertTrue(prepareCmd.contains("-DreleaseVersion=3.0.0"), "prepare command should contain releaseVersion");
        assertTrue(
                prepareCmd.contains("-DdevelopmentVersion=3.0.1-SNAPSHOT"),
                "prepare command should contain developmentVersion");
        assertTrue(prepareCmd.contains("-Dtag=my-plugin-3.0.0"), "prepare command should contain tag");
    }

    @Test
    void defaultCommandsWithNoVersionHasNoVersionArgs() {
        ReleaseState state = ReleaseState.create("my-plugin", null, null, ComponentType.PLUGIN, Path.of("/tmp"));

        List<String> commands = step.defaultCommands(state);

        assertEquals("mvn release:prepare", commands.get(0));
        assertEquals("mvn release:perform", commands.get(1));
    }

    @Test
    void dryRunAppendsDryRunFlagToEachCommand() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "3.0.0", ComponentType.PLUGIN, Path.of("/tmp"));
        List<String> original = List.of("mvn release:prepare -DreleaseVersion=3.0.0", "mvn release:perform");

        List<String> dryRunCommands = collectDryRunCommands(state, original);

        assertTrue(dryRunCommands.get(0).endsWith("-DdryRun=true"), "prepare should have -DdryRun=true appended");
        assertTrue(dryRunCommands.get(1).endsWith("-DdryRun=true"), "perform should have -DdryRun=true appended");
    }

    @Test
    void dryRunDoesNotDuplicateDryRunFlag() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "3.0.0", ComponentType.PLUGIN, Path.of("/tmp"));
        List<String> commands = List.of("mvn release:prepare -DdryRun=true", "mvn release:perform -DdryRun=true");

        List<String> dryRunCommands = collectDryRunCommands(state, commands);

        assertEquals(
                1, countOccurrences(dryRunCommands.get(0), "-DdryRun=true"), "should not duplicate flag in prepare");
        assertEquals(
                1, countOccurrences(dryRunCommands.get(1), "-DdryRun=true"), "should not duplicate flag in perform");
    }

    /**
     * Captures what dryRun() would pass to runCommands by intercepting the
     * AbstractStep.dryRun implementation through a test subclass.
     */
    private List<String> collectDryRunCommands(ReleaseState state, List<String> commands) {
        // We cannot run actual processes in unit tests, so we use a subclass
        // that captures the commands before running them.
        final List<String>[] captured = new List[1];
        MavenPrepareAndPerformStep spy = new MavenPrepareAndPerformStep(new CommandRunner()) {
            @Override
            protected org.apache.maven.release.tool.model.StepResult runCommands(ReleaseState s, List<String> cmds) {
                captured[0] = cmds;
                return org.apache.maven.release.tool.model.StepResult.ok();
            }
        };
        spy.dryRun(state, commands);
        return captured[0];
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
