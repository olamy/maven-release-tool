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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.OptionalInt;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Modifier;
import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.maven.release.tool.exec.CommandRunner;
import org.apache.maven.release.tool.integration.GitHubClient;
import org.apache.maven.release.tool.model.ComponentType;
import org.apache.maven.release.tool.model.ReleaseState;
import org.apache.maven.release.tool.model.StepResult;
import org.apache.maven.release.tool.persistence.StateStore;

public class CallVoteStep extends AbstractStep {

    private record VoteEmailInputs(String previousVersion, String stagingRepoId, String sha512sum) {}

    private final StateStore stateStore;

    public CallVoteStep(CommandRunner runner, StateStore stateStore) {
        super(runner);
        this.stateStore = stateStore;
    }

    @Override
    public String name() {
        return "call-vote";
    }

    @Override
    public String describe() {
        return "Generate vote email and save to release directory (for core only)";
    }

    @Override
    public List<String> defaultCommands(ReleaseState state) {
        return List.of();
    }

    @Override
    public StepResult execute(ReleaseState state, List<String> commands) throws IOException {
        VoteEmailInputs inputs = promptForInputs(state);
        applyInputsToState(state, inputs);
        stateStore.save(state);

        String email = generateVoteEmail(state, inputs);

        try {
            stateStore.writeArtifact(state, "vote-email.txt", email);
        } catch (IOException e) {
            return StepResult.failure("Failed to save vote email: " + e.getMessage());
        }

        return StepResult.okFullScreen("Vote email saved to release directory (vote-email.txt).\n"
                + "Review and send it to dev@maven.apache.org.\n\n"
                + email);
    }

    @Override
    public StepResult dryRun(ReleaseState state, List<String> commands) throws IOException {
        VoteEmailInputs inputs = promptForInputs(state);
        String email = generateVoteEmail(state, inputs);
        return StepResult.okFullScreen("DRY-RUN: Would generate vote email:\n\n" + email);
    }

    /**
     * Prompts the user for values that are needed in the vote email but may not yet
     * be stored in state. Each field shows its current value (if known) as the default;
     * pressing Enter without typing keeps the placeholder.
     */
    private VoteEmailInputs promptForInputs(ReleaseState state) throws IOException {
        System.out.println();
        printHeader("Vote email — enter values (Enter to keep placeholder)");
        System.out.println();

        String previousVersion = promptField("Previous version", state.getPreviousTag(), "<previous-tag>");

        String stagingRepoId = promptField("Staging repository ID", state.getStagingRepoId(), "<staging-repo-id>");

        String sha512sum = promptField("SHA512 checksum of source-release.zip", null, "<SHA512SUM>");

        System.out.println();
        return new VoteEmailInputs(previousVersion, stagingRepoId, sha512sum);
    }

    /**
     * Prompts for a single named field. Shows {@code currentValue} (if non-null/blank)
     * as the suggested default. Returns {@code placeholder} when the user presses Enter
     * without providing a value and there is no current value.
     */
    private String promptField(String label, String currentValue, String placeholder) throws IOException {
        boolean hasCurrent = currentValue != null && !currentValue.isBlank();
        String hint = hasCurrent ? " [" + currentValue + "]" : " [" + placeholder + "]";

        Buffer buf = Buffer.empty(Rect.of(80, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("  " + label, Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)),
                        Span.styled(hint + ": ", Style.EMPTY.fg(Color.DARK_GRAY)))))
                .build()
                .render(buf.area(), buf);
        System.out.print(buf.toAnsiStringTrimmed());
        System.out.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String input = reader.readLine();
        if (input == null || input.isBlank()) {
            return hasCurrent ? currentValue : placeholder;
        }
        return input.trim();
    }

    private void printHeader(String text) {
        Buffer buf = Buffer.empty(Rect.of(80, 1));
        Paragraph.builder()
                .text(Text.from(Line.from(
                        Span.styled("─── ", Style.EMPTY.fg(Color.CYAN)),
                        Span.styled(text, Style.EMPTY.fg(Color.CYAN).addModifier(Modifier.BOLD)))))
                .build()
                .render(buf.area(), buf);
        System.out.println(buf.toAnsiStringTrimmed());
    }

    /**
     * Persists user-supplied values back into the release state so they are available
     * to subsequent steps (e.g. WaitForVoteStep) and future resumes.
     * Only overwrites fields that were previously unset.
     */
    private void applyInputsToState(ReleaseState state, VoteEmailInputs inputs) {
        if (!inputs.previousVersion().startsWith("<") && state.getPreviousTag() == null) {
            state.setPreviousTag(inputs.previousVersion());
        }
        if (!inputs.stagingRepoId().startsWith("<") && state.getStagingRepoId() == null) {
            state.setStagingRepoId(inputs.stagingRepoId());
            // derive the staging URL from the ID if not already set
            if (state.getStagingRepoUrl() == null) {
                state.setStagingRepoUrl("https://repository.apache.org/content/repositories/" + inputs.stagingRepoId());
            }
        }
    }

    private String generateVoteEmail(ReleaseState state, VoteEmailInputs inputs) {
        String componentName = buildComponentName(state);
        String repoName = extractRepoName(state.getGitRemoteUrl(), state.getArtifactId());

        // Use user-provided staging repo ID to build the URL, falling back to state then placeholder
        String stagingUrl;
        if (!inputs.stagingRepoId().startsWith("<")) {
            stagingUrl = "https://repository.apache.org/content/repositories/" + inputs.stagingRepoId();
        } else if (state.getStagingRepoUrl() != null) {
            stagingUrl = state.getStagingRepoUrl();
        } else {
            stagingUrl = "https://repository.apache.org/content/repositories/maven-<staging-repo-id>";
        }

        String currentTag = state.getReleaseTag() != null ? state.getReleaseTag() : "<current-tag>";
        String previousTag = !inputs.previousVersion().startsWith("<")
                ? inputs.previousVersion()
                : (state.getPreviousTag() != null ? state.getPreviousTag() : "<previous-tag>");

        String issueCount = fetchIssueCount(repoName, state.getVersion());

        StringBuilder sb = new StringBuilder();
        sb.append("To: \"Maven Developers List\" <dev@maven.apache.org>\n");
        sb.append("Subject: [VOTE] Release ").append(componentName).append("\n\n");

        sb.append("Hi,\n\n");
        sb.append("We solved ")
                .append(issueCount)
                .append(" issue")
                .append(issueCount.equals("1") ? "" : "s")
                .append(":\n");
        sb.append("https://github.com/apache/")
                .append(repoName)
                .append("/issues?q=is%3Aclosed+milestone%3A")
                .append(state.getVersion())
                .append("\n\n");

        sb.append("Changes since the last release:\n");
        sb.append("https://github.com/apache/")
                .append(repoName)
                .append("/compare/")
                .append(previousTag)
                .append("...")
                .append(currentTag)
                .append("\n\n");

        sb.append("Staging repo:\n");
        sb.append(stagingUrl).append("\n");
        sb.append(stagingUrl)
                .append("/org/apache/maven/")
                .append(state.getComponentType() == ComponentType.PLUGIN ? "plugins/" : "")
                .append(state.getArtifactId())
                .append("/")
                .append(state.getVersion())
                .append("/")
                .append(state.getArtifactId())
                .append("-")
                .append(state.getVersion())
                .append("-source-release.zip\n\n");

        sb.append("Source release checksum(s):\n");
        sb.append(state.getArtifactId())
                .append("-")
                .append(state.getVersion())
                .append("-source-release.zip sha512: ")
                .append(inputs.sha512sum())
                .append("\n\n");

        sb.append("Staging site:\n");
        sb.append("https://maven.apache.org/")
                .append(getSiteCategory(state))
                .append("-archives/")
                .append(state.getArtifactId())
                .append("-LATEST/\n\n");

        sb.append("Guide to testing staged releases:\n");
        sb.append("https://maven.apache.org/guides/development/guide-testing-releases.html\n\n");

        sb.append("Vote open for at least 72 hours.\n\n");

        sb.append("[ ] +1\n");
        sb.append("[ ] +0\n");
        sb.append("[ ] -1\n");

        return sb.toString();
    }

    /**
     * Queries the GitHub API for the count of closed issues in the given milestone.
     * Returns the count as a string, or {@code "N"} on any error so the email is
     * still generated with a placeholder.
     */
    private String fetchIssueCount(String repoName, String version) {
        try {
            GitHubClient client = new GitHubClient();
            OptionalInt count = client.countClosedItems(repoName, version);
            return count.isPresent() ? String.valueOf(count.getAsInt()) : "N";
        } catch (Exception e) {
            return "N";
        }
    }

    /**
     * Builds the full human-readable component name for use in the subject line,
     * e.g. "Apache Maven Compiler Plugin version 3.14.0".
     */
    private String buildComponentName(ReleaseState state) {
        String artifactId = state.getArtifactId();
        String version = state.getVersion();

        // Strip leading "maven-" prefix and trailing "-plugin" suffix, then title-case each word
        String stripped = artifactId.replaceFirst("^maven-", "").replaceFirst("-plugin$", "");
        String[] words = stripped.split("-");
        StringBuilder name = new StringBuilder("Apache Maven");
        for (String word : words) {
            if (!word.isEmpty()) {
                name.append(" ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        if (state.getComponentType() == ComponentType.PLUGIN) {
            name.append(" Plugin");
        }
        name.append(" version ").append(version);
        return name.toString();
    }

    /**
     * Extracts the GitHub repository name from a git remote URL.
     * Handles both SSH ({@code git@github.com:apache/foo.git}) and
     * HTTPS ({@code https://github.com/apache/foo.git}) formats.
     * Falls back to the artifactId if the URL cannot be parsed.
     */
    static String extractRepoName(String gitRemoteUrl, String fallback) {
        if (gitRemoteUrl == null || gitRemoteUrl.isBlank()) {
            return fallback;
        }
        // SSH: git@github.com:apache/repo-name.git  → last path segment before .git
        // HTTPS: https://github.com/apache/repo-name.git
        String url = gitRemoteUrl.trim();
        int lastSlash = url.lastIndexOf('/');
        int lastColon = url.lastIndexOf(':');
        int sep = Math.max(lastSlash, lastColon);
        if (sep < 0 || sep >= url.length() - 1) {
            return fallback;
        }
        String repoName = url.substring(sep + 1);
        if (repoName.endsWith(".git")) {
            repoName = repoName.substring(0, repoName.length() - 4);
        }
        return repoName.isEmpty() ? fallback : repoName;
    }

    private String getSiteCategory(ReleaseState state) {
        return switch (state.getComponentType()) {
            case PLUGIN -> "plugins";
            case SHARED -> "shared";
            case PARENT_POM -> "pom";
            case SKIN -> "skins";
            default -> "ref";
        };
    }
}
