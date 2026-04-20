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

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import dev.tamboui.inline.InlineDisplay;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
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

    private final InlineDisplay display;
    private final ReleaseState state;
    private final List<Step> steps;
    private final EtaTracker etaTracker;

    public ReleaseDashboard(ReleaseState state, List<Step> steps, EtaTracker etaTracker) throws IOException {
        int displayHeight = Math.min(steps.size() + 4, 24);
        this.display = InlineDisplay.create(displayHeight);
        this.state = state;
        this.steps = steps;
        this.etaTracker = etaTracker;
    }

    public void render() {
        display.render((area, buf) -> {
            Rect[] rows = Layout.vertical()
                    .constraints(
                            Constraint.length(1),
                            Constraint.length(1),
                            Constraint.min(1),
                            Constraint.length(1),
                            Constraint.length(1))
                    .split(area)
                    .toArray(new Rect[0]);

            renderHeader(rows[0], buf);
            renderEta(rows[1], buf);
            renderStepList(rows[2], buf);
            renderProgressBar(rows[3], buf);
            renderKeyHints(rows[4], buf);
        });
    }

    private void renderHeader(Rect area, dev.tamboui.buffer.Buffer buf) {
        String title = "Maven Release Tool — " + state.getArtifactId() + " " + state.getVersion();
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("▸ ", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.styled(title, Style.EMPTY.addModifier(Modifier.BOLD)))))
                .build()
                .render(area, buf);
    }

    private void renderEta(Rect area, dev.tamboui.buffer.Buffer buf) {
        Duration remaining = etaTracker.estimateRemaining(state, steps);
        String etaStr = remaining.isZero() ? "" : "  ETA: " + etaTracker.formatDuration(remaining);
        String phaseInfo = "  Step " + (state.getCurrentStepIndex() + 1) + "/" + steps.size() + etaStr;

        Paragraph.builder()
                .text(Text.styled(phaseInfo, Style.EMPTY.fg(Color.GRAY)))
                .build()
                .render(area, buf);
    }

    private void renderStepList(Rect area, dev.tamboui.buffer.Buffer buf) {
        int startIdx = Math.max(0, state.getCurrentStepIndex() - (area.height() / 2));
        int endIdx = Math.min(steps.size(), startIdx + area.height());

        for (int i = startIdx; i < endIdx; i++) {
            int row = i - startIdx;
            if (row >= area.height()) {
                break;
            }

            Rect lineArea = new Rect(area.x(), area.y() + row, area.width(), 1);
            StepState stepState = i < state.getSteps().size() ? state.getSteps().get(i) : null;
            StepStatus status = stepState != null ? stepState.getStatus() : StepStatus.PENDING;
            boolean isCurrent = i == state.getCurrentStepIndex();

            Span icon =
                    switch (status) {
                        case COMPLETED -> Span.styled("✓ ", Style.EMPTY.fg(Color.GREEN));
                        case FAILED -> Span.styled("✗ ", Style.EMPTY.fg(Color.RED));
                        case SKIPPED -> Span.styled("⊘ ", Style.EMPTY.fg(Color.YELLOW));
                        case IN_PROGRESS ->
                            Span.styled("⟳ ", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD));
                        case WAITING -> Span.styled("⏳", Style.EMPTY.fg(Color.YELLOW));
                        default -> Span.styled("○ ", Style.EMPTY.fg(Color.DARK_GRAY));
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

            Paragraph.builder()
                    .text(Text.from(Line.from(
                            icon,
                            Span.styled(steps.get(i).describe(), nameStyle),
                            Span.styled(duration, Style.EMPTY.fg(Color.DARK_GRAY)))))
                    .build()
                    .render(lineArea, buf);
        }
    }

    private void renderProgressBar(Rect area, dev.tamboui.buffer.Buffer buf) {
        double ratio = steps.isEmpty() ? 0 : (double) state.completedStepCount() / steps.size();
        String label = state.completedStepCount() + "/" + steps.size() + " steps (" + Math.round(ratio * 100) + "%)";

        Gauge.builder()
                .ratio(ratio)
                .label(label)
                .gaugeStyle(Style.EMPTY.fg(Color.CYAN))
                .build()
                .render(area, buf);
    }

    private void renderKeyHints(Rect area, dev.tamboui.buffer.Buffer buf) {
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("[Enter]", Style.EMPTY.fg(Color.GREEN).addModifier(Modifier.BOLD)),
                        Span.raw(" Run  "),
                        Span.styled("[s]", Style.EMPTY.fg(Color.YELLOW).addModifier(Modifier.BOLD)),
                        Span.raw(" Skip  "),
                        Span.styled("[d]", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.raw(" Dry-run  "),
                        Span.styled("[v]", Style.EMPTY.fg(Color.MAGENTA).addModifier(Modifier.BOLD)),
                        Span.raw(" Detail  "),
                        Span.styled("[q]", Style.EMPTY.fg(Color.RED).addModifier(Modifier.BOLD)),
                        Span.raw(" Save & quit"))))
                .build()
                .render(area, buf);
    }

    public void println(String message) {
        display.println(message);
    }

    public void println(Text text) {
        display.println(text);
    }

    @Override
    public void close() {
        try {
            display.close();
        } catch (Exception e) {
            // ignore
        }
    }
}
