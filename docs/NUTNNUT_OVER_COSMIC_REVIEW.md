# NuTNNuT Over Cosmic Change Review

Purpose: review NuTNNuT's non-bot changes over upstream `P0nk/Cosmic` and decide which behavior to keep, revert, port, or redesign.

This file is a review control document, not an implementation patch list. Use it to make decisions first, then apply changes in focused commits.

Scope rule from 2026-07-02:

- This review list should focus on non-bot-related changes, or changes that are clearly not part of bot runtime.
- Bot runtime, bot commands, bot protocol, bot persistence, bot ownership, agent navigation/combat/looting support, and bot-motivated shared helpers are deferred to the agent reconstruction review.
- If a shared server change has both normal-player behavior and bot/agent support impact, keep only the normal-player/server behavior question in this review and move the bot/agent part to reconstruction.

## Comparison Baseline

- Upstream Cosmic ref: `cosmic/master`
- NuTNNuT ref: `source/master`
- Last checked: 2026-07-01
- Cosmic commit: `fec53bc7714dc0f1ae3f50b2986cdf2727e0912a`
- NuTNNuT commit: `5f0447172e501d85bc406be2599f0a010a82a2d2`
- Merge base: `9f643cc739de4661745d3f0ecd4ac9c60e380905`

Diff scale:

- `2033` commits in `source/master` over `cosmic/master`.
- `22466` total changed files.
- `286` changed files excluding `wz/**`.
- `22180` changed files under `wz/**`.
- `106` obvious bot files under bot-specific paths.
- `182` non-WZ files remain after filtering obvious bot-only paths, including shared server code that may still be bot-adjacent.

Useful commands:

```bash
git diff --name-status cosmic/master..source/master -- ':!wz/**'
git diff cosmic/master..source/master -- path/to/file
git log --oneline --no-merges cosmic/master..source/master
```

## Decision Legend

- `KEEP`: keep NuTNNuT behavior.
- `REVERT`: restore upstream Cosmic behavior.
- `REDESIGN`: keep the goal but rework the implementation.
- `PORT-LATER`: useful, but defer until agent/server architecture is ready.
- `REVIEW`: not decided yet.

## Non-Bot Review Buckets

### A. WZ Asset Tree Differences

Status: `USE COSMIC ASSET SOURCE`

Scope:

- `wz/**`

Observation:

- The raw diff reports `22180` WZ file differences, mostly deletions relative to upstream Cosmic.
- This is too large to review as normal source code.
- Treat as asset packaging/layout difference, not a feature decision by itself.

Recommended decision path:

- Do not use NuTNNuT's WZ asset tree as the source of truth.
- Use Cosmic/original Cosmic-compatible assets.
- Do not commit accidental WZ mass deletes unless intentionally changing asset distribution.
- Review only targeted WZ files needed for cataloging, combat timing, navigation, or server data correctness.
- If catalog/navigation work shows foothold data needs adjustment, review targeted Cosmic-based foothold updates then.

Decision: `USE COSMIC AS BASE; REVIEW TARGETED FOOTHOLD NEEDS ONLY IF CATALOG/NAVIGATION REQUIRES IT`

### B. Config Defaults / Local Runtime Values

Status: `USE CURRENT WORKSPACE CONFIG VALUES`

Scope:

- `config.yaml`
- `src/main/java/config/ServerConfig.java`
- `src/main/java/config/WorldConfig.java`
- `src/main/java/config/LevelExpRateConfig.java`

Changes found:

- Branding/rates changed to GreenCatMS-style defaults.
- `DB_PASS` changed to `1234` in NuTNNuT source, while this workspace currently wants `root`.
- `ENABLE_PIN` disabled.
- static LAN/WAN/Tailscale addresses added.
- beginner EXP enforcement enabled.
- quest rate enabled.
- ultra nimble feet enabled.
- always max inventory added.
- custom feature toggles added for scroll bonus, Maker crystal behavior, godly stats, untradeable trade, one-of-a-kind bypass.
- world-level `mob_rate`, `max_mob_per_spawnpoint`, `maker_rate`, and `level_exp_rate_multipliers` added.

Current workspace decision:

- Use the current `config.yaml` values set in this workspace.
- Ignore NuTNNuT's `config.yaml` defaults.
- If later implementation touches config shape, preserve current local runtime values unless explicitly told otherwise.
- Keep useful config knobs only where they support the future server/agent design.

Decision: `USE CURRENT CONFIG VALUES`

### C. Gameplay Customizations Already Decided

Status: mixed

Source of prior decisions: `docs/COSMIC_REVERT_REVIEW.md`.

- Arcane River monster HP debuff: `REVERT`.
- Dynamic MP autopot threshold update: `REVERT`.
- Always max inventory slots: `KEEP`.
- Item expiration override: `REDESIGN` as on/off toggle.
- 300k HP/MP cap: `REDESIGN` as configurable cap amount.
- Per-level EXP rate multipliers: `REVERT`.
- Mob spawn rate config: `REDESIGN` as default plus per-map/per-map-set overrides.
- PIC/PIN config differences: `KEEP`.
- Quest reward godly stats: `REVERT`.
- NPC/event reward godly stats: `REVERT`.
- Maker-created godly stats: `REVERT`.
- Maker crystal only improves: `REVERT`.
- Configurable Maker crystal output rate: `KEEP`.
- Untradeable items tradeable: `KEEP`.
- One-of-a-kind check disabled: `KEEP`, but rename to clearer positive flag.
- Drop-limited maps allow drops: `KEEP`.
- KPQ coupon drop rate tweak: `REVERT`.
- `!warp` accepts map names: `KEEP`.
- `!dupe` GM command: `KEEP`.
- Dressing room commands: `KEEP`.
- EXP debug command: `KEEP`.
- GM delete-character command: `KEEP`.
- Tailscale IP support: `KEEP`.
- Movement packet logging muted: `KEEP`, review again later.
- Reusable server-side consume method: `OUT OF SCOPE HERE` if only for agent use; review under reconstruction.

Decision: see item list.

### D. Shared Combat / Damage Formula Changes

Status: `NEEDS FORMULA PARITY REVIEW`

Scope:

- `src/main/java/server/combat/CombatFormulaProvider.java`
- `src/main/java/net/server/channel/handlers/AbstractDealDamageHandler.java`
- `src/main/java/net/server/channel/handlers/CloseRangeDamageHandler.java`
- `src/main/java/net/server/channel/handlers/MagicDamageHandler.java`
- `src/main/java/net/server/channel/handlers/RangedAttackHandler.java`
- `src/main/java/server/StatEffect.java`
- skill constants touched by buff/combat handling.

Review goal:

- Verify player damage parity against Cosmic.
- Keep only normal-player combat correctness in this review.
- Defer bot/agent combat reuse, autonomous spell coverage, Magic Guard for agents, Meso Guard for agents, teleport/flash jump for agents, and capability concerns to reconstruction.
- Do not change this area from the NuTNNuT review track yet.

Decision: `REVIEW WHETHER PLAYER-FACING FORMULA MATCHES ORIGINAL COSMIC; AGENT COMBAT REUSE IS RECONSTRUCTION SCOPE`

### E. Shared Map / Physics / Navigation Helpers

Status: `OUT OF SCOPE IF BOT/AGENT RUNTIME`

Scope:

- `src/main/java/server/maps/Foothold.java`
- `src/main/java/server/maps/FootholdTree.java`
- `src/main/java/server/maps/Rope.java`
- `src/main/java/server/maps/MapFactory.java`
- `src/main/java/server/maps/MapleMap.java`
- `src/main/java/server/life/SpawnPoint.java`

Review goal:

- Defer agent navigation, route ETA, foothold graph, ropes, swim navigation, background simulation, and bot/agent pathing helpers to reconstruction.
- Keep only direct normal-player/server behavior questions in this review, such as spawn-rate changes or Arcane River HP behavior.
- Do not change this area yet.

Known decisions:

- Arcane River monster HP multiplier: `REVERT`.
- Mob spawn rate / max mobs per spawnpoint: `REDESIGN` as default plus per-map/per-map-set overrides.
- `onUserEnter` / `onFirstUserEnter` bot/client behavior: leave to agent reconstruction.
- Hidden/controller changes for agent/server simulation: leave to agent reconstruction.
- Foothold/rope helpers: reconstruction/catalog scope unless they affect normal players.

Decision: `BOT/AGENT MAP-PHYSICS HELPERS OUT OF SCOPE HERE; NORMAL SERVER BEHAVIOR ONLY`

### F. Stability / Bug-Fix Candidates

Status: mostly `KEEP`

Known useful items:

- Ranking login task null guard: `KEEP`.
- Friendship ring concurrent modification fix: `KEEP`, review again later.
- Summon values snapshot fix: `KEEP`, review again later.
- Inventory list snapshot fix: `KEEP`, review again later.
- Equip slot text-slot null guard: `KEEP`.
- Server-only hardening already committed in this branch: `KEEP`.

Additional server-hardening work should live in `docs/SERVER_SCALE_TODO.md`.

Decision: mostly `KEEP`

### G. Admin / QoL Commands

Status: `KEEP NON-BOT COMMANDS FOR NOW / REVIEW AGAIN`

Scope:

- Original Cosmic commands with NuTNNuT modifications:
  - `DisposeCommand`
  - `RatesCommand`
  - `ShowRatesCommand`
  - `BuffMeCommand`
  - `BuffMapCommand`
  - `GmShopCommand`
  - `ItemCommand`
  - `ItemDropCommand`
  - `SearchCommand`
  - `WarpCommand`
  - `WhatDropsFromCommand`
  - `WhoDropsCommand`
- NuTNNuT-added non-bot commands:
  - `MapSearchHelper`
  - `DeleteCharCommand`
  - `DressingRoomCommand`
  - `DressingRoomCashCommand`
  - `DupeCommand`
  - `ExpDebugCommand`
  - `MobRateCommand`
  - `MobpointRateCommand`
- Related command registration in `CommandsExecutor`.

Out of scope here:

- Bot commands such as spawn/config/nav/perf/LLM/ownership/airshow; leave those to reconstruction.

Review goal:

- Keep commands that improve admin workflow.
- Harden destructive commands with permission and confirmation.
- Rework hard-coded paths or local assumptions.

Decision: `KEEP NON-BOT COMMANDS FOR NOW; MARK NUTNNUT-ADDED COMMANDS ABOVE; BOT COMMANDS OUT OF SCOPE; REVIEW AGAIN`

### H. Economy / Item System Changes

Status: mixed

Scope:

- `src/main/java/server/ItemInformationProvider.java`
- `src/main/java/server/MakerItemFactory.java`
- `src/main/java/server/Shop.java`
- `src/main/java/server/Trade.java`
- `src/main/java/client/inventory/Item.java`
- `src/main/java/client/inventory/Inventory.java`
- `src/main/java/client/inventory/manipulator/InventoryManipulator.java`
- `src/main/java/client/processor/action/MakerProcessor.java`
- `src/main/java/net/server/channel/handlers/ScrollHandler.java`
- `src/main/java/net/server/channel/handlers/PlayerInteractionHandler.java`

Known decisions:

- Follow the user's detailed item/economy inputs already recorded in this review set.
- Scroll success bonus: `KEEP` as optional toggle plus configurable value.
- Godly stat reward paths: `REVERT` for quest/NPC/Maker/PQ reward paths to original Cosmic behavior by default, but keep/reintroduce separate toggles and configurable value ranges for each path.
- Maker crystal output rate: `KEEP`.
- Maker crystal only improves: `REVERT`.
- Item expiration override: `REDESIGN` as on/off toggle instead of hardcoded permanent items.
- Untradeable trade/drop override: `KEEP`, but consider future per-item/per-world control.
- One-of-a-kind bypass: `KEEP`, rename flag, and make configurable by item-id array for now.

Decision: mixed; preferences recorded above, no implementation yet.

### I. Quest / NPC / Script Changes

Status: `DECISIONS RECORDED / SOME REVIEW LATER`

Scope:

- `scripts/event/**`
- `scripts/npc/**`
- `scripts/item/**`
- `scripts/portal/**`
- `src/main/java/scripting/**`
- `src/main/java/server/quest/**`
- `src/main/resources/db/data/152-drop-data.sql`

Known decisions:

- KPQ coupon drop rate: `REVERT`.
- Quest reward godly stats: `REVERT`.
- NPC/event reward godly stats: `REVERT`.
- Dressing room script flow: `KEEP`.
- EXP reward helper conversion from manual `gainExp(x * rate)` to `gainExpRateScaled(...)` / `gainQuestExpRateScaled(...)`: `KEEP`, review again later.
- Scripts changed from `cm.gainExp(amount * rate)` to `cm.gainExp(amount)`: `KEEP`, review again later to confirm rate application is not lost or double-applied.
- `TD_Battle1.js` min players `2 -> 1`: `REVERT` to original value.
- `2030000.js` level requirement around `50 -> 30`: `REVERT` to original value.
- `scripts/npc/dressingroom.js`: `KEEP`, review again later.
- `AbstractPlayerInteraction` pending NPC talk message storage and dressing room item spawning: `KEEP`, review again later.
- `Quest.java` bot sync calls: bot-related; leave to bot/agent reconstruction.
- `ItemAction.java` godly-stat quest reward logic: follow economy decision, revert default to Cosmic and later allow toggle/range.

Review goal:

- Separate real script bug fixes from custom progression/reward tuning.
- Preserve NPC catalog compatibility and future agent NPC interaction support.

Decision: `APPLY RECORDED DECISIONS LATER; KEEP RATE/DRESSINGROOM CHANGES FOR NOW WITH REVIEW MARKERS; REVERT CUSTOM TD_BATTLE1 AND 2030000 TUNING`

### J. Networking / Login / Session Changes

Status: `KEEP NON-BOT / EXCLUDE BOT PROTOCOL`

Scope:

- `src/main/java/net/server/Server.java`
- `src/main/java/net/server/coordinator/login/LoginBypassCoordinator.java`
- `src/main/java/net/server/coordinator/session/IpAddresses.java`
- `src/main/java/net/server/handlers/login/AcceptToSHandler.java`
- `src/main/java/net/server/handlers/login/DeleteCharHandler.java`
- `src/main/java/net/server/handlers/login/LoginPasswordHandler.java`
- `src/main/java/client/Client.java`
- `src/main/java/net/PacketProcessor.java`
- opcode changes.

Out of scope here:

- `BOT_EQUIP` opcodes and handler.
- Bot-specific disconnect cleanup.
- Bot ownership/session shortcuts.
- Bot-client protocol support.

Known direction:

- If a change is only `config.yaml`, follow the current workspace config value.
- Keep non-bot networking/login/session changes for now.
- Exclude bot-related networking/session changes from this review.
- Bot-specific networking/session protocol belongs to reconstruction.
- Keep local/LAN/Tailscale support if deployment needs it.
- Keep logout/account-unlock hardening.
- Review login/PIC/PIN behavior against desired live-server security.

Decision: `KEEP NON-BOT NETWORKING/LOGIN/SESSION FOR NOW; CURRENT CONFIG VALUES WIN; BOT PROTOCOL OUT OF SCOPE`

### K. Database / Resource Changes

Status: `REVIEW AGAIN / BOT OWNERSHIP LEGACY`

Scope:

- `src/main/resources/db/changelog-tables.xml`
- `src/main/resources/db/data/152-drop-data.sql`
- `src/main/resources/db/tables/025-bot-ownership.sql`

Known direction:

- Bot ownership table should be treated as legacy.
- Future plan should remove/replace bot ownership DB dependency when the reconstructed agent system is ready.
- KPQ coupon drop data: `REVERT` to Cosmic.
- Changelog/resource registration for new table/resources: `KEEP`, review again later.
- Review DB changes for migration safety before applying to production-like data.

Decision: `BOT OWNERSHIP LEGACY WITH FUTURE REMOVAL PLAN; REVERT KPQ COUPON DROP; KEEP CHANGELOG FOR NOW WITH REVIEW MARKER`

### L. Docs / Tooling / Local Assistant Files

Status: `KEEP FOR REFERENCE`

Scope:

- `.claude/**`
- `CLAUDE.md`
- `AGENTS.md`
- `.mvn/maven.config`
- `tools/**`
- bot docs and formula notes.

Recommended direction:

- Keep docs/tooling/assistant files for reference.
- Do not rely on local assistant/tooling files as server behavior.
- Review `.claude/**` and local settings before committing to shared branches.

Decision: `KEEP FOR REFERENCE`

## Suggested Review Order

1. Review player-facing shared combat formula parity against original Cosmic.
2. Implement/review recorded Quest / NPC / Script decisions later.
3. Implement/review recorded Database / Resource decisions later.
4. Defer bot/agent map-physics helpers to reconstruction; review only normal server behavior changes here.
5. Review movement packet logging, friendship ring snapshots, summon snapshots, inventory snapshots, and non-bot Admin/QoL commands again later.
6. Leave bot/agent system and bot runtime support to reconstruction.
7. Use Cosmic WZ assets, not NuTNNuT's WZ asset tree.

## Open Review Questions

- Should scroll success bonus remain as an optional toggle, or be removed entirely?
- Should high HP/MP cap be per-world, global, or class-specific?
- Should mob-rate overrides live in server config, catalog data, or both?
- Should one-of-a-kind bypass be global, per-item, or per-world?
- Which admin commands are safe for production GMs versus local-only tooling?
- Which bot-derived shared helpers should become part of the portable agent package?
- Should WZ XML remain in-repo, be external assets, or be generated/imported by catalog builders?
