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
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;
import org.apache.maven.release.tool.persistence.StateStore;

public class RecordVoteResultStep extends AbstractStep {

    private final StateStore stateStore;

    public RecordVoteResultStep(CommandRunner runner, StateStore stateStore) {
        super(runner);
        this.stateStore = stateStore;
    }

    @Override
    public String name() {
        return "record-vote-result";
    }

    @Override
    public String describe() {
        return "Generate vote result email and save to release directory";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of();
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        String email = generateResultEmail(state);

        try {
            stateStore.writeArtifact(state, "vote-result-email.txt", email);
        } catch (IOException e) {
            return StepResult.failure("Failed to save vote result email: " + e.getMessage());
        }

        return StepResult.ok("Vote result email saved to release directory (vote-result-email.txt).\n"
                + "Review and send to dev@maven.apache.org (cc: private@maven.apache.org).\n\n"
                + email);
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) {
        return StepResult.ok("DRY-RUN: Would generate vote result email:\n\n" + generateResultEmail(state));
    }

    private String generateResultEmail(ReleaseState state) {
        String componentName = state.getArtifactId().replace("maven-", "").replace("-plugin", " Plugin");
        componentName = Character.toUpperCase(componentName.charAt(0)) + componentName.substring(1);

        StringBuilder sb = new StringBuilder();
        sb.append("To: dev@maven.apache.org\n");
        sb.append("Cc: private@maven.apache.org\n");
        sb.append("Subject: [RESULT] [VOTE] Release Apache ")
                .append(componentName)
                .append(" ")
                .append(state.getVersion())
                .append("\n\n");

        sb.append("Hi,\n\n");
        sb.append("The vote has passed with the following result:\n\n");

        sb.append("+1 (binding): <list PMC members who voted +1>\n");
        sb.append("+1 (non-binding): <list non-PMC who voted +1>\n\n");

        sb.append("PMC quorum: reached\n\n");

        sb.append("I will promote the source release zip file to the Apache distribution area\n");
        sb.append("and the artifacts to the central repo.\n\n");

        sb.append("-The Apache Maven team\n");

        return sb.toString();
    }
}
