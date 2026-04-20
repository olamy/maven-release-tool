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
package org.apache.maven.release.tool.exec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.apache.maven.release.tool.model.StepResult;

public class CommandRunner {

    private final Consumer<String> outputHandler;

    public CommandRunner(Consumer<String> outputHandler) {
        this.outputHandler = outputHandler;
    }

    public CommandRunner() {
        this(System.out::println);
    }

    /**
     * Execute a command given as a pre-split argument list (no shell involved).
     * Each element in args must be a single argument — this uses ProcessBuilder
     * directly, which does NOT invoke a shell and is safe from injection.
     */
    public StepResult exec(Path workingDir, List<String> args) {
        String display = String.join(" ", args);
        outputHandler.accept("$ " + display);

        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputHandler.accept(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return StepResult.ok();
            } else {
                return StepResult.failure("Command exited with code " + exitCode);
            }
        } catch (IOException e) {
            return StepResult.failure("Failed to execute command: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.failure("Command interrupted");
        }
    }

    /**
     * Execute a command and capture its stdout (no streaming to output handler).
     * Uses ProcessBuilder directly — no shell involved.
     */
    public String getOutput(Path workingDir, List<String> args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().reduce("", (a, b) -> a + b + "\n").trim();
            }

            process.waitFor();
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }
}
