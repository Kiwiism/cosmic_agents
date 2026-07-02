# Cosmic Revert Review List

Pending items to review before reverting NuTNNuT custom behavior back toward original Cosmic.

## Gameplay / Server Behavior

- Arcane River monster HP debuff
  - Current custom behavior: maps whose ID prefix is `450` spawn monsters with `0.01x` HP.
  - Code: `src/main/java/server/maps/MapleMap.java`, `getBuffMultiplierFromMapID`.
  - Related code: `src/main/java/server/life/Monster.java`, `buff(double multiplier)`.
  - Cosmic revert target: remove the map-prefix HP multiplier and spawned monster `buff(...)` call.
  - Decision: revert to original Cosmic behavior.

- Dynamic MP autopot threshold update
  - Current custom behavior: when pet MP autopot triggers, the character's saved MP alert threshold can be raised to the trigger ratio plus `0.01`.
  - Code: `src/main/java/client/processor/action/PetAutopotProcessor.java`, `triggerAutopotAction`.
  - Related handler: `src/main/java/net/server/channel/handlers/PetAutoPotHandler.java`.
  - Cosmic revert target: restore static Cosmic autopot threshold handling and avoid dynamically raising the saved MP threshold.
  - Decision: revert to original Cosmic behavior.

- Always max inventory slots
  - Current custom behavior: if `ALWAYS_MAX_INVENTORY_SLOTS` is enabled, new characters and loaded characters are normalized to max equip/use/setup/etc slots.
  - Config: `config.yaml`, `ALWAYS_MAX_INVENTORY_SLOTS`.
  - Code: `src/main/java/client/Character.java`, `applyInitialInventorySlotLimits`, `normalizeInventorySlotLimits`, `setStandardInventorySlotLimits`.
  - Cosmic revert target: restore original starting slot limits and remove login-time normalization.
  - Decision: keep.

- Pet and item expiration override
  - Current custom behavior: `Item.setExpiration` sets pets to `Long.MAX_VALUE` and all non-pet items to `-1`, ignoring the requested expiration value.
  - Code: `src/main/java/client/inventory/Item.java`, `setExpiration`.
  - Cosmic revert target: restore Cosmic expiration handling so non-permanent items preserve their requested expiry.
  - Decision: change to an on/off toggle instead of hardcoded behavior.

- 300k HP/MP cap and related stat calculations
  - Current custom behavior: local HP/MP caps and HP/MP ratio update math use `300000`; GM level-up also grants `300000` HP/MP.
  - Code: `src/main/java/client/AbstractCharacterObject.java`, `setMaxHp`, `setMaxMp`.
  - Code: `src/main/java/client/Character.java`, level-up HP/MP gain, `reapplyLocalStats`, `calcHpRatioUpdate`, `calcMpRatioUpdate`.
  - Cosmic revert target: restore Cosmic HP/MP caps and remove custom 300k scaling unless intentionally keeping high-stat gameplay.
  - Decision: keep, but make the cap amount configurable instead of hardcoded `300000`.

- Per-level EXP rate multipliers
  - Current custom behavior: worlds can define `level_exp_rate_multipliers`, applied on top of the base world EXP rate.
  - Config: `config.yaml`, `level_exp_rate_multipliers`.
  - Code: `src/main/java/config/LevelExpRateConfig.java`.
  - Code: `src/main/java/config/WorldConfig.java`, `level_exp_rate_multipliers`.
  - Code: `src/main/java/net/server/Server.java`, world startup rate loading.
  - Cosmic revert target: remove the level multiplier config and restore plain world EXP rate behavior.
  - Decision: revert to original Cosmic behavior.

- Mob spawn rate config
  - Current custom behavior: worlds have `mob_rate` and `max_mob_per_spawnpoint` settings.
  - Config: `config.yaml`, `mob_rate`, `max_mob_per_spawnpoint`.
  - Code: `src/main/java/config/WorldConfig.java`.
  - Code: `src/main/java/net/server/Server.java`, world startup rate loading.
  - Related command: `src/main/java/client/command/commands/gm4/MobRateCommand.java`.
  - Related command: `src/main/java/client/command/commands/gm4/MobpointRateCommand.java`.
  - Cosmic revert target: restore fixed Cosmic spawn capacity behavior and remove runtime mob-rate commands if not wanted.
  - Decision: change design. Keep a default spawn rate for all maps, and add per-map-id custom spawn-rate entries for multiple map sets.

- PIC/PIN config differences
  - Current custom behavior: local config has `ENABLE_PIC: false` and `ENABLE_PIN: false`.
  - Config: `config.yaml`, `ENABLE_PIC`, `ENABLE_PIN`.
  - Cosmic revert target: restore original Cosmic defaults if login security parity matters.
  - Decision: keep.

- Quest reward equips can roll godly stats
  - Current custom behavior: quest reward equips can be replaced with randomized godly-stat equips based on `GODLY_STATS_QUEST_CHANCE`.
  - Config: `config.yaml`, `GODLY_STATS_ENABLED`, `GODLY_STATS_QUEST_CHANCE`.
  - Code: `src/main/java/server/quest/actions/ItemAction.java`, equip reward path.
  - Cosmic revert target: restore vanilla quest reward equip creation without godly reroll.
  - Decision: revert to original Cosmic behavior by default, but later allow this path as a separate toggle with configurable value range.

- NPC/event reward equips can roll godly stats
  - Current custom behavior: NPC/script/event reward equips can roll godly stats based on `GODLY_STATS_NPC_CHANCE`.
  - Config: `config.yaml`, `GODLY_STATS_ENABLED`, `GODLY_STATS_NPC_CHANCE`.
  - Code: `src/main/java/scripting/AbstractPlayerInteraction.java`, `gainItem`.
  - Cosmic revert target: restore vanilla NPC/script reward equip creation without godly reroll.
  - Decision: revert to original Cosmic behavior by default, but later allow this path as a separate toggle with configurable value range.

- Maker-created equips can roll godly stats
  - Current custom behavior: Maker-created equips can roll godly stats based on `GODLY_STATS_MAKER_CHANCE`.
  - Config: `config.yaml`, `GODLY_STATS_ENABLED`, `GODLY_STATS_MAKER_CHANCE`.
  - Code: `src/main/java/server/ItemInformationProvider.java`, `getEquipById` / Maker equip creation path.
  - Cosmic revert target: restore Cosmic Maker equip stat generation.
  - Decision: revert to original Cosmic behavior by default, but later allow this path as a separate toggle with configurable value range.

- Maker crystals only improve stats
  - Current custom behavior: `MAKER_CRYSTAL_ONLY_IMPROVE` makes black/dark crystal Maker rolls improve stats without negative rolls.
  - Config: `config.yaml`, `MAKER_CRYSTAL_ONLY_IMPROVE`.
  - Code: `src/main/java/server/ItemInformationProvider.java`, `scrollOptionEquipWithChaos`.
  - Cosmic revert target: restore vanilla positive/negative Maker crystal variance.
  - Decision: revert to original Cosmic behavior.

- Configurable Maker crystal output rate
  - Current custom behavior: world config has `maker_rate`, used to multiply Monster Crystal output from Maker leftover conversion.
  - Config: `config.yaml`, `maker_rate`.
  - Code: `src/main/java/config/WorldConfig.java`, `maker_rate`.
  - Code: `src/main/java/server/MakerItemFactory.java`, `generateLeftoverCrystalEntry`.
  - Related code: `src/main/java/client/processor/action/MakerProcessor.java`.
  - Cosmic revert target: restore fixed vanilla Monster Crystal output amount.
  - Decision: keep.

- Untradeable items can be made tradeable
  - Current custom behavior: `UNTRADEABLE_ITEMS_TRADEABLE` allows normally untradeable/drop-restricted items through trade, player shops, drops, and some shared inventory logic.
  - Config: `config.yaml`, `UNTRADEABLE_ITEMS_TRADEABLE`.
  - Code: `src/main/java/net/server/channel/handlers/PlayerInteractionHandler.java`, trade and player shop checks.
  - Code: `src/main/java/server/maps/MapleMap.java`, drop-limited map drop behavior.
  - Code: `src/main/java/client/inventory/manipulator/InventoryManipulator.java`.
  - Cosmic revert target: restore Cosmic restrictions for untradeable/drop-restricted items.
  - Decision: keep.

- One-of-a-kind item restriction can be disabled
  - Current custom behavior: `DISABLE_ONE_OF_A_KIND_CHECK` bypasses pickup restriction for one-of-a-kind items.
  - Config: `config.yaml`, `DISABLE_ONE_OF_A_KIND_CHECK`.
  - Code: `src/main/java/server/ItemInformationProvider.java`, `isPickupRestricted`.
  - Related code: `src/main/java/server/Trade.java`, one-of-a-kind inventory fit messages.
  - Related code: `src/main/java/server/quest/actions/ItemAction.java`, one-of-a-kind reward checks.
  - Cosmic revert target: restore Cosmic one-of-a-kind checks.
  - Decision: keep, but rename the config flag to a clearer positive name and make it configurable by item-id array for now.
  - Rename candidate: `ALLOW_MULTIPLE_ONE_OF_A_KIND_ITEMS`.

- Drop-limited maps allow drops when untradeable override is enabled
  - Current custom behavior: maps with `FieldLimit.DROP_LIMIT` still allow drops when `UNTRADEABLE_ITEMS_TRADEABLE` is true.
  - Config: `config.yaml`, `UNTRADEABLE_ITEMS_TRADEABLE`.
  - Code: `src/main/java/server/maps/MapleMap.java`, `spawnItemDrop`.
  - Cosmic revert target: restore disappearing drops on drop-limited maps.
  - Decision: keep.

- KPQ coupon drop rate tweak
  - Current custom behavior: KPQ coupon drop data was changed.
  - Code/data: `src/main/resources/db/data/152-drop-data.sql`.
  - Cosmic revert target: restore the original Cosmic KPQ coupon drop chance after confirming the intended rate.
  - Decision: revert to original Cosmic behavior.

- `!warp` accepts map names
  - Current custom behavior: `!warp` can resolve map name strings through `MapSearchHelper` instead of only numeric map IDs / character targets.
  - Code: `src/main/java/client/command/commands/gm2/WarpCommand.java`.
  - Helper: `src/main/java/client/command/commands/gm2/MapSearchHelper.java`.
  - Related command: `src/main/java/client/command/commands/gm2/SearchCommand.java`.
  - Cosmic revert target: restore Cosmic `!warp` argument behavior if GM command parity matters.
  - Decision: keep.

- `!dupe` GM command
  - Current custom behavior: GM2 command duplicates the first item in the user's equip inventory.
  - Code: `src/main/java/client/command/commands/gm2/DupeCommand.java`.
  - Registration: `src/main/java/client/command/CommandsExecutor.java`.
  - Cosmic revert target: remove the command and registration.
  - Decision: keep.

- Dressing room commands and selectable item prompts
  - Current custom behavior: `!dress` and `!dresscash` expose equipment lookup / selectable dressing room item flows.
  - Code: `src/main/java/client/command/commands/gm1/DressingRoomCommand.java`.
  - Code: `src/main/java/client/command/commands/gm1/DressingRoomCashCommand.java`.
  - Related script bridge: `src/main/java/scripting/AbstractPlayerInteraction.java`, `npcTalkDressingRoom`.
  - Related script: `scripts/npc/dressingroom.js`.
  - Cosmic revert target: remove custom dressing room command/script flow if not wanted.
  - Decision: keep.

- EXP debug command and tracker
  - Current custom behavior: `@expdebug` records party EXP sessions and writes logs under a hard-coded local path.
  - Code: `src/main/java/client/command/commands/gm2/ExpDebugCommand.java`.
  - Code: `src/main/java/client/ExpDebugTracker.java`.
  - Registration: `src/main/java/client/command/CommandsExecutor.java`.
  - Cosmic revert target: remove the command/tracker, or replace the hard-coded path before keeping it.
  - Decision: keep.

- GM delete-character command
  - Current custom behavior: `!deletechar` / `!delchar` lets authorized GMs delete a character after confirmation.
  - Code: `src/main/java/client/command/commands/gm3/DeleteCharCommand.java`.
  - Code: `src/main/java/client/CharacterDeletionService.java`.
  - Registration: `src/main/java/client/command/CommandsExecutor.java`.
  - Cosmic revert target: remove this admin command if Cosmic parity or safety is preferred.
  - Decision: keep.

- Tailscale IP support
  - Current custom behavior: config can allow Tailscale/private overlay addresses in session/IP handling.
  - Config: `config.yaml`.
  - Code: `src/main/java/config/ServerConfig.java`.
  - Code: `src/main/java/net/server/Server.java`.
  - Code: `src/main/java/net/server/coordinator/session/IpAddresses.java`.
  - Cosmic revert target: remove Tailscale-specific IP handling if deployment should match Cosmic exactly.
  - Decision: keep non-bot networking/login/session changes for now, excluding bot-related protocol/session changes.

- Movement packet logging muted
  - Current custom behavior: monitored packet logging suppresses noisy movement packets.
  - Code: `src/main/java/net/packet/logging/MonitoredChrLogger.java`.
  - Cosmic revert target: restore Cosmic logging behavior if packet trace parity is needed.
  - Decision: keep; review again later.

- Use-item handler exposes reusable server-side consume method
  - Current custom behavior: item use logic was refactored into `UseItemHandler.consumeUseItem(...)`, so non-packet callers can consume items and cure debuffs.
  - Code: `src/main/java/net/server/channel/handlers/UseItemHandler.java`.
  - Cosmic revert target: restore packet-only handler shape unless shared non-packet item consumption is needed.
  - Decision: leave to bot/agent reconstruction if only needed for agent use.

## Quest / NPC / Script Review Decisions

- EXP reward helper conversion
  - Current custom behavior: many scripts and quest/event paths changed from manual `gainExp(x * rate)` style calls to rate-aware helpers such as `gainExpRateScaled(...)` and `gainQuestExpRateScaled(...)`.
  - Decision: keep for now; review again later to confirm rates are not lost or double-applied.

- Plain `cm.gainExp(amount)` script conversions
  - Current custom behavior: some scripts changed from `cm.gainExp(amount * cm.getPlayer().getExpRate())` to `cm.gainExp(amount)`.
  - Decision: keep for now; review again later to confirm `cm.gainExp(...)` applies the intended rate.

- `TD_Battle1.js` minimum player count
  - Current custom behavior: minimum players changed from `2` to `1`.
  - Decision: revert to original Cosmic value.

- `2030000.js` level requirement
  - Current custom behavior: level requirement changed from around `50` to `30`.
  - Decision: revert to original Cosmic value.

- Dressing room script flow
  - Current custom behavior: added `scripts/npc/dressingroom.js` plus bridge methods for pending NPC talk messages and dressing room item spawning.
  - Decision: keep for now; review again later.

- `Quest.java` bot sync calls
  - Current custom behavior: quest start/complete can sync party bots.
  - Decision: bot-related; leave to bot/agent reconstruction.

- Quest/NPC/PQ godly reward paths
  - Current custom behavior: reward equips can roll godly stats.
  - Decision: revert defaults to Cosmic; later allow separate toggles and configurable value ranges.

## Database / Resource Review Decisions

- Bot ownership table
  - Current custom behavior: adds bot ownership DB table.
  - Decision: treat as legacy; future plan should remove/replace it after reconstruction.

- KPQ coupon drop data
  - Current custom behavior: KPQ coupon drop data changed.
  - Decision: revert to Cosmic.

- DB changelog/resource registration
  - Current custom behavior: changelog updated to include new table/resources.
  - Decision: keep for now; review again later.

## Stability / Bug-Fix Candidates

These differ from Cosmic, but they look like defensive fixes. Review before reverting; many are probably worth keeping.

- Ranking login task null-guard for `lastlogin`
  - Current behavior: null `lastlogin` timestamps are treated as `0L` instead of throwing.
  - Code: `src/main/java/net/server/task/RankingLoginTask.java`.
  - Review note: likely keep unless exact Cosmic bug parity is required.
  - Decision: keep.

- Friendship ring concurrent modification fix
  - Current behavior: friendship ring collection access is synchronized / copied before iteration.
  - Code: `src/main/java/client/Character.java`, friendship ring methods.
  - Review note: likely keep.
  - Decision: keep; review again later.

- Summon values snapshot fix
  - Current behavior: `Character.getSummonsValues()` returns a snapshot to avoid concurrent modification.
  - Code: `src/main/java/client/Character.java`, `getSummonsValues`.
  - Review note: likely keep.
  - Decision: keep; review again later.

- Inventory list snapshot fix
  - Current behavior: `Inventory.list()` returns a snapshot/copy instead of the live backing collection.
  - Code: `src/main/java/client/inventory/Inventory.java`, `list`.
  - Review note: likely keep.
  - Decision: keep; review again later.

- Equip slot text-slot null guard
  - Current behavior: invalid/unknown WZ text-slot values avoid NPE paths that could corrupt item pickup/equip handling.
  - Code: `src/main/java/constants/inventory/EquipSlot.java`, `getFromTextSlot`.
  - Related code: `src/main/java/client/Character.java`.
  - Review note: likely keep.
  - Decision: keep.

## Bot-Adjacent Shared Code

Scope update: these are out of scope for the non-bot NuTNNuT-over-Cosmic review. They are not purely bot package files, but were likely introduced for bot/agent support. Leave them to the reconstruction track unless a direct normal-player/server behavior issue is identified.

- Shared combat formula provider and damage handler changes
  - Current behavior: shared combat calculation code exists outside `server.bots`, and some normal attack handlers were touched.
  - Code: `src/main/java/server/combat/CombatFormulaProvider.java`.
  - Related handlers: `src/main/java/net/server/channel/handlers/AbstractDealDamageHandler.java`, `RangedAttackHandler.java`, `MagicDamageHandler.java`, `CloseRangeDamageHandler.java`.
  - Review note: verify player damage parity before reverting any shared combat code.
  - Decision: out of scope for bot/agent runtime review; keep for now. Review only player-facing formula parity in the non-bot track.

- Shared map/physics helpers used by movement and navigation
  - Current behavior: foothold, rope, map, and wall-collision helpers were expanded for navigation/physics.
  - Code: `src/main/java/server/maps/Foothold.java`.
  - Code: `src/main/java/server/maps/FootholdTree.java`.
  - Code: `src/main/java/server/maps/Rope.java`.
  - Code: `src/main/java/server/maps/MapleMap.java`.
  - Review note: verify normal player movement/map loading before reverting.
  - Decision: out of scope if it is bot/agent runtime support; keep for now. Review only direct normal server behavior changes in the non-bot track.
