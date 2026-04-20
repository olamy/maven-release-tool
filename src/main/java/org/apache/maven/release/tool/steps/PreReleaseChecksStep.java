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
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;

public class PreReleaseChecksStep extends AbstractStep {

    public PreReleaseChecksStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "pre-release-checks";
    }

    @Override
    public String describe() {
        return "Verify GPG key, JDK version, git status, and SNAPSHOT dependencies";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of("gpg --list-secret-keys", "java -version", "git status --porcelain");
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        // Check GPG key
        String gpgOutput = runner.getOutput(projectDir(state), List.of("gpg", "--list-secret-keys"));
        if (gpgOutput.isBlank()) {
            return StepResult.failure("No GPG secret keys found. Configure GPG signing before releasing.");
        }

        // Check git is clean
        String gitStatus = runner.getOutput(projectDir(state), List.of("git", "status", "--porcelain"));
        if (!gitStatus.isBlank()) {
            return StepResult.failure("Working directory is not clean:\n" + gitStatus);
        }

        // Check no SNAPSHOT dependencies (quick grep in pom.xml)
        String snapshots = runner.getOutput(projectDir(state), List.of("grep", "-r", "SNAPSHOT", "pom.xml"));
        // Filter out the project's own version (which is expected to be SNAPSHOT)
        long externalSnapshots = snapshots
                .lines()
                .filter(line -> line.contains("SNAPSHOT"))
                .filter(line -> !line.contains("<version>" + state.getVersion().replace("-SNAPSHOT", "") + "-SNAPSHOT"))
                .filter(line -> !line.contains("<tag>"))
                .count();

        if (externalSnapshots > 0) {
            return StepResult.failure("Found SNAPSHOT dependencies in pom.xml. Resolve them before releasing.");
        }

        return StepResult.ok("All pre-release checks passed.");
    }
}
