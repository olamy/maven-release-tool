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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;

public class MavenPerformStep extends AbstractStep {

    public MavenPerformStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "maven-release-perform";
    }

    @Override
    public String describe() {
        return "Run mvn release:perform (checkout tag, deploy to staging)";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of("mvn release:perform");
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        return runCommands(state, commands);
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) {
        List<String> dryRunCommands = new ArrayList<>();
        for (String cmd : commands) {
            if (!cmd.contains("-DdryRun=true")) {
                dryRunCommands.add(cmd + " -DdryRun=true");
            } else {
                dryRunCommands.add(cmd);
            }
        }
        return runCommands(state, dryRunCommands);
    }
}
