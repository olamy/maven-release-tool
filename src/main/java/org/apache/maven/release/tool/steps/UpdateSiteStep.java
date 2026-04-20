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
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;

public class UpdateSiteStep extends AbstractStep {

    public UpdateSiteStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "update-site";
    }

    @Override
    public String describe() {
        return "Update website index tables (plugins/shared index.apt or core version properties)";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        if (state.getComponentType() == ComponentType.CORE) {
            return List.of(
                    "echo \"Update versions3x, currentStableVersion in maven-site pom.xml and history.md.vm manually\"");
        }
        if (state.getComponentType() == ComponentType.PLUGIN || state.getComponentType() == ComponentType.SHARED) {
            return List.of("mvn -Pupdate package");
        }
        return List.of();
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        if (state.getComponentType() == ComponentType.CORE) {
            return StepResult.ok("Manual step: Update versions3x, currentStableVersion, currentStableVersionDetails\n"
                    + "in maven-site pom.xml and update content/markdown/docs/history.md.vm.\n"
                    + "Then commit and deploy the site.");
        }
        return runCommands(state, commands);
    }

    @Override
    public boolean isApplicable(ComponentType type) {
        return type == ComponentType.CORE || type == ComponentType.PLUGIN || type == ComponentType.SHARED;
    }
}
