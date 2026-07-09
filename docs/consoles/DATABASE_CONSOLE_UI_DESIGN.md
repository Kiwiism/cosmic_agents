# Database Console UI Design

Purpose:

```text
Document the current implemented Database Console UI and keep future page ideas
aligned with the real interface.
```

The UI should feel like an operational data console, not a raw database table
viewer and not a popup-heavy admin panel. This document originally started as a
Phase 1 design mockup; the implemented console has since changed. The current
implementation below is the canonical direction. Older page examples further
down should be read as reference material and adjusted to match the current
shell when implemented.

## Current Implementation Snapshot

Implemented location:

```text
database-console/
  api/   Spring Boot API, Liquibase, catalog importer, game DB access
  web/   Next.js single-page Database Console interface
```

Current service model:

- `database-console/web` serves the Next.js interface.
- `database-console/api` reads and writes the `cosmic` game database.
- `cosmic_database_console` stores console-owned tables, catalog indexes,
  audit records, settings, and future override data.
- The live Cosmic bridge can update a narrow set of online character state when
  enabled; otherwise edits are offline database edits.

Current navigation:

```text
Dashboard
Account
  Account Search
  Create
Character
  AP / SP & Stats
  Inventory / Storage
  Equipment / Appearance
  Quests / Monster Book
Mobs
  Mob Catalog / Drop Table
Items
  Item Catalog
World Data
  Maps
  NPCs
  Shops
  Gachapon
Audit & Tools
  Global Audit
Settings
  Quality Colors
```

Current global shell:

```text
+--------------------------------------------------------------------------------+
| Left Nav | Sticky Top Header: breadcrumb/title + page tabs/actions              |
|          +---------------------------------------------------------------------+
|          | Main Workspace                                      | Right Inspector |
|          | search/filter/catalog/editor/content                | when selected   |
|          |                                                     | details/history |
|          |                                                     | technical data  |
+--------------------------------------------------------------------------------+
| Left Nav Footer: MySQL status | Quality settings gear | Day/Night toggle        |
+--------------------------------------------------------------------------------+
```

Important current differences from the original mockup:

- no global read/write toggle.
- no global pending-change bottom bar.
- most edits save immediately through focused controls or row-level actions.
- destructive actions use clean custom confirmation dialogs, not browser
  `confirm()` dialogs.
- update notifications stack at the bottom right and fade out automatically.
- night mode is supported globally and persisted locally.
- the right inspector owns entity details, cross-links, technical provenance, and
  its own back/forward history.
- top bars are full-width within the main shell and sit above the workspace and
  right inspector.

Current implemented page behavior:

- Account Search: account grid with filters for world/status/GM level, account
  right dock, character cards, and quick links to AP/SP, inventory/storage, and
  equipment/appearance.
- Create: clean account creation and character creation flow. Character creation
  supports existing-account selection, IGN check, GM level select, starter
  gender/skin/hair/face/equipment pools, and avatar preview.
- AP / SP & Stats: account-first character selection, live editable stats,
  location map autocomplete, GM level dropdown, unused AP/SP editing, AP reset
  helpers, per-job SP reset/max-all controls, and skill level editing.
- Inventory / Storage: 96-slot inventory grids by type, 48-slot storage grid,
  drag/drop within grids, drag targets between inventory and storage, item
  delete confirmation, item duplicate for inventory items, and right inspector
  item detail/edit layout.
- Equipment / Appearance: full avatar preview with cash-view toggle,
  searchable hair/face selection, skin/gender controls, equip and cash equip
  grids, equipment stat editing, equip quality border/corner indicators, and
  right inspector item detail.
- Quests / Monster Book: tabs for available, in-progress, completed, and monster
  book. Quest rows show NPC icons, status actions, readable timestamps, progress
  editing, start/forfeit/reset actions, full quest detail drawer, and clickable
  NPC/item/mob/quest references. Monster book shows all mobs grouped by book tab
  with 0-5 card count editing and medal-style completion icons.
- Mob Catalog / Drop Table: regional monster explorer, global drops, monster
  drop table editing, add-drop panel, meso drop icons, quest-id editable drop
  requirements, chance display as percent and `1 in N`, and right inspector mob
  details.
- Item Catalog: catalog search with "used in game only" default, item filters,
  grid/list style catalog cards, right inspector item details, drop/shop/gacha/
  ownership links, and equip average stat ranges.
- Maps: region/map browsing with right inspector map details, portals, mobs,
  NPCs, and technical data.
- NPCs: NPC catalog cards with icon/name/id, used-in-game badges, locations,
  shops, and technical provenance.
- Shops: sticky shop list, clean create-shop dialog, add-item autocomplete,
  order/price editing, row drag handle for ordering, clean delete confirmation,
  and right inspector item details.
- Gachapon: sticky gachapon list, tiered rarity dropdowns, clean add-rarity
  dialog, clean delete confirmation, individual reward chances, and right
  inspector item details.
- Quality Colors: settings page opened from the left-nav gear, used to configure
  non-cash equipment quality color thresholds for custom clients.

## Current Page Layouts

These layouts describe the implemented direction and supersede older mockups
where they conflict.

### Account Search

```text
Header: ACCOUNT > Account Search
Tabs: none

+-------------------------------------------------------------+----------------+
| Search account/character/id | World | Status | GM Level      | Account Dock   |
+-------------------------------------------------------------+ selected acct   |
| Account Grid                                                 | details        |
|  [account card] [account card] [account card]                | editable acct  |
|   world/status/gm/characters                                | fields         |
|   Create Character action per card                           | character      |
|                                                              | cards + links  |
+-------------------------------------------------------------+ technical      |
```

### Account / Character Create

```text
Header: ACCOUNT > Create

+-----------------------------------------+-------------------------------+
| Create Account                          | Create Character              |
|  username                               |  choose existing account       |
|  password + visible toggle              |  selected account detail dock  |
|  create account                         |  IGN + checker | GM level      |
|                                         |  gender/skin/hair/face pools   |
| Existing Account Picker                 |  starter top/bottom/shoes/wep  |
|  account cards                          |  full avatar preview           |
+-----------------------------------------+-------------------------------+
```

### AP / SP & Stats

```text
Header: ACCOUNT > CHARACTER
Tabs: AP/SP | Inventory | Equipment | Quests

+----------------------------------+--------------------------------------+
| Account/character browser         | Stats editor                         |
|  account list                     |  level/job/gm/map autocomplete       |
|  character list                   |  STR DEX INT LUK + unused AP         |
|                                   |  HP/MP/fame/location                 |
+----------------------------------+--------------------------------------+
| Skill allocation by job tier                                             |
| Beginner | 1st | 2nd | 3rd | 4th                                        |
|  unused SP, Max All, Reset All in each job header                        |
|  skill rows with value field and -/+ controls                            |
+-------------------------------------------------------------------------+
```

### Inventory / Storage

```text
Header: ACCOUNT > CHARACTER
Tabs: AP/SP | Inventory | Equipment | Quests

+-------------------------------------------------------+-------------------+
| Account/character browser + selected character summary | Item Inspector    |
+-------------------------------------------------------+ item details      |
| Inventory grids by type, 96 slots each                 | average stats     |
|  Equip / Use / Setup / Etc / Cash                      | source links      |
|  square slots, icon, quantity, tooltip                 | edit values       |
|  drag within grid                                      | save/delete       |
|  DRAG HERE TO PUT IN STORAGE                           | technical         |
+-------------------------------------------------------+                   |
| Account storage, 48 slots                              |                   |
|  storage meso                                          |                   |
|  DRAG HERE TO PUT IN INVENTORY                         |                   |
+-------------------------------------------------------+-------------------+
```

### Equipment / Appearance

```text
Header: ACCOUNT > CHARACTER
Tabs: AP/SP | Inventory | Equipment | Quests

+-------------------------------------+-------------------------------------+
| Character browser                    | Appearance                          |
| Avatar preview                       |  searchable hair                    |
|  cash view toggle                    |  searchable face                    |
|  full body with equipped items       |  skin dropdown                      |
|                                      |  gender selector                    |
+-------------------------------------+-------------------------------------+
| Equipped items grid                  | Cash equipment grid                  |
|  square slots                        |  square slots                        |
|  non-cash quality border/corner      |  no equip quality border             |
|  tooltip with stat/scroll quality    |  tooltip                             |
+-------------------------------------+-------------------------------------+
| Selected equip edit panel + right inspector with item catalog details     |
+-------------------------------------------------------------------------+
```

### Quests / Monster Book

```text
Header: ACCOUNT > CHARACTER
Tabs: AP/SP | Inventory | Equipment | Quests

+-----------------------------------+--------------------------------------+
| Account/character browser          | Quest / Mob Inspector                |
+-----------------------------------+ full quest details                    |
| Quest tabs                          | requirements, prerequisites           |
|  Available                          | completion criteria                   |
|  In Progress                        | start/actions/rewards                 |
|  Completed                          | NPC/item/mob/quest links              |
|  Monster Book                       | technical WZ data                     |
+-----------------------------------+--------------------------------------+
| Selected quest editor                                                     |
|  start / forfeit / reset actions                                          |
|  status, count, readable timestamps, progress rows                        |
| Monster Book: beginner/basic/intermediate/advanced/master, card 0-5        |
+-------------------------------------------------------------------------+
```

### Mob Catalog / Drop Table

```text
Header: MOBS > Catalog / Drop Table

+--------------------------------------------------------+------------------+
| Monster explorer / Global drops tabs                    | Mob/Item Dock     |
| Search and region filters                               | drops             |
| Region cards                                            | spawn maps        |
| Monster cards with icons                                | technical         |
+--------------------------------------------------------+------------------+
| Selected monster drop table          | Sticky Add Drop panel                |
|  item/meso rows                      |  item autocomplete                   |
|  min/max/chance/quest id             |  meso option                         |
|  percent + 1 in N                    |  chance preview                      |
|  clean delete                        |  quest id                            |
+--------------------------------------+-------------------------------------+
```

### Shops And Gachapon

```text
Header: WORLD DATA
Tabs: Maps | NPCs | Shops | Gachapon

Shops:
+------------------------------+-----------------------------------------+
| Sticky shop list              | Selected shop items                     |
| search + paging               | add item autocomplete                   |
| NPC icon/name/id              | drag handle ordering                    |
| selected row                  | position/price edit/delete              |
+------------------------------+-----------------------------------------+
| Clean Create Shop modal: NPC autocomplete, starter item, price, position, |
| pitch, audit reason                                                    |
+-------------------------------------------------------------------------+

Gachapon:
+------------------------------+-----------------------------------------+
| Sticky gachapon list          | Reward rows                             |
| search + paging               | common/uncommon/rare dropdown           |
| selected location             | chance percent + 1 in N                 |
+------------------------------+ add rarity dialog + clean delete -------+
```

## Visual Style

Use a clean minimalist style.

Principles:

- dense but readable.
- low visual noise.
- restrained color.
- icons where they improve recognition.
- no decorative cards for normal data sections.
- no unnecessary popups for common edits.
- clear selected-row and edited-cell states.
- consistent spacing and typography across all workspaces.
- consistent selected-card contrast between card body, text blocks, and inline
  fields in both day and night mode.
- square inventory/equipment grids where item identity is primarily icon,
  tooltip, border, corner marker, and right inspector detail.

General shell:

```text
Left Navigation
Top Header: breadcrumb/title, page tabs, navigation/actions
Middle Center: table/detail/editor
Right Inspector: detailed information for selected element
Footer Controls: MySQL status, settings, day/night toggle
```

Canonical layout:

```text
+--------------------------------------------------------------------------------+
| Left Navigation      | Top Header: title + tabs/actions                         |
|                      +---------------------------------------------+------------+
| Dashboard            | Main workspace                              | Inspector  |
| Account              | search/filter/catalog/editor                | selected   |
| Character            |                                             | entity     |
| Mobs                 |                                             | details    |
| Items                |                                             | links      |
| World Data           |                                             | technical  |
| Audit & Tools        |                                             | provenance |
|                      |                                             | history    |
| MySQL | Gear | Theme |                                             |            |
+--------------------------------------------------------------------------------+
```

## Core Principles

### 1. Edit In Place

Most editable fields should be modified directly where they are shown.

Examples:

- click drop chance -> edit value inline.
- click min/max quantity -> edit inline.
- click quest reward item quantity -> edit inline.
- click character meso/stat field -> edit inline with validation.

Avoid forcing users through a separate edit page or modal for simple field
changes.

### 2. Focused Edits Instead Of Global Write Mode

The implemented console no longer uses a global read/write toggle. Pages are
always browseable, and editable fields expose their own edit affordance where
the change is made.

Current edit model:

- simple numeric/text fields update from their own input or row action.
- destructive actions require a clean custom confirmation dialog.
- new records use focused add/create panels or clean modals.
- page refresh should be avoided after saves; selection and scroll context should
  remain stable whenever possible.
- updates should notify through the bottom-right stacked notification system.

This keeps normal browsing fast while avoiding a global mode that makes the
whole page feel armed for editing.

### 3. Use Autocomplete For References

Any field that references game data should support search/autocomplete.

Examples:

- add item to mob drop table by item id or item name.
- add mob to item drop table by mob id or mob name.
- add NPC reward by NPC id/name.
- select quest by quest id/name.
- select shop/NPC/map/item by id/name.
- edit a character saved map by map id or map name.
- edit an NPC/shop/item reference by id or name.

Autocomplete result rows should show:

- icon where useful.
- id.
- name.
- type/category.
- short context.

Rule:

```text
Anywhere an id appears, also show its name when known.
Anywhere an editable id appears, make it searchable by id and name.
```

Examples:

- map field: `100000000 - Henesys`.
- item field: `2000000 - Red Potion`.
- mob field: `100100 - Snail`.
- NPC field: `1012000 - Sera`.
- quest field: `1008 - Mai's Training`.

### 4. Badges And Tags Everywhere Applicable

Every entity and relationship row should expose meaningful badges/tags.

Badges should answer:

```text
What is this?
Where is it referenced?
Who owns this field?
Is this editable here?
Is this risky or unusual?
```

Examples:

Item badges:

- `Dropped`.
- `Sold By NPC`.
- `Quest Reward`.
- `Quest Required`.
- `Gachapon`.
- `Maker Ingredient`.
- `Maker Output`.
- `Owned`.
- `Cash`.
- `Quest Item`.
- `Untradeable`.
- `One-of-a-kind`.
- `Console Override`.
- `WZ`.
- `DB`.
- `Read Only`.
- `Server Console Owned`.

Mob badges:

- `Spawned In Map`.
- `Has Drops`.
- `Quest Mob`.
- `Normal Mob`.
- `Area Boss`.
- `Expedition Boss`.
- `No Known Drops`.
- `No Known Spawn`.
- `WZ`.
- `DB`.
- `Read Only`.

Map badges:

- `Has Portals`.
- `Has NPCs`.
- `Has Mobs`.
- `Has Reactors`.
- `Town`.
- `Hunting Map`.
- `Instance`.
- `Disconnected`.
- `Server Console Owned`.

Reward badges:

- `Override Active`.
- `Hardcoded Fallback`.
- `Duplicate Item`.
- `Rare Announce`.
- `Disabled`.
- `Weight Warning`.

Field ownership badges:

- `DB`.
- `WZ`.
- `Script`.
- `Console Override`.
- `Server Console Owned`.
- `Agent Catalog Owned`.
- `Read Only`.

Badges should be filterable where useful.

Recommended badge placement:

- catalog rows.
- relationship rows.
- right dock summary.
- right dock relationship sections.
- editable field labels.
- autocomplete result rows.
- apply-change preview.
- validation results.

Badge behavior:

- hover/click explains what the badge means.
- badges can be used as quick filters.
- ownership badges decide whether an edit is allowed in Database Console.
- warning badges link to the warning details in the right dock.

### 5. Right Detail Dock For Selected Context

Most pages should have a persistent right-side dock.

When a row/cell/entity is selected, the dock shows full detail for that selected
thing.

Examples:

- select an item in a mob drop table -> right dock shows item details.
- select a mob in an item drop table -> right dock shows mob details.
- select an NPC shop source -> right dock shows NPC/shop details.
- select a quest reward -> right dock shows quest summary.
- select a character inventory item -> right dock shows item/equip detail.

The right dock should include:

- MapleStory icon from maplestory.io where available.
- id/name/type.
- description.
- source/use summaries.
- validation warnings.
- quick links to deeper pages.

### 6. Cyclic Navigation Is Allowed

Database Console data is naturally graph-shaped.

Example:

```text
Item -> mobs that drop it -> mob detail -> drop table -> another item -> quest
```

The UI should support this without feeling lost.

Use:

- breadcrumbs.
- recently viewed stack.
- back/forward history.
- dock quick links.
- open-in-new-workspace/tab action.

### 7. Avoid Deep Page Chains

The user should not need to click through many levels for common edits.

Preferred:

```text
search -> open workspace -> inline edit grid -> right dock context
```

Avoid:

```text
search -> detail -> subpage -> popup -> form -> confirm -> back
```

### 8. Every Edit Shows State

Inline edits should show:

- clean.
- changed.
- invalid.
- saving.
- saved.
- failed.

Suggested visual states:

```text
unchanged: normal cell
changed: highlighted cell
invalid: red border + message
saving: spinner/small progress
saved: brief check indicator
failed: error state with retry/revert
```

### 9. Save Flow And Confirmation

The original design used draft state and a bottom apply bar. The current
implementation uses direct, focused saves instead.

Current model:

```text
Edit field or row -> validate locally/API-side -> save focused change -> toast
```

Rules:

- do not update on mere focus/click; update only when the value actually changes.
- keep the current selected row/item/entity selected after save.
- do not full-page refresh after normal save/delete/add operations.
- select the nearest sensible item after delete, such as the previous occupied
  inventory slot.
- use custom modals for destructive actions and rarity/shop creation prompts.
- avoid native browser `alert`, `prompt`, or `confirm`.
- keep inline inputs visually stable while selected, especially in night mode.

Still use explicit confirmation for dangerous changes:

- deleting inventory/storage/shop/drop/gachapon records.
- resetting a quest back to available.
- forfeiting an in-progress quest.
- creating a new shop for an NPC.
- future account/character destructive actions.

Warning examples:

- duplicate drop entry for the same mob/item/quest tuple.
- duplicate reward entry in the same pool.
- illegal item id, mob id, quest id, NPC id, or map id.
- min quantity greater than max quantity.
- negative chance, weight, stat, quantity, meso, or level.
- zero chance/weight on an enabled reward row.
- equip stat outside configured safety range.
- item type does not belong in the target inventory tab.
- one-of-a-kind duplicate.
- cash item missing cash id where required.
- duplicate cash id where uniqueness is required.
- duplicate item action attempted to reuse unique DB/item identity.
- duplicate item action attempted to reuse cash id, pet id, ring id, or other
  globally unique item linkage.
- replacement item does not match selected inventory type.
- replacement item would invalidate equipped slot requirements.
- drag/drop swap would violate inventory/equipment container rules.
- editing a field owned by Server Console.
- editing a field owned by Agent Console/catalog builder.
- deleting a row that is referenced elsewhere.
- adding an item with no known icon or metadata.
- changing a character currently online.
- changing account/session/security fields without elevated role.
- reward pool total/weight distribution looks suspicious.
- map or portal relationship points to missing map.
- mob has drops but no known spawn.
- item has quest-required badge but no known obtainable source.

## Global Layout

Recommended layout:

```text
Left Nav
  Dashboard
  Account
  Character
  Mobs
  Items
  World Data
  Audit & Tools
  footer: MySQL status, quality settings gear, day/night toggle

Sticky Header
  breadcrumb / section eyebrow
  page title
  page-level tabs and cross-page shortcuts

Main Workspace
  filters/search
  catalog cards/lists
  editor panels
  inline editable fields

Right Inspector
  selected entity detail
  icon/summary
  source/use relationships
  quick links
  history back/forward
  technical details
```

## First-Time Setup UI

The current local development target does not require login. Historical setup
and auth components may still exist in code, but the active operator workflow is
local trusted admin access.

If login/setup is re-enabled for a deployed version, it should remain outside
the main console shell and should not reintroduce a global read/write mode.

Setup layout:

```text
Database Console Setup

[1 Cosmic DB] -> [2 Console DB] -> [3 Assets] -> [4 Index] -> [5 Review]

+--------------------------------------------------------------------------------+
| Step 1: Cosmic Database                                                        |
|                                                                                |
| Host       [ localhost                         ]                               |
| Port       [ 3306                              ]                               |
| Database   [ cosmic                            ]                               |
| User       [ root                              ]                               |
| Password   [ ********                          ]                               |
|                                                                                |
| [Test Connection]                                                              |
|                                                                                |
| Status: Not tested                                                             |
+--------------------------------------------------------------------------------+
```

Console database step:

```text
Step 2: Console Database

Database Name [ cosmic_database_console ]
Status        Missing / Ready / Migration Needed

[Create Database] [Run Migrations]

Tables to create:
  console_schema_migrations
  console_settings
  console_audit_events
  console_index_runs
  console_reference_entities
  console_reference_relationships
  console_entity_tags
  console_saved_filters
  console_validation_results
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

Index step:

```text
Step 5: Build Index

[Full Rebuild]

Accounts      pending / indexing / done / warning
Characters    pending / indexing / done / warning
Items         pending / indexing / done / warning
Mobs          pending / indexing / done / warning
Maps          pending / indexing / done / warning
NPCs          pending / indexing / done / warning
Quests        pending / indexing / done / warning
Shops         pending / indexing / done / warning
Drops         pending / indexing / done / warning
Maker/Craft   pending / indexing / done / warning
```

Review step:

```text
Step 6: Review

Cosmic DB             connected
Console DB            cosmic_database_console
Migrations            up to date
Admin user            created
Reference index       built
Warnings              3

[Open Dashboard]
```

Setup rules:

- setup writes only to `cosmic_database_console`.
- setup only reads the original Cosmic database.
- setup should not alter original Cosmic tables.
- setup creates override-ready tables from the first migration.
- missing server hooks should mark affected pages `Override Ready`, not remove
  the editing model.
- setup should be resumable if interrupted.
- setup should store completion state in `console_settings`.
- full index rebuild can be run again later from Audit & Tools.

## Workspace Pattern

Each major page should follow the same structure:

Current implementation note:

```text
Some older ASCII examples below still show bottom change bars, global mode
controls, or draft/apply flows. Those parts are obsolete. Keep the data grouping
ideas, but render them through the current shell: left nav, sticky header,
focused save controls, custom confirmation modals, bottom-right toasts, and the
right inspector.
```

```text
Left Nav
  current section and parent groups

Sticky Header
  breadcrumb / section eyebrow
  title
  top-right tabs or related page shortcuts

Workspace
  page-local search/filter/action row
  catalog cards, grids, lists, or editor panels
  focused inline edits and row-level actions

Right Inspector
  selected row/entity detail
  cross-links
  collapsible relationship sections
  technical provenance
  back/forward entity history

Feedback
  bottom-right stacked toast notifications
  custom confirmation/dialog modals where needed
```

This creates consistency across:

- mob drop tables.
- item source views.
- item catalog/search.
- mob catalog/search.
- map catalog/search.
- character inventory.
- character equipment and appearance.
- quest progress.
- character quests and monster book.
- NPC shops.
- gachapon reward pools.
- quality color settings.

Top header tabs should be used for related views in the same major section.

Examples:

```text
Character: [AP / SP & Stats] [Inventory / Storage] [Equipment / Appearance] [Quests / Monster Book]
World Data: [Maps] [NPCs] [Shops] [Gachapon]
Mobs: [Monster explorer] [Global drops]
Quests / Monster Book: [Quest Available] [Quest In Progress] [Quest Completed] [Monster Book]
```

## Historical Page Mockups And Reference Notes

The following examples preserve earlier design discussion and detailed page
ideas. They are useful for future page work, but they are not canonical where
they conflict with the current implementation snapshot above. In particular,
do not copy old change bars, draft/apply flows, global read/write controls, or
deep modal stacks into new implementation.

## Example: Account And Character Creation

Creation should be a guided workspace, not a modal stack.

Entry points:

- left nav `Create`.
- Account workspace action: `Create Character`.
- global command palette: `create account`, `create character`.

Create account layout:

```text
Top Bar: Back / Forward | Global Search | Env | Write Mode | Changes
+--------------------------------------------------------------------------------+
| Left Nav       | Create Account                                      | Dock    |
|                | [Account] [Character] [Review]                     |         |
| Account        |                                                     | Account |
| > Create       | Account Name: [ new_account_name             ]     | rules   |
| Mobs           | Password:     [ ********                     ]     |         |
| Items          | Confirm:      [ ********                     ]     | Naming  |
| Rewards        | PIN/PIC:      [ optional / generated / none  ]     | policy  |
| Craft          | GM Level:     [ Normal Player v ]                  |         |
| World Data     | Flags:        [ ] banned [ ] admin [ ] tester      | Security|
| Audit & Tools  | Default World:[ 0 - Scania v ]                     | notes   |
|                |                                                     |         |
|                | [Create Account] [Create Account + First Character]| Audit   |
+--------------------------------------------------------------------------------+
| Change Bar: account creation pending | Validate | Apply Changes | Discard      |
+--------------------------------------------------------------------------------+
```

Create character layout:

```text
Top Bar: Back / Forward | Global Search | Env | Write Mode | Changes
+--------------------------------------------------------------------------------+
| Left Nav       | Create Character                                    | Dock    |
|                | [Account] [Appearance] [Starter Equip] [Review]    |         |
| Account        | Account: [ search account id/name...          ]    | Preview |
| > Create       | World:   [ 0 - Scania v ]                          | details |
| Mobs           | IGN:     [ CharacterName                      ]    |         |
| Items          |                                                     | Current |
| Rewards        | +----------------------+ +-----------------------+ | selected|
| Craft          | | Full Avatar Preview  | | Gender: [Male v]     | | hair/  |
| World Data     | |                      | | Skin:   [Light v]    | | face/  |
| Audit & Tools  | | updates live         | | Hair:   [search v]   | | equip  |
|                | |                      | | Face:   [search v]   | |        |
|                | +----------------------+ +-----------------------+ | Links   |
|                |                                                     |         |
|                | Starter Equipment                                   | Warnings|
|                | Weapon  [icon] [search allowed starter weapon...]  |         |
|                | Hat     [icon] [none / allowed starter hat...]     | Tech    |
|                | Top     [icon] [search allowed starter top...]     |         |
|                | Bottom  [icon] [search allowed starter bottom...]  |         |
|                | Shoes   [icon] [search allowed starter shoes...]   |         |
+--------------------------------------------------------------------------------+
| Change Bar: character creation pending | Validate | Apply Changes | Discard    |
+--------------------------------------------------------------------------------+
```

Review layout:

```text
Create Character Review

Account
  42 - new_account_name

Character
  IGN: IslandStart
  World: 0 - Scania
  Job: Beginner
  Initial Map: original server starter map

Appearance
  Gender: Male
  Skin: 0 - Light
  Hair: 30000 - Black Toben
  Face: 20000 - Motivated Look

Starter Items
  Weapon: 1302000 - Sword
  Top:    1040002 - White Undershirt
  Bottom: 1060002 - Blue Jean Shorts
  Shoes:  1072001 - Red Rubber Boots

Generated Rows Preview
  accounts: 1 row if creating account
  characters: 1 row
  inventory/equipment: N rows
  keymap/skills/quest defaults: generated by normal creation service

[Validate] [Apply Changes]
```

Creation UI rules:

- account creation and character creation can run separately.
- `Create Account + First Character` carries the newly-created account into the
  character creation workspace.
- character creation should use original allowed starter hair, face, skin,
  gender, and equipment lists by default.
- admin override can expose full searchable ids, but non-original choices must
  show a `Non-standard` badge and validation warning.
- every selectable game id uses autocomplete by id/name and displays icon where
  applicable.
- selecting hair, face, skin, gender, or equipment updates the right dock with
  item/appearance details, source, compatibility, and warnings.
- avatar preview updates immediately for unapplied changes.
- right dock technical section shows source list path, item id, slot id, and
  whether the choice came from original creation rules or admin override.
- applying creation writes through the same server-compatible creation service
  or mirrors it exactly, so console-created characters match client-created
  characters.
- password fields are write-only and never shown again after apply.

Implementation references:

- account creation should reference the normal auto-register insert in
  `src/main/java/net/server/handlers/login/LoginPasswordHandler.java` and the
  spawnbot account insert in
  `src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java`.
- character creation should reference `src/main/java/client/creator/BotCreator.java`
  because spawnbot already creates server-side characters using
  `Character.getDefault(...)`, starter equipment, `CharacterFactoryRecipe`, and
  `insertNewChar(...)`.
- normal client creation remains the parity target:
  `src/main/java/client/creator/CharacterFactory.java`.
- the implementation should extract a shared creation service if possible, then
  have Database Console, normal client creation, and server-side creation call
  that service with different validation modes.

## Universal Game Reference Inputs

Any input that selects a game element should use autocomplete/suggest.

Applies to:

- item ids.
- mob ids.
- NPC ids.
- quest ids.
- map ids.
- shop ids.
- reactor ids.
- skill ids.
- job ids.
- hair ids.
- face ids.
- skin ids.
- equipment ids.
- reward pool ids.

Display format:

```text
id - name
```

Examples:

```text
100000000 - Henesys
2000000 - Red Potion
100100 - Snail
1012000 - Sera
30000 - Black Toben
```

Autocomplete result rows should include the most useful available context:

- icon/sprite where applicable.
- id.
- name.
- type/category.
- region/map/source if applicable.
- warning badges if the target is unusual or invalid.

Plain numeric id editing should still be allowed for advanced users, but the UI
should resolve and display the name immediately when possible.

## Global Filter Model

Any page that supports filtering should use the same filter builder model.

Do not place every possible filter as a permanent top-level field. Pages with
many filter dimensions become unreadable that way, especially item stats, mob
stats, reward source filters, and audit filters.

Default toolbar:

```text
Search... | [x] common default toggle if applicable | + Add Filter | Reset
```

Active filters appear as removable chips:

```text
[Type: Equip x] [Req Lv: 20-35 x] [DEX >= 25 x] [Dropped By: Snail x]
```

Filter behavior:

- `+ Add Filter` opens a picker of valid filters for the current page.
- user chooses filter type.
- user enters/selects filter value.
- applying creates a filter chip.
- clicking a chip edits that filter.
- clicking `x` removes that filter.
- `Reset` removes all active filters and restores page defaults.
- filters are stored in page history so back/forward restores them.
- filters should be serializable into URL/query state where practical.

Filter value controls should match the filter type:

- text search for names.
- id/name autocomplete for references.
- min/max inputs for ranges.
- exact value input for exact match.
- checkbox/toggle for boolean fields.
- multi-select for categories.
- date/time range for audit and expiration fields.

Examples:

```text
Item filter:
  choose "Required DEX" -> operator "less than or equal" -> value 25
  result chip: [Required DEX <= 25 x]

Mob filter:
  choose "HP" -> operator "between" -> min 100 -> max 500
  result chip: [HP: 100-500 x]

Audit filter:
  choose "Editor" -> search account/admin -> select Admin
  result chip: [Editor: Admin x]
```

Recommended filter groups:

- common.
- category/type.
- numeric/stat.
- relationship/source.
- ownership.
- technical/metadata.
- audit/history.

## Catalog Pagination

Catalog pages should use pagination controls at both the top and bottom of the
result grid.

Top pagination is useful before scrolling through large result sets. Bottom
pagination is useful after reviewing the current page.

Recommended layout:

```text
Result count: 1,248 | Page 1 of 63 | < Prev | [1] [2] [3] ... | Next > | Size 50 v
```

Pagination behavior:

- available on catalog/search pages by default.
- appears above and below the grid.
- page size selector: 25, 50, 100, 250.
- preserve current filters, search query, and sort order.
- changing filter/search resets to page 1.
- back/forward restores page number.
- selected row should remain selected if still visible after page changes.
- if selected row is no longer visible, keep right dock pinned to previous
  selection until another row is selected.

Catalog pages that should use top and bottom pagination:

- item catalog.
- mob catalog.
- map catalog.
- account search.
- character search.
- reward pool search.
- craft/recipe search.
- audit search.

## Catalog View Modes

Every catalog page should support both list view and grid view where practical.

View toggle:

```text
View: [List] [Grid]
```

List view:

- dense table rows.
- best for comparison, sorting, and bulk review.
- shows more columns.
- default for admin-heavy data such as audit and pending changes.

Grid view:

- icon/image-first cards or tiles.
- best for visual browsing.
- shows fewer fields but stronger visual recognition.
- default can be grid for items, mobs, maps, NPCs, and character inventories if
  preferred.

Catalog shell:

```text
+--------------------------------------------------------------------------------+
| Catalog | Search... | [x] Referenced Only | + Add Filter | Reset | View: List/Grid |
+--------------------------------------------------------------------------------+
| Active Filters: [filter chips...]                                               |
+--------------------------------------------------------------------------------+
| Results | Pagination                                                            |
+----------------------+---------------------------------------------------------+
| Left Nav             | List or Grid results                        | Right Dock |
+----------------------+---------------------------------------------+------------+
| Results | Pagination                                                            |
+--------------------------------------------------------------------------------+
```

Selection behavior:

- single click selects a row/tile and updates right dock.
- double click, Enter, or Open opens the entity workspace.
- selected item/mob/map/etc remains highlighted.
- right dock behaves the same regardless of list or grid view.
- filters, sort, pagination, selection, and view mode are preserved by
  back/forward history.

Relevant images/icons:

- items: item icon from maplestory.io or catalog/WZ-derived icon.
- equips: item icon plus optional equip/avatar preview where useful.
- mobs: mob sprite/icon where available.
- NPCs: NPC sprite/icon where available.
- maps: minimap/thumbnail if available; otherwise map icon/category badge.
- shops: NPC icon plus shop badge.
- quests: quest icon/category badge; NPC icon for start/complete where useful.
- PQ/events: event/PQ badge plus entry NPC or map thumbnail if available.
- recipes/craft: output item icon.
- accounts/characters: character avatar preview where available.
- audit/pending changes: action/source icons, not decorative images.

Icon technical details:

- right dock technical section should show icon source URL/path.
- missing icons should show a consistent placeholder and `Missing Icon` badge.
- image loading failure should not break the catalog row/tile layout.

List view example:

```text
+--------------------------------------------------------------------------------+
| Items | Search item id/name... | [x] Referenced Only | + Filter | View: List    |
+--------------------------------------------------------------------------------+
| Icon  ID       Name             Type   Lv  Tags                 Source          |
| [i]   1472000  Garnier          Claw   10  [Drop] [Equip]       WZ              |
| [i]   2000000  Red Potion       Use    -   [Shop] [Drop]        WZ/DB           |
+--------------------------------------------------------------------------------+
```

Grid view example:

```text
+--------------------------------------------------------------------------------+
| Items | Search item id/name... | [x] Referenced Only | + Filter | View: Grid    |
+--------------------------------------------------------------------------------+
| [icon] Garnier          | [icon] Maple Claw       | [icon] Red Potion          |
| 1472000 | Claw | Lv10   | 1472030 | Claw | Lv35   | 2000000 | Use             |
| [Drop] [Equip]          | [Event] [Equip]         | [Shop] [Drop]             |
|                         |                         |                            |
| [icon] Blue Potion      | [icon] Snail Shell      | [icon] Glove ATT 60%      |
| 2000001 | Use           | 4000019 | Etc           | 2040601 | Scroll          |
| [Shop]                  | [Quest Req] [Drop]      | [Reward] [Gacha]          |
+--------------------------------------------------------------------------------+
```

Grid tile rules:

- stable tile size.
- icon area has fixed dimensions.
- names truncate with tooltip, not layout expansion.
- badges wrap within fixed badge area.
- selected tile has clear border/highlight.
- tile supports right-click/context menu later if needed.
- tile should not contain full descriptions.

Recommended defaults:

- Items: grid or list, remember user's last mode.
- Mobs: grid or list, remember user's last mode.
- Maps: list default, grid optional if thumbnails exist.
- Shops: list default.
- Quests: list default.
- PQ/events: list default.
- Craft/recipes: list default, grid useful by output item.
- Accounts/characters: list default, character grid optional.
- Audit/tools: list only unless there is a clear visual use.

Maker/Craft catalog source note:

- Maker recipes are DB-backed at runtime.
- original Maker source is `wz/Etc.wz/ItemMake.img.xml`.
- DB tables are `makercreatedata`, `makerrecipedata`, `makerrewarddata`, and
  `makerreagentdata`.
- non-Maker craft/exchange scripts may be `Server JS` owned under
  `scripts/npc`.
- right dock should show whether the selected recipe row is `DB`, `XML Source`,
  `Server JS`, or `Console Override`.

## Catalog To Workspace Pages

These pages use the two-layer pattern:

```text
Layer 1: Catalog/search with filters, list/grid view, pagination, right dock.
Layer 2: Specific entity workspace with tabs/editors, right dock, change bar.
```

Catalog behavior:

- search and filter from the catalog page.
- toggle list/grid view where practical.
- single click selects row/tile and updates right dock.
- double click, Enter, or Open enters the entity workspace.
- entity workspace is where edits happen.

Pages:

| Domain | Catalog Page | Workspace/Edit Page | Grid/List |
| --- | --- | --- | --- |
| Accounts | Account catalog/search | Account workspace | list default, grid optional |
| Characters | Character catalog/search | Character workspace | list default, grid optional with avatar |
| Items | Item catalog | Item workspace | list and grid |
| Mobs | Mob catalog | Mob workspace | list and grid |
| Maps | Map catalog | Map workspace | list and grid if thumbnails exist |
| NPCs | NPC catalog | NPC workspace | list and grid |
| Shops | Shop/NPC shop catalog | Shop workspace | list and grid |
| Quests | Quest catalog | Quest workspace | list and grid |
| PQ | PQ/event catalog | PQ reward workspace | list and grid |
| Events | Event reward catalog | Event reward workspace | list and grid |
| Gachapon | Gachapon machine/catalog | Gachapon pool workspace | list and grid |
| Cash Boxes | Cash box catalog | Cash box reward workspace | list and grid |
| NPC Rewards | NPC reward catalog | NPC reward workspace | list and grid |
| Maker/Craft | Recipe/output catalog | Recipe workspace | list and grid |
| Reactor Drops | Reactor catalog | Reactor drop workspace | list and grid |
| Reward Pools | Reward pool catalog | Reward pool workspace | list and grid |

Pages that are not primarily catalog-to-workspace:

| Page | Pattern |
| --- | --- |
| Character inventory | workspace-first slot grid, not catalog-first |
| Character equipment | workspace-first avatar/slot grid |
| Storage | workspace-first slot grid |
| Audit | filtered list with right dock, no separate edit workspace by default |
| Pending changes | filtered list with apply/discard workflow |
| Validation | filtered issue list with right dock |
| Import/export | tool workflow |

Inventory-like pages still use grid/list concepts, but they start from a
selected character/account workspace rather than from a global catalog.

## Navigation Model

The console should support browser-like navigation because data exploration is
cyclic.

Top bar controls:

```text
+--------------------------------------------------------------------------------+
| < Back | Forward > | Global Search / Command Palette | Env | Changes | User     |
+--------------------------------------------------------------------------------+
```

Navigation behavior:

- back returns to the previous workspace/entity/filter state.
- forward returns to the next state after going back.
- breadcrumbs show the current hierarchy.
- recent entities keep a short history for fast jumping.
- right dock links can navigate without losing the previous history.
- pinned dock content should survive main workspace navigation until unpinned.

History should preserve:

- selected top-level page.
- search query.
- filters.
- sort order.
- selected row/entity.
- right dock mode where practical.

Navigation guard:

```text
If current workspace has unapplied changes:
  show modal with Apply Changes / Discard Changes / Return
```

The guard should trigger on:

- back/forward navigation.
- left nav changes.
- opening another workspace.
- browser refresh/close.
- global search result navigation.
- right dock quick-link navigation.

The guard should not trigger if all changes were already applied or discarded.

## Catalog Search Defaults

Item, mob, and map catalog pages should default to useful data instead of
showing every raw WZ entry.

Default checkbox:

```text
[x] Show only referenced entries
```

For items, referenced means at least one known source or use exists:

- dropped by a mob.
- sold by a shop.
- gachapon/cash box reward.
- quest/PQ/event/NPC reward.
- maker/craft output or ingredient.
- owned by a character/storage record if ownership search is enabled.

For mobs, referenced means at least one known use exists:

- appears in a map spawn.
- has a drop table.
- used by a quest requirement.
- used by reactor/script/catalog metadata.

For maps, referenced means at least one known relationship exists:

- has portal links.
- has mob spawns.
- has NPCs.
- has reactors.
- is a character location.
- is used by a quest/script/catalog entry.

Users can uncheck the box to inspect raw catalog entries.

## Example: Item Catalog Search

Page:

```text
Items
```

Layout:

```text
+--------------------------------------------------------------------------------+
| Items | Search item id/name... | [x] referenced only | + Add Filter | Reset     |
+--------------------------------------------------------------------------------+
| Active Filters: [Type: Equip x] [Req Lv: 20-35 x] [DEX >= 25 x]                 |
+--------------------------------------------------------------------------------+
| Results: 1,248 | Page 1/63 | < Prev | [1] [2] [3] ... | Next > | Size 50 v      |
+----------------------+---------------------------------------------------------+
| Account              | Item Catalog                                | Item Dock  |
| Mobs                 |                                             |            |
| Items                | +-------+-------+-------------+------+-----+| Icon       |
| > Catalog            | | Icon  | ID    | Name        | Type | Lv  || Name / ID  |
| > Dropped By         | +-------+-------+-------------+------+-----+| Summary    |
| > Sold By            | | [ico] | 1302  | Sword       | Wep  | 10  || Sources    |
| Rewards              | | [ico] | 2000  | Red Potion  | Use  | -   || Used in    |
| Craft                | +-------+-------+-------------+------+-----+| Technical  |
+----------------------+---------------------------------------------+------------+
| Results: 1,248 | Page 1/63 | < Prev | [1] [2] [3] ... | Next > | Size 50 v      |
+--------------------------------------------------------------------------------+
| Change Bar: pending item edits | validate | apply changes | discard             |
+--------------------------------------------------------------------------------+
```

Recommended filters:

- referenced only.
- item type: equip, use, setup, etc, cash.
- equip cash type: normal equip, cash equip, any equip.
- equip type: hat, overall, top, bottom, shoes, glove, cape, shield, accessory.
- weapon type: sword, axe, blunt weapon, dagger, wand, staff, claw, bow,
  crossbow, gun, knuckle, spear, polearm.
- job type: beginner, warrior, magician, bowman, thief, pirate, common.
- required level min/max.
- required STR min/max.
- required DEX min/max.
- required INT min/max.
- required LUK min/max.
- stat ranges: STR, DEX, INT, LUK, HP, MP, weapon attack, magic attack,
  weapon defense, magic defense, accuracy, avoidability, speed, jump.
- slots min/max.
- cash item yes/no.
- cash equip yes/no.
- quest item yes/no.
- tradeable/untradeable.
- one-of-a-kind yes/no.
- sold by NPC yes/no.
- dropped by mob yes/no.
- reward source yes/no.
- craft/maker usage yes/no.
- ownership exists yes/no if permitted.
- price/NPC value min/max if available.

Useful extra filters:

- has icon / missing icon.
- has description / missing description.
- has conflicting metadata.
- has no known source.
- has no known use.
- only items available in selected region.
- only items useful for selected job.

Right dock item technical section:

- raw item id.
- WZ path.
- source catalog file.
- item category path.
- DB table rows that reference it.
- override status.
- cache key/version.
- last indexed timestamp.
- validation status.
- raw flags: cash, quest, trade block, expire, one-of-a-kind.
- raw equip stat block for equips.
- raw script/reward references if cataloged.

## Example: Mob Catalog Search

Page:

```text
Mobs
```

Layout:

```text
+--------------------------------------------------------------------------------+
| Mobs | Search mob id/name... | [x] referenced only | + Add Filter | Reset       |
+--------------------------------------------------------------------------------+
| Active Filters: [Region: Victoria x] [Level: 1-30 x] [Has Drops: Yes x]         |
+--------------------------------------------------------------------------------+
| Results: 318 | Page 1/7 | < Prev | [1] [2] [3] ... | Next > | Size 50 v         |
+----------------------+---------------------------------------------------------+
| Account              | Mob Catalog                                 | Mob Dock   |
| Mobs                 |                                             |            |
| > Catalog            | +-------+-------+-------------+------+-----+| Icon       |
| > Drop Table         | | Icon  | ID    | Name        | Lv   | HP  || Name / ID  |
| > Maps               | +-------+-------+-------------+------+-----+| Spawn maps |
| Items                | | [ico] | 1001  | Snail       | 1    | 8   || Drops      |
| Rewards              | | [ico] | 9300  | Boss        | 30   | ... || Quests     |
| Craft                | +-------+-------+-------------+------+-----+| Technical  |
+----------------------+---------------------------------------------+------------+
| Results: 318 | Page 1/7 | < Prev | [1] [2] [3] ... | Next > | Size 50 v         |
+--------------------------------------------------------------------------------+
| Change Bar: pending mob/drop edits | validate | apply changes | discard             |
+--------------------------------------------------------------------------------+
```

Recommended filters:

- referenced only.
- region: Maple Island, Victoria Island, Ossyria, Ludus Lake, Aqua Road, etc.
- map id/name contains.
- level min/max.
- HP min/max.
- MP min/max.
- EXP min/max.
- mob type: normal mob, area boss, expedition boss.
- boss flag yes/no.
- undead yes/no if available.
- element weakness/resistance/immunity if cataloged.
- has drops yes/no.
- drops item id/name.
- used by quest yes/no.
- appears in selected map.
- appears in selected region.
- spawn count min/max.
- level range relative to selected character level.

Useful extra filters:

- no known spawn map.
- no drop table.
- quest-relevant mobs.
- high-value drop mobs.
- low HP/high EXP mobs.
- mobs with reactor/script dependencies.
- mobs with incomplete metadata.

Right dock mob technical section:

- raw mob id.
- WZ path.
- source catalog file.
- DB drop rows.
- spawn metadata references.
- quest requirement references.
- raw stats block.
- boss/category inference reason.
- cache key/version.
- last indexed timestamp.
- validation status.

## Example: Map Catalog Search

Page:

```text
World Data -> Maps
```

The Database Console can show map metadata for inspection and relationship
navigation. Server-level map policy edits still belong to Server Console.

Layout:

```text
+--------------------------------------------------------------------------------+
| Maps | Search map id/name... | [x] referenced only | + Add Filter | Reset       |
+--------------------------------------------------------------------------------+
| Active Filters: [Region: Victoria x] [Has NPCs: Yes x] [Has External Portal: Y x]|
+--------------------------------------------------------------------------------+
| Results: 92 | Page 1/2 | < Prev | [1] [2] | Next > | Size 50 v                 |
+----------------------+---------------------------------------------------------+
| Account              | Map Catalog                                 | Map Dock   |
| Mobs                 |                                             |            |
| Items                | +--------+----------------------+----------+| Map name   |
| Rewards              | | ID     | Name                 | Region   || Portals    |
| Craft                | +--------+----------------------+----------+| Mobs       |
| World Data           | | 10000  | Mushroom Town        | Island   || NPCs       |
| > Maps               | | 100000 | Henesys              | Victoria || Reactors   |
| > Shops              | +--------+----------------------+----------+| Technical  |
+----------------------+---------------------------------------------+------------+
| Results: 92 | Page 1/2 | < Prev | [1] [2] | Next > | Size 50 v                 |
+--------------------------------------------------------------------------------+
| Change Bar: pending map changes if any | validate | apply changes | discard     |
+--------------------------------------------------------------------------------+
```

Recommended filters:

- referenced only.
- region.
- town/hunting/instance/event map if inferable.
- map id range.
- map name.
- has portals yes/no.
- has external portal links yes/no.
- has mob spawns yes/no.
- has NPCs yes/no.
- has shops yes/no.
- has reactors yes/no.
- has footholds yes/no.
- contains mob id/name.
- contains NPC id/name.
- contains reactor id.
- character currently located here yes/no if permitted.
- quest-related yes/no if cataloged.

Useful extra filters:

- disconnected maps.
- maps with portals to missing maps.
- maps with no return path.
- maps with spawns but no foothold data.
- maps with NPCs but no cataloged interactions.
- maps with incomplete metadata.
- maps reachable from selected region.

Right dock map sections:

- map id/name/street/category/region.
- portal link maps, excluding same-map warps by default.
- same-map warps behind an expandable technical subsection.
- inbound maps.
- outbound maps.
- mob spawn summary: mob id/name, expected count, spawn positions if cataloged.
- NPC list: NPC id/name, actions if cataloged, shops/quests/scripts.
- reactor list: reactor id, drops/actions if cataloged.
- shop list if known.
- common character location count if permitted.
- catalog warnings.

Right dock map technical section:

- raw map id.
- WZ path.
- source catalog file.
- foothold count.
- portal count.
- life object count.
- reactor count.
- seat/ladder/rope counts if indexed.
- map bounds.
- VR bounds if available.
- return map.
- forced return map.
- field type.
- swim/fly/clock/cloud flags if available.
- cache key/version.
- last indexed timestamp.
- validation status.

## Example: Mob Drop Table

Page:

```text
Mobs -> Mob Detail -> Drop Table
```

Main grid columns:

- item icon.
- item id.
- item name.
- item type.
- chance.
- chance percent.
- 1-in-X estimate.
- min.
- max.
- quest id.
- actions.

Inline edits:

- chance.
- min.
- max.
- quest id.

Drop-rate display:

Show both raw server chance and human-readable rates.

Example:

```text
Chance: 5000
Percent: 0.005%
Estimate: 1 in 20,000
```

The exact conversion must match the server's drop chance denominator. If the
denominator is configurable or context-specific, the right dock technical
section should show which denominator/rate modifier was used.

Add row:

```text
Add item...
```

Autocomplete:

- search item by id/name.
- show icon, id, name, item type.
- selecting an item creates an unapplied drop row with default chance/min/max.

Right dock when item row selected:

- item icon.
- item id/name/type.
- description.
- sold by shops.
- gachapon sources.
- quest/event/PQ reward sources.
- maker/crafting usage.
- mobs that drop it.
- characters owning it if permission allows.
- link to item detail workspace.
- technical details at the bottom.

## Example: Item Drop Sources

Page:

```text
Items -> Item Detail -> Drop Table By Mob
```

Main grid columns:

- mob icon if available.
- mob id.
- mob name.
- mob level.
- map count.
- chance.
- chance percent.
- 1-in-X estimate.
- min.
- max.
- quest id.

Add row:

```text
Add mob...
```

Autocomplete:

- search mob by id/name.
- show mob id/name/level.
- selecting a mob creates an unapplied drop row.

Right dock when mob row selected:

- mob id/name.
- level/HP/EXP if cataloged.
- map spawn summary.
- full drop table.
- related quests if known.
- link to mob detail workspace.
- technical details at the bottom.

## Example: Character Inventory

Page:

```text
Account -> Character -> Inventory
```

Main layout:

- tabs: equip, use, setup, etc, cash, equipped, cash equipped, storage.
- icon grid by slot as the primary layout.
- table view toggle for dense admin edits.
- clean icon-first layout for normal inspection.
- each inventory type has 96 slots.
- storage has 48 slots.
- equipped and cash equipped use equipment-slot grids instead of 96-slot grids.

Grid layout:

```text
+--------------------------------------------------------------------------------+
| Character > Inventory | [Equip] [Use] [Setup] [Etc] [Cash] [Storage]            |
+----------------------+---------------------------------------------------------+
| Left Nav             | Slot Grid                                   | Right Dock |
| Character            |                                             | Selected   |
| > Inventory          | 01 02 03 04 05 06 07 08                     | item/slot  |
| > Equipment          | [] [] [] [] [] [] [] []                     | details    |
|                      | 09 10 11 12 13 14 15 16                     |            |
|                      | [] [] [] [] [] [] [] []                     | Summary    |
|                      | 17 18 19 20 21 22 23 24                     | Sources    |
|                      | [] [] [] [] [] [] [] []                     | Warnings   |
|                      |                                             | Technical  |
+----------------------+---------------------------------------------+------------+
| Change Bar: pending inventory changes | Validate | Apply Changes | Discard     |
+--------------------------------------------------------------------------------+
```

Selected slot expansion:

```text
Collapsed:

[] [] [] [] [] [] [] []
[] [] [] [] [] [] [] []
[] [] [] [] [] [] [] []

Selected:

[] [] [] [] [] [] [] []
[] [] [selected] [] [] [] [] []
      +-------------------------------------------+
      | Edit Slot 11                              |
      | Item: [2000000 - Red Potion          v]   |
      | Quantity: [20]                            |
      | Expiration: [none]                        |
      | Flags: [tradeable] [lock]                 |
      | [Duplicate] [Clear Slot]                  |
      +-------------------------------------------+
[] [] [] [] [] [] [] []
```

Expansion behavior:

- selecting a slot expands the edit panel directly below that row.
- selecting another slot collapses the current edit panel and expands the new
  selected slot.
- empty slots expand with add-item controls.
- occupied slots expand with replace/edit/duplicate/remove controls.
- right dock always updates to the selected slot/item, same as item catalog.
- selected slot remains highlighted.
- expansion must not change slot numbering.

Empty slot add flow:

```text
Empty Slot 22
Item: [search item id/name, filtered to current inventory type...]
Quantity: [1]
Stats: shown only after item selected
[Add Item]
```

Existing slot edit/replace flow:

```text
Slot 22
Current: 1302000 - Sword
Replace Item: [search item id/name...]
Quantity: [1]
Owner: [...]
Expiration: [...]
[Duplicate] [Replace Item] [Clear Slot]
```

Inline edits:

- quantity.
- slot.
- owner.
- expiration.
- flags.
- equip stats.
- item id replacement.
- unique item-instance identifiers where applicable.

Hover behavior:

- hovering over an item icon shows a compact tooltip.
- tooltip includes item name and real stats.
- tooltip does not need long description text.
- tooltip should be fast and not block drag/drop.

Tooltip example:

```text
Red Potion
Use Item
Quantity: 20
```

Equip tooltip example:

```text
Blue Bamboo Hat
STR +1
DEX +2
Weapon Def +15
Slots 7
```

Add item:

- select an empty slot.
- autocomplete item by id/name, filtered to the selected inventory type.
- choose quantity.
- edit generated instance stats if the item is an equip.
- validate slot capacity and one-of-a-kind rules.

Replace item:

- select an occupied slot.
- use item search to choose replacement item by id/name.
- preserve or reset editable instance fields based on replacement policy.
- validate inventory type compatibility.

Duplicate item:

- available on occupied slots.
- creates another item with the same item id and editable instance stats.
- duplicated item must get a unique database/item instance identity.
- cash id, pet id, ring id, and other globally unique ids must not be reused.
- target slot can be chosen by selecting an empty slot or using first available
  slot.
- duplicate action creates unapplied changes until `Apply Changes`.

Drag/drop:

- drag and drop items within the same inventory.
- drag and drop items between inventory and storage where allowed.
- support equipped/equip/use/setup/etc/cash/storage universally where rules
  allow.
- dragging onto an occupied slot swaps places where the target container allows
  swapping.
- dragging onto an empty slot moves directly.
- drag/drop should create unapplied changes.
- invalid drops should show a clear warning and reject the move.
- storage/inventory restrictions should be validated before apply.

Apply this grid/edit behavior to:

- equip inventory.
- use inventory.
- setup inventory.
- etc inventory.
- cash inventory.
- storage.
- equipped slots.
- cash equipped slots.

For equipped and cash equipped pages:

- use equipment slot names instead of 1-96 slot numbers.
- support drag/drop between compatible inventory and equipped slots.
- invalid equip attempts show requirement/type warnings.

Right dock:

- selected item details.
- equip stat breakdown.
- real instance stats.
- general/base item stats.
- possible roll stat range where known.
- source/use info.
- ownership warnings.
- duplicate cash id warning.
- link to item detail workspace.
- technical details at the bottom.

Inline expanded edit panel:

- contains editable instance fields only.
- does not duplicate the full item-catalog detail view.
- full relationship/source details stay in the right dock.
- equip stats expand into editable fields directly below the selected slot.
- non-equip items show quantity/expiration/flags fields.

Equip edit fields:

- STR.
- DEX.
- INT.
- LUK.
- HP.
- MP.
- weapon attack.
- magic attack.
- weapon defense.
- magic defense.
- accuracy.
- avoidability.
- speed.
- jump.
- upgrade slots.
- level.
- item EXP if applicable.
- owner.
- expiration.
- flags.

Stat display:

```text
STR: 5 (3-7)
DEX: 2 (0-4)
Weapon Attack: 150 (145-155)
Slots: 7
```

The first value is the selected item instance value. The parenthesized range is
the general possible roll range from catalog/WZ/known generation rules.

If the range is unknown:

```text
Weapon Attack: 150 (range unknown)
```

## Example: Character Equipment And Appearance

Page:

```text
Account -> Character -> Equipment / Appearance
```

Equipment page should include a full avatar preview.

Layout:

```text
+--------------------------------------------------------------------------------+
| Character > Equipment | [Equipment] [Cash Equipment] [Appearance] [Stats]       |
+----------------------+---------------------------------------------------------+
| Left Nav             | Avatar + Appearance + Equip Grids           | Right Dock |
|                      |                                             | Selected   |
| Account              | +----------------------+ +----------------+ | equip/item |
| Character            | |                      | | Hair: [search] | | details    |
| > Equipment          | | full avatar preview  | | Face: [search] | |            |
| > Appearance         | |                      | | Skin: [search] | | Real stats |
|                      | |                      | | Gender:[sel.] | | Base stats |
|                      | +----------------------+ +----------------+ | Roll range |
|                      |                                             | Sources    |
|                      | Equipment Grid                              | Warnings   |
|                      | [Hat] [Face] [Eye] [Top] [Bottom] [Shoes]   | Technical  |
|                      | [Glove] [Cape] [Weapon] [Shield] [Ring]     | collapsed  |
|                      |                                             |            |
|                      | Cash Equipment Grid                         |            |
|                      | [Cash Hat] [Cash Face] [Cash Eye] [Cash Top]|            |
|                      | [Cash Shoes] [Cash Weapon] [Cash Cape]      |            |
+----------------------+---------------------------------------------+------------+
| Change Bar: pending appearance/equip changes | validate | apply changes         |
+--------------------------------------------------------------------------------+
```

Top section:

- avatar preview on the left.
- appearance selectors on the right.
- hair, face, skin, and gender are searchable/selectable fields.
- changing appearance updates avatar preview immediately in UI.
- current implementation saves each appearance selection immediately.
- when the character is online, appearance saves route through the live bridge;
  when offline, they write through the validated DB path.

Lower section:

- equipment icon grid.
- cash equipment icon grid.
- selecting a slot highlights it and expands an edit panel below the selected
  grid row.
- selecting another slot collapses the current edit panel and expands the new
  selected slot.
- right dock updates with selected equipment details, same as item catalog.

Equipment slot expansion:

```text
[Hat] [Face] [Eye] [Top] [Bottom] [Shoes]
[Glove] [Cape] [Weapon] [Shield] [Ring]
              [selected]
              +-------------------------------------+
              | Edit Equipment Slot: Weapon         |
              | Item: [1302000 - Sword          v]  |
              | STR: [5]        DEX: [2]            |
              | W.Att: [150]    Slots: [5]          |
              | Owner: [...]    Expiration: [...]   |
              | [Duplicate] [Replace] [Unequip]     |
              +-------------------------------------+
```

Empty equipment slot expansion:

```text
Edit Equipment Slot: Cape
Item: [search compatible cape by id/name...]
[Equip Item]
```

Existing equipment slot behavior:

- search item field can replace current equipment.
- replacement search should filter by compatible equipment slot/type.
- invalid replacement shows warning and cannot apply.
- equip stats are editable in the inline expansion.
- full item/source/ownership details stay in right dock.
- current implementation saves an equipment slot edit from the editor action
  rather than batching it into a page-level change bar.

Cash equipment behavior:

- same grid/selection/expansion model as equipment.
- search replacement should filter to cash-equipment-compatible items.
- duplicate must not reuse cash id or other unique linkage.
- right dock shows `Cash`, `Cash Equip`, and source badges.
- online cash-equipped slot add/modify is supported through the same live
  bridge path as normal equipped slots.

Equipment hover tooltip:

- hovering over an equipped/cash-equipped item icon shows compact stats.
- tooltip includes item name and real stats.
- no long description in tooltip.
- tooltip must not block drag/drop.

Drag/drop:

- drag equipment between compatible equipment/cash equipment slots where valid.
- drag from inventory to equipment slot where compatible.
- drag from equipment slot back to inventory where space exists.
- occupied compatible slot can swap if rules allow.
- all drag/drop changes remain unapplied until `Apply Changes`.

Editable appearance fields:

- hair.
- face.
- skin.
- gender, if server rules allow.

All appearance fields should use autocomplete/suggest by id and name where a
name catalog exists.

Live-write scope:

- supported online today: appearance updates and equipped/cash-equipped slot
  add or modify from `Equipment / Appearance`.
- still offline-only today: normal bag inventory, storage, item deletes,
  duplicates, transfers, and IGN rename.
- if the bridge is unreachable, the UI must show the bridge/offline error
  rather than silently dropping the save.

Examples:

```text
Hair: 30000 - Black Toben
Face: 20000 - Motivated Look
Skin: 0 - Normal
Gender: Male
```

Avatar preview should update immediately in the UI when unapplied changes are
made, but game DB should only update after `Apply Changes`.

## Example: Reward Pool

Page:

```text
Rewards -> Gachapon
```

Main grid:

- machine/town.
- item icon.
- item id.
- item name.
- weight/chance.
- chance percent if calculable.
- 1-in-X estimate if calculable.
- quantity.
- rare announce.
- enabled.

Inline edits:

- weight/chance.
- quantity.
- rare announce.
- enabled.

Right dock:

- selected item detail.
- other reward pools containing it.
- drop/shop/quest sources.
- ownership/rarity hints.
- technical details at the bottom.

Applying:

- edits stay unapplied until confirmed.
- validate reward pool sums/weights.
- apply as active override.
- hardcoded fallback remains available.

## Example: Character Quests And Monster Book

Access:

```text
Left Nav -> Character -> Quests / Monster Book
```

The page is character-owned and uses the same account/character selector as
AP/SP, Inventory, and Equipment. The selected character should persist while
switching between character pages.

Tabs:

```text
[Quest Available] [Quest In Progress] [Quest Completed] [Monster Book]
```

ASCII layout:

```text
| Character > Quests / Monster Book                                      |
| [Quest Available] [Quest In Progress] [Quest Completed] [Monster Book] |
|------------------------------------------------------------------------|
| Search accounts / characters                        World filter       |
|------------------------------------------------------------------------|
| Browse Accounts             | Account's Characters                     |
| admin                       | Kiwi, Lv 160 Shadower                    |
|------------------------------------------------------------------------|
| Quest In Progress                              | Selected Quest         |
|                                                |                        |
| [Quest 1009] Mai's Training                    | Status: In Progress    |
|   3 / 10 Orange Mushroom                       | Completed count: 0     |
|                                                | Forfeited: 0           |
| [Quest 1021] Roger's Apple                     | Progress rows          |
|   Apple in inventory 1 / 1                     | 0: 3                  |
|                                                |                        |
|                                                | Technical collapsed    |
```

Monster Book tab:

```text
| Monster Book                                      Page 1 / N            |
| [All] [Beginner] [Basic] [Intermediate] [Advanced] [Master]             |
|------------------------------------------------------------------------|
| [icon] Snail              | [icon] Blue Snail       | [icon] Slime       |
|       100100 / card 238   |       100101 / card ... |       ...          |
|       4 / 5 collected     |       1 / 5 collected   |       5 / 5        |
```

Display rules:

- match the character-facing quest window categories.
- do not flood the page with every raw `NOT_STARTED` database placeholder.
- show available quests only when the server/catalog eligibility layer can
  prove they would appear to the character.
- show in-progress quest details using quest progress rows and live inventory
  counts where requirements are known.
- completed quests show completion count/time where available.
- Monster Book shows mob id, mob name, card id, and collected card count.
- Monster Book paginates within each book tab/section.

Editing rules:

- status and progress edits require the account/character to be offline unless
  a future live bridge implements safe online quest updates.
- raw technical fields remain available but should stay visually secondary.
- completing a quest from the console should warn that normal quest rewards are
  not automatically granted unless a future live quest bridge handles it.

## Example: Quest Rewards And Requirements

Page:

```text
Rewards -> Quests
```

Quest data is not a simple DB-only table. In Cosmic, normal quest requirements
and actions are loaded from `Quest.wz` XML through `server.quest.Quest`.
Scripted starts/completions can also route through `scripts/quest/*.js`.

Primary XML files:

- `Quest.wz/QuestInfo.img.xml`: quest name, metadata, auto flags, time limits.
- `Quest.wz/Check.img.xml`: start/complete requirements.
- `Quest.wz/Act.img.xml`: start/complete actions/rewards.
- `Quest.wz/Say.img.xml`: dialogue text.
- `Quest.wz/Exclusive.img.xml`: exclusivity/grouping metadata.
- `Quest.wz/PQuest.img.xml`: party quest metadata where applicable.

Layout:

```text
+--------------------------------------------------------------------------------+
| Quests | Search quest id/name/map/NPC... | [x] referenced only | + Filter | Reset|
+--------------------------------------------------------------------------------+
| Active Filters: [Region: Victoria x] [Level: 1-30 x] [Reward: Item x]           |
+--------------------------------------------------------------------------------+
| Results: 420 | Page 1/9 | < Prev | [1] [2] [3] ... | Next > | Size 50 v         |
+----------------------+---------------------------------------------------------+
| Left Nav             | Quest Catalog / Rewards                    | Right Dock |
|                      |                                             | Quest      |
| Rewards              | +------+--------------------+-------+------+| Summary    |
| > Quests             | | ID   | Name               | Lv    | Tags ||            |
| > PQ                 | +------+--------------------+-------+------+| Start reqs |
| > Events             | | 1008 | Mai's Training     | 1     | XML  || Complete   |
| > Gachapon           | | 1046 | Bigg's Collection  | 1     | XML  || reqs       |
|                      | +------+--------------------+-------+------+| Rewards    |
|                      |                                             | Technical  |
+----------------------+---------------------------------------------+------------+
| Results: 420 | Page 1/9 | < Prev | [1] [2] [3] ... | Next > | Size 50 v         |
+--------------------------------------------------------------------------------+
| Change Bar: pending quest override changes | validate | apply changes | discard |
+--------------------------------------------------------------------------------+
```

Middle top tabs:

```text
[Overview] [Start Requirements] [Complete Requirements] [Start Rewards]
[Complete Rewards] [Dialogue] [Scripts] [Overrides] [Audit]
```

Recommended filters:

- referenced only.
- quest id/name.
- region.
- map id/name.
- NPC id/name.
- required level min/max.
- max level min/max.
- job.
- starts at NPC.
- completes at NPC.
- requires item id/name.
- rewards item id/name.
- requires mob id/name.
- requires previous quest.
- gives next quest.
- gives EXP.
- gives meso.
- gives fame.
- gives skill.
- gives buff item.
- selectable reward.
- random/probability reward.
- auto start.
- auto complete.
- repeatable.
- time limited.
- has script.
- missing script.
- XML source.
- JS override/source.
- Java override/source.
- DB override/source.
- console XML override/source.
- blocked/incomplete metadata.

Useful extra filters:

- quests available to selected character.
- quests completable by selected character.
- quests in selected map's reachable region.
- quests with requirements but no known source for required item.
- quests requiring mobs with no known spawn map.
- quests with rewards that have missing item metadata.
- quests with script-only behavior.
- quests where XML and script behavior may conflict.

Right dock quest sections:

- summary: quest id/name/category/region/level/job.
- badges/tags.
- start NPC and complete NPC.
- related maps.
- start requirements.
- complete requirements.
- start rewards/actions.
- complete rewards/actions.
- dialogue summary from `Say.img.xml`.
- scripts involved.
- warnings.
- quick links: NPC, map, required items, reward items, required mobs.
- recent changes.
- technical details, collapsed by default.

Right dock requirement display:

```text
Start Requirements
  NPC: 1012000 - Sera
  Level: >= 1
  Previous Quest: none

Complete Requirements
  Item: 4000019 - Snail Shell x10
  Mob: 100100 - Snail x5
```

Right dock reward display:

```text
Complete Rewards
  EXP: 35
  Item: 2000000 - Red Potion x5
  Meso: 100
  Next Quest: 1009 - Next Training
```

Reward/action types to support:

- EXP.
- item give/take.
- meso give/take.
- next quest.
- quest state/progress mutation.
- skill.
- fame.
- buff item.
- pet skill.
- pet tameness.
- pet speed.
- info/infoEx.
- selectable item reward.
- random/probability item reward.
- guaranteed reward.
- choice reward group.
- random reward group.
- conditional reward, where the server/script supports it.
- custom generated equip reward, Phase 2 only.

Requirement types to support:

- job.
- item.
- previous quest.
- completed quest.
- min level.
- max level.
- end date.
- mob kill.
- NPC.
- field enter.
- repeat interval.
- script requirement.
- pet.
- pet tameness.
- monster book.
- info number.
- infoEx.
- meso.
- buff.
- except buff.

### Quest Source Badges

Quest rows, reward rows, requirement rows, and right-dock sections should show
source/override badges.

Recommended badges:

- `XML`.
- `DB Override`.
- `Server Java Override`.
- `Server JS Override`.
- `Server XML Override`.
- `Console Override`.
- `Read Only`.
- `Scripted Start`.
- `Scripted Complete`.
- `Auto Start`.
- `Auto Complete`.
- `Repeatable`.
- `Time Limited`.
- `Selectable Reward`.
- `Random Reward`.
- `Blocked`.
- `Needs Review`.

Source meaning:

```text
XML:
  Original Quest.wz XML-derived data.

DB Override:
  Console-owned override stored in console DB or game DB override table.

Server Java Override:
  Behavior changed by Java quest/action/requirement code.

Server JS Override:
  Behavior changed by scripts/quest or NPC script code.

Server XML Override:
  Server-side XML override layer replaces or patches Quest.wz data before quest
  objects are built.

Console Override:
  Published override owned by Database Console.
```

### Quest Override Implications

Do not treat quest rewards as only DB rows.

Current implication from server code:

- `server.quest.Quest` loads quest info, checks, and actions from WZ data.
- `QuestActionHandler` can run normal XML-backed quest start/complete.
- `QuestActionHandler` can also run scripted start/end through
  `QuestScriptManager`.
- item rewards/removals are executed by `ItemAction`.
- EXP rewards are executed by `ExpAction`, with quest-rate scaling.
- item requirements are checked by `ItemRequirement`.

Therefore, a quest override system needs clear layers:

```text
1. Original XML from Quest.wz.
2. Server XML override patch, if implemented.
3. Java behavior override, if hardcoded behavior exists.
4. JS script behavior, if quest start/end is scripted.
5. Database Console override, if enabled and valid.
```

Recommended rule:

```text
Database Console can edit/publish quest overrides, but server runtime must have
an explicit override lookup layer before those changes affect gameplay.
```

Possible implementation strategies:

1. **Console DB override layer**
   - Store quest requirement/reward overrides in console DB.
   - Server checks override provider before using XML-derived action/requirement
     data.
   - Preferred for portability and auditability.

2. **Generated XML override files**
   - Console writes patched XML override files.
   - Server loads override XML before or instead of original Quest.wz XML.
   - Requires cache invalidation/reload rules.

3. **Direct WZ XML editing**
   - Console edits `wz/Quest.wz/*.xml` directly.
   - Not recommended as default because diffs are noisy and rollback is riskier.

4. **JS script override**
   - Console marks script-owned quests and links to script behavior.
   - Actual script editing should be separate and gated because scripts can do
     arbitrary server actions.

Validation warnings:

- XML reward exists but JS scripted completion may bypass or duplicate it.
- DB override conflicts with JS script behavior.
- requirement references missing item/mob/NPC/map.
- reward references missing item/skill.
- required mob has no known spawn map.
- required item has no known source.
- selectable reward has invalid selection index.
- random reward probabilities do not sum or contain dead entries.
- quest has start/complete NPC but NPC is not found in any reachable map.
- quest has map/field requirement but map is missing/disconnected.
- edited quest is already active on online characters.
- EXP reward preview differs due to quest-rate config.

### How JS/Event Reward Overrides Work

Many PQ/event rewards are not XML quest rewards. They are defined in JS event
scripts and passed into Java event runtime helpers.

Example pattern:

```text
scripts/event/KerningPQ.js

function setEventRewards(eim) {
    evLevel = 1;
    itemSet = [2040505, 2040514, ...];
    itemQty = [1, 1, ...];
    eim.setEventRewards(evLevel, itemSet, itemQty);

    expStages = [100, 200, 400, 800, 1500];
    eim.setEventClearStageExp(expStages);
}
```

Runtime path:

```text
JS event script
-> eim.setEventRewards(eventLevel, itemSet, itemQty)
-> EventInstanceManager stores reward item ids and quantities
-> NPC/script calls eim.giveEventReward(player, eventLevel)
-> EventInstanceManager randomly picks one item from the list uniformly
-> AbstractPlayerInteraction.gainItem(...) gives the item
```

Stage EXP/meso path:

```text
JS event script
-> eim.setEventClearStageExp([...])
-> eim.setEventClearStageMeso([...])
-> eim.giveEventPlayersStageReward(stage)
-> EventInstanceManager gives stage EXP/meso to event players
```

Ownership note:

- PQ/event item reward pools belong in Database Console when a server reward
  override hook exists.
- PQ party size, level ranges, time limits, and stage EXP/meso tuning belong in
  Server Console.
- Database Console can still catalog stage EXP/meso values for inspection, but
  should mark them `Server JS` or `Server Console Owned` and read-only here.

Current runtime behavior:

- `setEventRewards` stores one reward list per event level.
- `giveEventReward` chooses one item by uniform random index.
- quantities are paired by array index.
- optional fixed EXP can be given with the random item.
- stage rewards are EXP/meso lists by stage number and are not Database Console
  editable unless a later ownership decision moves those concrete values here.
- the JS script owns the reward pool definition unless an override provider is
  inserted into the server runtime.

UI implication:

- PQ/event reward rows sourced from JS should show `Server JS Override`.
- event reward arrays should be cataloged as script-derived reward pools.
- editing them in Phase 1 should mean creating a Database Console override, not
  editing the JS file directly.
- direct JS editing should be gated and treated as script/code editing, not a
  normal reward-table edit.

Recommended Phase 1 approach:

```text
Read/catalog JS-defined PQ/event rewards.
Show source as Server JS.
Allow Database Console override records only if server override hook exists.
Do not rewrite event JS files from the normal reward editor.
```

Recommended later server hook:

```text
EventInstanceManager.setEventRewards(...)
-> ask RewardOverrideProvider for replacement/patch by event script name +
   event level
-> store overridden itemSet/itemQty in the event instance
```

Alternative hook:

```text
EventInstanceManager.giveEventReward(...)
-> ask RewardOverrideProvider at reward time
-> choose from override pool if active
```

Preferred hook:

```text
Override during setEventRewards.
```

Reason:

- catalog/debug state reflects what the event instance will actually use.
- right dock can show resolved rewards.
- avoids changing reward pool mid-instance unless explicitly supported.

### Reward Modes

Reward editors must support more than a flat list of rewards.

Reward row modes:

- guaranteed: always granted.
- take item/meso: removes item or meso.
- choice group: player selects one reward from a group.
- random group: server rolls one or more rewards by weight/probability.
- conditional: reward applies only if a requirement is met.
- script-owned: display-only unless script override editing is explicitly
  enabled.
- custom generated equip: console-defined equip instance generation, Phase 2
  only.

Recommended row layout:

```text
Mode        Group  ID/Name                  Qty  Weight  Source      Tags
Guaranteed  -      2000000 - Red Potion     5    -       XML         [Item]
Choice      A      1302000 - Sword          1    -       XML         [Choice]
Choice      A      1322000 - Club           1    -       XML         [Choice]
Random      B      2040601 - Glove ATT 60%  1    10      DB Override [Random]
CustomEquip -      1302000 - Sword          1    -       Console     [Custom] [P2]
Take        -      4000019 - Snail Shell    10   -       XML         [Take]
```

Choice/random groups should be visually grouped and collapsible.

Group display:

```text
Choice Group A
  choose exactly 1
  1302000 - Sword
  1322000 - Club

Random Group B
  roll 1 item
  total weight: 100
  2040601 - Glove ATT 60% weight 10
```

Validation:

- choice group must have at least two options.
- choice group selection count must be valid.
- random group must have positive total weight.
- random group rows should show percent and 1-in-X estimate.
- take rows should use negative/consume semantics clearly.
- script-owned rewards should warn that XML/DB display may not be full runtime
  truth.

### Custom Generated Equip Rewards And Drops

Phase:

```text
Phase 2. Do not include in Database Console Phase 1 implementation.
```

The console should support custom generated equip definitions, but this requires
server runtime support. It is not only a UI/database change.

Use cases:

- NPC reward gives a sword with clean roll plus fixed bonus stats.
- quest reward gives an equip equivalent to passing two 10% scrolls.
- monster drop can produce a clean equip plus one 60% scroll-equivalent bonus.
- event reward gives a pre-scrolled or partially scrolled equip.

Display requirement:

```text
Any custom generated equip must be clearly marked as custom and not original
Cosmic.
```

Required badges:

- `Custom`.
- `Console Generated`.
- `Not Original Cosmic`.
- `Custom Stats`.
- `Custom Drop`.
- `Custom Reward`.

Example drop row:

```text
Mob        Item                  Chance   Custom Rule              Tags
Snail      1302000 - Sword       0.01%    +1 pass 60% equivalent   [Custom Drop]
```

Example reward row:

```text
Quest      Item                  Mode         Custom Rule             Tags
1008       1302000 - Sword       CustomEquip  +2 pass 10% equivalent  [Custom Reward]
```

Right dock custom equip section:

```text
Custom Generated Equip
  Base Item: 1302000 - Sword
  Base Roll: normal clean variable roll
  Bonus Profile: +2 pass 10% equivalent
  Scroll Slots Consumed: 2
  Applied Stats:
    Weapon Attack +10
    STR +6
  Source: Database Console custom equip definition
  Runtime Hook: required
```

Supported custom generation models:

1. Fixed stat override
   - explicitly set STR/DEX/INT/LUK/attack/defense/speed/jump/HP/MP/slots.

2. Additive stat bonus
   - roll clean equip normally, then add configured stat deltas.

3. Scroll-equivalent profile
   - roll clean equip normally, then apply a named virtual scroll profile one or
     more times.

4. Template instance
   - use an exact stored custom equip template.

5. Weighted custom variants
   - choose from multiple custom equip profiles by weight.

Recommended MVP:

```text
Base item id
Generation mode: clean roll + additive stat bonus
Explicit stat deltas
Slots consumed
Expiration
Owner text if needed
Custom source badge
Audit note
```

Avoid implementing scroll-equivalent profiles first unless the server already
has reusable scroll stat application logic. Additive stat deltas are simpler and
less ambiguous.

Technical implication:

```text
Database Console stores custom equip definitions.
Server drop/reward code detects custom reward/drop reference.
Server creates equip instance using ItemInformationProvider base equip.
Server applies custom stat modifiers.
Server inserts/gives/drops the resulting equip instance.
```

Required server integration points:

- mob drop generation path.
- quest reward `ItemAction` path.
- NPC/script reward helper path if used.
- event/PQ reward path.
- audit/logging path for custom generated items.

Validation warnings:

- custom stat exceeds configured safety range.
- custom stat creates impossible equip.
- slots consumed is greater than available slots.
- custom rule references unknown base item.
- custom rule references unsupported virtual scroll profile.
- custom equip is tradeable when policy expects account-bound/event-bound.
- custom drop row lacks `Custom` badge.
- custom generated item is indistinguishable from original item in UI.

Recommended storage boundary:

```text
Console DB:
  custom_equip_templates
  custom_reward_entries
  custom_drop_entries
  custom_generation_audit

Game DB:
  only receives actual generated item instances when applied to characters,
  drops, or rewards.
```

Do not overwrite original Cosmic drop/reward rows for custom generated equips by
default. Store custom entries separately and merge them through the server
override/provider layer.

## Example: NPC Shop Editor

Page:

```text
World Data -> Shops -> NPC Shop
```

Main grid:

- order.
- item icon.
- item id.
- item name.
- price.
- currency.
- quantity/limit if applicable.
- enabled.

Drag/reorder behavior:

- shop rows can be dragged to rearrange order.
- use insert-style ordering, not swap-style ordering.
- dropping a row between two rows inserts it at that position and shifts the
  surrounding rows.
- order changes remain unapplied until `Apply Changes`.

Important Cosmic ordering note:

```text
Larger order number appears higher in the actual game shop list.
```

The UI should make this explicit. Recommended display:

```text
Display Position | Game Order Number | Item
1                | 9999              | Red Potion
2                | 9998              | Orange Potion
3                | 9997              | Blue Potion
```

When users drag rows, the UI should update display position first and calculate
the resulting game order numbers before apply.

Add item:

- autocomplete by item id/name.
- show icon, id, name, item type, NPC value if known.
- warn on duplicate item in same shop.
- warn if item is not normally sellable or has missing metadata.

Right dock:

- selected item details.
- current shop row details.
- other shops selling the item.
- NPC/map context.
- price/value warnings.
- technical details at the bottom.

## Detail Dock Design

Dock modes:

```text
Item
Mob
Character
Account
Quest
NPC
Shop
Reward Pool
Recipe
Audit Entry
Validation Issue
```

Dock sections:

- hero: icon, name, id, type.
- quick facts.
- relationships.
- warnings.
- recent changes.
- quick actions.
- links.
- technical details.

Each dock section should be collapsible.

Default expanded:

- hero.
- quick facts.
- relationships.
- warnings.
- recent changes.
- quick actions.
- links.

Default collapsed:

- technical details.

The technical details section should appear at the bottom by default. It should
be visually quieter than the gameplay/admin summary and expanded only when the
user wants raw implementation/debugging context.

Technical details can include:

- raw ids.
- WZ paths.
- DB table names and row ids.
- source file names.
- override source.
- icon source type.
- icon source URL, for example the maplestory.io URL used for the selected
  item/NPC/mob where available.
- icon fallback path or missing-icon reason.
- cache/index version.
- last indexed timestamp.
- validation state.
- raw flags.
- server ownership note, for fields owned by Server Console.
- agent/catalog ownership note, for fields owned by Agent Console or catalog
  builders.

Dock behavior:

- resizable.
- collapsible.
- can pin current entity while selecting other rows.
- can open entity as full workspace.
- follows selection by default.

## Autocomplete Design

All autocomplete fields should support:

- id search.
- exact name search.
- fuzzy name search.
- category filters.
- recent selections.
- icon preview for items.
- result metadata.

Examples:

```text
Item result:
  [icon] 4000016 Orange Mushroom Cap  ETC / quest-like item

Mob result:
  100100 Orange Mushroom  Lv 8  appears in 12 maps

Quest result:
  1008 Mai's Training  Maple Island  available Lv 1+
```

Autocomplete should never write game data by itself. Selection fills the
focused editor row, then the page saves through its API endpoint, validation,
confirmation when needed, and audit event.

## Editing And Validation

Every editable field should have a validator.

Examples:

- item id exists.
- mob id exists.
- chance is within valid range.
- min <= max.
- quantity does not exceed slot max unless allowed.
- equip stat is within configured safety range.
- quest id exists.
- referenced reward pool is enabled.
- one-of-a-kind rules are respected unless override is explicit.

Validation severity:

```text
info
warning
blocking error
```

Only blocking errors prevent save.

## Recommendations Over The Initial Idea

The proposed right dock and inline editing model is strong. Recommended additions:

### Add A Command Palette

Keyboard-first global action/search:

```text
Ctrl+K
search account / IGN / item / mob / quest / map / NPC / action
```

This reduces navigation clicks.

### Add Recent Trail

Because cyclic navigation is intentional, keep:

- breadcrumbs for hierarchy.
- recent entities list.
- back/forward history.

### Add Pinned Compare

Allow pinning one entity in the right dock and selecting another in the grid.

Useful for:

- comparing two items.
- comparing two mobs.
- checking item source while editing reward pool.

### Add Bulk Edit Mode

Normal mode should be safe and single-row friendly. Bulk mode should be explicit.

Bulk examples:

- multiply drop chances.
- disable a reward pool.
- import drop rows.
- batch add gachapon rewards.

### Add Environment Safety Banner

Always show:

```text
DEV / STAGING / PRODUCTION
```

Dangerous edits should require stronger confirmation in production.

### Add Preview SQL / Preview Diff

For every apply:

- show before/after.
- show affected rows.
- optionally show SQL or structured mutation plan.

### Add Permission-Based Field Visibility

Some fields should require elevated role:

- account password/session fields.
- GM flag.
- ban/security fields.
- direct meso/NX edits.
- equip stat edits.
- item creation/deletion.

### Add Relationship Graph Later

For complex items/quests/rewards:

```text
item -> mobs -> maps -> quests -> reward pools -> owners
```

This can be a later visual mode, not MVP.

## Technical Implementation Direction

The Database Console should be implemented as a graph inspection and safe
mutation console, not as a collection of unrelated CRUD pages.

Core concepts:

```text
Entity
Relationship
Badge/Tag
Source of Truth
Unapplied Change
Validation
Apply Transaction
Audit Event
```

### Entity Metadata Schema

Use metadata to describe fields, editability, filters, badges, and ownership.

Example:

```text
entity: item
field: requiredDex
type: number
source: WZ
editable: false
filter: range
badges: [WZ, Read Only]
owner: Server Console if overridden
```

This avoids hardcoding every page separately.

### Unified Reference Index

Build fast read indexes for right-dock relationships and catalog filters.

Indexes:

- item -> mobs that drop it.
- item -> shops selling it.
- item -> quests/rewards/pools using it.
- item -> maker/craft usage.
- mob -> maps where it appears.
- mob -> drops.
- mob -> quest requirements.
- map -> portals.
- map -> mobs.
- map -> NPCs.
- map -> reactors.
- quest -> required items/mobs/NPCs.

Catalog pages should query read models/indexes instead of joining many raw
tables live.

### Focused Save And Audit Layer

The current console uses focused saves instead of a global unapplied-change
layer. Inline edits should validate and save only when the value actually
changes.

Focused save flow:

```text
UI edit
-> local/API validation
-> focused transaction
-> audit event
-> toast
-> refresh indexes affected by the change
```

Normal game DB edits should not wait for a global `Apply Changes` button.
Dangerous changes still require a custom confirmation modal and should preserve
the selected entity after saving.

### Optimistic Concurrency

Prevent overwriting newer edits.

Track one or more of:

- row version.
- updated timestamp.
- original row hash.

If the underlying row changed after the workspace loaded, require refresh or
manual conflict resolution before apply.

### Audit Event

Every saved change should create an audit event.

Minimum fields:

- actor.
- target type.
- target id.
- field/relationship changed.
- before value.
- after value.
- timestamp.
- reason/note if required.
- request/action id.
- source console.
- validation warnings acknowledged.

### Console Database Boundary

Store console-owned data outside the original Cosmic game DB where practical.

Console DB:

- UI preferences.
- saved filters.
- badges/tags if manually curated.
- override records.
- audit logs.
- permissions/roles, if authentication is enabled later.

Original Cosmic DB:

- actual game data.
- character data.
- inventory data.
- original reward/drop/shop tables.

The server should still run without Database Console installed.

### Route And API Contract Pattern

Each catalog-to-workspace page should follow the same route and API shape. This
keeps the frontend predictable and lets each domain be implemented independently.

Database access rule:

```text
The web UI always talks to Database Console API endpoints.
The UI never connects directly to the original Cosmic database or
cosmic_database_console.
```

Write flow:

```text
UI edit
-> API validate
-> API writes focused change
-> service/repository writes cosmic_database_console or original Cosmic DB
-> audit event
-> index refresh
-> bottom-right toast / local UI refresh without losing selection
```

Original Cosmic DB writes are allowed only through Database Console API
operations for explicit original-DB edits. Dangerous writes use custom
confirmation modals, not browser prompts. Override edits write to
`cosmic_database_console` first and become runtime-active only through server
providers/hooks.

Route pattern:

```text
/{domain}
/{domain}/{id}
/{domain}/{id}/{tab}
```

Examples:

```text
/items
/items/1472000
/items/1472000/dropped-by

/mobs
/mobs/100100
/mobs/100100/drops

/quests
/quests/1008
/quests/1008/rewards

/characters/12345/inventory
/characters/12345/equipment

/create/account
/accounts/42/create-character
/create/character?accountId=42
```

Catalog query contract:

```text
GET /api/{domain}

query:
  search
  filters
  sort
  page
  pageSize
  viewMode

returns:
  rows
  total
  page
  pageSize
  availableFilters
  badges
  indexVersion
```

Workspace read contract:

```text
GET /api/{domain}/{id}

returns:
  entity
  tabs
  relationships
  badges
  sourceOwnership
  validationSummary
  technical
  indexVersion
```

Tab read contract:

```text
GET /api/{domain}/{id}/{tab}

returns:
  tabData
  editableFields
  sourceOwnership
  validators
  relatedEntities
```

Reference autocomplete contract:

```text
GET /api/reference/{type}

query:
  search
  filters
  limit

returns:
  id
  name
  icon
  type
  context
  badges
  warnings
```

Creation defaults contract:

```text
GET /api/create/account/defaults
GET /api/create/character/defaults

query for character defaults:
  accountId
  world
  gender, optional

returns:
  allowedWorlds
  availableSlots
  namingRules
  defaultInitialMap
  defaultJob
  allowedGenders
  allowedSkins
  allowedHair
  allowedFaces
  allowedStarterEquipment
  serverCreationPolicy
  warnings
```

Creation validation contract:

```text
POST /api/create/account/validate
POST /api/create/character/validate

payload:
  requestedAccountOrCharacter
  adminOverrideFlags

returns:
  validation
  generatedRowsPreview
  warnings
  auditPreview
```

Creation apply contract:

```text
POST /api/create/account/apply
POST /api/create/character/apply
POST /api/create/account-with-character/apply

payload:
  requestedAccountOrCharacter
  baseCreationPolicyRevision
  acknowledgedWarnings
  reason

returns:
  accountId
  characterId
  generatedItemInstanceIds
  affectedRows
  auditBatchId
  nextRoute
```

Unapplied change contract:

```text
POST /api/changes/validate
POST /api/changes/apply
POST /api/changes/discard

payload:
  workspaceId
  baseRevision
  changes[]
  reason

returns:
  validation
  mutationPlan
  affectedRows
  auditBatchId
  refreshedEntities
```

Change object shape:

```text
changeId
domain
targetId
targetPath
operation        # set, add, remove, reorder, replace, duplicate
before
after
sourceOwnership
runtimeStatus
validation
```

Runtime status values:

- `Runtime Active`.
- `Catalog Only`.
- `Override Ready`.
- `Server Console Owned`.
- `Requires Reload`.

Apply rules:

- apply runs validation first.
- blocking errors stop apply.
- warnings require explicit acknowledgement.
- apply creates an audit batch.
- apply refreshes affected read indexes.
- if `baseRevision` is stale, require refresh or conflict resolution.

Recommended route ownership:

| Area | Routes |
| --- | --- |
| Accounts | `/accounts`, `/accounts/{id}`, `/create/account`, `/api/create/account/*` |
| Characters | `/characters`, `/characters/{id}`, `/characters/{id}/inventory`, `/characters/{id}/equipment`, `/accounts/{id}/create-character`, `/create/character`, `/api/create/character/*` |
| Items | `/items`, `/items/{id}` |
| Mobs | `/mobs`, `/mobs/{id}` |
| Maps | `/maps`, `/maps/{id}` |
| NPCs | `/npcs`, `/npcs/{id}` |
| Shops | `/shops`, `/shops/{id}` |
| Quests | `/quests`, `/quests/{id}` |
| Rewards | `/rewards/{type}`, `/rewards/{type}/{id}` |
| Craft | `/craft`, `/craft/{id}` |
| Audit | `/audit`, `/audit/{batchId}` |

Implementation rule:

```text
Do not make each page invent its own mutation model. Pages should call focused
domain API operations that share validation, confirmation hooks, audit logging,
toast reporting, and selection-preserving refresh behavior.
```

## MVP UI Implementation Order

1. Global layout: sticky page header, left nav, main workspace, right dock.
2. Shared entity autocomplete.
3. Shared editable grid component.
4. Shared detail dock component.
5. Account/character read-only workspaces.
6. Mob drop table editor.
7. Item source/drop editor.
8. Character inventory editor.
9. Gachapon reward override editor.
10. Focused save, confirmation modal, toast, and audit workflow.
