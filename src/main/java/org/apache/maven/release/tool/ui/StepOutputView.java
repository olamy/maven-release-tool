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
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * Displays the captured output of the most recently executed step below the
 * dashboard. Shows the last {@value #COLLAPSED_LINES} lines by default.
 *
 * <ul>
 *   <li>Ctrl+E — expand to full output</li>
 *   <li>Ctrl+R — collapse back to last {@value #COLLAPSED_LINES} lines</li>
 *   <li>Any other key — dismiss and continue</li>
 * </ul>
 */
public class StepOutputView {

    private static final int COLLAPSED_LINES = 10;
    private static final int CTRL_E = 5;
    private static final int CTRL_R = 18;
    private static final int WIDTH = 80;

    /**
     * Shows the step output below the already-rendered dashboard.
     * Blocks until the user presses a key; Ctrl+E/Ctrl+R toggle between
     * collapsed and expanded views (each toggle re-renders the full screen).
     * If {@code lines} is empty, returns immediately without blocking.
     */
    public void show(ReleaseDashboard dashboard, List<String> lines, String stepName) throws IOException {
        if (lines.isEmpty()) {
            return;
        }

        boolean expanded = false;
        printOutput(lines, stepName, expanded);

        try (Terminal terminal =
                TerminalBuilder.builder().system(true).jansi(true).build()) {
            terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            while (true) {
                int ch = reader.read();
                if (ch == -1) {
                    break;
                }
                if (ch == CTRL_E && !expanded) {
                    expanded = true;
                    clearAndRedraw(dashboard, lines, stepName, expanded);
                } else if (ch == CTRL_R && expanded) {
                    expanded = false;
                    clearAndRedraw(dashboard, lines, stepName, expanded);
                } else {
                    break;
                }
            }
        }
    }

    private void clearAndRedraw(ReleaseDashboard dashboard, List<String> lines, String stepName, boolean expanded) {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        dashboard.render();
        printOutput(lines, stepName, expanded);
    }

    private void printOutput(List<String> lines, String stepName, boolean expanded) {
        System.out.println();
        printSectionHeader(stepName, expanded, lines.size());

        List<String> toShow;
        if (expanded || lines.size() <= COLLAPSED_LINES) {
            toShow = lines;
        } else {
            toShow = lines.subList(lines.size() - COLLAPSED_LINES, lines.size());
        }

        for (String line : toShow) {
            System.out.println("  " + line);
        }

        System.out.println();
        printHint(expanded, lines.size() > COLLAPSED_LINES);
    }

    private void printSectionHeader(String stepName, boolean expanded, int totalLines) {
        String mode = expanded
                ? "[full output — " + totalLines + " lines]"
                : "[last " + Math.min(COLLAPSED_LINES, totalLines) + " of " + totalLines + " lines]";
        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("▸ ", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.styled(stepName + "  ", Style.EMPTY.addModifier(Modifier.BOLD)),
                        Span.styled(mode, Style.EMPTY.fg(Color.DARK_GRAY)))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    private void printHint(boolean expanded, boolean canToggle) {
        String hint;
        if (!canToggle) {
            hint = "  any key to continue";
        } else if (expanded) {
            hint = "  Ctrl+R collapse  ·  any key continue";
        } else {
            hint = "  Ctrl+E expand  ·  any key continue";
        }
        Buffer buf = Buffer.empty(Rect.of(WIDTH, 1));
        Paragraph.builder()
                .text(Text.styled(hint, Style.EMPTY.fg(Color.DARK_GRAY)))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }
}
