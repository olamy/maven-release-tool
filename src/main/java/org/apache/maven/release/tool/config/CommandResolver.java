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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.steps.Step;

public class CommandResolver {

    private final ProjectConfig projectConfig;

    public CommandResolver(ProjectConfig projectConfig) {
        this.projectConfig = projectConfig;
    }

    public ResolvedCommands resolve(Step step, ReleaseState state) {
        String source;
        List<String> commands;

        if (projectConfig != null && projectConfig.hasOverride(step.name())) {
            CommandOverride override = projectConfig.getOverride(step.name());
            commands = interpolate(override.commands(), state);
            source = "project override" + (override.reason() != null ? " (" + override.reason() + ")" : "");
        } else {
            commands = step.defaultCommands(state);
            source = "default (" + state.getComponentType() + ")";
        }

        return new ResolvedCommands(commands, source);
    }

    private List<String> interpolate(List<String> commands, ReleaseState state) {
        List<String> result = new ArrayList<>(commands.size());
        for (String cmd : commands) {
            result.add(interpolate(cmd, state));
        }
        return result;
    }

    static String interpolate(String command, ReleaseState state) {
        String result = command;
        if (state.getVersion() != null) {
            result = result.replace("${version}", state.getVersion());
        }
        if (state.getNextVersion() != null) {
            result = result.replace("${nextVersion}", state.getNextVersion());
        }
        if (state.getReleaseTag() != null) {
            result = result.replace("${tag}", state.getReleaseTag());
        }
        if (state.getArtifactId() != null) {
            result = result.replace("${artifactId}", state.getArtifactId());
        }
        if (state.getGroupId() != null) {
            result = result.replace("${groupId}", state.getGroupId());
        }
        if (state.getStagingRepoId() != null) {
            result = result.replace("${stagingRepoId}", state.getStagingRepoId());
        }
        if (state.getStagingRepoUrl() != null) {
            result = result.replace("${stagingRepoUrl}", state.getStagingRepoUrl());
        }
        return result;
    }

    public record ResolvedCommands(List<String> commands, String source) {}
}
