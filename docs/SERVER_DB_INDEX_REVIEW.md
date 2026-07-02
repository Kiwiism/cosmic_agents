# Server DB Index Review

Purpose: record non-invasive DB review notes before adding migrations. Do not add indexes blindly; compare these candidates with slow-query logs from soak tests first.

## Current Hot Tables Reviewed

### `accounts`

Existing indexes:

- Primary key: `id`
- Unique: `name`
- `ranking1 (id, banned)`
- `(id, name)`
- `(id, nxCredit, maplePoint, nxPrepaid)`

Review notes:

- Login lookup by account name is already covered by `UNIQUE KEY name`.
- Session/account updates by `id` are covered by the primary key.
- No immediate migration recommended without slow-query evidence.

### `characters`

Existing indexes:

- Primary key: `id`
- `accountid`
- `party`
- `ranking1 (level, exp)`
- `ranking2 (gm, job)`
- `(id, accountid, world)`
- `(id, accountid, name)`

Review notes:

- Character-list queries filter by `accountid` and `world`; current `accountid` helps, but a composite `(accountid, world)` may be better if character-list load appears in slow-query logs.
- Character-name lookup depends on query shape. If name lookup is frequent, consider a unique/non-unique `name` index after confirming existing migrations and case-sensitivity expectations.

### `queststatus` / `questprogress` / `medalmaps`

Existing indexes:

- `queststatus`: primary key only.
- `questprogress`: primary key only.
- `medalmaps`: primary key and `queststatusid`.

Review notes:

- Character save/load/delete paths commonly filter by `characterid`.
- Candidate indexes if slow logs confirm pressure:
  - `queststatus(characterid)`
  - `questprogress(characterid)`
  - `medalmaps(characterid)`
- `QuestStatus Placeholder Save Filter` already reduces unnecessary row growth; validate that before adding indexes.

### `inventoryitems` / `inventoryequipment` / `inventorymerchant`

Existing indexes:

- `inventoryitems`: primary key and `CHARID(characterid)`.
- `inventoryequipment`: primary key and `INVENTORYITEMID(inventoryitemid)`.
- `inventorymerchant`: primary key and `INVENTORYITEMID(inventoryitemid)`.

Review notes:

- Character inventory load/delete is partly covered by `inventoryitems(characterid)`.
- Storage/account inventory paths may benefit from `inventoryitems(accountid)` if slow logs show storage/cash/shop pressure.
- Merchant inventory cleanup may benefit from `inventorymerchant(characterid)` if merchant retrieval/cleanup is slow.

### `storages`

Existing indexes:

- Primary key only.

Review notes:

- Storage load uses `accountid` and `world`.
- Candidate index if slow logs show storage load pressure: `storages(accountid, world)`.

### `shopitems`

Existing indexes:

- Primary key only.

Review notes:

- Shop loading filters by `shopid` and orders by `position`.
- Candidate index if shop load is slow: `shopitems(shopid, position)`.

### `newyear`

Existing indexes:

- Primary key only.

Review notes:

- Player card load filters by `senderid OR receiverid`.
- Candidate indexes if this feature is used at scale:
  - `newyear(senderid)`
  - `newyear(receiverid)`

## Migration Rules

1. Capture slow-query evidence first.
2. Add one migration batch at a time.
3. Prefer indexes matching exact `WHERE` and `ORDER BY` usage.
4. Re-run character login, save, storage, shop, and deletion flows after migration.
5. Document before/after query timings in this file.
