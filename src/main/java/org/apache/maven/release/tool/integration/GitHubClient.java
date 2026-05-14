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
package org.apache.maven.release.tool.integration;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal GitHub REST API client for querying issue counts.
 * Uses the {@code GITHUB_TOKEN} environment variable for authentication when
 * present; falls back to unauthenticated (60 req/hour rate limit).
 */
public class GitHubClient {

    private static final String API_BASE = "https://api.github.com";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String token;

    public GitHubClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.token = System.getenv("GITHUB_TOKEN");
    }

    /**
     * Returns the number of closed issues associated with the given milestone on
     * the {@code apache/<repoName>} GitHub repository, or {@link OptionalInt#empty()}
     * if the count cannot be determined (network error, milestone not found, etc.).
     * The count includes both issues and pull requests, matching what GitHub shows
     * on the milestone page.
     */
    public OptionalInt countClosedItems(String repoName, String version) throws IOException, InterruptedException {
        // Use the search API without is:issue so PRs are included, matching the GitHub milestone page count
        String query = "repo:apache/" + repoName + " milestone:" + version + " is:closed";
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI uri = URI.create(API_BASE + "/search/issues?q=" + encoded + "&per_page=1");

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "maven-release-tool");

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response = http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return OptionalInt.empty();
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode totalCount = root.get("total_count");
        if (totalCount == null || !totalCount.isInt()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(totalCount.asInt());
    }
}
