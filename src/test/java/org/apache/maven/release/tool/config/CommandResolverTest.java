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
package org.apache.maven.release.tool.config;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.steps.MavenPrepareAndPerformStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandResolverTest {

    @Test
    void interpolatesAllVariables() {
        ReleaseState state = ReleaseState.create(
                "maven-compiler-plugin", "org.apache.maven.plugins", "3.14.0", ComponentType.PLUGIN, Path.of("/tmp"));
        state.setNextVersion("3.14.1-SNAPSHOT");
        state.setReleaseTag("maven-compiler-plugin-3.14.0");
        state.setStagingRepoId("orgapachemaven-1234");
        state.setStagingRepoUrl("https://repository.apache.org/content/repositories/orgapachemaven-1234");

        String result = CommandResolver.interpolate(
                "mvn release:prepare -DreleaseVersion=${version} -Dtag=${tag} -DartifactId=${artifactId}", state);

        assertEquals(
                "mvn release:prepare -DreleaseVersion=3.14.0 -Dtag=maven-compiler-plugin-3.14.0"
                        + " -DartifactId=maven-compiler-plugin",
                result);
    }

    @Test
    void interpolatesStagingRepoVars() {
        ReleaseState state = ReleaseState.create("test", "g", "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        state.setStagingRepoId("repo-123");
        state.setStagingRepoUrl("https://repo.example.com/repo-123");

        String result = CommandResolver.interpolate("nexus-close ${stagingRepoId} at ${stagingRepoUrl}", state);
        assertEquals("nexus-close repo-123 at https://repo.example.com/repo-123", result);
    }

    @Test
    void interpolateLeavesUnknownVariablesAlone() {
        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));

        String result = CommandResolver.interpolate("echo ${unknown} and ${version}", state);
        assertEquals("echo ${unknown} and 1.0", result);
    }

    @Test
    void globalOverrideIsUsedWhenNoProjectOverride() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "1.0", ComponentType.PLUGIN, Path.of("/tmp"));

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setOverride(
                "maven-release-prepare-and-perform",
                new CommandOverride(List.of("mvn release:prepare -Pglobal", "mvn release:perform -Pglobal"), null));

        CommandResolver resolver = new CommandResolver(null, globalConfig);
        CommandResolver.ResolvedCommands resolved =
                resolver.resolve(new MavenPrepareAndPerformStep(new CommandRunner()), state);

        assertEquals(List.of("mvn release:prepare -Pglobal", "mvn release:perform -Pglobal"), resolved.commands());
        assertTrue(resolved.source().startsWith("global override"), "source should indicate global override");
    }

    @Test
    void projectOverrideTakesPrecedenceOverGlobalOverride() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "1.0", ComponentType.PLUGIN, Path.of("/tmp"));

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setOverride(
                "maven-release-prepare-and-perform",
                new CommandOverride(List.of("mvn release:prepare -Pglobal"), null));

        ProjectConfig projectConfig = new ProjectConfig("git@example.com/repo.git");
        projectConfig.setOverride(
                "maven-release-prepare-and-perform",
                new CommandOverride(List.of("mvn release:prepare -Pproject"), null));

        CommandResolver resolver = new CommandResolver(projectConfig, globalConfig);
        CommandResolver.ResolvedCommands resolved =
                resolver.resolve(new MavenPrepareAndPerformStep(new CommandRunner()), state);

        assertEquals(List.of("mvn release:prepare -Pproject"), resolved.commands());
        assertTrue(resolved.source().startsWith("project override"), "project override should win over global");
    }

    @Test
    void stepDefaultIsUsedWhenNoOverrideExists() {
        ReleaseState state =
                ReleaseState.create("my-plugin", "org.apache.maven", "2.0", ComponentType.PLUGIN, Path.of("/tmp"));

        CommandResolver resolver = new CommandResolver(null, null);
        CommandResolver.ResolvedCommands resolved =
                resolver.resolve(new MavenPrepareAndPerformStep(new CommandRunner()), state);

        assertTrue(
                resolved.commands().stream().anyMatch(c -> c.contains("release:prepare")),
                "default should include release:prepare");
        assertTrue(
                resolved.commands().stream().anyMatch(c -> c.contains("release:perform")),
                "default should include release:perform");
        assertTrue(resolved.source().startsWith("default"), "source should indicate default");
    }
}
