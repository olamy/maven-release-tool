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

# CLAUDE.md

This file provides guidance to Claude Code when working with this repository.

## Build Commands

```bash
# Compile
mvn compile

# Run tests (36 tests across 7 test classes)
mvn test

# Full build (compile + checkstyle + enforcer + test + fat JAR)
mvn verify

# Package fat JAR (7MB, includes all dependencies)
mvn package -DskipTests

# Run the tool
java -jar target/maven-release-tool-0.1.0-SNAPSHOT.jar --help
java -jar target/maven-release-tool-0.1.0-SNAPSHOT.jar start --dry-run
java -jar target/maven-release-tool-0.1.0-SNAPSHOT.jar start --version 3.14.0 --dry-run
java -jar target/maven-release-tool-0.1.0-SNAPSHOT.jar list
```

## Known Build Notes

- The enforcer plugin exempts `org.jline:jline` because it is a multi-release JAR
  whose FFM classes target JDK 22+ but the base classes work fine on JDK 17.
- Checkstyle is inherited from `maven-parent` and enforces braces on all `if/for/while`
  blocks and `default` cases in `switch` statements.
- RAT (Release Audit Tool) checks all files for Apache license headers. Every `.java`,
  `.md`, and `.xml` file must start with the ASF license block or RAT will fail the build.
  For Markdown files use an HTML comment `<!-- ... -->` wrapping the license.

## Architecture

This is a TUI-based CLI tool that supervises the full Apache Maven release process.
It wraps `mvn release:prepare` / `release:perform` and orchestrates the surrounding
Apache governance steps (voting, dist publishing, documentation, announcements).

### Package Layout

```
org.apache.maven.release.tool
├── MavenReleaseTool.java          Main CLI entry (Picocli subcommands: start/resume/list/clean/stats)
├── model/                         Data model (ReleaseState, ComponentType, StepState, StepResult, StepStatus)
├── steps/                         One class per release step, all implement Step interface
│   ├── Step.java                  Interface: name(), describe(), defaultCommands(), execute(), dryRun(), isApplicable()
│   ├── AbstractStep.java          Base class with CommandRunner and runCommands() helper
│   └── ...                        18 concrete steps (PreReleaseChecks through VerifyDistTool)
├── pipeline/                      PipelineBuilder (assembles steps by ComponentType) + ReleasePipeline (drives execution)
├── config/                        Per-project command overrides (CommandOverrideStore, CommandResolver, ProjectConfig)
├── persistence/                   StateStore — JSON serialization to ~/.maven/release-tool/releases/<id>/
├── eta/                           EtaTracker + EtaHistory — median step durations from past releases
├── exec/                          CommandRunner — ProcessBuilder-based command execution (no shell involved)
├── integration/                   NexusClient (Jetty HTTP Client) for Nexus staging API
└── ui/                            ReleaseDashboard (styled stdout) + CommandConfirmView (interactive prompts)
```

### Key Design Decisions

- **Shell out to `mvn`** for release:prepare/perform rather than using the ReleaseManager Java API.
  This keeps the tool transparent — user sees exact commands, uses their own settings.xml.
- **Per-project command overrides** keyed by git remote URL. Stored in
  `~/.maven/release-tool/projects/<name>/commands.json`. Resolved via 3-level hierarchy:
  project override > component-type default > global default.
- **Every command is confirmed** before execution. User can Accept/Edit/Skip/Dry-run/Quit.
  Edits can optionally be saved as project overrides for future releases.
- **State persists** across the multi-day vote period. `WaitForVoteStep` saves state and exits.
  `resume` command picks up where it left off.
- **Rollback handling** on `release:prepare`/`release:perform` failure offers
  Retry/Rollback(`release:rollback` + `release:clean`)/Quit.
- `--version` and `--next-version` are both optional on `start`. Version is auto-detected
  from pom.xml (strips `-SNAPSHOT`). The release plugin prompts for anything not provided.

### Adding a New Step

1. Create a class in `steps/` extending `AbstractStep`
2. Implement `name()`, `describe()`, `defaultCommands()`, `execute()`
3. Override `isApplicable(ComponentType)` if it's not for all component types
4. Add it to `PipelineBuilder.allSteps()` in the correct position

### Component Types

`ComponentType` enum: CORE, PLUGIN, SHARED, PARENT_POM, SKIN, EXTENSION.
Auto-detected from pom.xml parent artifactId via `ComponentType.fromParentArtifactId()`.
Each step's `isApplicable()` controls which types it runs for.

### Storage Layout

```
~/.maven/release-tool/
├── projects/<name>/commands.json   Per-project command overrides
├── releases/<id>/                  Per-release state, emails, logs
│   ├── release-state.json
│   ├── vote-email.txt
│   ├── announcement-email.txt
│   └── output/*.log
├── history.json                    ETA timing data
└── config.json                     User preferences
```
