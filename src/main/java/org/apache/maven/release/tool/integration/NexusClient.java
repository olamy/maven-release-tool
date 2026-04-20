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

import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.ContentResponse;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.StringRequestContent;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;

public class NexusClient implements AutoCloseable {

    private static final String DEFAULT_BASE_URL = "https://repository.apache.org";
    private static final String STAGING_API = "/service/local/staging";
    private static final int TIMEOUT_SECONDS = 30;

    private final String baseUrl;
    private final String authHeader;
    private final HttpClient httpClient;

    public NexusClient(String baseUrl, String username, String password) throws Exception {
        this.baseUrl = baseUrl;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        this.httpClient = new HttpClient();
        this.httpClient.start();
    }

    public NexusClient(String username, String password) throws Exception {
        this(DEFAULT_BASE_URL, username, password);
    }

    public void closeStagingRepo(String repoId, String description)
            throws ExecutionException, InterruptedException, TimeoutException {
        String body = buildPromoteRequest(repoId, description);
        ContentResponse response = httpClient
                .newRequest(baseUrl + STAGING_API + "/bulk/close")
                .method(HttpMethod.POST)
                .headers(h -> {
                    h.add(HttpHeader.AUTHORIZATION, authHeader);
                    h.add(HttpHeader.CONTENT_TYPE, "application/json");
                })
                .body(new StringRequestContent("application/json", body))
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .send();

        if (response.getStatus() != 201 && response.getStatus() != 200) {
            throw new RuntimeException("Failed to close staging repo " + repoId + ": HTTP " + response.getStatus() + " "
                    + response.getContentAsString());
        }
    }

    public void releaseStagingRepo(String repoId, String description)
            throws ExecutionException, InterruptedException, TimeoutException {
        String body = buildPromoteRequest(repoId, description);
        ContentResponse response = httpClient
                .newRequest(baseUrl + STAGING_API + "/bulk/promote")
                .method(HttpMethod.POST)
                .headers(h -> {
                    h.add(HttpHeader.AUTHORIZATION, authHeader);
                    h.add(HttpHeader.CONTENT_TYPE, "application/json");
                })
                .body(new StringRequestContent("application/json", body))
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .send();

        if (response.getStatus() != 201 && response.getStatus() != 200) {
            throw new RuntimeException("Failed to release staging repo " + repoId + ": HTTP " + response.getStatus()
                    + " " + response.getContentAsString());
        }
    }

    public void dropStagingRepo(String repoId, String description)
            throws ExecutionException, InterruptedException, TimeoutException {
        String body = buildPromoteRequest(repoId, description);
        ContentResponse response = httpClient
                .newRequest(baseUrl + STAGING_API + "/bulk/drop")
                .method(HttpMethod.POST)
                .headers(h -> {
                    h.add(HttpHeader.AUTHORIZATION, authHeader);
                    h.add(HttpHeader.CONTENT_TYPE, "application/json");
                })
                .body(new StringRequestContent("application/json", body))
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .send();

        if (response.getStatus() != 201 && response.getStatus() != 200) {
            throw new RuntimeException("Failed to drop staging repo " + repoId + ": HTTP " + response.getStatus() + " "
                    + response.getContentAsString());
        }
    }

    private String buildPromoteRequest(String repoId, String description) {
        return """
                {
                  "data": {
                    "stagedRepositoryIds": ["%s"],
                    "description": "%s",
                    "autoDropAfterRelease": true
                  }
                }""".formatted(repoId, description.replace("\"", "\\\""));
    }

    @Override
    public void close() throws Exception {
        httpClient.stop();
    }
}
