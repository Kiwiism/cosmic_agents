# Server Cache Audit

Purpose: classify server-side caches for 30-day uptime and future agent load.

## Current Diagnostics Added

- `Storage` fee caches now report size growth through `CacheDiagnostics`.
- `Server` periodic scale health logging includes heap, DB pool, scheduler, and timer pressure.
- GM6 `!serverhealth` exposes a compact on-demand runtime snapshot.
- Character logout cleanup now warns if known scheduled tasks remain after cleanup.
- Map dispose now logs dispose-time object/drop/portal/stat-update counts at debug level.
- Event instance dispose now logs dispose-time character/mob/map/property/timer counts at debug level.
- Logout clears NPC/quest script manager state for the client before character references are emptied.
- Scale health and `!serverhealth` include EXP debug, monitored character, pending NPC runtime, NPC/quest script, login-attempt, login-bypass, New Year card, and login-state counts.

## Cache Classes To Keep Reviewing

### Intentionally Permanent

- WZ-derived data caches keyed by item, skill, map, mob, NPC, or reactor id.
- Static quest, item, skill, and drop metadata loaded from assets or DB.
- These should grow to the size of the game data, then stabilize.

### Bounded By Runtime Cleanup

- Character/session keyed maps.
- Event instance player/mob/map state.
- Map objects, drops, reactors, and timed map state.
- Merchant/shop owner registrations.
- Debug sessions and temporary monitoring sets.

### Needs Ongoing Soak-Test Watch

- Any `static Map<Integer, ...>` keyed by character id, account id, object id, or map id.
- Any cache populated from live character, map, party, merchant, messenger, guild, buddy, or event objects.
- Any queue/list fed by frequent packets or timer callbacks.

## Current Runtime Cache Owners

| Cache | Owner | Cleanup / Bound |
| --- | --- | --- |
| EXP debug sessions | `ExpDebugTracker` | TTL cleanup during scale health and tracking toggles |
| Monitored characters | packet logging monitor | count exposed in scale health / `!serverhealth` |
| Pending NPC/dressing room state | `AbstractPlayerInteraction` | cleared on poll and logout cleanup |
| NPC script conversations | `NPCScriptManager` | disposed normally and during client cleanup |
| Quest script actions | `QuestScriptManager` | disposed normally and during client cleanup |
| Login attempt history | `LoginStorage` | pruned during login registration and scheduled login storage task |
| Login bypass entries | `LoginBypassCoordinator` | expires on read/update and scheduled login storage task |
| New Year cards | `Server` / `NewYearCardRecord` | removed on card delete; repeating task stopped on removal |
| In-login-state clients | `Server` | removed on login-state unregister and idle login cleanup |
| Account character views | `World` | one entry per loaded account; cleared when the account view is released; count in `!serverhealth` |
| Account storages | `World` | one entry per active/loaded account; removed on account storage unregister; count in `!serverhealth` |
| Families | `World` | bounded by persisted families in that world; explicit family removal; count in `!serverhealth` |
| Messengers | `World` | at most active non-empty messengers; removed when the final member leaves; count in `!serverhealth` |
| Player shops | `World` | at most active shops; explicit unregister on close; count in `!serverhealth` |
| Hired merchants | `World` / `Channel` | at most one identity-checked registration per owner; removed on close/timeout; count in `!serverhealth` |
| Cash Shop/MTS buff handoff | `PlayerBuffStorage` | one entry per character in transition; consumed on world login; retained count in `!serverhealth` |
| WZ/DB metadata | item, mob, quest, map, NPC, reactor providers | bounded by source game-data IDs; intentionally process-lifetime |

The Cash Shop/MTS handoff does not yet have a TTL because expiring it changes
buff restoration semantics. Watch its count during soak tests; add explicit
failed-transition cleanup only with a reproduced retention path.

## Review Rules

1. Metadata cache: document expected max cardinality.
2. Runtime cache: document owner and cleanup path.
3. Queue: document dedupe rule and drain interval.
4. Character-keyed cache: clear on logout and agent despawn, or add size diagnostics.
5. Map/event cache: clear on map/event dispose, and log retained counts during soak tests.
