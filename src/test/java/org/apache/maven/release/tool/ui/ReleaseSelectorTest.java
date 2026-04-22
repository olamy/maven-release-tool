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
package org.apache.maven.release.tool.ui;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepState;
import org.apache.maven.release.tool.model.StepStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseSelectorTest {

    @Test
    void renderShowsReleaseIds() {
        ReleaseState r1 =
                ReleaseState.create("maven-compiler-plugin", null, "3.14.0", ComponentType.PLUGIN, Path.of("/tmp"));
        r1.getSteps().add(new StepState("pre-release-checks"));
        r1.getSteps().add(new StepState("maven-release-prepare"));

        ReleaseState r2 = ReleaseState.create("maven-surefire", null, "3.5.0", ComponentType.SHARED, Path.of("/tmp"));
        StepState waiting = new StepState("wait-for-vote");
        waiting.setStatus(StepStatus.WAITING);
        r2.getSteps().add(new StepState("pre-release-checks"));
        r2.getSteps().add(waiting);
        r2.setCurrentStepIndex(1);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(baos));
            ReleaseSelector selector = new ReleaseSelector();
            selector.render(List.of(r1, r2), 0);
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertTrue(output.contains("maven-compiler-plugin-3.14.0"), "Should contain first release ID");
        assertTrue(output.contains("maven-surefire-3.5.0"), "Should contain second release ID");
        assertTrue(output.contains("step 1/2"), "Should show step progress");
        assertTrue(output.contains("Navigate"), "Should show help line");
        assertTrue(output.contains("Resume"), "Should show resume hint");
    }

    @Test
    void renderHighlightsSelectedRelease() {
        ReleaseState r1 = ReleaseState.create("plugin-a", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        r1.getSteps().add(new StepState("step1"));

        ReleaseState r2 = ReleaseState.create("plugin-b", null, "2.0", ComponentType.PLUGIN, Path.of("/tmp"));
        r2.getSteps().add(new StepState("step1"));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(baos));
            ReleaseSelector selector = new ReleaseSelector();
            selector.render(List.of(r1, r2), 1);
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertTrue(output.contains("❯"), "Should contain selection pointer");
    }

    @Test
    void renderShowsWaitingStatus() {
        ReleaseState release = ReleaseState.create("maven-core", null, "4.0.0", ComponentType.CORE, Path.of("/tmp"));
        StepState waiting = new StepState("wait-for-vote");
        waiting.setStatus(StepStatus.WAITING);
        release.getSteps().add(waiting);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(baos));
            ReleaseSelector selector = new ReleaseSelector();
            selector.render(List.of(release), 0);
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertTrue(output.contains("⏳"), "Should show waiting icon for WAITING status");
    }

    @Test
    void renderShowsEmptyMessage() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(baos));
            ReleaseSelector selector = new ReleaseSelector();
            try {
                selector.select(List.of());
            } catch (Exception e) {
                // select on empty list prints message and returns null
            }
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertTrue(output.contains("No in-progress releases"), "Should show empty message");
    }

    @Test
    void renderShowsFailedStatus() {
        ReleaseState release = ReleaseState.create("maven-plugin", null, "1.0", ComponentType.PLUGIN, Path.of("/tmp"));
        StepState failed = new StepState("maven-release-prepare");
        failed.setStatus(StepStatus.FAILED);
        release.getSteps().add(failed);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(baos));
            ReleaseSelector selector = new ReleaseSelector();
            selector.render(List.of(release), 0);
        } finally {
            System.setOut(original);
        }

        String output = baos.toString();
        assertTrue(output.contains("✗"), "Should show failed icon for FAILED status");
        assertFalse(output.isEmpty(), "Output should not be empty");
    }
}
