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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepState;
import org.apache.maven.release.tool.persistence.StateStore;
import org.apache.maven.release.tool.pipeline.PipelineBuilder;
import org.apache.maven.release.tool.steps.Step;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaTrackerTest {

    @TempDir
    Path tempDir;

    @Test
    void estimateRemainingWithNoHistory() {
        EtaHistory history = new EtaHistory(tempDir);
        EtaTracker tracker = new EtaTracker(history);

        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        PipelineBuilder builder = new PipelineBuilder(new CommandRunner(), new StateStore(tempDir));
        List<Step> steps = builder.buildPipeline(ComponentType.PLUGIN);

        Duration remaining = tracker.estimateRemaining(state, steps);
        assertEquals(Duration.ZERO, remaining);
    }

    @Test
    void estimateRemainingUsesHistoricalData() {
        EtaHistory history = new EtaHistory(tempDir);
        history.recordStepDuration(ComponentType.PLUGIN, "pre-release-checks", 120);
        history.recordStepDuration(ComponentType.PLUGIN, "maven-release-prepare", 300);

        EtaTracker tracker = new EtaTracker(history);

        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        state.getSteps().add(new StepState("pre-release-checks"));
        state.getSteps().add(new StepState("maven-release-prepare"));

        PipelineBuilder builder = new PipelineBuilder(new CommandRunner(), new StateStore(tempDir));
        List<Step> steps = builder.buildPipeline(ComponentType.PLUGIN);

        Duration remaining = tracker.estimateRemaining(state, steps);
        assertTrue(remaining.getSeconds() > 0, "Should have non-zero estimate with history data");
    }

    @Test
    void formatDurationShowsDaysHoursMinutes() {
        EtaTracker tracker = new EtaTracker(new EtaHistory(tempDir));

        assertEquals("~3d 4h remaining", tracker.formatDuration(Duration.ofHours(76)));
        assertEquals("~2h 30m remaining", tracker.formatDuration(Duration.ofMinutes(150)));
        assertEquals("~5m remaining", tracker.formatDuration(Duration.ofMinutes(5)));
        assertEquals("unknown", tracker.formatDuration(Duration.ZERO));
    }

    @Test
    void historyPersistsAcrossSaveLoad() throws IOException {
        EtaHistory history = new EtaHistory(tempDir);
        history.recordStepDuration(ComponentType.PLUGIN, "pre-release-checks", 120);
        history.save();

        EtaHistory loaded = new EtaHistory(tempDir);
        loaded.load();

        assertEquals(120, loaded.getMedianDuration(ComponentType.PLUGIN, "pre-release-checks"));
    }
}
