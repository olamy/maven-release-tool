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

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReleaseState {

    private String artifactId;
    private String groupId;
    private String version;
    private String nextVersion;
    private ComponentType componentType;
    private String projectDir;
    private String gitRemoteUrl;
    private Instant startedAt;
    private int currentStepIndex;
    private List<StepState> steps = new ArrayList<>();
    private String stagingRepoId;
    private String stagingRepoUrl;
    private String releaseTag;
    private String previousTag;
    private Instant estimatedCompletionAt;
    private boolean dryRun;
    private String distributionManagementSiteUrl;
    private String scmUrl;

    public ReleaseState() {}

    public static ReleaseState create(
            String artifactId, String groupId, String version, ComponentType componentType, Path projectDir) {
        ReleaseState state = new ReleaseState();
        state.artifactId = artifactId;
        state.groupId = groupId;
        state.version = version;
        state.componentType = componentType;
        state.projectDir = projectDir.toAbsolutePath().toString();
        state.startedAt = Instant.now();
        state.currentStepIndex = 0;
        return state;
    }

    @JsonIgnore
    public String getReleaseId() {
        return artifactId + "-" + version;
    }

    @JsonIgnore
    public StepState getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }

    public void advanceToNextStep() {
        if (currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
        }
    }

    public boolean goBackToPreviousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isComplete() {
        return currentStepIndex >= steps.size() - 1
                && steps.get(steps.size() - 1).getStatus() == StepStatus.COMPLETED;
    }

    @JsonIgnore
    public long completedStepCount() {
        return steps.stream()
                .filter(s -> s.getStatus() == StepStatus.COMPLETED || s.getStatus() == StepStatus.SKIPPED)
                .count();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getNextVersion() {
        return nextVersion;
    }

    public void setNextVersion(String nextVersion) {
        this.nextVersion = nextVersion;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(String projectDir) {
        this.projectDir = projectDir;
    }

    public String getGitRemoteUrl() {
        return gitRemoteUrl;
    }

    public void setGitRemoteUrl(String gitRemoteUrl) {
        this.gitRemoteUrl = gitRemoteUrl;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public List<StepState> getSteps() {
        return steps;
    }

    public void setSteps(List<StepState> steps) {
        this.steps = steps;
    }

    public String getStagingRepoId() {
        return stagingRepoId;
    }

    public void setStagingRepoId(String stagingRepoId) {
        this.stagingRepoId = stagingRepoId;
    }

    public String getStagingRepoUrl() {
        return stagingRepoUrl;
    }

    public void setStagingRepoUrl(String stagingRepoUrl) {
        this.stagingRepoUrl = stagingRepoUrl;
    }

    public String getReleaseTag() {
        return releaseTag;
    }

    public void setReleaseTag(String releaseTag) {
        this.releaseTag = releaseTag;
    }

    public String getPreviousTag() {
        return previousTag;
    }

    public void setPreviousTag(String previousTag) {
        this.previousTag = previousTag;
    }

    public Instant getEstimatedCompletionAt() {
        return estimatedCompletionAt;
    }

    public void setEstimatedCompletionAt(Instant estimatedCompletionAt) {
        this.estimatedCompletionAt = estimatedCompletionAt;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String getDistributionManagementSiteUrl() {
        return distributionManagementSiteUrl;
    }

    public void setDistributionManagementSiteUrl(String distributionManagementSiteUrl) {
        this.distributionManagementSiteUrl = distributionManagementSiteUrl;
    }

    /**
     * Parsed view of {@link #getDistributionManagementSiteUrl()} when present and valid.
     * Returns {@link Optional#empty()} if the URL is missing or does not match the
     * expected ASF Maven {@code components/} layout.
     */
    @JsonIgnore
    public Optional<SitePaths> sitePaths() {
        return SitePaths.parse(distributionManagementSiteUrl);
    }

    public String getScmUrl() {
        return scmUrl;
    }

    public void setScmUrl(String scmUrl) {
        this.scmUrl = scmUrl;
    }

    /**
     * Returns the base browseable URL of the SCM (typically GitHub) for this project, with
     * any {@code /tree/...} suffix, trailing {@code .git} and trailing {@code /} stripped.
     * Returns {@link Optional#empty()} when {@link #getScmUrl()} is not set.
     *
     * <p>Example: {@code https://github.com/apache/maven-surefire/tree/HEAD} →
     * {@code https://github.com/apache/maven-surefire}.
     */
    @JsonIgnore
    public Optional<String> scmBrowseUrl() {
        if (scmUrl == null || scmUrl.isBlank()) {
            return Optional.empty();
        }
        String url = scmUrl.trim();
        int treeIdx = url.indexOf("/tree/");
        if (treeIdx > 0) {
            url = url.substring(0, treeIdx);
        }
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - 4);
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? Optional.empty() : Optional.of(url);
    }
}
