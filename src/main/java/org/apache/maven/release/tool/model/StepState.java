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
package org.apache.maven.release.tool.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StepState {

    private String name;
    private StepStatus status = StepStatus.PENDING;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationSeconds;
    private List<String> commands;
    private Map<String, String> metadata = new HashMap<>();

    public StepState() {}

    public StepState(String name) {
        this.name = name;
    }

    public void markStarted() {
        this.status = StepStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = StepStatus.COMPLETED;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationSeconds = completedAt.getEpochSecond() - startedAt.getEpochSecond();
        }
    }

    public void markFailed() {
        this.status = StepStatus.FAILED;
        this.completedAt = Instant.now();
        if (startedAt != null) {
            this.durationSeconds = completedAt.getEpochSecond() - startedAt.getEpochSecond();
        }
    }

    public void markSkipped() {
        this.status = StepStatus.SKIPPED;
    }

    public void markWaiting() {
        this.status = StepStatus.WAITING;
        if (startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StepStatus getStatus() {
        return status;
    }

    public void setStatus(StepStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
}
