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
package org.apache.maven.release.tool.model;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseStateTest {

    @Test
    void createSetsFields() {
        ReleaseState state = ReleaseState.create(
                "maven-compiler-plugin", "org.apache.maven.plugins", "3.14.0", ComponentType.PLUGIN, Path.of("/tmp"));

        assertEquals("maven-compiler-plugin", state.getArtifactId());
        assertEquals("org.apache.maven.plugins", state.getGroupId());
        assertEquals("3.14.0", state.getVersion());
        assertEquals(ComponentType.PLUGIN, state.getComponentType());
        assertEquals(0, state.getCurrentStepIndex());
        assertNotNull(state.getStartedAt());
    }

    @Test
    void releaseIdCombinesArtifactAndVersion() {
        ReleaseState state =
                ReleaseState.create("maven-compiler-plugin", null, "3.14.0", ComponentType.PLUGIN, Path.of("/tmp"));
        assertEquals("maven-compiler-plugin-3.14.0", state.getReleaseId());
    }

    @Test
    void advanceToNextStepIncrements() {
        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        state.getSteps().add(new StepState("step-1"));
        state.getSteps().add(new StepState("step-2"));
        state.getSteps().add(new StepState("step-3"));

        assertEquals(0, state.getCurrentStepIndex());
        state.advanceToNextStep();
        assertEquals(1, state.getCurrentStepIndex());
        state.advanceToNextStep();
        assertEquals(2, state.getCurrentStepIndex());
        // Should not go past the last step
        state.advanceToNextStep();
        assertEquals(2, state.getCurrentStepIndex());
    }

    @Test
    void completedStepCountTracksCompletedAndSkipped() {
        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));

        StepState s1 = new StepState("step-1");
        s1.markCompleted();
        StepState s2 = new StepState("step-2");
        s2.markSkipped();
        StepState s3 = new StepState("step-3");

        state.getSteps().add(s1);
        state.getSteps().add(s2);
        state.getSteps().add(s3);

        assertEquals(2, state.completedStepCount());
    }

    @Test
    void isCompleteWhenLastStepDone() {
        ReleaseState state = ReleaseState.create("test", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));

        StepState s1 = new StepState("step-1");
        s1.markCompleted();
        StepState s2 = new StepState("step-2");

        state.getSteps().add(s1);
        state.getSteps().add(s2);
        state.setCurrentStepIndex(1);

        assertFalse(state.isComplete());
        s2.markCompleted();
        assertTrue(state.isComplete());
    }
}
