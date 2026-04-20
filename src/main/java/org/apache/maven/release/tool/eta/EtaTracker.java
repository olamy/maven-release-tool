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
package org.apache.maven.release.tool.eta;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepState;
import org.apache.maven.release.tool.model.StepStatus;
import org.apache.maven.release.tool.steps.Step;

public class EtaTracker {

    private final EtaHistory history;

    public EtaTracker(EtaHistory history) {
        this.history = history;
    }

    public Duration estimateRemaining(ReleaseState state, List<Step> steps) {
        long totalRemainingSeconds = 0;
        ComponentType type = state.getComponentType();

        for (int i = state.getCurrentStepIndex(); i < steps.size(); i++) {
            StepState stepState = i < state.getSteps().size() ? state.getSteps().get(i) : null;
            if (stepState != null
                    && (stepState.getStatus() == StepStatus.COMPLETED || stepState.getStatus() == StepStatus.SKIPPED)) {
                continue;
            }
            long median = history.getMedianDuration(type, steps.get(i).name());
            if (median > 0) {
                totalRemainingSeconds += median;
            }
        }

        return Duration.ofSeconds(totalRemainingSeconds);
    }

    public Instant estimateCompletion(ReleaseState state, List<Step> steps) {
        Duration remaining = estimateRemaining(state, steps);
        if (remaining.isZero()) {
            return null;
        }
        return Instant.now().plus(remaining);
    }

    public String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        if (totalSeconds <= 0) {
            return "unknown";
        }
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        StringBuilder sb = new StringBuilder("~");
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 && days == 0) {
            sb.append(minutes).append("m ");
        }
        sb.append("remaining");
        return sb.toString().trim();
    }

    public void recordCompletedStep(ReleaseState state, StepState stepState) {
        if (stepState.getDurationSeconds() != null && stepState.getDurationSeconds() > 0) {
            history.recordStepDuration(state.getComponentType(), stepState.getName(), stepState.getDurationSeconds());
        }
    }
}
