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

import java.nio.file.Path;
import java.util.Arrays;
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
    public StepResult dryRun(ReleaseState state, List<String> commands) {
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
     * (no shell involved). Each command string is split on whitespace into
     * an argument array.
     *
     * Note: This splitting is safe here because all commands are either
     * tool defaults or user-reviewed overrides (never raw external input).
     * Commands that need quoted arguments should use the List-based
     * CommandRunner.exec() overload directly.
     */
    protected StepResult runCommands(ReleaseState state, List<String> commands) {
        for (String command : commands) {
            List<String> args = Arrays.asList(command.split("\\s+"));
            StepResult result = runner.exec(projectDir(state), args);
            if (!result.succeeded()) {
                return result;
            }
        }
        return StepResult.ok();
    }
}
