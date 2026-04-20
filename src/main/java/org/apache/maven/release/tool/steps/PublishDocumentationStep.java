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

public class PublishDocumentationStep extends AbstractStep {

    private static final String SVNPUBSUB = "https://svn.apache.org/repos/asf/maven/website/components";

    public PublishDocumentationStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "publish-documentation";
    }

    @Override
    public String describe() {
        return "Publish versioned component reference documentation (svn cp/rm)";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        if (state.getComponentType() == ComponentType.CORE) {
            return List.of("svn cp " + SVNPUBSUB + "/ref/3-LATEST"
                    + " " + SVNPUBSUB + "/ref/" + state.getVersion()
                    + " -m \"Maven " + state.getVersion() + " released\"");
        }

        String category = getSiteCategory(state);
        String artifactId = state.getArtifactId();
        String version = state.getVersion();

        return List.of(
                "svn cp " + SVNPUBSUB + "/" + category + "-archives/" + artifactId + "-LATEST"
                        + " " + SVNPUBSUB + "/" + category + "-archives/" + artifactId + "-" + version
                        + " -m \"Archive " + artifactId + " " + version + " documentation\"",
                "svn rm " + SVNPUBSUB + "/" + category + "/" + artifactId + " -m \"Remove old " + artifactId
                        + " site\"",
                "svn cp " + SVNPUBSUB + "/" + category + "-archives/" + artifactId + "-LATEST"
                        + " " + SVNPUBSUB + "/" + category + "/" + artifactId
                        + " -m \"Publish " + artifactId + " " + version + " site\"");
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        return runCommands(state, commands);
    }

    @Override
    public boolean isApplicable(ComponentType type) {
        return type != ComponentType.EXTENSION;
    }

    private String getSiteCategory(ReleaseState state) {
        return switch (state.getComponentType()) {
            case PLUGIN -> "plugins";
            case SHARED -> "shared";
            case PARENT_POM -> "pom";
            case SKIN -> "skins";
            default -> "plugins";
        };
    }
}
