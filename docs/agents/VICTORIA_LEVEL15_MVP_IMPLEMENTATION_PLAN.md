# Victoria Island autonomous level-15 MVP

## Outcome and acceptance criteria

The first post-Maple-Island vertical slice is complete only when an Agent can resume after relog and independently:

1. restore one durable career/build bundle;
2. finish the full Maple Island plan and leave through Shanks's ordinary NPC script;
3. complete active Biggs quest `1046`, Olaf lesson `2081`, and start the profile-selected Olaf career path;
4. reach level 10, using Right Around Lith Harbor only when the handoff EXP is insufficient;
5. use the Lith Harbor taxi's ordinary NPC script to reach its career town;
6. complete the Olaf career path at the selected instructor, then advance through the instructor's ordinary NPC script;
7. receive the script's native starter kit exactly once;
8. complete all four first-job instructor training quests in order;
9. complete the career-town quest pack, complete the configured rotation-town pack when applicable, then grind only
   the remaining experience needed for level 15;
10. assign AP and SP from the selected profiles at every level;
11. suspend the foreground objective for critical supplies, shop, and resume the same objective;
12. survive logout/relog without changing career, build, quest cursor, or foreground intent.

“Reach level 15” is a postcondition, not a timer or an assumed quest reward.

## Implemented foundation

### Executable plan and content catalog

The state machine is now described and content-driven by two versioned resources instead of duplicated content
switches:

- `src/main/resources/agents/progression/victoria-level15-stage-contract.json` declares the internal
  level-15 step stages and postconditions used by the universal `victoria-level15-mvp` plan,
  and every capability the slice depends on;
- `src/main/resources/agents/catalogs/victoria-level15-mvp-catalog.json` owns taxi selections, town/instructor/shop
  locations, Biggs/Olaf handoff quests, three reset variants, each career's Olaf path, native starter-kit items,
  relevant verified shop stock, route corridors, scripted portals, all instructor quest kill requirements/reward EXP,
  five pre-level-15 town quest packs, each career's home/rotation policy, and each career's fallback grind map.

Both resources are loaded and validated before `!victoria run` or `!victoria reset` can mutate an Agent. Catalog entries are
cross-checked against every durable career bundle: job, instructor, map, and ordered quest IDs must agree. The live
objective records the plan ID as its behavior version, so test evidence identifies the exact plan contract used. The
loader also requires the plan's stage order to match the stages implemented by the progression state machine.

The required capability contract covers career assignment, AP/SP allocation, starter-gear auto-equip, portal
navigation, NPC scripts, shops, quest execution, generic combat, loot, potion use, procurement, bounded inventory
handling, and physics recovery.
The vertical-slice runner orchestrates those capabilities; it does not duplicate movement, combat, or shop logic.

### Durable career/build bundles

Source-controlled choices live in `src/main/resources/agents/profiles/career-build-bundles.json`. A bundle binds:

- career and first-job id;
- advancement level;
- AP profile id;
- SP profile id;
- instructor NPC and map;
- the ordered four-quest training chain;
- milestone level.

The first assignment is deterministic by character id. It is then persisted under
`.runtime/agents/progression/careers/<characterId>.json`. Relog restores the stored bundle; it does not roll again.
A bundle-version mismatch fails closed so changing a template cannot silently rewrite a live Agent build.

Registration restores both AP and SP profiles before normal Agent ticks. Beginner Agents retain the selected profile
while it is inapplicable; the existing level/job guards begin spending points when the matching first job is active.

### Generic objective suspension

The objective kernel now has a foreground slot and a LIFO suspended stack. A maintenance objective can suspend the
foreground objective without cancelling its plan progress. Successful maintenance restores the exact objective id,
correlation id, source, retry budget, and behavior version and records `SUSPENDED`/`RESUMED` journal events.

Only one component owns foreground replacement. Combat, shopping, navigation, and dialogue must request an
interruption; they must not independently clear plan state.

### Supply request consumption

Critical/empty `AgentProcurementRequest` records now become `maintenance.resupply` objectives. On a map containing a
suitable shop, the existing shop workflow performs validated purchases. Once observation reports the target restored,
the maintenance objective succeeds and the suspended objective resumes.

Cross-map procurement is now implemented. A critical request first tries a suitable shop on the current map, then uses
the career shop catalog and Victoria route service to select a verified supplier. The maintenance objective records the
return map, travels through ordinary portals, invokes the existing shop workflow, returns, and resumes the exact
suspended foreground objective. Missing routes, unavailable shops, expiry, and retry exhaustion terminate the
maintenance objective and restore the foreground objective instead of deadlocking it. The shop workflow still owns
meso and inventory validation; enforcing `maximumBudget` as a separate purchasing constraint remains future policy.

### Real first-job journey

The journey deliberately does not call `changeJob`, grant a custom starter kit, deduct mesos, or warp directly.

- Southperry: approach Shanks (`22000`) and run his NPC script. The script consumes Lucas's recommendation letter or
  150 mesos and warps to Lith Harbor (`104000000`).
- Lith Harbor handoff: complete the already-active Biggs quest `1046` at Olaf (`1002101`), complete Olaf's job lesson
  `2081`, then start exactly one path selected from the durable build bundle: Warrior `2077`, Bowman `2078`, Thief
  `2079`, Magician `2080`, or Pirate `2212`.
- Pre-job level gate: if the Agent is still below level 10, walk to Right Around Lith Harbor (`104000100`), grind only
  Snail `100100` and Blue Snail `100101`, then walk back to Lith Harbor.
- Lith Harbor: approach the regular cab (`1002000`) and choose the career destination from its real menu. The script
  charges the beginner fare.
- Career town: navigate to the direct instructor-room portal and enter it.
- Instructor room: complete the active Olaf career path at the matching instructor before attempting advancement.
- Instructor room: wait until grounded, approach the instructor, and run the instructor script. The script validates
  level/stats, changes the job, resets stats as defined by the server, and grants the native starter equipment.

This avoids double starter kits: `AgentStarterKitService.advanceJob` is not used by this path.

After the instructor changes the job, the normal Agent build hook is invoked immediately. This spends the initial
first-job SP and any reset AP from the selected profiles instead of waiting for the next level-up tick. The equipment
capability then performs a forced reconciliation so the native script-granted weapon is equipped before training.

### Reusable Lith Harbor reset point

The guarded GM command below prepares one live Agent and immediately starts the reference run after a three-second
observation window:

```text
!victoria run <AgentIGN> <warrior|bowman|magician|thief|pirate> [lv10|lv9-olaf|lv9-grind]
!victoria reset <AgentIGN> <career> [lv10|lv9-olaf|lv9-grind]
!victoria status <AgentIGN>
```

`thief-dagger` and `pirate-knuckle` select the alternative build bundles; the simple `thief` and `pirate` aliases use
the claw and gun profiles. Repeat the reset for five spawned Agents to run one representative of every first job.

The reset is intentionally destructive and test-only. Every variant creates a Beginner at Lith Harbor with 1,000
mesos, clean inventories with the canonical Beginner Sword equipped, reset handoff/path/training quests, Biggs `1046`
active and ready to complete at Olaf, and an
explicitly persisted AP/SP career bundle. It does not change the job or grant a starter kit. Those mutations still
happen only through the ordinary instructor NPC script.

- `lv10`: level 10 with 0 EXP; validates the complete handoff without a pre-job grind.
- `lv9-olaf`: level 9 with 579/1,144 EXP. Biggs adds 500 and Olaf `2081` adds 65, so Olaf's lesson lands exactly on level 10.
- `lv9-grind`: level 9 with 0/1,144 EXP. The same 565 handoff EXP is insufficient, so the Agent must grind and return.

For this fixture the sequence is:

1. complete Biggs and Olaf's lesson at Olaf and start the career-specific path selected by the build bundle;
2. grind at Right Around Lith Harbor and return only when the selected fixture remains below level 10;
3. use the Lith Harbor taxi and pay its normal beginner fare;
4. enter the real instructor map, complete the Olaf path, and advance through the ordinary NPC script;
5. let the configured AP/SP profiles reconcile the script-created AP/SP pool;
6. walk to the career town's verified potion shop and run the ordinary shop workflow;
7. walk back to the instructor and run the four instructor quests;
8. complete the configured home-town pack, then the configured rotation-town pack for Warrior, Magician, and Thief;
9. grind locally only if the Agent is still below level 15;
10. return to and approach the original instructor before marking the objective complete.

The initial shop stops are Perion Department Store (`102000002`), Henesys Department Store (`100000102`), Ellinia
Department Store (`101000002`), Kerning Pharmacy (`103000002`), and Nautilus Mid Floor (`120000200`). A shortfall from
the deliberately small 1,000-meso budget is a valid reconciled shop visit; a missing shop/NPC or interrupted workflow
blocks the test instead of silently skipping it.

### Instructor training, town packs, and milestone grind

The runner reconciles each ordered quest from live quest status, starts/completes it through the normal quest gateway,
walks the verified portal corridor to its hunting map, and gives combat only the required mob ids. It returns to the
instructor after `canComplete` becomes true. After quest four, the durable cursor enters a data-driven town-pack stage:

- Warrior: Perion home pack, then Ellinia;
- Magician: Ellinia home pack, then Nautilus;
- Bowman: Henesys home pack, then local grind if still required;
- Thief: Kerning home pack, then Perion;
- Pirate: Nautilus home pack, then local grind if still required.

Each pack reconciles its cursor from live quest status after relog, so completed quests are never replayed. Only after
the required pack sequence does the Agent recalculate level and use the cataloged fallback grind while below level 15.
The final return writes an immutable `first-job-level15.json` evidence checkpoint under
`.runtime/agents/progression/milestones/<characterId>/`. The reset command clears pack quests, transient post-15 state,
and prior test milestone evidence so repeated runs are independent.

## Verified instructor chains

These values were read from `wz/Quest.wz/Check.img.xml` and `Act.img.xml`. Quest `2196` is the one deliberate source
correction: its QuestInfo and Say records repeatedly name ordinary Green Mushroom `1110100`, while the lone
`Check.img` value `9101000` contradicts the text and has no normal Victoria spawn.

| Career | Instructor | Quests | Kill requirements | Total EXP |
|---|---:|---|---|---:|
| Warrior | `1022000` | `2128`–`2131` | Stump 20/50/80, Dark Stump 15 | 2,685 |
| Magician | `1032001` | `2132`–`2135` | Slime 8/20/35, Dark Stump 10 | 1,428 |
| Bowman | `1012100` | `2136`–`2139` | Slime 16/40/65, Ribbon Pig 15 | 2,685 |
| Thief (claw/dagger) | `1052001` | `2140`–`2143` | Stump 20/50/80, Octopus 10 | 2,685 |
| Pirate (gun/knuckle) | `1090000` | `2193`–`2196` | Pig 11/26/43, quest Green Mushroom 10 | 2,685 |

The instructor chains do not guarantee level 15. Magician is especially short, so the home/rotation packs close the
expected gap first; the fallback grind is retained as a postcondition guard rather than assuming fixed EXP rates.

### Pirate quest 2196 source correction

The server now narrowly maps `9101000` to ordinary Green Mushroom `1110100` only while loading quest `2196`. The same
correction is applied to the quest's relevant-mob tracker and completion requirement, so normal Green Mushroom kills
both increment progress and satisfy completion. No other quest or mob ID is changed.

The real reachable hunt is cataloged as Nautilus -> Pig Beach (`100030000`) -> Henesys Hunting Ground
(`100040000`) -> Hidden Street (`100040003`). `100040003` actually spawns Green Mushrooms in this WZ set. There is no
`120020000` map in this repository, so the runner does not use that invalid destination.

## Catalog validation gates

Focused tests fail the build if any MVP content drifts:

- WZ validation checks that every training/fallback mob really spawns on its map, every instructor/shop NPC is placed
  on its map, the Biggs/Olaf/path quest NPCs and EXP match source data, the level-9 EXP variants straddle the actual
  level threshold, and every route edge is traversable in both directions;
- SQL validation checks that every shop NPC has a shop row and sells every cataloged potion/ammunition item;
- NPC-script validation checks the Lith Harbor taxi selection order and every native instructor starter-kit grant;
- repository validation checks all seven career bundles, ordered quests, counts, reward EXP, starter preferences, and
  plan-to-catalog/capability references;
- quest validation loads the real quest `2196` and verifies that only its contradictory mob ID is corrected.

These gates caught and corrected two remembered-map assumptions during implementation: Kerning Construction Site
does not spawn ordinary Stumps, and `101010101` does not connect directly to `101020000`.

## Implemented level 15-30 continuation

### 1. Generated Victoria route graph and bounded replanning

The curated level-15 training corridors remain the first-choice edges, followed by a generated graph of 626 verified
standard Victoria portal edges in `victoria-portal-route-graph.json`. The runner asks for one next hop and never warps.
Failed edges are counted, temporarily excluded after three failures, and reconsidered after a bounded cooldown.
Scripted and self-referential portals are not inferred as ordinary travel edges. Current level-15 hunting destinations
are:

- warrior: `102010000` for Stump/Dark Stump;
- magician: `101010000` for Slime and `101010101` for Dark Stump;
- bowman: `100040000` for Slime and `100030000` for Ribbon Pig;
- thief: `102020000` for Stump and `103010000` for Octopus/fallback grind;
- pirate: `120010000` for Pig and `100040003` for Green Mushroom.

Every map, spawn, NPC, taxi choice, and shop used by this MVP has been audited against WZ/scripts/SQL. The graph
generator records its source SHA so regeneration after map-data changes is reviewable. Rich edge cost, preconditions,
and arrival-anchor metadata remain useful later refinements.

### 2. Durable plan checkpoint and relog reattachment

Foreground and suspended objective state is atomically persisted under
`.runtime/agents/plans/objective-checkpoints`. Career stage and build identity are independently persisted under
`.runtime/agents/progression/checkpoints`. Registration restores both and reattaches the applicable Maple Island plan
runner with bounded retry. On restore, runners still reconcile character, map, quest, and inventory truth before issuing
an action; starter grants and quest rewards are never replayed from a cached command flag.

### 3. Cross-map supply maintenance

The deterministic post-advancement shop and live procurement requests now share the same transaction workflow.
Critical potion or ammunition requests suspend training, route to a career-appropriate supplier, shop, return, and
resume. Failure and expiry are explicit journal outcomes and no longer strand the foreground objective. Inventory-space
recovery and a hard per-request maximum budget remain bounded follow-up policies rather than hidden shop behavior.

### 4. Reward-choice policy

Quest completion now asks `AgentQuestRewardChoicePolicy` for an explicit selection. Its stable order is authored fixed
choice, job-eligible equipment, current supply need, market value, then source order. The headless NPC gateway no longer
silently treats its menu default as Agent policy. Fixed per-quest overrides can be added to
`quest-reward-choice-policy.json` without changing the scheduler.

### 5. Mixed quest/grind continuation and evidence gate

`progression.victoria-training` now drives levels 15-30. It selects ranked maps using live player plus Agent occupancy,
ignores cataloged hazard mobs as combat targets, and replans around temporarily failed route edges. `mixed` mode uses
the Agent's durable progression profile to choose quests versus grinding and to rank both ordinary training maps and
quest hunt maps; `grind` mode disables quest scheduling. The conservative runnable quest catalog contains 71 quests
whose acquisition requirements are fully supported by current combat/loot execution.

Five versioned profiles live in `agents/profiles/progression-profiles.json`: balanced, quester, grinder, explorer, and
hunter. They independently weight quest/grind preference, efficiency, exploration, crowd avoidance, travel tolerance,
risk tolerance, and routine. The first deterministic assignment is stored with the durable career assignment, and old
assignment files are migrated once without changing their career bundle. Hard route eligibility and map capacity remain
constraints; personality only ranks valid candidates.

Use:

```text
!victoria train <AgentIGN> [16-30] [mixed|grind]
!victoria stop <AgentIGN>
!victoria snapshot <AgentIGN>
```

Automatic per-level evidence is written atomically under
`.runtime/agents/progression/evidence/<characterId>/level-<level>.json`.
Immutable `victoria-level20.json`, `victoria-level25.json`, and `victoria-level30.json` milestone snapshots are also
captured under `.runtime/agents/progression/milestones/<characterId>/`. Evidence schema 2 records the progression
profile id/version and the map-selection reason so cohort behavior can be audited rather than inferred.

Run one standard thief-claw Agent first because its AP/SP profile and quest chain exercise DEX/LUK, ranged ammunition,
and the common Stump/Octopus routes. Capture after every level:

- job, level, EXP;
- STR/DEX/INT/LUK and remaining AP;
- skill levels and remaining SP;
- current objective plus suspended stack;
- quest cursor/status;
- potion/star counts and mesos;
- map and last route edge.

Acceptance: the Agent reaches level 15, all four training quests are complete, no points remain unintentionally, no
starter item is duplicated, and a forced resupply detour resumes the exact interrupted quest. Then enable the same flow
for every bundle. For level 30, additionally require that mixed-mode quest decisions, occupancy fallback, failed-edge
replanning, logout/relogin reattachment, and explicit selectable rewards appear correctly in the evidence before a
large cohort rollout.

## Rollback and branch safety

The work is additive under `server.agents.progression`, the objective kernel, and the primitive gateway. It does not
rewrite movement physics or legacy combat. The physics branch can merge master with ordinary conflict resolution. To
disable rollout while retaining the data and supervisor, stop invoking the post-Maple-Island journey; durable bundle
files remain harmless and reusable.

The local `server/server-physics-engine` worktree was used as the compatibility reference. Its only overlap with this
increment is the two primitive gateway files: physics adds configured spawn-id perception, while this increment adds
headless NPC/reward-choice operations in separate method regions. Preserve both additions when that branch pulls
`master`; the progression implementation has no direct dependency on physics-branch classes.
