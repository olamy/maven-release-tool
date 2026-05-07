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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.persistence.StateStore;
import org.apache.maven.release.tool.steps.CallVoteStep;
import org.apache.maven.release.tool.steps.CloseStageRepoStep;
import org.apache.maven.release.tool.steps.CopyToDistStep;
import org.apache.maven.release.tool.steps.MavenPrepareAndPerformStep;
import org.apache.maven.release.tool.steps.PreReleaseChecksStep;
import org.apache.maven.release.tool.steps.PromoteArtifactsStep;
import org.apache.maven.release.tool.steps.PublishDocumentationStep;
import org.apache.maven.release.tool.steps.RecordVoteResultStep;
import org.apache.maven.release.tool.steps.ReportReleaseStep;
import org.apache.maven.release.tool.steps.SendAnnouncementStep;
import org.apache.maven.release.tool.steps.StageDocumentationStep;
import org.apache.maven.release.tool.steps.Step;
import org.apache.maven.release.tool.steps.UpdateSiteStep;
import org.apache.maven.release.tool.steps.VerifyDistToolStep;
import org.apache.maven.release.tool.steps.VerifySiteStep;
import org.apache.maven.release.tool.steps.WaitForVoteStep;
import org.apache.maven.release.tool.steps.WaitSyncStep;

public class PipelineBuilder {

    private final CommandRunner runner;
    private final StateStore stateStore;

    public PipelineBuilder(CommandRunner runner, StateStore stateStore) {
        this.runner = runner;
        this.stateStore = stateStore;
    }

    public List<Step> buildPipeline(ComponentType componentType) {
        List<Step> allSteps = allSteps();
        List<Step> applicable = new ArrayList<>();
        for (Step step : allSteps) {
            if (step.isApplicable(componentType)) {
                applicable.add(step);
            }
        }
        return applicable;
    }

    private List<Step> allSteps() {
        return List.of(
                // Pre-release
                new PreReleaseChecksStep(runner),
                new VerifySiteStep(runner),
                // Release plugin delegation
                new MavenPrepareAndPerformStep(runner),
                new CloseStageRepoStep(runner),
                // Documentation
                new StageDocumentationStep(runner),
                // Vote
                new CallVoteStep(runner, stateStore),
                new WaitForVoteStep(runner),
                new RecordVoteResultStep(runner, stateStore),
                // Post-vote promotion
                new CopyToDistStep(runner),
                new ReportReleaseStep(runner),
                new PromoteArtifactsStep(runner),
                new PublishDocumentationStep(runner),
                new UpdateSiteStep(runner),
                new WaitSyncStep(runner),
                new SendAnnouncementStep(runner, stateStore),
                new VerifyDistToolStep(runner));
    }
}
