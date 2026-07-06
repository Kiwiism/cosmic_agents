# Portable Agent Platform Installer Technical Specification

Purpose:

```text
Define how to install, verify, update, and uninstall the portable Agent
platform in a clean Cosmic-like server with minimal, marker-blocked edits.
```

This is a post-reconstruction implementation contract. It does not implement
the installer yet and does not change live Agent runtime behavior.

Related target contract:

- `docs/agents/server-adapter/MINIMAL_COSMIC_EDIT_INSTALL_TARGET.md`

Recommended package:

```text
agent-platform-installer
```

## Goals

- Keep the Agent platform portable across Cosmic-based servers.
- Keep server edits small, explicit, reversible, and easy to audit.
- Install portable Agent files without mixing core runtime code into unrelated
  Cosmic files.
- Use anchor-based patching instead of fixed line numbers.
- Wrap every server edit in stable marker blocks.
- Make install, update, verify, and uninstall idempotent.
- Default to inert behavior through `agents.ENABLED: false`.
- Fail safely when the target server does not match expected anchors.

## Non-Goals

- Do not implement Agent runtime behavior.
- Do not migrate existing Nutnnut bot code.
- Do not edit BotClient behavior.
- Do not silently rewrite unrelated Cosmic code.
- Do not force server owners to adopt Database Console, Server Console, or
  Agent Console.
- Do not require the target server to already contain Agent packages.

## Installer Modes

### Plan

Reads the target server and produces an install plan without writing files.

Outputs:

- detected Cosmic root.
- server flavor/version hints.
- files to copy.
- files to patch.
- anchors found.
- anchors missing.
- marker blocks already present.
- expected config additions.
- risk summary.

### Install

Copies portable files and inserts marker-block patches.

Rules:

- refuse to install if required anchors are missing.
- refuse to overwrite modified installed files unless `--update` is used.
- create backup files or a patch journal before modifying files.
- leave `agents.ENABLED` false by default.

### Verify

Checks whether the target server is correctly installed.

Verification includes:

- required files exist.
- marker blocks exist exactly once.
- imports are not duplicated.
- config key exists.
- no disabled-by-default behavior is accidentally enabled.
- compile command succeeds, when requested.

### Update

Updates installed portable files and marker-block contents.

Rules:

- only replace content inside known marker blocks.
- refuse to update if marker blocks were manually edited in unsupported ways.
- preserve server owner config values.
- record previous installed version.

### Uninstall

Removes installed files and marker-block edits.

Rules:

- remove only files installed by the installer manifest.
- remove only known marker blocks.
- leave user-created data, catalogs, profiles, and logs unless explicitly
  requested.
- verify the target compiles after uninstall when requested.

## Required CLI Shape

Suggested PowerShell entry points:

```powershell
.\install\cosmic-v83\install-cosmic-agent.ps1 `
  -CosmicRoot "C:\path\to\cosmic" `
  -AgentRoot "C:\path\to\portable-agent-platform"

.\install\cosmic-v83\verify-install.ps1 `
  -CosmicRoot "C:\path\to\cosmic"

.\install\cosmic-v83\uninstall-cosmic-agent.ps1 `
  -CosmicRoot "C:\path\to\cosmic"
```

Suggested future unified CLI:

```text
agent-platform-installer plan --cosmic-root <path> --agent-root <path>
agent-platform-installer install --cosmic-root <path> --agent-root <path>
agent-platform-installer verify --cosmic-root <path>
agent-platform-installer update --cosmic-root <path> --agent-root <path>
agent-platform-installer uninstall --cosmic-root <path>
```

## Install Manifest

Each installer package should carry a manifest.

```json
{
  "installerVersion": 1,
  "targetFamily": "cosmic-v83",
  "agentPlatformVersion": "0.1.0",
  "installedFiles": [],
  "patchedFiles": [],
  "configKeys": [],
  "markerPrefix": "AGENT_PLATFORM",
  "defaultEnabled": false
}
```

The target server should receive an install journal such as:

```text
.agent-platform-install/install-manifest.json
.agent-platform-install/install-journal.jsonl
```

The journal allows verify/update/uninstall to know what the installer owns.

## File Copy Rules

Copy groups:

- adapter files.
- plugin bridge files.
- config class files.
- optional admin command files.
- package metadata.

Rules:

- create directories when missing.
- do not overwrite existing non-installed files by default.
- compare content hashes for installed files.
- support `--dry-run`.
- support `--force` only for explicit operator-driven repair.

## Patch Rules

Patches must be:

- anchor-based.
- marker-blocked.
- idempotent.
- reversible.
- small.

Marker format:

```java
// AGENT_PLATFORM_BEGIN server-start
CosmicAgentPlugin.start(this);
// AGENT_PLATFORM_END server-start
```

Patch identifiers must be stable:

- `server-start`
- `server-shutdown`
- `character-login`
- `character-logout`
- `map-change-old-map`
- `map-change-event`
- `quest-change`
- `command-register`

## Anchor Matching

Each patch should define:

- target file.
- required imports.
- primary anchor.
- fallback anchors.
- insertion position.
- marker id.
- expected inserted content.

Example:

```json
{
  "targetFile": "src/main/java/net/server/Server.java",
  "markerId": "server-start",
  "imports": ["server.agents.cosmic.CosmicAgentPlugin"],
  "anchor": "online = true;",
  "insertAfter": "log.info(\"Cosmic is now online after {} ms.\", initDuration.toMillis());",
  "content": "CosmicAgentPlugin.start(this);"
}
```

If the anchor is ambiguous, the installer must stop and report the candidate
matches instead of guessing.

## Config Rules

`agents.ENABLED` must default to `false`.

Initial config:

```yaml
agents:
  ENABLED: false
  CATALOG_PATH: "agent-catalog"
  PROFILE_PATH: "agent-profiles"
  MAX_AGENTS: 2000
  SCHEDULER_THREADS: 4
```

Update mode must not overwrite operator-edited values.

If `config.yaml` is absent or server owner prefers Java defaults only, the
installer may still install code, but verify should report config as missing.

## Safety Checks

Before patching:

- target root exists.
- target has `pom.xml`.
- target has `config.yaml` or an accepted config alternative.
- target has expected source files.
- Git status is shown if target is a Git repository.
- required anchors are found once.
- marker blocks are not partially present.

After patching:

- marker blocks are present once.
- imports are present once.
- installed files match manifest hashes.
- config default remains disabled.
- compile command passes when requested.

## Verification Report

The verifier should output:

```json
{
  "status": "PASS",
  "targetRoot": "C:\\path\\to\\cosmic",
  "installedVersion": "0.1.0",
  "checks": [
    {
      "checkId": "server-start-marker",
      "status": "PASS",
      "file": "src/main/java/net/server/Server.java"
    }
  ],
  "warnings": [],
  "errors": []
}
```

Recommended statuses:

- `PASS`
- `WARN`
- `FAIL`
- `UNKNOWN`

## Update Rules

Update should:

- read the installed manifest.
- check current marker block hashes.
- replace only owned marker contents.
- update copied files owned by the installer.
- preserve local config values.
- write a new journal entry.
- run verify after update.

Update must refuse when:

- marker blocks are missing.
- marker block boundaries overlap.
- target file was manually edited inside marker blocks and cannot be merged.
- installed manifest is missing unless `--adopt-existing` is provided.

## Uninstall Rules

Uninstall should:

- remove marker blocks.
- remove imports only when no remaining code uses them.
- remove installed files listed in the manifest.
- preserve catalog/profile/agent data by default.
- preserve config values by default, or comment/remove only with explicit flag.
- write an uninstall report.

Uninstall must not delete arbitrary directories by computed path without
verifying they are inside the target root and listed in the manifest.

## Portability Boundaries

Only the adapter package should import Cosmic classes.

Allowed:

```text
agent-cosmic-adapter -> Cosmic server classes
CosmicAgentPlugin -> portable Agent platform APIs
```

Forbidden:

```text
agent-core -> Cosmic server classes
agent-profile -> Cosmic server classes
agent-economy -> Cosmic server classes
agent-llm -> Cosmic server classes
agent-catalog -> Cosmic runtime classes
```

The installer should optionally scan installed portable source files and report
unexpected Cosmic imports outside the adapter package.

## Tests

### Unit Tests

- marker block insert is idempotent.
- marker block remove restores original content.
- duplicate imports are not created.
- missing anchor fails safely.
- ambiguous anchor fails safely.
- update replaces only marker contents.
- uninstall removes only owned files.
- config merge preserves user-edited values.

### Golden File Tests

Use clean Cosmic fixture files:

- `Server.java`
- `PlayerLoggedinHandler.java`
- `Client.java`
- `Character.java`
- `CommandsExecutor.java`
- `YamlConfig.java`
- `config.yaml`

For each fixture:

1. apply patch.
2. compare against expected patched file.
3. apply patch again.
4. verify no change.
5. uninstall.
6. compare against original file.

### Integration Tests

- install into clean Cosmic clone.
- run compile.
- run verify.
- uninstall.
- run compile again.
- install with `agents.ENABLED: false` and confirm default inert mode.

## Implementation Order

1. Define install manifest format.
2. Build dry-run planner.
3. Build marker-block patch engine.
4. Add file copy planner.
5. Add config merge helper.
6. Add verify report.
7. Add uninstall flow.
8. Add update flow.
9. Add golden file tests.
10. Add clean-clone compile verification.

## Deferred Until After Reconstruction

- actual portable Agent runtime files.
- final Cosmic adapter implementation.
- live Agent command behavior.
- live Agent lifecycle start/stop behavior.
- console-driven installer UI.
