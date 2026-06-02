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
import java.util.List;

import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.SitePaths;
import org.apache.maven.release.tool.model.StepResult;
import org.apache.maven.release.tool.persistence.StateStore;

public class SendAnnouncementStep extends AbstractStep {

    private final StateStore stateStore;

    public SendAnnouncementStep(CommandRunner runner, StateStore stateStore) {
        super(runner);
        this.stateStore = stateStore;
    }

    @Override
    public String name() {
        return "send-announcement";
    }

    @Override
    public String describe() {
        return "Generate announcement email and save to release directory";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of();
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) {
        String email = generateAnnouncement(state);

        try {
            stateStore.writeArtifact(state, "announcement-email.txt", email);
        } catch (IOException e) {
            return StepResult.failure("Failed to save announcement email: " + e.getMessage());
        }

        return StepResult.okFullScreen("Announcement email saved to release directory (announcement-email.txt).\n"
                + "Send from your @apache.org email address.\n\n"
                + email);
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) {
        return StepResult.okFullScreen("DRY-RUN: Would generate announcement email:\n\n" + generateAnnouncement(state));
    }

    private String generateAnnouncement(ReleaseState state) {
        String componentName = formatComponentName(state);
        String siteUrl = buildSiteUrl(state);

        StringBuilder sb = new StringBuilder();

        sb.append("To: announce@maven.apache.org, users@maven.apache.org\n");
        sb.append("Cc: dev@maven.apache.org\n");

        if (state.getComponentType() == ComponentType.CORE) {
            sb.append("Bcc: announce@apache.org\n");
        } else if (state.getComponentType() == ComponentType.PARENT_POM) {
            sb.append("Bcc: release-discuss@apache.org\n");
        }

        sb.append("Subject: [ANN] Apache ").append(componentName).append(" Released\n\n");

        sb.append("The Apache Maven team is pleased to announce the release of\n");
        sb.append("Apache ").append(componentName).append(".\n\n");

        sb.append(siteUrl).append("\n\n");

        if (state.getComponentType() == ComponentType.PLUGIN) {
            sb.append("You should specify the version in your project's plugin configuration:\n\n");
            sb.append("<plugin>\n");
            sb.append("  <groupId>org.apache.maven.plugins</groupId>\n");
            sb.append("  <artifactId>").append(state.getArtifactId()).append("</artifactId>\n");
            sb.append("  <version>").append(state.getVersion()).append("</version>\n");
            sb.append("</plugin>\n\n");
        }

        sb.append("Release Notes - Apache ").append(componentName).append(":\n");
        sb.append("https://github.com/apache/")
                .append(state.getArtifactId())
                .append("/releases/tag/")
                .append(state.getReleaseTag())
                .append("\n\n");

        sb.append("Enjoy,\n\n");
        sb.append("-The Apache Maven team\n");

        return sb.toString();
    }

    private String formatComponentName(ReleaseState state) {
        String name = state.getArtifactId();
        if (state.getComponentType() == ComponentType.CORE) {
            return "Maven " + state.getVersion();
        }
        name = name.replace("maven-", "").replace("-plugin", " Plugin");
        return "Maven " + Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + state.getVersion();
    }

    private String buildSiteUrl(ReleaseState state) {
        if (state.getComponentType() == ComponentType.CORE) {
            return "https://maven.apache.org/ref/" + state.getVersion() + "/";
        }
        return state.sitePaths().map(SitePaths::liveSiteUrl).orElse("https://maven.apache.org/");
    }
}
