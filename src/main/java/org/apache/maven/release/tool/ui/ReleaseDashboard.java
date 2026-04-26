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

import java.time.Duration;
import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.gauge.Gauge;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.maven.release.tool.eta.EtaTracker;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepState;
import org.apache.maven.release.tool.model.StepStatus;
import org.apache.maven.release.tool.steps.Step;

public class ReleaseDashboard implements AutoCloseable {

    private static final int WIDTH = 80;

    private final ReleaseState state;
    private final List<Step> steps;
    private final EtaTracker etaTracker;

    public ReleaseDashboard(ReleaseState state, List<Step> steps, EtaTracker etaTracker) {
        this.state = state;
        this.steps = steps;
        this.etaTracker = etaTracker;
    }

    public void render() {
        System.out.println();
        printHeader();
        printEta();
        System.out.println();
        printStepList();
        System.out.println();
        printProgressBar();
    }

    public void clearAndRender() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        render();
    }

    private void printHeader() {
        String version = state.getVersion() != null ? " " + state.getVersion() : "";
        String title = "Maven Release Tool — " + state.getArtifactId() + version;
        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("▸ ", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.styled(title, Style.EMPTY.addModifier(Modifier.BOLD)))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    private void printEta() {
        Duration remaining = etaTracker.estimateRemaining(state, steps);
        String etaStr = remaining.isZero() ? "" : "  ETA: " + etaTracker.formatDuration(remaining);
        String phaseInfo = "  Step " + (state.getCurrentStepIndex() + 1) + "/" + steps.size() + etaStr;

        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.styled(phaseInfo, Style.EMPTY.fg(Color.GRAY)))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    private void printStepList() {
        for (int i = 0; i < steps.size(); i++) {
            StepState stepState = i < state.getSteps().size() ? state.getSteps().get(i) : null;
            StepStatus status = stepState != null ? stepState.getStatus() : StepStatus.PENDING;
            boolean isCurrent = i == state.getCurrentStepIndex();

            String icon =
                    switch (status) {
                        case COMPLETED -> "  ✓ ";
                        case FAILED -> "  ✗ ";
                        case SKIPPED -> "  ⊘ ";
                        case IN_PROGRESS -> "  ⟳ ";
                        case WAITING -> "  ⏳ ";
                        default -> "  ○ ";
                    };

            Style iconStyle =
                    switch (status) {
                        case COMPLETED -> Style.EMPTY.fg(Color.GREEN);
                        case FAILED -> Style.EMPTY.fg(Color.RED);
                        case SKIPPED -> Style.EMPTY.fg(Color.YELLOW);
                        case IN_PROGRESS -> Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD);
                        case WAITING -> Style.EMPTY.fg(Color.YELLOW);
                        default -> Style.EMPTY.fg(Color.DARK_GRAY);
                    };

            Style nameStyle = isCurrent
                    ? Style.EMPTY.addModifier(Modifier.BOLD)
                    : (status == StepStatus.COMPLETED || status == StepStatus.SKIPPED)
                            ? Style.EMPTY.fg(Color.DARK_GRAY)
                            : Style.EMPTY;

            String duration = "";
            if (stepState != null && stepState.getDurationSeconds() != null && stepState.getDurationSeconds() > 0) {
                long secs = stepState.getDurationSeconds();
                duration = String.format("  %02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
            }

            Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
            Paragraph.builder()
                    .text(Text.from(Line.from(
                            Span.styled(icon, iconStyle),
                            Span.styled(steps.get(i).describe(), nameStyle),
                            Span.styled(duration, Style.EMPTY.fg(Color.DARK_GRAY)))))
                    .build()
                    .render(buf.area(), buf);
            System.out.println(buf.toAnsiStringTrimmed());
        }
    }

    private void printProgressBar() {
        double ratio = steps.isEmpty() ? 0 : (double) state.completedStepCount() / steps.size();
        String label = state.completedStepCount() + "/" + steps.size() + " steps (" + Math.round(ratio * 100) + "%)";

        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Gauge.builder()
                .ratio(ratio)
                .label(label)
                .gaugeStyle(Style.EMPTY.fg(Color.CYAN))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    public void println(String message) {
        System.out.println(message);
    }

    @Override
    public void close() {
        // nothing to close — no InlineDisplay
    }
}
