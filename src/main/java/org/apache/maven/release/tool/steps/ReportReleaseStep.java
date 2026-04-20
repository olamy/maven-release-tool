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

public class ReportReleaseStep extends AbstractStep {

    public ReportReleaseStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "report-release";
    }

    @Override
    public String describe() {
        return "Report release to reporter.apache.org";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of();
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        return StepResult.ok("Manual step: Visit https://reporter.apache.org/addrelease.html?maven\n"
                + "and submit:\n"
                + "  Full Version Name: " + state.getArtifactId() + " " + state.getVersion() + "\n"
                + "  Date of Release: today\n\n"
                + "If you are not a PMC member, ask a PMC member to do this.");
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) {
        return StepResult.ok("DRY-RUN: Would report release to reporter.apache.org.");
    }
}
