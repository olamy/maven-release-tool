<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements. See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership. The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied. See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Apache Maven Release Tool

A TUI-based release supervisor for Apache Maven components (plugins, shared libraries, parent POMs, and core).

It wraps `mvn release:prepare` / `release:perform` and orchestrates the full Apache release ceremony: pre-release checks, staging, voting, promotion, documentation publishing, website updates, and announcements.

Built with [Tamboui](https://github.com/tamboui/tamboui) for terminal UI and [Picocli](https://picocli.info/) for CLI parsing.

## Features

- **18-step release pipeline** covering the entire process from pre-release checks to dist-tool verification
- **Interactive command confirmation** — review, edit, skip, or dry-run every command before it runs
- **Per-project command overrides** — customize commands per project (e.g., Surefire needs `-pl !surefire-its` for site builds), saved for future releases
- **State persistence** — release state saved to disk, survives the 72h+ vote period, resume anytime
- **ETA tracking** — learns step durations from past releases, projects remaining time
- **Dry-run** — global (`--dry-run`) runs the full pipeline showing commands without executing; per-step (`d` key) previews a single step
- **Rollback handling** — on `release:prepare`/`release:perform` failure, offers Retry / Rollback (`release:rollback` + `release:clean`) / Quit
- **Email generation** — vote, vote result, and announcement emails pre-filled and saved to the release directory

## Requirements

- Java 17+
- Maven 3.9+ (or Maven wrapper)
- GPG key configured for signing
- SVN client (for dist area and documentation publishing)

## Building

```bash
mvn verify
```

This produces a runnable fat JAR at `target/maven-release-tool-0.1.0-SNAPSHOT.jar` (~7MB).

## Running

```bash
# Build the fat JAR
mvn package -DskipTests

# Create an alias for convenience
alias maven-release-tool='java -jar /path/to/maven-release-tool-0.1.0-SNAPSHOT.jar'

# Or run directly
java -jar target/maven-release-tool-0.1.0-SNAPSHOT.jar --help
```

## Usage

### Start a new release

```bash
# Auto-detects artifactId and component type from pom.xml
maven-release-tool start --version 3.14.0

# Explicit options
maven-release-tool start \
  --version 3.14.0 \
  --component maven-compiler-plugin \
  --type PLUGIN \
  --project-dir /path/to/maven-compiler-plugin

# Dry-run (preview all commands without executing)
maven-release-tool start --version 3.14.0 --dry-run
```

### Resume after vote or interruption

```bash
maven-release-tool resume --component maven-compiler-plugin --version 3.14.0
```

### List in-progress releases

```bash
maven-release-tool list
```

### Show ETA statistics

```bash
maven-release-tool stats
maven-release-tool stats --type PLUGIN
```

### Clean up a completed release

```bash
maven-release-tool clean --component maven-compiler-plugin --version 3.14.0
```

## Release Pipeline Steps

The tool guides you through these steps (filtered by component type):

| # | Step | Description |
|---|------|-------------|
| 1 | Pre-release checks | GPG key, JDK, git clean, SNAPSHOT dependencies |
| 2 | Verify site | `mvn -Preporting site site:stage` |
| 3 | `release:prepare` | Version bumps, SCM tag, clean verify |
| 4 | `release:perform` | Checkout tag, deploy to staging |
| 5 | Close staging repo | Nexus REST API (requires `NEXUS_USERNAME`/`NEXUS_PASSWORD` env vars) |
| 6 | Stage documentation | `mvn site + scm-publish:publish-scm` |
| 7 | Call vote | Generates vote email, saves to `vote-email.txt` |
| 8 | Wait for vote | **Pauses here.** State saved. Resume after 72h+ vote. |
| 9 | Record vote result | Generates result email, saves to `vote-result-email.txt` |
| 10 | Copy to dist | SVN copy source release to `dist.apache.org` |
| 11 | Report release | Prompt to submit at `reporter.apache.org` |
| 12 | Promote artifacts | Nexus REST API: release staging repo to Maven Central |
| 13 | Publish documentation | `svn cp/rm` versioned docs |
| 14 | Update website | `mvn -Pupdate package` (plugins/shared) or manual (core) |
| 15 | Deploy site | `deploySite.sh` or `mvn site-deploy` |
| 16 | Wait for sync | Maven Central ~4h, website ~1h |
| 17 | Send announcement | Generates announcement email with correct recipients |
| 18 | Verify dist-tool | Check dist-tool report for errors |

## Interactive Controls

At each step, you see the command(s) that will run and their source (default or project override):

```
--- maven-release-prepare: Run mvn release:prepare ---
  Source: default (PLUGIN)
  $ mvn release:prepare -DreleaseVersion=3.14.0 -Dtag=maven-compiler-plugin-3.14.0

  [Enter/y] Accept  [e] Edit  [s] Skip  [d] Dry-run  [q] Quit
```

- **Enter/y** — accept and run the command
- **e** — edit the command, optionally save as a project override
- **s** — skip this step
- **d** — dry-run (show what would happen without executing)
- **q** — save state and quit

On failure of `release:prepare` or `release:perform`:

```
  [r] Retry (re-run after fixing the issue)
  [b] Rollback (mvn release:rollback + release:clean)
  [q] Save & quit (fix manually, resume later)
```

## Per-Project Command Overrides

Each project can have custom commands saved in `~/.maven/release-tool/projects/<name>/commands.json`.
Projects are identified by their git remote URL.

When you edit a command during a release, you're prompted to save it:

```
Save this override for future releases of this project? [y/N]
Reason (optional): Exclude ITs module from site generation
```

Overrides are resolved via a 3-level hierarchy (most specific wins):

1. Per-project saved overrides
2. Per-component-type defaults (built into the tool)
3. Global defaults (built into the tool)

Commands support variable interpolation: `${version}`, `${tag}`, `${nextVersion}`, `${artifactId}`, `${groupId}`, `${stagingRepoId}`, `${stagingRepoUrl}`.

## Storage

All data is stored under `~/.maven/release-tool/`:

```
~/.maven/release-tool/
├── projects/                            Per-project command overrides
│   └── apache-maven-compiler-plugin/
│       └── commands.json
├── releases/                            Per-release state and artifacts
│   └── maven-compiler-plugin-3.14.0/
│       ├── release-state.json           Main state file
│       ├── vote-email.txt               Generated vote email
│       ├── vote-result-email.txt        Generated result email
│       ├── announcement-email.txt       Generated announcement
│       ├── commands.log                 All executed commands
│       └── output/                      Build output per step
├── history.json                         ETA timing data
└── config.json                          User preferences
```

A release directory can be exported (tar/zip) to hand off a release mid-process to another committer.

## Nexus API Access

Steps that interact with the Nexus staging API (close, promote, drop) require:

```bash
export NEXUS_USERNAME=your-apache-id
export NEXUS_PASSWORD=your-nexus-password
```

## Component Types

The tool auto-detects component type from the pom.xml parent:

| Parent artifactId | Component Type |
|---|---|
| `maven-plugins` | PLUGIN |
| `maven-shared-components` | SHARED |
| `maven-parent` / `apache` | PARENT_POM |
| `maven` | CORE |
| `maven-skins` | SKIN |
| `maven-extensions` | EXTENSION |

Override with `--type PLUGIN` if auto-detection is wrong.
