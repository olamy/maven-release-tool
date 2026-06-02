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
package org.apache.maven.release.tool.steps;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;

/**
 * Base class for release steps. Subclasses provide step name, description,
 * default commands, and execution logic.
 */
public abstract class AbstractStep implements Step {

    protected final CommandRunner runner;

    protected AbstractStep(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("DRY-RUN: ").append(describe()).append("\n");
        sb.append("Would execute:\n");
        for (String cmd : commands) {
            sb.append("  $ ").append(cmd).append("\n");
        }
        return StepResult.ok(sb.toString());
    }

    @Override
    public boolean isApplicable(ComponentType type) {
        return true;
    }

    protected Path projectDir(ReleaseState state) {
        return Path.of(state.getProjectDir());
    }

    /**
     * Runs each command as a separate process using ProcessBuilder directly
     * (no shell involved). Each command string is split via {@link #tokenize(String)}
     * which respects single and double quotes so that values such as
     * {@code -Darguments="-DskipTests -Dspotbugs.skip=true"} survive splitting as
     * a single argument with the quote characters stripped.
     *
     * Note: This splitting is safe here because all commands are either
     * tool defaults or user-reviewed overrides (never raw external input).
     * Commands that need quoted arguments should use the List-based
     * CommandRunner.exec() overload directly.
     */
    protected StepResult runCommands(ReleaseState state, List<String> commands) {
        for (String command : commands) {
            List<String> args = tokenize(command);
            StepResult result = runner.exec(projectDir(state), args);
            if (!result.succeeded()) {
                return result;
            }
        }
        return StepResult.ok();
    }

    /**
     * Splits a shell-like command string into argument tokens, respecting single
     * ({@code '}) and double ({@code "}) quotes. The quote characters themselves
     * are stripped from the resulting tokens. Whitespace inside quotes is preserved.
     *
     * <p>Example:
     * <pre>
     *   mvn release:prepare -Darguments="-DskipTests -Dspotbugs.skip=true"
     * </pre>
     * tokenises to four arguments, the last of which is
     * {@code -Darguments=-DskipTests -Dspotbugs.skip=true} — exactly what
     * ProcessBuilder needs so the Maven release plugin receives a syntactically
     * valid value for its {@code arguments} property.
     */
    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (quote == 0 && Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    inToken = false;
                }
            } else if (quote == 0 && (c == '"' || c == '\'')) {
                quote = c;
                inToken = true;
            } else if (c == quote) {
                quote = 0;
            } else {
                cur.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            tokens.add(cur.toString());
        }
        return tokens;
    }
}
