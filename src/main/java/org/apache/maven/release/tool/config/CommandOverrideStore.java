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
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class CommandOverrideStore {

    private static final String COMMANDS_FILE = "commands.json";

    private final Path projectsDir;
    private final ObjectMapper mapper;

    public CommandOverrideStore(Path baseDir) {
        this.projectsDir = baseDir.resolve("projects");
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ProjectConfig load(String gitRemoteUrl) throws IOException {
        Path configFile = getProjectDir(gitRemoteUrl).resolve(COMMANDS_FILE);
        if (!Files.exists(configFile)) {
            return new ProjectConfig(gitRemoteUrl);
        }
        return mapper.readValue(configFile.toFile(), ProjectConfig.class);
    }

    public void save(ProjectConfig config) throws IOException {
        Path projectDir = getProjectDir(config.getGitRemoteUrl());
        Files.createDirectories(projectDir);
        mapper.writeValue(projectDir.resolve(COMMANDS_FILE).toFile(), config);
    }

    private Path getProjectDir(String gitRemoteUrl) {
        return projectsDir.resolve(toDirectoryName(gitRemoteUrl));
    }

    static String toDirectoryName(String gitRemoteUrl) {
        if (gitRemoteUrl == null || gitRemoteUrl.isBlank()) {
            return "unknown";
        }
        String name = gitRemoteUrl;
        // strip protocol
        int schemeEnd = name.indexOf("://");
        if (schemeEnd >= 0) {
            name = name.substring(schemeEnd + 3);
        }
        // strip host prefix for known Apache hosts
        name = name.replaceFirst("^(gitbox\\.apache\\.org/repos/asf/|github\\.com/apache/)", "");
        // strip .git suffix
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        // prefix with "apache-" for clarity
        if (!name.startsWith("apache-")) {
            name = "apache-" + name;
        }
        // sanitize remaining path separators
        name = name.replace('/', '-');
        return name;
    }
}
