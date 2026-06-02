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

public class CopyToDistStep extends AbstractStep {

    private static final String DIST_RELEASE = "https://dist.apache.org/repos/dist/release/maven";
    private static final String DIST_DEV = "https://dist.apache.org/repos/dist/dev/maven";

    public CopyToDistStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "copy-to-dist";
    }

    @Override
    public String describe() {
        return "Copy source release to Apache distribution area (dist.apache.org)";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        if (state.getComponentType() == ComponentType.CORE) {
            return List.of("svn mv " + DIST_DEV + "/maven-3/" + state.getVersion()
                    + " " + DIST_RELEASE + "/maven-3"
                    + " -m \"Publish Maven " + state.getVersion() + " Distribution Archives\"");
        }

        String distCategory = getDistCategory(state);
        String stagingRepoUrl = state.getStagingRepoUrl() != null ? state.getStagingRepoUrl() : "<staging-repo-url>";
        String groupPath = state.getGroupId() != null ? state.getGroupId().replace('.', '/') : "<group-id>";

        return List.of("svn import " + stagingRepoUrl + "/" + groupPath + "/"
                + state.getArtifactId() + "/" + state.getVersion() + "/"
                + state.getArtifactId() + "-" + state.getVersion() + "-source-release.zip"
                + " " + DIST_RELEASE + "/" + distCategory + "/"
                + state.getArtifactId() + "-" + state.getVersion() + "-source-release.zip"
                + " -m \"Copy " + state.getArtifactId() + " " + state.getVersion() + " source release\"");
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        return runCommands(state, commands);
    }

    private String getDistCategory(ReleaseState state) {
        return switch (state.getComponentType()) {
            case PLUGIN -> "plugins";
            case SHARED -> "shared";
            case PARENT_POM -> "pom";
            case SKIN -> "skins";
            default -> "misc";
        };
    }
}
