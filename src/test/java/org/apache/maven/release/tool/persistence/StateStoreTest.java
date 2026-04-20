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
package org.apache.maven.release.tool.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        StateStore store = new StateStore(tempDir);
        ReleaseState state = ReleaseState.create(
                "maven-compiler-plugin",
                "org.apache.maven.plugins",
                "3.14.0",
                ComponentType.PLUGIN,
                Path.of("/tmp/project"));
        state.setReleaseTag("maven-compiler-plugin-3.14.0");
        state.setNextVersion("3.14.1-SNAPSHOT");
        state.getSteps().add(new StepState("pre-release-checks"));
        state.getSteps().add(new StepState("maven-release-prepare"));

        store.save(state);

        ReleaseState loaded = store.load("maven-compiler-plugin-3.14.0");
        assertNotNull(loaded);
        assertEquals("maven-compiler-plugin", loaded.getArtifactId());
        assertEquals("3.14.0", loaded.getVersion());
        assertEquals(ComponentType.PLUGIN, loaded.getComponentType());
        assertEquals("maven-compiler-plugin-3.14.0", loaded.getReleaseTag());
        assertEquals("3.14.1-SNAPSHOT", loaded.getNextVersion());
        assertEquals(2, loaded.getSteps().size());
        assertEquals("pre-release-checks", loaded.getSteps().get(0).getName());
    }

    @Test
    void loadReturnsNullForMissing() throws IOException {
        StateStore store = new StateStore(tempDir);
        assertNull(store.load("nonexistent-1.0"));
    }

    @Test
    void loadByArtifactAndVersion() throws IOException {
        StateStore store = new StateStore(tempDir);
        ReleaseState state = ReleaseState.create("test-plugin", null, "2.0", ComponentType.PLUGIN, Path.of("/tmp"));
        store.save(state);

        ReleaseState loaded = store.load("test-plugin", "2.0");
        assertNotNull(loaded);
        assertEquals("test-plugin", loaded.getArtifactId());
    }

    @Test
    void listAllFindsAllReleases() throws IOException {
        StateStore store = new StateStore(tempDir);

        store.save(ReleaseState.create("plugin-a", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp")));
        store.save(ReleaseState.create("plugin-b", null, "2.0", ComponentType.SHARED, Path.of("/tmp")));

        List<ReleaseState> all = store.listAll();
        assertEquals(2, all.size());
    }

    @Test
    void listAllReturnsEmptyWhenNoReleases() throws IOException {
        StateStore store = new StateStore(tempDir);
        List<ReleaseState> all = store.listAll();
        assertTrue(all.isEmpty());
    }

    @Test
    void deleteRemovesReleaseDir() throws IOException {
        StateStore store = new StateStore(tempDir);
        ReleaseState state = ReleaseState.create("to-delete", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        store.save(state);

        assertNotNull(store.load("to-delete-1.0"));
        store.delete("to-delete-1.0");
        assertNull(store.load("to-delete-1.0"));
    }

    @Test
    void writeArtifactCreatesFile() throws IOException {
        StateStore store = new StateStore(tempDir);
        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        store.save(state);

        store.writeArtifact(state, "vote-email.txt", "Subject: [VOTE] Test\n\nBody here");

        Path emailFile = store.getReleaseDir(state).resolve("vote-email.txt");
        assertTrue(Files.exists(emailFile));
        assertEquals("Subject: [VOTE] Test\n\nBody here", Files.readString(emailFile));
    }

    @Test
    void appendToCommandsLogAccumulates() throws IOException {
        StateStore store = new StateStore(tempDir);
        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        store.save(state);

        store.appendToCommandsLog(state, "$ mvn release:prepare");
        store.appendToCommandsLog(state, "$ mvn release:perform");

        Path logFile = store.getReleaseDir(state).resolve("commands.log");
        String content = Files.readString(logFile);
        assertTrue(content.contains("$ mvn release:prepare"));
        assertTrue(content.contains("$ mvn release:perform"));
    }
}
