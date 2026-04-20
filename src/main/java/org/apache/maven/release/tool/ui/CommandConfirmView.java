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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import org.apache.maven.release.tool.config.CommandOverride;
import org.apache.maven.release.tool.config.CommandOverrideStore;
import org.apache.maven.release.tool.config.CommandResolver;
import org.apache.maven.release.tool.config.ProjectConfig;

public class CommandConfirmView {

    private final BufferedReader stdinReader;

    public CommandConfirmView() {
        this.stdinReader = new BufferedReader(new InputStreamReader(System.in));
    }

    public enum Action {
        ACCEPT,
        SKIP,
        DRY_RUN,
        QUIT,
        EDITED
    }

    public enum FailureAction {
        RETRY,
        ROLLBACK,
        IGNORE,
        QUIT
    }

    public record ConfirmResult(Action action, List<String> commands) {}

    public FailureAction promptOnFailure(String stepName, boolean offerRollback) throws IOException {
        System.out.println();
        Buffer buf = Buffer.empty(Rect.of(80, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("Step failed: ", Style.EMPTY.fg(Color.RED).addModifier(Modifier.BOLD)),
                        Span.raw(stepName))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());

        System.out.println();
        System.out.println("  [r] Retry (re-run after fixing the issue)");
        System.out.println("  [i] Ignore and continue to next step");
        if (offerRollback) {
            System.out.println("  [b] Rollback (mvn release:rollback + release:clean)");
        }
        System.out.println("  [q] Save & quit (fix manually, resume later)");
        System.out.print("  > ");
        System.out.flush();

        while (true) {
            String input = stdinReader.readLine();
            if (input == null) {
                return FailureAction.QUIT;
            }
            input = input.trim().toLowerCase();
            switch (input) {
                case "r":
                    return FailureAction.RETRY;
                case "i":
                    return FailureAction.IGNORE;
                case "b":
                    if (offerRollback) {
                        return FailureAction.ROLLBACK;
                    }
                    System.out.println("  Rollback not available for this step. Choose r/i/q.");
                    System.out.print("  > ");
                    System.out.flush();
                    break;
                case "", "q":
                    return FailureAction.QUIT;
                default:
                    System.out.println("  Unknown option. Choose r/i" + (offerRollback ? "/b" : "") + "/q.");
                    System.out.print("  > ");
                    System.out.flush();
                    break;
            }
        }
    }

    public ConfirmResult confirm(String stepName, String stepDescription, CommandResolver.ResolvedCommands resolved)
            throws IOException {

        printStepHeader(stepName, stepDescription);
        printCommands(resolved);
        printOptions();

        while (true) {
            String input = stdinReader.readLine();
            if (input == null) {
                return new ConfirmResult(Action.QUIT, resolved.commands());
            }
            input = input.trim().toLowerCase();

            return switch (input) {
                case "", "y" -> new ConfirmResult(Action.ACCEPT, resolved.commands());
                case "s" -> new ConfirmResult(Action.SKIP, resolved.commands());
                case "d" -> new ConfirmResult(Action.DRY_RUN, resolved.commands());
                case "q" -> new ConfirmResult(Action.QUIT, resolved.commands());
                case "e" -> handleEdit(resolved);
                default -> {
                    System.out.println("Unknown option. Press Enter to accept, or s/d/e/q.");
                    yield null;
                }
            };
        }
    }

    public boolean promptSaveOverride(
            List<String> editedCommands,
            String stepName,
            CommandOverrideStore overrideStore,
            ProjectConfig projectConfig)
            throws IOException {

        System.out.println();
        printStyled("Save this override for future releases of this project? [y/N] ", Style.EMPTY.fg(Color.YELLOW));
        String input = stdinReader.readLine();

        if (input != null && input.trim().equalsIgnoreCase("y")) {
            System.out.print("Reason (optional): ");
            String reason = stdinReader.readLine();
            if (reason != null && reason.isBlank()) {
                reason = null;
            }

            projectConfig.setOverride(stepName, new CommandOverride(editedCommands, reason));
            overrideStore.save(projectConfig);
            System.out.println("Override saved.");
            return true;
        }
        return false;
    }

    private ConfirmResult handleEdit(CommandResolver.ResolvedCommands resolved) throws IOException {
        System.out.println();
        printStyled("Edit commands (one per line, empty line to finish):\n", Style.EMPTY.fg(Color.CYAN));

        for (String cmd : resolved.commands()) {
            System.out.println("  current: " + cmd);
        }
        System.out.println();

        java.util.List<String> editedCommands = new java.util.ArrayList<>();
        System.out.print("  > ");
        String line;
        while ((line = stdinReader.readLine()) != null && !line.isBlank()) {
            editedCommands.add(line.trim());
            System.out.print("  > ");
        }

        if (editedCommands.isEmpty()) {
            System.out.println("No changes. Using original commands.");
            return new ConfirmResult(Action.ACCEPT, resolved.commands());
        }

        System.out.println("Modified commands:");
        for (String cmd : editedCommands) {
            System.out.println("  $ " + cmd);
        }

        return new ConfirmResult(Action.EDITED, editedCommands);
    }

    private void printStepHeader(String name, String description) {
        System.out.println();
        Buffer buf = Buffer.empty(Rect.of(80, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("─── ", Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(name, Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.styled(": ", Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(description, Style.EMPTY))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    private void printCommands(CommandResolver.ResolvedCommands resolved) {
        if (resolved.commands().isEmpty()) {
            System.out.println("  (no commands — interactive step)");
            return;
        }

        Buffer buf = Buffer.empty(Rect.of(80, 1));
        Paragraph.builder()
                .text(Text.styled("  Source: " + resolved.source(), Style.EMPTY.fg(Color.DARK_GRAY)))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());

        for (String cmd : resolved.commands()) {
            buf = Buffer.empty(Rect.of(80, 1));
            Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled("  $ ", Style.EMPTY.fg(Color.GREEN)), Span.raw(cmd))))
                    .build()
                    .render(buf.area(), buf);
            System.out.println(buf.toAnsiStringTrimmed());
        }
    }

    private void printOptions() {
        System.out.println();
        Buffer buf = Buffer.empty(Rect.of(80, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("[Enter/y]", Style.EMPTY.fg(Color.GREEN).addModifier(Modifier.BOLD)),
                        Span.raw(" Accept  "),
                        Span.styled("[e]", Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.raw(" Edit  "),
                        Span.styled("[s]", Style.EMPTY.fg(Color.YELLOW).addModifier(Modifier.BOLD)),
                        Span.raw(" Skip  "),
                        Span.styled("[d]", Style.EMPTY.fg(Color.MAGENTA).addModifier(Modifier.BOLD)),
                        Span.raw(" Dry-run  "),
                        Span.styled("[q]", Style.EMPTY.fg(Color.RED).addModifier(Modifier.BOLD)),
                        Span.raw(" Quit"))))
                .build()
                .render(buf.area(), buf);
        System.out.print(buf.toAnsiStringTrimmed() + " > ");
        System.out.flush();
    }

    private void printStyled(String text, Style style) {
        Buffer buf = Buffer.empty(Rect.of(80, 1));
        Paragraph.builder().text(Text.styled(text, style)).build().render(buf.area(), buf);
        System.out.print(buf.toAnsiStringTrimmed());
        System.out.flush();
    }
}
