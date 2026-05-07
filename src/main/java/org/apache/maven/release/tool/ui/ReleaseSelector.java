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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepState;
import org.apache.maven.release.tool.model.StepStatus;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * Interactive TUI selector that displays in-progress releases and lets the user
 * pick one with arrow keys.
 */
public class ReleaseSelector {

    private static final int WIDTH = 80;
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public enum ManageAction {
        RESUME,
        DELETE,
        QUIT
    }

    public record ManageResult(ManageAction action, ReleaseState release) {}

    /**
     * Shows an interactive list of releases and returns the one selected by the user,
     * or {@code null} if the user quit without selecting.
     */
    public ReleaseState select(List<ReleaseState> releases) throws IOException {
        if (releases.isEmpty()) {
            System.out.println("No in-progress releases.");
            return null;
        }

        try (Terminal terminal =
                TerminalBuilder.builder().system(true).jansi(true).build()) {
            terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            int selectedIndex = 0;
            boolean running = true;

            while (running) {
                render(releases, selectedIndex);

                int ch = reader.read();
                if (ch == -1) {
                    return null;
                }

                if (ch == 27) {
                    // ESC sequence — check for arrow keys
                    int next = reader.read(50);
                    if (next == '[') {
                        int arrow = reader.read(50);
                        switch (arrow) {
                            case 'A': // up
                                selectedIndex = Math.max(0, selectedIndex - 1);
                                break;
                            case 'B': // down
                                selectedIndex = Math.min(releases.size() - 1, selectedIndex + 1);
                                break;
                            default:
                                break;
                        }
                    } else if (next == -1 || next == 27) {
                        // bare ESC — quit
                        return null;
                    }
                } else if (ch == 'q' || ch == 'Q') {
                    return null;
                } else if (ch == '\r' || ch == '\n') {
                    return releases.get(selectedIndex);
                } else if (ch == 'k' || ch == 'K') {
                    selectedIndex = Math.max(0, selectedIndex - 1);
                } else if (ch == 'j' || ch == 'J') {
                    selectedIndex = Math.min(releases.size() - 1, selectedIndex + 1);
                }
            }
            return null;
        }
    }

    void render(List<ReleaseState> releases, int selectedIndex) {
        // Move cursor to top-left and clear screen
        System.out.print("\033[H\033[2J");
        System.out.flush();

        printHeader();
        System.out.println();

        for (int i = 0; i < releases.size(); i++) {
            printRelease(releases.get(i), i == selectedIndex);
        }

        System.out.println();
        printHelpLine();
    }

    private void printHeader() {
        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("▸ ", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.styled("Maven Release Dashboard", Style.EMPTY.addModifier(Modifier.BOLD)),
                        Span.styled("  — select a release to resume", Style.EMPTY.fg(Color.GRAY)))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    private void printRelease(ReleaseState release, boolean selected) {
        StepState currentStep = release.getCurrentStep();
        StepStatus status = currentStep != null ? currentStep.getStatus() : StepStatus.PENDING;

        String statusIcon =
                switch (status) {
                    case WAITING -> "⏳";
                    case IN_PROGRESS -> "⟳";
                    case FAILED -> "✗";
                    default -> "○";
                };

        Style statusStyle =
                switch (status) {
                    case WAITING -> Style.EMPTY.fg(Color.YELLOW);
                    case IN_PROGRESS -> Style.EMPTY.fg(Color.CYAN);
                    case FAILED -> Style.EMPTY.fg(Color.RED);
                    default -> Style.EMPTY.fg(Color.DARK_GRAY);
                };

        String pointer = selected ? "❯ " : "  ";
        Style pointerStyle = selected ? Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD) : Style.EMPTY;

        String id = release.getReleaseId();
        Style idStyle = selected ? Style.EMPTY.addModifier(Modifier.BOLD) : Style.EMPTY;

        int total = release.getSteps().size();
        String progress = String.format("  step %d/%d", release.getCurrentStepIndex() + 1, total);

        String started = "";
        if (release.getStartedAt() != null) {
            started = "  " + DATE_FMT.format(release.getStartedAt());
        }

        String currentStepName = "";
        if (currentStep != null) {
            currentStepName = "  [" + currentStep.getName() + "]";
        }

        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled(pointer, pointerStyle),
                        Span.styled(statusIcon + " ", statusStyle),
                        Span.styled(id, idStyle),
                        Span.styled(progress, Style.EMPTY.fg(Color.GRAY)),
                        Span.styled(currentStepName, Style.EMPTY.fg(Color.DARK_GRAY)),
                        Span.styled(started, Style.EMPTY.fg(Color.DARK_GRAY)))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    private void printHelpLine() {
        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("[↑↓/jk]", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.raw(" Navigate  "),
                        Span.styled("[Enter]", Style.EMPTY.fg(Color.GREEN).addModifier(Modifier.BOLD)),
                        Span.raw(" Resume  "),
                        Span.styled("[q/Esc]", Style.EMPTY.fg(Color.RED).addModifier(Modifier.BOLD)),
                        Span.raw(" Quit"))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    /**
     * Shows an interactive manage view where the user can resume or delete a release.
     * Returns a {@link ManageResult} describing the chosen action and the selected release,
     * or {@code null} if the list is empty or the user quit.
     */
    public ManageResult manage(List<ReleaseState> releases) throws IOException {
        if (releases.isEmpty()) {
            System.out.println("No in-progress releases.");
            return null;
        }

        try (Terminal terminal =
                TerminalBuilder.builder().system(true).jansi(true).build()) {
            terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            int selectedIndex = 0;

            while (true) {
                renderForManage(releases, selectedIndex);

                int ch = reader.read();
                if (ch == -1) {
                    return null;
                }

                if (ch == 27) {
                    int next = reader.read(50);
                    if (next == '[') {
                        int arrow = reader.read(50);
                        switch (arrow) {
                            case 'A':
                                selectedIndex = Math.max(0, selectedIndex - 1);
                                break;
                            case 'B':
                                selectedIndex = Math.min(releases.size() - 1, selectedIndex + 1);
                                break;
                            default:
                                break;
                        }
                    } else if (next == -1 || next == 27) {
                        return new ManageResult(ManageAction.QUIT, null);
                    }
                } else if (ch == 'q' || ch == 'Q') {
                    return new ManageResult(ManageAction.QUIT, null);
                } else if (ch == '\r' || ch == '\n') {
                    return new ManageResult(ManageAction.RESUME, releases.get(selectedIndex));
                } else if (ch == 'd' || ch == 'D') {
                    return new ManageResult(ManageAction.DELETE, releases.get(selectedIndex));
                } else if (ch == 'k' || ch == 'K') {
                    selectedIndex = Math.max(0, selectedIndex - 1);
                } else if (ch == 'j' || ch == 'J') {
                    selectedIndex = Math.min(releases.size() - 1, selectedIndex + 1);
                }
            }
        }
    }

    void renderForManage(List<ReleaseState> releases, int selectedIndex) {
        System.out.print("\033[H\033[2J");
        System.out.flush();

        printHeader();
        System.out.println();

        for (int i = 0; i < releases.size(); i++) {
            printRelease(releases.get(i), i == selectedIndex);
        }

        System.out.println();
        printManageHelpLine();
    }

    private void printManageHelpLine() {
        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("[↑↓/jk]", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.raw(" Navigate  "),
                        Span.styled("[Enter]", Style.EMPTY.fg(Color.GREEN).addModifier(Modifier.BOLD)),
                        Span.raw(" Resume  "),
                        Span.styled("[d]", Style.EMPTY.fg(Color.RED).addModifier(Modifier.BOLD)),
                        Span.raw(" Delete  "),
                        Span.styled("[q/Esc]", Style.EMPTY.fg(Color.DARK_GRAY).addModifier(Modifier.BOLD)),
                        Span.raw(" Quit"))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }
}
