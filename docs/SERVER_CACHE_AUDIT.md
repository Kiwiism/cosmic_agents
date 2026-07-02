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

## Review Rules

1. Metadata cache: document expected max cardinality.
2. Runtime cache: document owner and cleanup path.
3. Queue: document dedupe rule and drain interval.
4. Character-keyed cache: clear on logout and agent despawn, or add size diagnostics.
5. Map/event cache: clear on map/event dispose, and log retained counts during soak tests.
