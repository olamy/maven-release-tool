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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.maven.release.tool.model.ReleaseState;

public class StateStore {

    private static final String RELEASE_STATE_FILE = "release-state.json";

    private final Path baseDir;
    private final ObjectMapper mapper;

    public StateStore(Path baseDir) {
        this.baseDir = baseDir;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public StateStore() {
        this(defaultBaseDir());
    }

    private static Path defaultBaseDir() {
        return Path.of(System.getProperty("user.home"), ".maven", "release-tool");
    }

    public Path getBaseDir() {
        return baseDir;
    }

    public Path getReleaseDir(ReleaseState state) {
        return baseDir.resolve("releases").resolve(state.getReleaseId());
    }

    public Path getReleaseDir(String releaseId) {
        return baseDir.resolve("releases").resolve(releaseId);
    }

    public void save(ReleaseState state) throws IOException {
        Path releaseDir = getReleaseDir(state);
        Files.createDirectories(releaseDir);
        Path stateFile = releaseDir.resolve(RELEASE_STATE_FILE);
        mapper.writeValue(stateFile.toFile(), state);
    }

    public ReleaseState load(String releaseId) throws IOException {
        Path stateFile = getReleaseDir(releaseId).resolve(RELEASE_STATE_FILE);
        if (!Files.exists(stateFile)) {
            return null;
        }
        return mapper.readValue(stateFile.toFile(), ReleaseState.class);
    }

    public ReleaseState load(String artifactId, String version) throws IOException {
        return load(artifactId + "-" + version);
    }

    public List<ReleaseState> listAll() throws IOException {
        Path releasesDir = baseDir.resolve("releases");
        List<ReleaseState> releases = new ArrayList<>();
        if (!Files.exists(releasesDir)) {
            return releases;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(releasesDir)) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir)) {
                    Path stateFile = dir.resolve(RELEASE_STATE_FILE);
                    if (Files.exists(stateFile)) {
                        releases.add(mapper.readValue(stateFile.toFile(), ReleaseState.class));
                    }
                }
            }
        }
        return releases;
    }

    public void delete(String releaseId) throws IOException {
        Path releaseDir = getReleaseDir(releaseId);
        if (Files.exists(releaseDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(releaseDir)) {
                for (Path file : stream) {
                    if (Files.isDirectory(file)) {
                        try (DirectoryStream<Path> subStream = Files.newDirectoryStream(file)) {
                            for (Path subFile : subStream) {
                                Files.delete(subFile);
                            }
                        }
                        Files.delete(file);
                    } else {
                        Files.delete(file);
                    }
                }
            }
            Files.delete(releaseDir);
        }
    }

    public void writeArtifact(ReleaseState state, String filename, String content) throws IOException {
        Path releaseDir = getReleaseDir(state);
        Files.createDirectories(releaseDir);
        Files.writeString(releaseDir.resolve(filename), content);
    }

    public void appendToCommandsLog(ReleaseState state, String entry) throws IOException {
        Path releaseDir = getReleaseDir(state);
        Files.createDirectories(releaseDir);
        Path logFile = releaseDir.resolve("commands.log");
        Files.writeString(
                logFile,
                entry + "\n",
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    public void writeStepOutput(ReleaseState state, String stepName, String output) throws IOException {
        Path outputDir = getReleaseDir(state).resolve("output");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve(stepName + ".log"), output);
    }
}
