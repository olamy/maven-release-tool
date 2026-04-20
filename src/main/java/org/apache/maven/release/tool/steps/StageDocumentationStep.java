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

public class StageDocumentationStep extends AbstractStep {

    public StageDocumentationStep(CommandRunner runner) {
        super(runner);
    }

    @Override
    public String name() {
        return "stage-documentation";
    }

    @Override
    public String describe() {
        return "Build and stage component reference documentation";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        if (state.getComponentType() == ComponentType.PARENT_POM) {
            return List.of("mvn -f docs site site:stage", "mvn -f docs scm-publish:publish-scm");
        }
        return List.of("mvn -Preporting site site:stage", "mvn scm-publish:publish-scm");
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        return runCommands(state, commands);
    }
}
