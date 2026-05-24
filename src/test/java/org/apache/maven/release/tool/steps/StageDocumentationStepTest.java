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
import org.apache.maven.release.tool.model.StepResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageDocumentationStepTest {

    private final StageDocumentationStep step = new StageDocumentationStep(new CommandRunner());

    @Test
    void nameIsCorrect() {
        assertEquals("stage-documentation", step.name());
    }

    @Test
    void executesInTargetCheckoutSubdirectory() {
        Path projectRoot = Path.of("/tmp/my-project");
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "3.0.0", ComponentType.PLUGIN, projectRoot);

        Path[] capturedDir = new Path[1];
        StageDocumentationStep spy = new StageDocumentationStep(new CommandRunner()) {
            @Override
            protected StepResult runCommands(ReleaseState s, List<String> commands) {
                capturedDir[0] = projectDir(s);
                return StepResult.ok();
            }
        };

        spy.execute(state, List.of("mvn -Preporting site site:stage"));

        assertEquals(
                projectRoot.resolve("target/checkout"),
                capturedDir[0],
                "StageDocumentationStep must run in target/checkout, not the project root");
    }

    @Test
    void defaultCommandsForPluginIncludeReportingProfile() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "3.0.0", ComponentType.PLUGIN, Path.of("/tmp"));

        List<String> commands = step.defaultCommands(state);

        assertTrue(commands.stream().anyMatch(c -> c.contains("-Preporting")), "should include -Preporting profile");
        assertTrue(commands.stream().anyMatch(c -> c.contains("site:stage")), "should include site:stage goal");
    }

    @Test
    void defaultCommandsForParentPomUsesDocsModule() {
        ReleaseState state = ReleaseState.create(
                "maven-parent", "org.apache.maven", "40", ComponentType.PARENT_POM, Path.of("/tmp"));

        List<String> commands = step.defaultCommands(state);

        assertTrue(commands.stream().anyMatch(c -> c.contains("-f docs")), "should run with -f docs for parent POM");
    }
}
