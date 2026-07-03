# Database Console Information Architecture

Purpose:

```text
Define the Database Console hierarchy for Phase 1 so game data can be edited
from a safe UI instead of directly editing database tables.
```

The Database Console focuses on concrete game data and reward configuration.
Server-level behavior, rates, policies, transport rules, map metadata, spawn
rules, command gates, and item/monster metadata overrides belong to the Server
Console.

Implementation start point:

```text
See DATABASE_CONSOLE_BUILD_READINESS.md for the first-time setup flow,
database_console bootstrap, initial table list, index populate plan, and
recommended first vertical slice.
```

## Scope

### Phase 1 - Original Game DB Management

Manage these original Cosmic database areas:

- accounts.
- characters.
- inventory.
- equipment.
- skills.
- quests.
- drops.
- storage.
- monster book.
- maker/crafting DB tables.
- name/world transfers.

Exclude from Phase 1:

- `bot_owners`, because it is not original Cosmic.
- server behavior/rate/policy overrides, which belong to Server Console.
- agent profile/plan/memory systems, which belong to Agent Console.
- custom generated equip drops/rewards, which are Phase 2 because they require
  server runtime hooks.

### Phase 1 - Database Console Override Systems

Manage console-owned, override-ready records for:

- gachapon lists.
- cash gachapon / boxes.
- event rewards.
- NPC rewards.
- quest rewards.
- DB-backed Maker / craft content overrides.

Database Console override systems should manage concrete content rows: reward
items, reward quantities, reward weights/chances, shop item rows, drop rows, and
DB-backed Maker/craft records. Server runtime policy numbers, rates, level
limits, party-size limits, transport rules, spawn rules, and behavior toggles
belong to Server Console.

Phase 1 should create the override tables, UI status, validation, audit, and
publish flow from the start. A page can be `Override Ready` before the server
hook is connected, but it should not require a database redesign later.

Override lookup rule:

```text
1. Database Console published override, if enabled and valid.
2. Original DB/script/WZ value, if applicable.
3. Current hardcoded fallback.
```

## Top-Level Navigation

Recommended left navigation:

```text
Account
Create Account / Character
Mobs
Items
Rewards
Craft
World Data
Audit & Tools
```

`Character` should be reachable in two ways:

```text
Account -> Characters -> Character Detail
Global character search -> Character Detail
```

This supports both workflows:

- admin knows account name.
- admin only knows IGN.

## Ownership Boundary Matrix

Use this matrix when deciding whether a field is editable in Database Console or
only visible for context.

| Area | Database Console Owns | Server Console Owns | Database Console Can Show Read-Only |
| --- | --- | --- | --- |
| Accounts/characters | account and character DB rows, inventory, equipment, quests, skills, storage | online-session policy, server login policy | login/session state and safety warnings |
| Drops | concrete drop rows, chance, quantity, quest id | global drop modifiers, map drop policy, event rate policy | resolved effective rate preview |
| Shops | shop item rows, price, order, currency | shop behavior policy, restock/rate/feature gates | NPC/map/shop relationship context |
| Rewards | item reward rows, quantities, weights/chances, source overrides | reward rate policy, event/PQ limits, stage EXP/meso tuning | JS/XML/Java source ownership and resolved preview |
| Quests | quest reward/requirement override records when supported | quest-rate policy and scripted behavior gates | XML/script details, dialogue, start/complete NPCs |
| Maker/craft | DB-backed Maker/craft content rows | Maker output-rate policy and global Maker toggles | WZ source and script-owned exchange behavior |
| Maps | relationship context only | map metadata overrides, spawn policy, transport rules | portals, mobs, NPCs, reactors, technical WZ data |
| NPCs | relationship context and reward/shop links | NPC metadata overrides and behavior policy | NPC locations, shops, quests, scripts, dialogue summary |
| Items/mobs metadata | ownership/source relationships and DB references | item metadata overrides, monster stat overrides | WZ stats, names, icons, flags |

Rule:

```text
If changing the value alters concrete DB/content rows, Database Console can own
it. If changing the value alters runtime behavior, rates, policies, map logic,
transport, spawn behavior, or server feature gates, Server Console owns it.
```

Write boundary:

```text
Database Console UI -> Database Console API -> service/repository layer.
```

- The UI must never write directly to the original Cosmic database.
- The UI must never write directly to `database_console`.
- Original Cosmic DB writes are allowed only for explicit original-DB edit
  operations after validation, diff preview, and apply.
- Override-ready records are stored in `database_console`.
- Runtime-active override behavior is provided by server hooks/providers that
  read published console overrides and fall back to original Cosmic behavior
  when disabled or missing.

## Catalog Presentation

All major catalog/search pages should support both list and grid presentation
where practical.

List view:

- dense rows.
- more columns.
- better for sorting and comparison.

Grid view:

- icon/image-first tiles.
- better for visual browsing.
- fewer fields.

Relevant visual assets:

- item icons.
- equip icons.
- mob sprites/icons.
- NPC sprites/icons.
- map thumbnails/minimap images where available.
- character avatar previews where available.
- output item icons for craft/recipe catalogs.

Every catalog row/tile should still use the same right-dock detail model.

## Account

Purpose:

```text
Manage account-owned state and drill into characters.
```

Sections:

### Create Account

Purpose:

```text
Create a new account without opening the database directly.
```

Fields:

- account name / login id.
- password.
- confirm password.
- optional PIN/PIC fields if the server requires them.
- GM/admin level, default normal player.
- account flags, default normal player.
- gender default if account-level gender is used by the server.
- world/slot context.

Validation:

- account name is unique.
- account name matches server naming rules.
- password meets configured minimum requirements.
- password confirmation matches.
- account flags are legal for the selected admin role.
- account slot creation does not exceed server policy if slot policy is
  enforced at account creation.

Apply behavior:

- writes the original Cosmic account row.
- hashes password using the same server-compatible password path as normal
  registration/login.
- creates an audit event with actor, timestamp, account id, and selected flags.
- does not create a character unless the user continues into character creation.

Implementation reference:

- normal auto-register path:
  `src/main/java/net/server/handlers/login/LoginPasswordHandler.java`.
- spawnbot account creation path:
  `src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java`.
- account creation should reuse/extract the same insert defaults for
  `birthday`, `tempban`, generated id handling, and password hashing instead of
  creating a separate incompatible console-only account path.

### Create Character For Account

Purpose:

```text
Create a new character under an existing account using the same starter
constraints as the original client/server character creation flow.
```

Entry points:

- `Account -> Characters -> Create Character`.
- global action: `Create -> Character`, then search/select account.
- after creating a new account: `Create first character`.

Fields:

- account id/name, selected from existing account or newly created account.
- world.
- character name / IGN.
- gender.
- skin.
- hair.
- face.
- starter equipment selections.
- starter weapon selection, if the original server/client flow exposes options.
- starter top/bottom/overall/shoes selections where available.
- initial map, defaulting to original starter map for this server version.
- job, default Beginner.

Avatar preview:

- show full avatar preview before apply.
- preview updates immediately when hair, face, skin, gender, or starter equip
  changes.
- available hair/face/skin/gender/equipment choices should come from the
  original character-creation allowed lists, not from the full item catalog by
  default.
- advanced/admin mode can allow searching broader hair/face/equipment ids, but
  those selections must be marked `Non-standard` and validated.

Validation:

- IGN is unique.
- IGN follows server naming rules.
- account has available character slot in selected world.
- selected hair/face/skin/gender combination is valid.
- selected starter equipment is compatible with gender/job/slot.
- selected starter equipment exists in WZ/item metadata.
- initial map exists and is available.
- account is not banned or locked.

Apply behavior:

- writes the original Cosmic character row.
- writes starter inventory/equipment rows.
- initializes skills, quests, keymap, monster book, mount/pet defaults, and
  other rows required by the existing server character creation path.
- creates audit entries for the character and every generated starter item row.
- should reuse or mirror the server's normal character creation service where
  possible so console-created characters match client-created characters.

Implementation reference:

- normal client character creation pipeline:
  `src/main/java/client/creator/CharacterFactory.java`.
- server-side spawnbot character creation precedent:
  `src/main/java/client/creator/BotCreator.java`.
- shared primitives to reuse/extract:
  `Character.getDefault(...)`, `CharacterFactoryRecipe`,
  `ItemInformationProvider.getEquipById(...)`, equipped-slot placement, and
  `Character.insertNewChar(...)`.
- validation should account for the difference between client packet validation
  (`MakeCharInfoValidator`) and trusted server-side/admin creation, similar to
  how `BotCreator` creates a character without packet validation.

Safety notes:

- character creation should be disabled for online accounts unless the server
  explicitly supports safe live slot refresh.
- generated item instance ids must be unique.
- starter equipment should be created as normal item instances, not console
  custom generated equip definitions.
- password should never be shown again after account creation.

### Account Details

- account id.
- username.
- login state.
- account flags.
- cash/NX/points fields where applicable.
- creation/last-login fields where available.
- GM/admin fields if exposed.

### World / Slots

- world-level character slots.
- collective slot state if enabled.
- character list grouped by world.

### Transfers

- pending/completed name changes.
- pending/completed world transfers.
- approve/cancel/edit where safe.

### Ban / Security

- tempban.
- IP bans.
- MAC bans.
- HWID bans.
- reports.
- suspicious login/session flags when exposed.

### Characters

List:

- IGN.
- character id.
- level.
- job.
- map.
- online/offline status.
- meso summary.

Clicking a character opens the Character Detail workspace.

Actions:

- create character for this account.
- open character workspace.
- compare character slot usage by world.
- view account creation audit.

## Character Detail

Purpose:

```text
Manage all character-owned state from one workspace.
```

Sections:

### Character Details

- character id.
- account id/name.
- world.
- name.
- gender.
- skin.
- hair.
- face.
- level.
- job.
- EXP.
- fame.
- meso.
- GM flag.

### Stats / AP / SP

- STR / DEX / INT / LUK.
- HP / MP / max HP / max MP.
- AP.
- SP by job/book.
- AP allocation tools.
- SP allocation tools.

### Location

- current map.
- spawnpoint.
- saved locations.
- teleport rock locations.
- VIP teleport rock locations.

### Inventory / Storage

- equipped.
- equip.
- use.
- setup.
- etc.
- cash.
- account storage.
- Fredrick storage.
- Duey packages.
- gifts/notes where useful.
- 96 slots per character inventory type.
- 48 slots for storage.
- icon-grid slot editing.
- drag/drop slot movement and swap where valid.
- add item into empty slot.
- replace item in occupied slot.
- duplicate item into a new unique item instance.

### Equipment / Cash Equipment

- equipped gear slots.
- cash equip slots.
- full avatar preview data.
- avatar preview with searchable hair/face/skin/gender controls.
- equipment icon grid.
- cash equipment icon grid.
- equip stats.
- upgrade slots.
- owner.
- expiration.
- flags.
- cash id.
- validation warnings.
- equipment-slot grid editing.
- cash-equipment-slot grid editing.
- drag/drop between inventory and compatible equipped slots.
- inline selected-slot expansion for stat edits and replacement.
- compact equipment stat tooltip on hover.
- duplicate prevention for globally unique ids.

### Skills

- skill id/name.
- current level.
- master level.
- cooldowns.
- skill macros.

### Quests / Monster Book

- quest status.
- quest progress.
- completion time.
- forfeits.
- medal maps.
- area info.
- event stats.
- monster cards.
- monster book cover.

### Social

- buddies.
- guild.
- alliance.
- family.
- marriage.
- rings.
- fame logs.

### Character State

State that is important but not always top-of-mind:

- diseases.
- keymap.
- quickslot.
- cooldowns.
- saved locations.
- pet state.
- pet ignores.

### Character Audit

- edits affecting this character.
- before/after diff.
- editor.
- timestamp.
- reason/note.
- batch id.

## Mobs

Purpose:

```text
Manage monster-facing drop data and inspect where mobs appear.
```

Sections:

### Mob Details

- mob id.
- name.
- level.
- HP/MP/EXP if available from WZ/catalog.
- boss flag if available.
- WZ-derived details as read-only unless Server Console owns overrides.

### Map Spawn

- maps where mob appears.
- spawn count.
- spawn positions where cataloged.
- map name.
- map category.

Map spawn policy/rate edits belong to Server Console. Database Console can show
spawn info for context.

### Drop Table

Mob -> item view:

- item id/name.
- chance.
- min quantity.
- max quantity.
- quest id.
- actions: add/edit/remove/reorder if relevant.

### Reverse Drop Lookup

Item -> mobs view exposed inside the mob workspace for quick cross-checking.

## Items

Purpose:

```text
Answer what an item is, where it comes from, who owns it, and where it is used.
```

Sections:

### Item Details

- item id.
- name.
- type/category.
- description.
- icon.
- WZ-derived metadata.
- stack size.
- cash/quest/trade flags as read-only unless Server Console owns overrides.

### Equipment Details

For equips:

- base stats.
- required level/job/stats.
- slots.
- weapon type.
- attack speed.

### Sold By

- shop id.
- NPC id/name.
- map if known.
- price.
- currency.

### Obtained From

- mob drops.
- reactor drops.
- global drops.
- gachapon.
- cash boxes.
- quests.
- PQ rewards.
- event rewards.
- maker/crafting.
- NPC rewards.

### Used In

- quests requiring the item.
- maker/crafting recipes.
- exchange scripts if cataloged later.

### Ownership Search

- characters holding the item.
- storage entries holding the item.
- equipped-by list.
- duplicate cash id warnings.
- unusually high stat equips.

### Drop Table By Mob

Item -> mob reverse drop editor. This should use the same underlying drop table
editor as `Mobs -> Drop Table`, but with item-centric navigation.

## Rewards

Purpose:

```text
Manage reward pools and reward overrides.
```

Sections:

### Quests

- quest id/name.
- region/category if cataloged.
- start NPC.
- complete NPC.
- related maps.
- start requirements.
- complete requirements.
- EXP/meso/fame rewards.
- item rewards.
- selectable rewards.
- random/probability rewards.
- guaranteed rewards.
- take item/meso actions.
- custom generated equip rewards, Phase 2 only.
- dialogue summary.
- script ownership.
- source/override badges.
- override status.

Quest base data comes mostly from `Quest.wz` XML:

- `QuestInfo.img.xml`.
- `Check.img.xml`.
- `Act.img.xml`.
- `Say.img.xml`.
- `Exclusive.img.xml`.
- `PQuest.img.xml`.

Quest behavior may also be affected by:

- Java quest action/requirement classes.
- `scripts/quest/*.js`.
- NPC scripts that start/complete or reward quests manually.
- future Database Console overrides.
- future XML override files.

Quest override records should preserve source ownership:

- original XML.
- DB override.
- server Java override.
- server JS override.
- server XML override.
- console override.

Custom generated equip rewards are Phase 2. They should be stored separately
from original Cosmic quest XML and require a server-side reward provider/hook
before they affect gameplay.

### PQ

- PQ reward pools.
- stage rewards.
- completion rewards.
- bonus rewards.
- script-derived event reward pools.
- event level reward pools.
- stage EXP/meso values as read-only script context unless Server Console owns
  an explicit override.
- source ownership: JS, Java helper, DB override, console override.

Many PQ rewards are defined in `scripts/event/*.js` through calls such as:

- `eim.setEventRewards(eventLevel, itemSet, itemQty)`.
- `eim.setEventClearStageExp(expStages)`.
- `eim.setEventClearStageMeso(mesoStages)`.
- `eim.giveEventReward(player, eventLevel)`.
- `eim.giveEventPlayersStageReward(stage)`.

Phase 1 should catalog these as script-derived reward pools and mark them with
`Server JS Override`. It should not rewrite JS files from the normal reward
editor. Runtime overrides require a server reward override provider/hook.
PQ party size, PQ level ranges, PQ time limits, and stage EXP/meso tuning belong
to Server Console. Database Console owns item reward pool editing only when the
server has a reward override provider/hook.

### Events

- event reward pools.
- time-limited reward rules.
- reward pool enable/disable.
- guaranteed rewards.
- choice rewards.
- random weighted rewards.
- custom generated equip rewards, Phase 2 only.

### Gachapon

- machine/town.
- reward pool.
- item weight/chance.
- rare announcement flag.
- enabled/disabled.

### Cash Gacha / Boxes

- box item id.
- reward pool.
- jackpot flag.
- quantity.
- chance.

### NPC Rewards

- NPC id/name.
- script/action key.
- exchange rewards.
- selectable rewards.
- guaranteed rewards.
- random rewards.
- custom generated equip rewards, Phase 2 only.

## Custom Generated Equip Definitions

Phase:

```text
Phase 2. Do not include in Database Console Phase 1 implementation.
```

Purpose:

```text
Allow Database Console to define custom equip instances for drops/rewards
without modifying original Cosmic WZ/XML/drop rows directly.
```

Examples:

- monster drops a clean equip plus `+1 pass 60% equivalent` bonus.
- NPC reward gives an equip with `+2 pass 10% equivalent` bonus.
- event reward gives a pre-scrolled equip template.

Required concepts:

- base item id.
- generation mode.
- stat deltas or exact stat overrides.
- slots consumed.
- expiration.
- owner text.
- trade/account-bound policy.
- custom source badge.
- audit note.

Source badges:

- `Custom`.
- `Console Generated`.
- `Not Original Cosmic`.
- `Custom Stats`.
- `Custom Drop`.
- `Custom Reward`.

Storage recommendation:

- keep templates and custom drop/reward references in the Database Console DB.
- keep original Cosmic drop/reward rows unchanged.
- require a server-side provider layer to merge custom entries at runtime.

Runtime integration required:

- mob drop generation.
- quest reward item action.
- NPC/script reward helpers.
- event/PQ reward helpers.
- generated item audit/logging.

## Craft

Purpose:

```text
Manage maker and other DB-backed crafting data.
```

Sections:

### Maker

- `makercreatedata`.
- `makerrecipedata`.
- `makerrewarddata`.
- `makerreagentdata`.

Source/runtime ownership:

- original source data is `wz/Etc.wz/ItemMake.img.xml`.
- runtime Maker recipes are DB-backed through `makercreatedata`,
  `makerrecipedata`, `makerrewarddata`, and `makerreagentdata`.
- seed data lives under `src/main/resources/db/data/111-makercreate-data.sql`
  through `114-makerreagent-data.sql`.
- table schema lives in `src/main/resources/db/tables/020-maker.sql`.
- runtime reads happen in `server.ItemInformationProvider`.
- Maker execution happens through `client.processor.action.MakerProcessor` and
  `server.MakerItemFactory`.

Database Console Phase 1 can edit the DB-backed Maker tables. Original WZ data
should remain read-only source context unless a later XML override system is
implemented.

### Crafting / Exchanges

- recipe.
- required items.
- required meso.
- required level/job.
- output item.
- output quantity.
- success rate if it is DB-backed recipe content. If it is a server policy or
  script behavior value, show it as read-only and mark it Server Console or
  Server JS owned.

Non-Maker crafting/exchange behavior may be hardcoded in NPC JS scripts under
`scripts/npc`. These should be cataloged with `Server JS` source badges.
Database Console should not rewrite JS scripts from the normal craft editor.

### Reagents

- reagent item id.
- stat.
- value.

### DB-Backed Overrides

- output item/content override.
- reward table override.
- reagent table override.

Maker output-rate policy and global Maker behavior toggles belong to Server
Console. Database Console should only edit DB-backed Maker/craft content or
console-owned content overrides.

## World Data

Purpose:

```text
Expose DB-backed world data that is useful to Database Console without taking
over Server Console policy ownership.
```

Sections:

### Maps

- map id/name.
- region/street/category if cataloged.
- portal links for navigation context.
- inbound/outbound map relationships.
- mob spawn summary as read-only context.
- NPC list as read-only context.
- reactor list as read-only context.
- shop links where known.
- technical metadata from WZ/catalog.

Server-level map metadata overrides, spawn policies, transport rules, and map
behavior flags belong to Server Console. Database Console can show map data for
search, inspection, and cross-linking.

### NPCs

- NPC id/name.
- sprite/icon.
- maps where the NPC appears.
- shop links.
- quest start/complete links.
- script file links.
- reward/action links if cataloged.
- dialogue summary if cataloged.
- technical metadata from WZ/catalog.

NPC metadata overrides and NPC behavior policy belong to Server Console.
Database Console can show NPCs for search, inspection, cross-linking, shop
editing, quest/reward context, and script ownership review.

### Shops

- `shops`.
- `shopitems`.
- NPC shop links.
- item order/display position.
- item price/currency.
- shop row enable/disable if represented by override.

Shop behavior policy overrides belong to Server Console. DB-backed shop item
editing can live here.

### Reactors

- reactor drops.
- reactor ids.
- map links if cataloged.

### Field Objects

- player NPCs.
- player NPC equipment.
- player NPC field data.
- permanent life (`plife`).

## Audit & Tools

Purpose:

```text
Make every edit reviewable, reversible where possible, and safer than direct DB
editing.
```

Sections:

### Global Audit

- all changes.
- by user.
- by target type.
- by target id.
- by date.
- before/after diff.

### Pending Changes

- unapplied edits.
- validation status.
- apply/discard.
- approval workflow later.

### Change Batches

- grouped edits.
- deployment notes.
- rollback point.

### Validation

- orphaned rows.
- invalid item ids.
- invalid mob ids.
- invalid quest ids.
- duplicate cash ids.
- impossible equip stats.
- broken inventory slots.
- missing foreign keys.
- empty `NOT_STARTED` quest placeholders.

### Import / Export

- CSV import/export.
- JSON import/export.
- reward table export.
- drop table export.
- snapshot before apply.

### Rollback

- restore from saved snapshots where supported.
- reverse change batches where deterministic.

## Console-Owned Database Schema Proposal

Purpose:

```text
Keep Database Console state and overrides portable without polluting original
Cosmic tables.
```

Recommended separate console database:

```text
database_console
```

First-time setup should create this database if it is missing:

```sql
CREATE DATABASE IF NOT EXISTS database_console
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Core tables:

| Table | Purpose |
| --- | --- |
| `console_schema_migrations` | Tracks applied Database Console migrations. |
| `console_users` | Console-only users/admins, separate from game accounts. |
| `console_sessions` | Web console login sessions. |
| `console_settings` | Console app settings, setup completion, source paths, and connection aliases. |
| `console_change_batches` | One apply operation, with actor, timestamp, note, environment, and result. |
| `console_change_events` | Per-field/per-row before-after audit records linked to a batch. |
| `console_saved_filters` | User/global saved filters for catalog pages. |
| `console_entity_tags` | Manual tags/badges attached to accounts, characters, items, mobs, quests, maps, NPCs, shops, rewards, or recipes. |
| `console_validation_results` | Stored validation findings for review and historical comparison. |
| `console_index_runs` | Catalog/index build metadata, source hashes, and timestamps. |
| `console_reference_entities` | Fast catalog/search rows for DB, WZ, script, and console entities. |
| `console_reference_relationships` | Fast graph edges between items, mobs, maps, NPCs, quests, shops, rewards, recipes, accounts, and characters. |

Override tables:

| Table | Purpose |
| --- | --- |
| `console_drop_overrides` | Adds, disables, or patches mob/reactor/global drop rows. |
| `console_shop_overrides` | Adds, disables, reorders, or patches DB-backed shop item rows. |
| `console_gachapon_overrides` | Console-owned gachapon pool entries and weights. |
| `console_cash_box_overrides` | Console-owned cash box/cash gacha reward entries. |
| `console_quest_reward_overrides` | Quest reward item/meso/fame/choice/random override records. |
| `console_quest_requirement_overrides` | Quest requirement override records, if server hook exists. |
| `console_event_reward_overrides` | Event/PQ item reward pool overrides by script/event key. |
| `console_npc_reward_overrides` | NPC/script reward override records by NPC id and action key. |
| `console_maker_overrides` | DB-backed Maker content overrides. |
| `console_craft_overrides` | DB-backed craft/exchange content overrides where supported. |

Minimum shared columns for override tables:

```text
id
target_type
target_id
source_type
source_key
operation          # add, patch, disable, replace
enabled
payload_json
priority
valid_from
valid_to
created_by
created_at
updated_by
updated_at
last_published_batch_id
```

Rules:

- Original Cosmic rows remain unchanged unless the apply action explicitly edits
  an original DB table.
- Console overrides are portable and can be disabled without data loss.
- Every override row is editable/publishable in `database_console` once its
  page is implemented.
- Every runtime-active override needs a matching server hook.
- Missing hooks should show `Override Ready`, not block storing the override.
- Every override row must expose source badges in the UI.
- Custom generated equip templates stay Phase 2 and should use separate tables
  from normal reward/drop overrides.

## Runtime Hook Readiness Checklist

Use this checklist to avoid building UI edits that cannot affect gameplay.

| Feature | Database Console Phase 1 Behavior | Runtime Hook Needed Before Gameplay Changes |
| --- | --- | --- |
| Character/account/inventory/equipment DB edits | Editable and active after apply to original DB | No new hook, but online-character safety handling is required. |
| Mob drop table edits to original DB rows | Editable and active after server reload/cache refresh | Cache refresh/reload path if server keeps drops cached. |
| Reactor drop edits to original DB rows | Editable and active after server reload/cache refresh | Cache refresh/reload path if server keeps reactor drops cached. |
| Shop item edits to original DB rows | Editable and active after server reload/cache refresh | Shop cache refresh/reload path. |
| Gachapon overrides | Catalog/edit override records | Gachapon reward provider checks console override. |
| Cash box/cash gacha overrides | Catalog/edit override records | Cash box reward provider checks console override. |
| Quest reward overrides | Catalog/edit override records | Quest action/reward provider checks console override before XML action. |
| Quest requirement overrides | Catalog/edit override records | Quest requirement provider checks console override before XML check. |
| PQ/event item reward overrides | Catalog/edit override records | `EventInstanceManager` or event reward provider checks console override. |
| PQ party size/level/time | Read-only here | Server Console feature, not Database Console. |
| Stage EXP/meso tuning | Read-only here | Server Console feature, not Database Console. |
| NPC reward overrides | Catalog/edit override records | NPC/script reward helper checks console override. |
| Maker DB content edits | Editable if writing original Maker DB tables | Cache refresh/reload path if Maker data is cached. |
| Maker output-rate policy | Read-only here | Server Console feature, not Database Console. |
| Custom generated equip rewards/drops | Phase 2 design only | Dedicated generated-equip provider in drop/reward paths. |

Runtime status badges:

- `Runtime Active`: edit affects gameplay with current server hooks.
- `Catalog Only`: visible/searchable but not editable or not runtime-active.
- `Override Ready`: console record can be edited, but server hook must be
  installed/enabled.
- `Server Console Owned`: shown here for context only.
- `Requires Reload`: edit is valid but needs cache/server reload before runtime
  sees it.
