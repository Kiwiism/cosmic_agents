# NuTNNuT Policy Implementation

Date: 2026-07-15

This records the implemented disposition of the approved NuTNNuT-over-Cosmic review set. Cosmic remains the gameplay and asset baseline. NuTNNuT behavior is retained only when it is explicit, bounded, and useful to the current project.

## N01 to N26 status

| ID | Final disposition | Implemented state |
|---|---|---|
| N01 | Discard bulk port | NuTNNuT's WZ tree is not used as an asset baseline. Cosmic WZ remains authoritative. Targeted data or foothold fixes still require an individual review and regression test. |
| N02 | Discard donor defaults | Workspace database, address, credential, and deployment settings remain authoritative. NuTNNuT local defaults are not imported. |
| N03 | Revert to Cosmic | Removed the hidden `0.01x` Arcane River monster HP multiplier from `MapleMap`. |
| N04 | Revert to Cosmic | MP autopot no longer rewrites the character's saved alert threshold when it triggers. |
| N05 | Keep | `ALWAYS_MAX_INVENTORY_SLOTS` remains the explicit existing toggle. |
| N06 | Improve | Normal requested expiration is restored by default. `ITEMS_NEVER_EXPIRE` and `PETS_NEVER_EXPIRE` are separate, explicit, default-off overrides. |
| N07 | Keep completed improvement | `PLAYER_HP_MP_CAP` remains configurable, defaults to 30,000, and retains the existing 300,000 hard safety ceiling. |
| N08 | Revert to Cosmic | Removed `level_exp_rate_multipliers`, its config type, world wiring, dynamic calculation, and display. World and quest EXP rates are the only progression multipliers. |
| N09 | Improve | World spawn defaults remain. `mob_spawn_overrides` now supports explicit map IDs and inclusive map-ID ranges, with later matches winning per field. Runtime `mobrate` and `mobpoint` commands were retired. |
| N10 | Keep locally, harden deployment | PIN and PIC remain disabled in the local profile. `DEPLOYMENT_PROFILE: production` requires both unless `ALLOW_INSECURE_PRODUCTION_AUTH` explicitly accepts the risk. |
| N11 | Keep optional | Scroll success bonus code remains, default disabled. |
| N12 | Improve | Quest reward godly stats now have an independent enable flag, chance, stat range, and HP/MP range. Default disabled. |
| N13 | Improve | NPC and event reward godly stats now have an independent enable flag, chance, stat range, and HP/MP range. Default disabled. |
| N14 | Improve | Maker reward godly stats now have an independent enable flag, chance, stat range, and HP/MP range. Default disabled. |
| N15 | Revert default | `MAKER_CRYSTAL_ONLY_IMPROVE` now defaults to `false`. The optional toggle remains. |
| N16 | Keep | Per-world `maker_rate` remains, with default `1` preserving Cosmic output. |
| N17 | Improve | The global untradeable bypass was removed. Each world now has an explicit broad switch and an item-ID allowlist. Player and Agent trade, shop, recommendation, and drop paths consume the same policy. Defaults deny bypasses. |
| N18 | Improve | The negative global one-of-a-kind flag was removed. Each world now has the positive `allow_multiple_one_of_a_kind_items` switch and `multiple_one_of_a_kind_item_allowlist`. Defaults enforce Cosmic restrictions. |
| N19 | Separate | `DROP_LIMIT` behavior is no longer coupled to item tradeability. `ALLOW_DROPS_ON_DROP_LIMIT_MAPS` is an independent, default-off override used by both Player and Agent drop paths. |
| N20 | Revert to Cosmic | KPQ coupon chance is restored to `200000` in seed data and in a Liquibase update for existing databases. |
| N21 | Revert to Cosmic | `TD_Battle1` again requires two players. No implicit solo-PQ mode was added. |
| N22 | Revert to Cosmic | NPC `2030000` again requires level 50, matching its dialogue. |
| N23 | Keep and verify | Rate-aware EXP helpers remain. Scaling is centralized in `ExperienceRateScaler` with regression coverage for exact-once scaling and overflow. |
| N24 | Keep and verify | Plain script `gainExp` calls remain rate-aware through their Java bridge. Two remaining manually multiplied event rewards were fixed, and a regression test rejects manual `getExpRate` or `getQuestExpRate` use in scripts. |
| N25 | Keep with access control | Dressing-room commands and script bridge remain. Access requires `DRESSING_ROOM_ENABLED` and the configured minimum GM level. Normal players and Agents cannot use it. Loading is skipped when disabled. |
| N26 | Keep as an Agent boundary | Reusable server-side use-item logic is package-private. The only non-packet entry is `AgentUseItemBridge`, which verifies Agent identity before delegating. Inventory slot, quantity, item ID, life-state, and effect rules remain authoritative. |

## Agent and Player boundaries

| Area | Player boundary | Agent boundary |
|---|---|---|
| Character progression | Player HP/MP caps, world EXP rates, quest rates, item expiration, and autopot settings are authoritative. | Agents use the same character-domain rules. Agent policies may choose an action but may not alter progression multipliers, expiration, or saved player-style settings. |
| Inventory legality | One-of-a-kind, untradeable, drop-restricted, shop, trade, and `DROP_LIMIT` rules are resolved from the shared world/item policy. | Agent collection, recommendation, trade, stall, dialogue, and drop services call the same policy. There is no Agent-wide tradeability bypass. |
| Item use | A player uses an item through the packet handler. The handler validates the live USE slot, item ID, quantity, life state, and effect. | An Agent may request the same operation only through `InventoryGateway` and `AgentUseItemBridge`. The bridge rejects non-Agent characters and then executes the same validated server logic. |
| Quest state | `Quest.canStart`, `Quest.start`, `Quest.canComplete`, reward validation, inventory capacity, and post-state verification remain server authority. | Agents may discover, score, commit to, and navigate toward quests, but must use the normal quest primitives. Force-complete and reset mechanisms are test/admin boundaries, not autonomous gameplay. |
| NPC and reward interaction | Normal players receive Cosmic rewards unless an explicit reward-source toggle is enabled. | Agents receive the same rewards and inventory checks. A planner cannot request godly stats or bypass a reward choice that the runtime has not resolved. |
| Dressing room | GM/admin tool only, behind enable and minimum-GM-level configuration. | Not an Agent capability. An Agent identity does not grant dressing-room access. |
| Spawn and map rules | World defaults and map-set overrides control spawns. Map field limits remain authoritative. | Agents observe and act within those map rules. They do not receive private spawn multipliers or map-drop exemptions. |
| Presentation and transport | Players enter actions through client packets and scripts. | Agents may be headless and use integration gateways, but state mutation must terminate in the same Cosmic domain operation or a narrowly validated Agent bridge. |
| Test and showcase controls | GM, test, and showcase reset controls are not normal player gameplay. | They remain explicitly named, configured, identity-scoped harnesses and must never be used by generic autonomous quest policy. |

## Configuration defaults

The checked-in configuration is a local-development profile. Production operators must review authentication, dressing-room access, permanence overrides, custom reward sources, item allowlists, and drop-limit overrides rather than inheriting local assumptions.

## Verification

- Main source compilation succeeds under Java 21.
- The scoped regression run passes 44 tests covering expiration policy, spawn override matching and precedence, item restriction allowlists, EXP scaling, script EXP-rate boundaries, Agent inventory policy, the Agent inventory gateway boundary, and Agent `DROP_LIMIT` behavior.
- A broader run reached 3,950 tests and exposed the now-fixed null-map test seam in the Agent drop check. The remaining observed errors are pre-existing legacy-dialogue tests that assume replies are enabled while committed `config.yaml` disables them; the one movement timing failure passed on a fresh targeted rerun.
- The broader run was stopped while an unrelated navigation simulation spent more than 37 minutes rebuilding one map graph. A complete green full-suite run remains the final merge gate.
