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

import java.io.IOException;
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;
import org.apache.maven.release.tool.persistence.StateStore;

public class CallVoteStep extends AbstractStep {

    private final StateStore stateStore;

    public CallVoteStep(CommandRunner runner, StateStore stateStore) {
        super(runner);
        this.stateStore = stateStore;
    }

    @Override
    public String name() {
        return "call-vote";
    }

    @Override
    public String describe() {
        return "Generate vote email and save to release directory";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of();
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        String email = generateVoteEmail(state);

        try {
            stateStore.writeArtifact(state, "vote-email.txt", email);
        } catch (IOException e) {
            return StepResult.failure("Failed to save vote email: " + e.getMessage());
        }

        return StepResult.ok("Vote email saved to release directory (vote-email.txt).\n"
                + "Review and send it to dev@maven.apache.org.\n\n"
                + email);
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) {
        String email = generateVoteEmail(state);
        return StepResult.ok("DRY-RUN: Would generate vote email:\n\n" + email);
    }

    private String generateVoteEmail(ReleaseState state) {
        String componentName = formatComponentName(state);
        String stagingUrl = state.getStagingRepoUrl() != null
                ? state.getStagingRepoUrl()
                : "https://repository.apache.org/content/repositories/<staging-repo-id>";

        StringBuilder sb = new StringBuilder();
        sb.append("To: dev@maven.apache.org\n");
        sb.append("Subject: [VOTE] Release Apache ").append(componentName).append("\n\n");

        sb.append("Hi,\n\n");
        sb.append("We solved N issues:\n");
        sb.append("https://issues.apache.org/jira/secure/ReleaseNote.jspa?projectId=12317828&version=TODO\n\n");

        sb.append("There are still a couple of issues left in JIRA:\n");
        sb.append("https://issues.apache.org/jira/issues/?jql=TODO\n\n");

        sb.append("Staging repo:\n");
        sb.append(stagingUrl).append("\n");
        sb.append(stagingUrl)
                .append("/org/apache/maven/")
                .append(state.getComponentType() == ComponentType.PLUGIN ? "plugins/" : "")
                .append(state.getArtifactId())
                .append("/")
                .append(state.getVersion())
                .append("/")
                .append(state.getArtifactId())
                .append("-")
                .append(state.getVersion())
                .append("-source-release.zip\n\n");

        sb.append("Source release checksum(s):\n");
        sb.append(state.getArtifactId())
                .append("-")
                .append(state.getVersion())
                .append("-source-release.zip sha512: <SHA512>\n\n");

        sb.append("Staging site:\n");
        sb.append("https://maven.apache.org/")
                .append(getSiteCategory(state))
                .append("-archives/")
                .append(state.getArtifactId())
                .append("-LATEST/\n\n");

        sb.append("Guide to testing staged releases:\n");
        sb.append("https://maven.apache.org/guides/development/guide-testing-releases.html\n\n");

        sb.append("Vote open for at least 72 hours.\n\n");

        sb.append("[ ] +1\n");
        sb.append("[ ] +0\n");
        sb.append("[ ] -1\n\n");

        sb.append("-The Apache Maven team\n");

        return sb.toString();
    }

    private String formatComponentName(ReleaseState state) {
        String name = state.getArtifactId().replace("maven-", "").replace("-plugin", " Plugin");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + state.getVersion();
    }

    private String getSiteCategory(ReleaseState state) {
        return switch (state.getComponentType()) {
            case PLUGIN -> "plugins";
            case SHARED -> "shared";
            case PARENT_POM -> "pom";
            case SKIN -> "skins";
            default -> "ref";
        };
    }
}
