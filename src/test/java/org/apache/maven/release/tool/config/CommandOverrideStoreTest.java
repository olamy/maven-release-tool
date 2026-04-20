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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandOverrideStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void toDirectoryNameStripsGitboxUrl() {
        assertEquals(
                "apache-maven-compiler-plugin",
                CommandOverrideStore.toDirectoryName("https://gitbox.apache.org/repos/asf/maven-compiler-plugin.git"));
    }

    @Test
    void toDirectoryNameStripsGithubUrl() {
        assertEquals(
                "apache-maven-surefire",
                CommandOverrideStore.toDirectoryName("https://github.com/apache/maven-surefire.git"));
    }

    @Test
    void toDirectoryNameHandlesAlreadyPrefixed() {
        assertEquals(
                "apache-maven",
                CommandOverrideStore.toDirectoryName("https://gitbox.apache.org/repos/asf/apache-maven.git"));
    }

    @Test
    void toDirectoryNameHandlesNull() {
        assertEquals("unknown", CommandOverrideStore.toDirectoryName(null));
        assertEquals("unknown", CommandOverrideStore.toDirectoryName(""));
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        CommandOverrideStore store = new CommandOverrideStore(tempDir);
        String gitUrl = "https://gitbox.apache.org/repos/asf/maven-compiler-plugin.git";

        ProjectConfig config = new ProjectConfig(gitUrl);
        config.setOverride(
                "verify-site", new CommandOverride(List.of("mvn -Preporting site site:stage -pl !its"), "Skip ITs"));

        store.save(config);

        ProjectConfig loaded = store.load(gitUrl);
        assertTrue(loaded.hasOverride("verify-site"));
        assertEquals("Skip ITs", loaded.getOverride("verify-site").reason());
        assertEquals(1, loaded.getOverride("verify-site").commands().size());
        assertEquals(
                "mvn -Preporting site site:stage -pl !its",
                loaded.getOverride("verify-site").commands().get(0));
    }

    @Test
    void loadReturnsEmptyConfigForNewProject() throws IOException {
        CommandOverrideStore store = new CommandOverrideStore(tempDir);
        ProjectConfig config = store.load("https://github.com/apache/maven-clean-plugin.git");
        assertFalse(config.hasOverride("anything"));
    }
}
