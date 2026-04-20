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
package org.apache.maven.release.tool.config;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ProjectConfig {

    private String gitRemoteUrl;
    private Map<String, CommandOverride> overrides = new HashMap<>();
    private Instant savedAt;

    public ProjectConfig() {}

    public ProjectConfig(String gitRemoteUrl) {
        this.gitRemoteUrl = gitRemoteUrl;
    }

    public boolean hasOverride(String stepName) {
        return overrides.containsKey(stepName);
    }

    public CommandOverride getOverride(String stepName) {
        return overrides.get(stepName);
    }

    public void setOverride(String stepName, CommandOverride override) {
        overrides.put(stepName, override);
        savedAt = Instant.now();
    }

    public String getGitRemoteUrl() {
        return gitRemoteUrl;
    }

    public void setGitRemoteUrl(String gitRemoteUrl) {
        this.gitRemoteUrl = gitRemoteUrl;
    }

    public Map<String, CommandOverride> getOverrides() {
        return overrides;
    }

    public void setOverrides(Map<String, CommandOverride> overrides) {
        this.overrides = overrides;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }
}
