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

import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
