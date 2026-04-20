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

import java.io.IOException;
import java.util.List;

import org.apache.maven.release.tool.config.CommandOverrideStore;
import org.apache.maven.release.tool.config.CommandResolver;
import org.apache.maven.release.tool.config.ProjectConfig;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;
import org.apache.maven.release.tool.model.StepState;
import org.apache.maven.release.tool.persistence.StateStore;
import org.apache.maven.release.tool.steps.Step;

public class ReleasePipeline {

    private final List<Step> steps;
    private final ReleaseState state;
    private final StateStore stateStore;
    private final CommandResolver commandResolver;
    private final CommandOverrideStore overrideStore;
    private final ProjectConfig projectConfig;

    public ReleasePipeline(
            List<Step> steps,
            ReleaseState state,
            StateStore stateStore,
            CommandOverrideStore overrideStore,
            ProjectConfig projectConfig) {
        this.steps = steps;
        this.state = state;
        this.stateStore = stateStore;
        this.overrideStore = overrideStore;
        this.projectConfig = projectConfig;
        this.commandResolver = new CommandResolver(projectConfig);

        if (state.getSteps().isEmpty()) {
            for (Step step : steps) {
                state.getSteps().add(new StepState(step.name()));
            }
        }
    }

    public List<Step> getSteps() {
        return steps;
    }

    public ReleaseState getState() {
        return state;
    }

    public Step getCurrentStep() {
        int idx = state.getCurrentStepIndex();
        if (idx >= 0 && idx < steps.size()) {
            return steps.get(idx);
        }
        return null;
    }

    public StepState getCurrentStepState() {
        return state.getCurrentStep();
    }

    public CommandResolver.ResolvedCommands resolveCurrentCommands() {
        Step step = getCurrentStep();
        if (step == null) {
            return new CommandResolver.ResolvedCommands(List.of(), "none");
        }
        return commandResolver.resolve(step, state);
    }

    public StepResult executeCurrentStep(List<String> confirmedCommands) throws IOException {
        Step step = getCurrentStep();
        StepState stepState = getCurrentStepState();
        if (step == null || stepState == null) {
            return StepResult.abort("No more steps to execute.");
        }

        stepState.markStarted();
        stepState.setCommands(confirmedCommands);
        stateStore.save(state);

        StepResult result;
        if (state.isDryRun()) {
            result = step.dryRun(state, confirmedCommands);
        } else {
            result = step.execute(state, confirmedCommands);
        }

        if (result.succeeded()) {
            if (result.suggestedAction() == StepResult.Action.PAUSE) {
                stepState.markWaiting();
            } else {
                stepState.markCompleted();
                state.advanceToNextStep();
            }
        } else {
            stepState.markFailed();
        }

        stateStore.save(state);
        return result;
    }

    public StepResult dryRunCurrentStep() throws IOException {
        Step step = getCurrentStep();
        if (step == null) {
            return StepResult.abort("No more steps.");
        }
        CommandResolver.ResolvedCommands resolved = resolveCurrentCommands();
        return step.dryRun(state, resolved.commands());
    }

    public void skipCurrentStep() throws IOException {
        StepState stepState = getCurrentStepState();
        if (stepState != null) {
            stepState.markSkipped();
            state.advanceToNextStep();
            stateStore.save(state);
        }
    }

    public boolean hasMoreSteps() {
        return state.getCurrentStepIndex() < steps.size() && !state.isComplete();
    }

    public void save() throws IOException {
        stateStore.save(state);
    }
}
