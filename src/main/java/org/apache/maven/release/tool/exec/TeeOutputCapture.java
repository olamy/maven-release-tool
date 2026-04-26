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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Output handler that tees each line to stdout and retains it in an in-memory
 * buffer so the most recent step output can be replayed after screen clear.
 * Call {@link #reset()} before each step execution to discard previous lines.
 */
public class TeeOutputCapture implements Consumer<String> {

    private final List<String> lines = new ArrayList<>();

    @Override
    public void accept(String line) {
        System.out.println(line);
        lines.add(line);
    }

    public List<String> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * Adds lines to the capture buffer without printing to stdout.
     * Use this for content that is already about to be displayed via the output view
     * (e.g., a step result message generated internally rather than via a subprocess).
     */
    public void buffer(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String line : text.split("\n", -1)) {
            lines.add(line);
        }
    }

    public void reset() {
        lines.clear();
    }
}
