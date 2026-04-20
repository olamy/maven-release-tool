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

import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.integration.NexusClient;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;

public class CloseStageRepoStep extends AbstractStep {

    public CloseStageRepoStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "close-staging-repo";
    }

    @Override
    public String describe() {
        return "Close staging repository on repository.apache.org (Nexus)";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        String repoId = state.getStagingRepoId() != null ? state.getStagingRepoId() : "<staging-repo-id>";
        return List.of("nexus-close " + repoId + " \"VOTE " + state.getArtifactId() + " " + state.getVersion() + "\"");
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        String repoId = state.getStagingRepoId();
        if (repoId == null || repoId.isBlank()) {
            return StepResult.failure(
                    "Staging repository ID not set. Set it manually with the resume command or check release:perform output.");
        }

        String description = "VOTE " + state.getArtifactId() + " " + state.getVersion();

        String username = System.getenv("NEXUS_USERNAME");
        String password = System.getenv("NEXUS_PASSWORD");
        if (username == null || password == null) {
            return StepResult.failure(
                    "NEXUS_USERNAME and NEXUS_PASSWORD environment variables must be set for Nexus API access.");
        }

        try (NexusClient nexus = new NexusClient(username, password)) {
            nexus.closeStagingRepo(repoId, description);
            return StepResult.ok("Staging repository " + repoId + " closed with description: " + description);
        } catch (Exception e) {
            return StepResult.failure("Failed to close staging repository: " + e.getMessage());
        }
    }
}
