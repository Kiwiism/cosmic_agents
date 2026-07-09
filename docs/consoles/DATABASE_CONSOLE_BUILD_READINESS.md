# Database Console Build Readiness

Purpose:

```text
Define what is ready, what is still needed before implementation starts, and
how first-time setup should create and populate the Database Console database.
```

## Current Readiness

The Database Console is no longer just a pre-implementation plan. It has an
implemented Spring Boot API and Next.js web interface under `database-console/`.
This document now records the current readiness model and should not be read as
the original mockup.

Ready documents:

- `DATABASE_CONSOLE_INFORMATION_ARCHITECTURE.md`
  - ownership boundary between Database Console and Server Console.
  - Phase 1 scope.
  - account/character management.
  - account/character creation.
  - items, mobs, rewards, craft, world data, and audit tools.
  - console-owned database schema proposal.
- `DATABASE_CONSOLE_UI_DESIGN.md`
  - current implemented shell.
  - right detail dock.
  - catalog/list/grid pages.
  - account and character creation UI.
  - inventory/equipment grid UI.
  - quest/monster-book UI.
  - quality color settings.
  - route and API contract pattern.
- `SERVER_CONSOLE_SCOPE.md`
  - clear split for server behavior, rates, map overrides, NPC travel, mob stat
    overrides, commands, analytics, and publish flow.

The original smallest useful vertical slice was:

```text
First-time setup
-> connect to Cosmic DB read-only
-> create database_console
-> migrate console-owned tables
-> migrate override-ready tables
-> populate reference indexes
-> show account catalog
-> open account workspace
-> open character workspace
-> create account/character
-> edit one safe field with audit
```

That slice has been exceeded. Current follow-up work should focus on closing
page-specific gaps, runtime override hooks, audit consistency, and documented
server integration points rather than rebuilding the shell.

## What It Will Be Like

Database Console is an operational game-data editor.

Main shell:

```text
Sticky Header: Breadcrumb / Title | Page Tabs / Actions
+--------------------------------------------------------------------------------+
| Left Nav       | Search / Filters / Actions                       | Right Dock |
| Account        |                                                    | Selected   |
| Create         | Main catalog, workspace, or editor                 | element    |
| Mobs           |                                                    | details    |
| Items          |                                                    | links      |
| Rewards        |                                                    | warnings   |
| Craft          |                                                    | technical  |
| World Data     |                                                    |            |
| Audit & Tools  |                                                    |            |
+--------------------------------------------------------------------------------+
| Left Nav Footer: MySQL status | Quality settings gear | Day/Night toggle       |
+--------------------------------------------------------------------------------+
```

Default behavior:

- browse with search, filters, badges, list/grid views, and right dock.
- edit through focused controls, row actions, or page-specific save buttons.
- no global read/write toggle.
- no global pending-change bottom bar.
- destructive actions use custom confirmation dialogs, not browser dialogs.
- updates show stacked bottom-right toasts.
- right dock owns cross-links, entity details, and technical provenance.

## First-Time Setup

First-time setup should be a guided installer inside the web app and also
available as a CLI command.

Inputs:

- Cosmic DB host.
- Cosmic DB port.
- Cosmic DB name, usually `cosmic`.
- Cosmic DB user/password.
- Database Console DB name, fixed default `database_console`.
- optional console admin account, if authentication is enabled later.
- WZ/XML path, optional but recommended for icons/names/source metadata.
- maplestory.io asset base URL, optional.

Setup flow:

```text
1. Test Cosmic DB connection.
2. Verify required Cosmic tables exist.
3. Create database `database_console` if missing.
4. Run Database Console migrations.
5. Create first console admin user only if authentication is enabled.
6. Read Cosmic DB and WZ/XML sources.
7. Populate read/index tables.
8. Run validation.
9. Show setup summary and open dashboard.
```

Database name:

```sql
CREATE DATABASE IF NOT EXISTS database_console
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

The console must not create or modify original Cosmic tables during setup,
except optional read-only verification queries.

## Tables To Create First

Minimum tables for the first implementation:

```text
console_schema_migrations
console_settings
console_index_runs
console_reference_entities
console_reference_relationships
console_entity_tags
console_saved_filters
console_audit_events
console_validation_results

# Override-ready tables, created from first setup
console_drop_overrides
console_shop_overrides
console_gachapon_overrides
console_cash_box_overrides
console_quest_reward_overrides
console_quest_requirement_overrides
console_event_reward_overrides
console_npc_reward_overrides
console_maker_overrides
console_craft_overrides
```

Rule:

```text
Create override tables from day one, even if some rows are only
`Override Ready` until the matching server hook is installed.
```

## Initial Table Purposes

| Table | Purpose |
| --- | --- |
| `console_schema_migrations` | Tracks applied console DB migrations. |
| `console_settings` | Console app settings: Cosmic DB connection alias, asset URL, UI preferences, setup completion. |
| `console_index_runs` | Index build metadata and source versions. |
| `console_reference_entities` | Fast lookup rows for items, mobs, maps, NPCs, quests, accounts, characters, shops, rewards, and recipes. |
| `console_reference_relationships` | Fast graph edges such as item dropped by mob, mob appears in map, NPC appears in map, item sold by shop, quest uses item. |
| `console_entity_tags` | Manual and generated tags/badges. |
| `console_saved_filters` | Saved user/global filters for catalog pages. |
| `console_audit_events` | Per-action audit records for setup, imports, edits, runtime override changes, and destructive actions. |
| `console_validation_results` | Stored validation findings. |
| `console_drop_overrides` | Override-ready add/patch/disable rows for mob/reactor/global drops. |
| `console_shop_overrides` | Override-ready add/patch/disable/reorder rows for shops. |
| `console_gachapon_overrides` | Override-ready gachapon reward pools and weights. |
| `console_cash_box_overrides` | Override-ready cash gacha/box reward pools and weights. |
| `console_quest_reward_overrides` | Override-ready quest reward records. |
| `console_quest_requirement_overrides` | Override-ready quest requirement records, runtime-active only when supported. |
| `console_event_reward_overrides` | Override-ready event/PQ reward pools. |
| `console_npc_reward_overrides` | Override-ready NPC/script reward records. |
| `console_maker_overrides` | Override-ready DB-backed Maker content overrides. |
| `console_craft_overrides` | Override-ready DB-backed craft/exchange content overrides. |

## Override-Ready Contract

Override-ready means:

```text
The console database, API, UI, validation, audit, and publish records are ready
to store and manage overrides before every server hook exists.
```

Each override row must carry:

- enabled flag.
- operation: add, patch, disable, replace, reorder.
- target type/id.
- source type/key.
- payload JSON.
- priority.
- validation status.
- publish batch id.
- runtime status.

Runtime status values:

- `Catalog Only`: visible but not editable as an override.
- `Override Ready`: editable and publishable in `database_console`, but not live
  because the server hook is missing or disabled.
- `Runtime Active`: server hook exists and the published override affects
  gameplay.
- `Requires Reload`: server hook exists, but a cache reload is needed.
- `Server Console Owned`: shown for context, not editable here.

Server hook strategy:

```text
Phase 1 can ship provider interfaces and noop/fallback providers.
Specific gameplay hooks can be connected later without changing the console DB
shape or UI model.
```

Recommended provider contracts:

- `DropOverrideProvider`.
- `ShopOverrideProvider`.
- `GachaponOverrideProvider`.
- `RewardOverrideProvider`.
- `QuestOverrideProvider`.
- `NpcRewardOverrideProvider`.
- `MakerOverrideProvider`.
- `CraftOverrideProvider`.

Noop providers must return original Cosmic behavior. This keeps the server safe
when Database Console is not installed.

## Reference Index Model

Use generic read indexes first. This keeps the first build fast and avoids
custom tables for every catalog.

`console_reference_entities`:

```text
id
entity_type       # account, character, item, mob, map, npc, shop, quest, reward, recipe
entity_id         # original id as string for portability
display_name
subtitle
icon_url
source_type       # DB, WZ, Script, Console
source_key
payload_json
badges_json
search_text
updated_at
```

`console_reference_relationships`:

```text
id
from_type
from_id
relationship_type # drops, sold_by, appears_in, starts_quest, completes_quest, requires, rewards, owns
to_type
to_id
payload_json
source_type
source_key
badges_json
updated_at
```

Examples:

```text
item 4000000 -> dropped_by -> mob 100100
mob 100100 -> appears_in -> map 10000
npc 1012000 -> appears_in -> map 10000
quest 1008 -> rewards -> item 2000000
character 123 -> owns -> item_instance 987654
```

## Populate / Rebuild Indexes

First setup should populate indexes from:

- Cosmic DB:
  - accounts.
  - characters.
  - inventory/equipment.
  - drops.
  - shops.
  - quests/progress where DB-backed.
  - maker/craft tables.
- WZ/XML:
  - item names/icons/stats.
  - mob names/stats.
  - map names/regions/portals/mobs/NPCs/reactors.
  - NPC names and image references.
  - quest requirements/rewards/dialogue where available.
- scripts:
  - NPC/shop/reward/script references where detectable.

Index rebuild modes:

```text
Full rebuild
  Clears and repopulates reference entities/relationships.

Incremental rebuild
  Refreshes affected rows after an edit.

Validate only
  Checks current indexes and reports missing/broken relationships.
```

Setup should run full rebuild once.

## Deprecated Original Mockup Elements

Do not reintroduce these unless the product direction changes again:

- global read/write toggle.
- global pending-change bottom bar.
- browser `alert`, `confirm`, or `prompt`.
- draft/apply/discard workflow for normal row edits.
- required Database Console login.
- catalog-first navigation for character inventory/equipment pages.

Use the current page-specific save/delete controls, custom dialogs,
bottom-right toasts, and right-inspector detail model instead.

## First Setup UI

```text
Database Console Setup

[1 Database] -> [2 Console DB] -> [3 Assets] -> [4 Build Index] -> [5 Review]

Step 1: Cosmic Database
  Host     [localhost]
  Port     [3306]
  Database [cosmic]
  User     [root]
  Password [********]
  [Test Connection]

Step 2: Console Database
  Name     [database_console]
  Status   Missing
  [Create Database] [Run Migrations]

Step 3: Console Admin
  Username [admin]
  Password [********]
  Confirm  [********]

Step 4: Assets
  WZ/XML Path         [C:\...\Cosmic Agents\wz]
  maplestory.io URL   [https://maplestory.io/api/...]
  [Validate Assets]

Step 5: Build Index
  [Full Rebuild]
  Accounts      pending
  Characters    pending
  Items         pending
  Mobs          pending
  Maps          pending
  NPCs          pending
  Quests        pending
  Shops/Drops   pending

Step 6: Review
  Console DB ready
  Index version: 1
  Warnings: N
  [Open Dashboard]
```

## First Implementation Pages

Build these first:

1. Setup wizard.
2. Dashboard.
3. Account catalog.
4. Account workspace.
5. Character workspace.
6. Account/character creation.
7. Item catalog read-only.
8. Mob catalog read-only.
9. Right dock for selected account, character, item, mob, map, NPC.
10. Audit batches/events.

Then add editing:

1. account safe fields.
2. character location/stat safe fields.
3. inventory/equipment grid.
4. drop table editor.
5. shop editor.
6. reward editor.
7. maker/craft editor.

## Things Not Yet Ready

These should not block the first build, but must be decided before those pages
become runtime-active:

- exact runtime hook locations for each Database Console override provider.
- online-character safety policy for editing active players.
- exact WZ/XML parser/indexer implementation.
- icon cache policy for maplestory.io and local WZ assets.
- whether console users are local-only or can map to game GM accounts.
- production deployment shape: standalone app, package, or optional module.

## Recommended Technical Shape

Decision:

```text
Build Database Console directly into this branch as part of the master agent
source base, but keep a clean API/service boundary so it can still be separated
or ported later.
```

The UI should not connect directly to either the original Cosmic database or
`database_console`.

Write path:

```text
Browser UI
-> Database Console API
-> validation service
-> change/audit service
-> domain service
-> repository
-> database_console and/or original Cosmic DB
```

Read path:

```text
Browser UI
-> Database Console API
-> read/index service
-> database_console reference indexes
-> original Cosmic DB only when live source data is required
```

Rules:

- UI never executes SQL.
- UI never receives DB credentials.
- all edits go through API validation.
- all applied writes create an audit batch.
- `database_console` is always written through the console API/service layer.
- original Cosmic DB is written only for explicit original-DB edits, such as
  account, character, inventory, equipment, drops, shops, and DB-backed Maker
  edits.
- override rows are written to `database_console`, not original Cosmic tables.
- runtime-active overrides are read by server providers/hooks.

Embedded branch structure:

```text
src/main/java/tools/console/database/
  DatabaseConsoleServer.java
  DatabaseConsoleConfig.java

src/main/java/tools/console/database/api/
  AccountConsoleController.java
  CharacterConsoleController.java
  ReferenceConsoleController.java
  SetupConsoleController.java
  ChangeConsoleController.java

src/main/java/tools/console/database/service/
  SetupService.java
  MigrationService.java
  ReferenceIndexService.java
  AccountConsoleService.java
  CharacterConsoleService.java
  ChangeAuditService.java
  ValidationService.java

src/main/java/tools/console/database/repository/
  ConsoleRepository.java
  CosmicAccountRepository.java
  CosmicCharacterRepository.java
  ConsoleOverrideRepository.java
  ReferenceIndexRepository.java

src/main/java/tools/console/database/provider/
  DropOverrideProvider.java
  ShopOverrideProvider.java
  RewardOverrideProvider.java
  QuestOverrideProvider.java
  MakerOverrideProvider.java
  Noop*Provider.java

src/main/resources/console/database/
  web/
  migrations/
  seed/
```

Alternative standalone package shape, if separated later:

```text
database-console/
  web/
  api/
  migrations/
  indexer/
  cosmic-adapter/
  docs/
```

Runtime dependencies:

- reads Cosmic DB.
- writes `database_console`.
- writes Cosmic DB only when the admin applies an original-DB edit.
- no dependency on Agent Console.
- no dependency on Server Console.
- server can run with Database Console disabled.
- if console classes are present but disabled, providers must fall back to
  original Cosmic behavior.

Startup behavior:

```text
Default: Database Console disabled.
If enabled:
  start console API/web endpoint.
  verify database_console exists.
  expose setup wizard if not configured.
  load override providers.
If disabled:
  do not start web endpoint.
  use noop providers.
```

Preferred implementation order:

```text
1. config flag and disabled-by-default console bootstrap
2. database_console migrations and setup CLI/service
3. setup wizard API/UI
4. Cosmic DB read adapter
5. WZ/XML reference indexer
6. reference entity/relationship API
7. account/character pages
8. change/audit layer
9. first safe edit through API
10. override provider interfaces with noop providers
```
