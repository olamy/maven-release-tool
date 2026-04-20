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
package org.apache.maven.release.tool.eta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.release.tool.model.ComponentType;

public class EtaHistory {

    private final Path historyFile;
    private final ObjectMapper mapper;
    private Map<String, Map<String, StepTiming>> data;

    public EtaHistory(Path baseDir) {
        this.historyFile = baseDir.resolve("history.json");
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.data = new HashMap<>();
    }

    public void load() throws IOException {
        if (Files.exists(historyFile)) {
            data = mapper.readValue(historyFile.toFile(), new TypeReference<>() {});
        }
    }

    public void save() throws IOException {
        Files.createDirectories(historyFile.getParent());
        mapper.writeValue(historyFile.toFile(), data);
    }

    public void recordStepDuration(ComponentType type, String stepName, long durationSeconds) {
        String key = type.name();
        data.computeIfAbsent(key, k -> new HashMap<>());
        Map<String, StepTiming> typeData = data.get(key);

        StepTiming timing = typeData.getOrDefault(stepName, new StepTiming(0, 0));
        long newMedian = timing.samples() == 0
                ? durationSeconds
                : (timing.medianSeconds() * timing.samples() + durationSeconds) / (timing.samples() + 1);
        typeData.put(stepName, new StepTiming(newMedian, timing.samples() + 1));
    }

    public long getMedianDuration(ComponentType type, String stepName) {
        Map<String, StepTiming> typeData = data.get(type.name());
        if (typeData == null) {
            return 0;
        }
        StepTiming timing = typeData.get(stepName);
        return timing != null ? timing.medianSeconds() : 0;
    }

    public Map<String, Map<String, StepTiming>> getData() {
        return data;
    }

    public record StepTiming(long medianSeconds, int samples) {}
}
