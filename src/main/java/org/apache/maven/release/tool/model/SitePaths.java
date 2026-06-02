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

import java.util.Optional;

/**
 * Parsed view of a Maven component's {@code distributionManagement.site.url}, which on
 * Apache Maven projects encodes both the website URLs and the SVN publication paths.
 *
 * <p>For example, given the raw value:
 * <pre>scm:svn:https://svn.apache.org/repos/asf/maven/website/components/surefire-archives/surefire-LATEST</pre>
 * this record exposes:
 * <ul>
 *   <li>{@code archiveFolder} = {@code surefire-archives}</li>
 *   <li>{@code archiveName}   = {@code surefire-LATEST}</li>
 *   <li>{@code liveFolder}    = {@code surefire}</li>
 *   <li>{@code liveName}      = {@code surefire}</li>
 *   <li>{@code svnBase}       = {@code https://svn.apache.org/repos/asf/maven/website/components}</li>
 *   <li>{@link #stagingSiteUrl()} = {@code https://maven.apache.org/surefire-archives/surefire-LATEST/}</li>
 *   <li>{@link #liveSiteUrl()}    = {@code https://maven.apache.org/surefire/surefire/}</li>
 * </ul>
 *
 * @param archiveFolder folder under {@code /components/} for the versioned/staging site (e.g. {@code surefire-archives})
 * @param archiveName   entry under {@code archiveFolder} for the current staging/LATEST site (e.g. {@code surefire-LATEST})
 * @param liveFolder    folder under {@code /components/} for the live site (archiveFolder with {@code -archives} stripped)
 * @param liveName      entry under {@code liveFolder} for the live site (archiveName with {@code -LATEST} stripped)
 * @param svnBase       SVN base URL up to and including {@code /components} (no trailing slash)
 */
public record SitePaths(String archiveFolder, String archiveName, String liveFolder, String liveName, String svnBase) {

    private static final String SCM_SVN_PREFIX = "scm:svn:";
    private static final String COMPONENTS_SEGMENT = "/components/";
    private static final String ARCHIVES_SUFFIX = "-archives";
    private static final String LATEST_SUFFIX = "-LATEST";
    private static final String SITE_BASE = "https://maven.apache.org/";

    /**
     * Parses the raw {@code distributionManagement.site.url} value. Returns
     * {@link Optional#empty()} when the value is missing or does not match the
     * expected ASF Maven website {@code components/} layout.
     */
    public static Optional<SitePaths> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith(SCM_SVN_PREFIX)) {
            trimmed = trimmed.substring(SCM_SVN_PREFIX.length());
        }
        int idx = trimmed.indexOf(COMPONENTS_SEGMENT);
        if (idx < 0) {
            return Optional.empty();
        }
        String svnBase = trimmed.substring(0, idx + COMPONENTS_SEGMENT.length() - 1);
        String rest = trimmed.substring(idx + COMPONENTS_SEGMENT.length());
        while (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        String[] parts = rest.split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }
        String archiveFolder = parts[0];
        String archiveName = parts[1];
        String liveFolder = archiveFolder.endsWith(ARCHIVES_SUFFIX)
                ? archiveFolder.substring(0, archiveFolder.length() - ARCHIVES_SUFFIX.length())
                : archiveFolder;
        String liveName = archiveName.endsWith(LATEST_SUFFIX)
                ? archiveName.substring(0, archiveName.length() - LATEST_SUFFIX.length())
                : archiveName;
        return Optional.of(new SitePaths(archiveFolder, archiveName, liveFolder, liveName, svnBase));
    }

    /** Public URL of the staged (versioned) site, e.g. {@code https://maven.apache.org/surefire-archives/surefire-LATEST/}. */
    public String stagingSiteUrl() {
        return SITE_BASE + archiveFolder + "/" + archiveName + "/";
    }

    /** Public URL of the live site, e.g. {@code https://maven.apache.org/surefire/surefire/}. */
    public String liveSiteUrl() {
        return SITE_BASE + liveFolder + "/" + liveName + "/";
    }
}
