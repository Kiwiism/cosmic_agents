# Minimal Cosmic Edit Install Target

This document records the target structure for installing the portable Agent
platform into a clean Cosmic-based server with the smallest practical server
patch.

The goal is to keep Cosmic-specific edits limited to lifecycle hooks, state
change hooks, config, and one admin command. Agent runtime, catalog, profile,
planner, economy, and LLM systems should remain portable.

## Target Layout

```text
portable-agent-platform/
  agent-core/
    runtime/
    plans/
    capabilities/
    policy/
    events/
    model/

  agent-catalog/
    bundle-loader/
    query-api/
    indexes/

  agent-profile/
    profile-store/
    decision-policy/
    relationship-memory/
    decision-journal/

  agent-economy/
    market-model/
    demand-signals/
    pricing/
    shop-discovery/

  agent-llm/
    mcp-tools/
    prompt-context/
    safe-command-gateway/

  agent-cosmic-adapter/
    CosmicAgentServerAdapter
    CosmicCharacterGateway
    CosmicMapGateway
    CosmicInventoryGateway
    CosmicQuestGateway
    CosmicNpcGateway
    CosmicShopGateway
    CosmicCombatGateway

  install/
    cosmic-v83/
      files/
      patches/
      install-cosmic-agent.ps1
      verify-install.ps1
      uninstall-cosmic-agent.ps1
```

## Cosmic Files To Add

These files may be copied into the target Cosmic server by the installer.

```text
src/main/java/config/AgentConfig.java
src/main/java/server/agents/cosmic/CosmicAgentPlugin.java
src/main/java/server/agents/cosmic/CosmicAgentServerAdapter.java
src/main/java/client/command/commands/gm3/AgentCommand.java
```

`CosmicAgentPlugin` is the only class existing Cosmic files should call.
`CosmicAgentServerAdapter` is the only layer that should know Cosmic internals.

## Cosmic Files To Patch

The installer should patch only these existing Cosmic files for the minimal
install.

```text
src/main/java/config/YamlConfig.java
config.yaml
src/main/java/net/server/Server.java
src/main/java/net/server/channel/handlers/PlayerLoggedinHandler.java
src/main/java/client/Client.java
src/main/java/client/Character.java
src/main/java/client/command/CommandsExecutor.java
```

## Required Hook Points

### Server Startup

File:

```text
src/main/java/net/server/Server.java
```

Add import:

```java
import server.agents.cosmic.CosmicAgentPlugin;
```

Add after the server is marked online:

```java
// AGENT_PLATFORM_BEGIN server-start
CosmicAgentPlugin.start(this);
// AGENT_PLATFORM_END server-start
```

Expected nearby anchor:

```java
online = true;
Duration initDuration = Duration.between(beforeInit, Instant.now());
log.info("Cosmic is now online after {} ms.", initDuration.toMillis());
```

### Server Shutdown

File:

```text
src/main/java/net/server/Server.java
```

Add near the beginning of `shutdownInternal`:

```java
// AGENT_PLATFORM_BEGIN server-shutdown
CosmicAgentPlugin.shutdown();
// AGENT_PLATFORM_END server-shutdown
```

### Character Login

File:

```text
src/main/java/net/server/channel/handlers/PlayerLoggedinHandler.java
```

Add import:

```java
import server.agents.cosmic.CosmicAgentPlugin;
```

Add after the player is added to the map and has visited the map:

```java
// AGENT_PLATFORM_BEGIN character-login
CosmicAgentPlugin.onCharacterLogin(player);
// AGENT_PLATFORM_END character-login
```

Expected nearby anchor:

```java
player.getMap().addPlayer(player);
player.visitMap(player.getMap());
```

### Character Logout

File:

```text
src/main/java/client/Client.java
```

Add import:

```java
import server.agents.cosmic.CosmicAgentPlugin;
```

Add near the start of `disconnectInternal`, while `player` is still available:

```java
// AGENT_PLATFORM_BEGIN character-logout
CosmicAgentPlugin.onCharacterLogout(player);
// AGENT_PLATFORM_END character-logout
```

### Map Change

File:

```text
src/main/java/client/Character.java
```

Add import:

```java
import server.agents.cosmic.CosmicAgentPlugin;
```

In central map-change methods, capture the old map before mutation:

```java
// AGENT_PLATFORM_BEGIN map-change-old-map
int oldMapId = getMapId();
// AGENT_PLATFORM_END map-change-old-map
```

After map-change completion and `eventAfterChangedMap(...)`:

```java
// AGENT_PLATFORM_BEGIN map-change-event
CosmicAgentPlugin.onMapChanged(this, oldMapId, this.getMapId());
// AGENT_PLATFORM_END map-change-event
```

The minimum target is to hook both:

```java
changeMap(final MapleMap target, Portal pto)
changeMap(final MapleMap target, final Point pos)
```

### Quest Change

File:

```text
src/main/java/client/Character.java
```

Add inside `updateQuestStatus(QuestStatus qs)`, after the quest map is updated:

```java
// AGENT_PLATFORM_BEGIN quest-change
CosmicAgentPlugin.onQuestChanged(this, qs.getQuestID(), qs.getStatus().getId());
// AGENT_PLATFORM_END quest-change
```

### Agent Command

File:

```text
src/main/java/client/command/CommandsExecutor.java
```

Add import:

```java
import client.command.commands.gm3.AgentCommand;
```

Register as a GM3 command:

```java
// AGENT_PLATFORM_BEGIN command-register
addCommand("agent", 3, AgentCommand.class);
// AGENT_PLATFORM_END command-register
```

## Config Contract

Add to `YamlConfig`:

```java
public AgentConfig agents;
```

Add default config:

```yaml
agents:
  ENABLED: false
  CATALOG_PATH: "agent-catalog"
  PROFILE_PATH: "agent-profiles"
  MAX_AGENTS: 2000
  SCHEDULER_THREADS: 4
```

`ENABLED` must default to `false` so installation is inert until explicitly
enabled.

## Minimal Plugin Surface

The Cosmic plugin surface should remain small.

```java
public final class CosmicAgentPlugin {
    public static void start(Server server);
    public static void shutdown();

    public static void onCharacterLogin(Character chr);
    public static void onCharacterLogout(Character chr);
    public static void onMapChanged(Character chr, int oldMapId, int newMapId);
    public static void onQuestChanged(Character chr, int questId, int status);
}
```

Do not expose raw packet sends, server locks, mutable map collections, or quest
script internals through this plugin.

## Deferred Hooks

These are intentionally not part of the minimal Cosmic patch.

```text
inventory changed
item looted
mob killed
drop created
shop transaction
trade transaction
party joined/left
chat observed
FM shop observed
```

The first portable version should poll these through the adapter or catalog
runtime where possible. Add deeper hooks only when required for correctness or
performance.

## Installer Requirements

The installer should be anchor-based, idempotent, and reversible.

Required behavior:

- copy new files only if missing or if explicitly updating
- insert code by anchor text, not fixed line number
- wrap every insertion with `AGENT_PLATFORM_BEGIN` and `AGENT_PLATFORM_END`
- refuse to patch when an anchor cannot be found
- avoid duplicate imports and duplicate hook calls
- verify target has `pom.xml`, `config.yaml`, and expected source files
- run compile verification after patching

Example install command:

```powershell
.\install\cosmic-v83\install-cosmic-agent.ps1 `
  -CosmicRoot "C:\path\to\clean\cosmic" `
  -AgentRoot "C:\path\to\portable-agent-platform"
```

## Acceptance Criteria

Minimal install is successful when:

- a clean Cosmic clone compiles after patching
- `agents.ENABLED: false` produces no runtime behavior changes
- `agents.ENABLED: true` starts and stops the Agent runtime cleanly
- login, logout, map-change, and quest-change hooks reach the runtime bridge
- removing marker blocks restores Cosmic source files to the pre-install shape
- portable Agent modules do not import Cosmic classes except inside
  `agent-cosmic-adapter`
