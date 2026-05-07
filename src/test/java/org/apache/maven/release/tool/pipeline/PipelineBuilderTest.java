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
package org.apache.maven.release.tool.pipeline;

import java.nio.file.Path;
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.persistence.StateStore;
import org.apache.maven.release.tool.steps.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void pluginPipelineContainsExpectedSteps() {
        PipelineBuilder builder = new PipelineBuilder(new CommandRunner(), new StateStore(tempDir));
        List<Step> steps = builder.buildPipeline(ComponentType.PLUGIN);

        List<String> names = steps.stream().map(Step::name).toList();

        assertTrue(names.contains("pre-release-checks"));
        assertTrue(names.contains("maven-release-prepare-and-perform"));
        assertTrue(names.contains("call-vote"));
        assertTrue(names.contains("wait-for-vote"));
        assertTrue(names.contains("promote-artifacts"));
        assertTrue(names.contains("publish-documentation"));
        assertTrue(names.contains("update-site"));
        assertTrue(names.contains("send-announcement"));
    }

    @Test
    void pluginPipelineExcludesCoreOnlySteps() {
        PipelineBuilder builder = new PipelineBuilder(new CommandRunner(), new StateStore(tempDir));
        List<Step> steps = builder.buildPipeline(ComponentType.PLUGIN);
        List<String> names = steps.stream().map(Step::name).toList();

        // update-site is applicable to PLUGIN, so it should be present
        assertTrue(names.contains("update-site"));
    }

    @Test
    void skinPipelineExcludesUpdateSite() {
        PipelineBuilder builder = new PipelineBuilder(new CommandRunner(), new StateStore(tempDir));
        List<Step> steps = builder.buildPipeline(ComponentType.SKIN);
        List<String> names = steps.stream().map(Step::name).toList();

        assertTrue(!names.contains("update-site"), "SKIN should not have update-site step");
    }

    @Test
    void allComponentTypesProduceNonEmptyPipeline() {
        PipelineBuilder builder = new PipelineBuilder(new CommandRunner(), new StateStore(tempDir));
        for (ComponentType type : ComponentType.values()) {
            List<Step> steps = builder.buildPipeline(type);
            assertTrue(
                    steps.size() >= 5, "Pipeline for " + type + " should have at least 5 steps, got " + steps.size());
        }
    }
}
