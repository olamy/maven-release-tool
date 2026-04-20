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

public class WaitSyncStep extends AbstractStep {

    public WaitSyncStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "wait-sync";
    }

    @Override
    public String describe() {
        return "Wait for Maven Central sync (~4h) and website deployment (~1h)";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of();
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        String groupPath =
                state.getGroupId() != null ? state.getGroupId().replace('.', '/') : "org/apache/maven/plugins";
        String centralUrl = "https://repo.maven.apache.org/maven2/" + groupPath + "/" + state.getArtifactId() + "/"
                + state.getVersion() + "/";

        return StepResult.ok("Waiting for synchronization:\n"
                + "  Maven Central: syncs every ~4 hours from repository.apache.org\n"
                + "  Website: ~1 hour for svnpubsub deployment\n\n"
                + "Check Maven Central availability at:\n"
                + "  " + centralUrl + "\n\n"
                + "Proceed to announcement after confirming availability.");
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) {
        return StepResult.ok("DRY-RUN: Would wait for Maven Central sync (~4h) and website deployment (~1h).");
    }
}
