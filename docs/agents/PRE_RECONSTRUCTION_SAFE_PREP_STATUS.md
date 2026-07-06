# Pre-Reconstruction Safe Prep Status

Purpose:

```text
Track all safe work that can be completed before the Nutnnut bot-to-Agent
reconstruction is finished, without changing live Agent runtime behavior.
```

This document is the readiness map for the broad pre-reconstruction goal. It
ties together server hardening, catalogs, Maple Island MVP, portable platform
contracts, plan cards, profiles, economy, consoles, and NuTNNuT-over-Cosmic
review records.

## Hard Boundaries

Before reconstruction is stable:

- Do not edit `src/main/java/server/agents`.
- Do not edit `src/main/java/server/bots`.
- Do not change BotClient behavior.
- Do not change live Agent navigation, combat, looting, shop, NPC, quest, or
  LLM behavior.
- Do not wire LLM, economy, profile learning, plan cards, NPC quest execution,
  or background simulation into live runtime.
- Do not change `config.yaml` values unless explicitly requested.
- Keep server changes default-preserving.
- Keep Agent-related work as docs, contracts, catalog prep, schemas, TODOs, or
  inactive server-only hooks.

Allowed before reconstruction is stable:

- server-only diagnostics and hardening that preserve behavior.
- documentation.
- catalog builders and generated read-only catalog data.
- schema and interface contracts.
- implementation plans.
- review records.
- test/soak plans that do not alter live Agent behavior.

## Readiness Matrix

| Area | Status | Primary Evidence | Safe Next Work |
| --- | --- | --- | --- |
| Server-only hardening and scaling prep | Ready for current phase | `docs/SERVER_PLAYER_SCALE_IMPLEMENTATION_PLAN.md`, `docs/SERVER_SCALE_TODO.md`, `docs/SERVER_HARDENING_DIAGNOSTICS.md` | Run soak tests and use diagnostics to choose later behavior changes. |
| Agent scaling documentation | Ready as strategy, implementation waits | `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`, `docs/agents/AGENT_ENGINE_OPTIMIZATION.md` | Promote missing scaling packages into technical specs. |
| Catalog and runtime knowledge prep | Partially ready | `docs/agents/catalog-platform/*`, `docs/agents/llm-autonomy/GAME_KNOWLEDGE_CATALOGS.md`, `tools/game-catalog`, `tools/npc-catalog`, `tools/agent-llm-catalog` | Unify catalog builders into bundle format and add validation reports. |
| Maple Island MVP documentation | Ready as first gameplay slice | `docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md`, MVP design/technical/sequence docs, `docs/agents/plans/maple-island-mvp.plan.json` | Implement only after Agent capability boundaries are stable. |
| Portable Agent architecture prep | Ready as contract | `docs/agents/POST_RECONSTRUCTION_AGENT_PLATFORM_SPECIFICATION.md`, `docs/agents/server-adapter/*` | Build installer/adapter code after reconstruction boundary is stable. |
| Plan Card system prep | Ready as contract | `docs/agents/llm-autonomy/PLAN_CARD_SYSTEM.md`, `docs/agents/plan-runtime/*`, `docs/agents/plans/maple-island-mvp.plan.json` | Add JSON schema files before coding. |
| Agent Profile system prep | Ready as contract | `docs/agents/profile-platform/*`, `docs/agents/llm-autonomy/AGENT_PROFILE_SCHEMA.md` | Add schema files and profile store after runtime boundary is stable. |
| Economy engine prep | Ready as contract | `docs/agents/llm-autonomy/ECONOMY_*`, `docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md` | Implement observation store only after event bus/profile/catalog foundation. |
| Database Console / Server Console planning | Ready as planning docs | `docs/consoles/DATABASE_CONSOLE_*`, `docs/consoles/SERVER_CONSOLE_SCOPE.md` | Keep console work modular and separate from Agent runtime. |
| NuTNNuT over Cosmic review records | Ready as decision log | `docs/NUTNNUT_OVER_COSMIC_REVIEW.md`, `docs/COSMIC_REVERT_REVIEW.md` | Apply only explicit approved reversions; leave bot-related items to reconstruction. |
| Verification | Ready for current server batch | recent clean compile and scope checks from server diagnostics commit | Repeat after every safe-prep batch. |

## Current Safe-Prep Completion State

### Completed Or Sufficiently Documented

- Server diagnostics foundation for player scaling:
  - save pressure.
  - save section timing.
  - save reasons.
  - broadcast pressure.
  - DB pool visibility.
  - timer lane visibility.
  - slow-operation thresholds in `!serverhealth`.
- Server behavior-changing optimizations are deferred and documented.
- Agent scaling tiers are documented:
  - visible/full simulation.
  - nearby/light simulation.
  - offscreen abstract simulation.
  - route ETA simulation.
  - combat/loot shortcut simulation.
  - DB write spreading and reduced persistence.
- Agent shortcut methods are documented for after reconstruction:
  - direct loot-to-inventory when unseen.
  - abstract combat resolution.
  - reduced physics ticks.
  - portal-to-portal ETA travel.
  - map population spreading.
  - deferred saves.
  - Agent DB separation.
  - reduced broadcast.
  - plan outcome simulation.
- Maple Island MVP is documented as the first vertical gameplay slice.
- Profile, economy, LLM, catalog, and server-adapter contracts are documented.
- Database Console and Server Console boundaries are documented.
- NuTNNuT-over-Cosmic decisions are recorded separately from bot/Agent
  reconstruction work.

### Prepared But Not Yet Implemented

These are intentionally not live runtime features yet:

- catalog runtime loader.
- unified catalog bundle builder.
- manifest and source hashing.
- catalog validation report generator.
- Plan Runtime.
- Capability Runtime.
- NPC/Quest interaction capability.
- Recovery policy.
- Event bus.
- Agent observability package.
- Agent simulation tier runtime.
- LLM gateway.
- profile store and adaptation runtime.
- economy observation store and market decision engine.
- portable installer/patcher.
- Agent soak test harness.

### Must Wait For Reconstruction Stability

- any edit to live Agent runtime behavior.
- any edit to legacy BotClient behavior.
- movement/combat/loot/shop/NPC/quest behavior changes for Agents.
- Agent save routing behavior.
- Agent background simulation.
- packet suppression for Agent presentation.
- LLM command execution.
- economy automation.
- profile learning that changes behavior.
- server adapter code that depends on reconstructed runtime entry points.

## Package Readiness

Use `docs/agents/PACKAGE_REGISTRY.md` as the package index.

Well-defined packages:

- Catalog Platform.
- NPC Catalog Package.
- Profile Platform.
- Economy Engine.
- Server Adapter Package.
- Maple Island MVP Package.
- Plan Runtime Package.
- Capability Runtime Package.
- NPC / Quest Interaction Capability Package.
- Runtime Event Bus.

Partially defined packages that need specs before implementation:

- Interaction Realism Package.
- Agent Engine Optimization Package.
- LLM Control Gateway Package.
- Perception / Memory Package.
- Quest / Combat Focus Policy Package.

Backlog packages to promote:

- Agent Population Director.
- Relationship / Social Graph Runtime.
- Recovery / Survival Policy.
- Agent Observability / Diagnostics.
- Portable Installer / Patcher.
- Background Action Runtime.
- Agent Soak Test Harness.

## Verification Rules For Every Safe-Prep Batch

Before committing:

```powershell
git status --short
git diff --check
git diff --cached --name-only -- src/main/java/server/agents src/main/java/server/bots src/test/java/server/agents src/test/java/server/bots
git diff --cached --name-only -- config.yaml src/main/resources/config.yaml
.\mvnw.cmd -DskipTests clean compile
```

Required result:

- no Agent/bot source files staged unless explicitly requested.
- no config YAML staged unless explicitly requested.
- compile passes, or a known unrelated reconstruction blocker is documented.
- docs state what was completed and what remains deferred.

## Next Best Safe Work

Highest value before reconstruction finishes:

1. Write Recovery Policy design and technical specs.
2. Write Agent Observability design and technical specs.
3. Write Interaction Realism design and technical specs.
4. Write Simulation Tier Runtime design and technical specs.
5. Unify catalog builder output shape in documentation before touching runtime.
6. Add catalog validation report requirements for dangling NPC, quest, shop,
   item, reactor, portal, and reward references.
7. Keep server-only diagnostics stable and collect soak evidence.

Do not start implementation of Agent gameplay packages until the reconstruction
has stable entry points and the relevant package spec exists.
