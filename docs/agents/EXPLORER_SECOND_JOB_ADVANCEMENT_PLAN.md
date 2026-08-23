# Explorer second-job advancement implementation plan

## Current implementation status (verified 2026-08-23)

The typed `victoria-second-job` plan is registered and the World Director's
level-30 route validates end to end. All twelve Explorer branches use one
live-state-reconciled executor. It routes from the Agent's current location,
uses the real leader/instructor/examiner scripts, reserves shared trials,
reuses generic combat and quest loot, constrains only Pirate trial skills,
verifies the final job, and installs a branch-specific level-30 SP handoff
profile. The default handoff is now the branch's
`mapleroyals-optimal-2026-*` profile, while the previous `*-v1` profiles remain
available as legacy alternatives for saved tests and explicit comparisons.

The Pirate examiner script now completes quest `2191` or `2192` before removing
the crystals; previously it removed the completion items without completing
the quest, preventing Kyrin from recognizing either successful trial. The
normal level 30-70 SP progression is authored for every branch. The
advancement transaction still owns only the one newly granted second-job SP;
the independent SP-profile service owns later allocations and switches to the
matching third- and fourth-job profile only after a real job change.

All twelve branch destinations and the canonical flows below were checked
against the current NPC scripts. Two script-level details need explicit
regression coverage rather than assumptions:

- the standard four trial maps are shared hidden maps in the current scripts,
  while the Pirate script rejects entry when its selected room already has a
  player; Agent trial admission therefore needs a concurrency contract;
- several legacy leader scripts contain copied letter-ID checks in dialogue
  branches (for example Athena checks `4031011` in one preamble although her
  own letter is `4031010`). The actual grant/consume IDs are correct, but the
  Agent flow must be tested through the real scripts before rollout.

## Goal and ownership

Second-job advancement is a resumable Questing activity selected by the World Director when an Explorer is level 30, still has a first-job ID, and has not completed its branch commitment. Questing owns the sequence. Navigation, combat, looting, NPC interaction, inventory, and supply services remain reusable capabilities; the advancement plan must not implement parallel versions of them.

The branch choice is an explicit input recorded before the irreversible final NPC interaction. A policy or future LLM may propose a branch, but only the Director commits it. Restart reconciliation derives the current phase from live job, quest, and item state rather than replaying completed dialogue.

## Canonical flows

### Warrior, Magician, Bowman, and Thief

| Family | Leader and map | Letter | Field instructor and map | Trial map | Trial quests | Branches |
|---|---|---:|---|---:|---|---|
| Warrior | Dances with Balrog `1022000`, Perion `102000003` | `4031008` | `1072000`, West Rocky Mountain IV `102020300` | `108000300` | `100003` -> `100004` -> `100005` | Fighter `110`, Page `120`, Spearman `130` |
| Magician | Grendel `1032001`, Magic Library `101000003` | `4031009` | `1072001`, Forest North of Ellinia `101020000` | `108000200` | `100006` -> `100007` -> `100008` | F/P Wizard `210`, I/L Wizard `220`, Cleric `230` |
| Bowman | Athena Pierce `1012100`, Bowman Instructional School `100000201` | `4031010` | `1072002`, Road to the Dungeon `106010000` | `108000100` | `100000` -> `100001` -> `100002` | Hunter `310`, Crossbowman `320` |
| Thief | Dark Lord `1052001`, Thieves' Hideout `103000003` | `4031011` | `1072003`, Construction Site North of Kerning `102040000` | `108000400` | `100009` -> `100010` -> `100011` | Assassin `410`, Bandit `420` |

Sequence:

1. Reconcile readiness: level 30, correct first-job ID, alive, free ETC slot, adequate HP/MP supplies, valid weapon and ammunition where applicable.
2. Navigate to the family leader and interact to start the first trial quest and receive the family letter.
3. Navigate normally to the field instructor. Preserve the letter and foreground activity through map transitions and restarts.
4. Interact with the instructor. The server consumes the letter, advances the trial quest, and warps the character into the private trial.
5. Use generic combat and quest-aware loot to collect 30 Dark Marbles `4031013`. Trial drops outrank ordinary loot and the exit NPC.
6. Interact with the in-trial examiner (`1072004` through `1072007`). It consumes all marbles, advances the family quest, grants Proof of a Hero `4031012`, and returns the character to the instructor map.
7. Navigate to the family leader, present the proof, and commit the preselected branch.
8. Verify the new job ID, proof removal, quest completion, SP/stat state, and inventory expansion before publishing success.

Dark Marbles have database chance `700000`, i.e. 70% per eligible kill before
channel/character/card drop multipliers. The expected unmodified requirement is
about 42.9 kills for 30 marbles.

### Pirate

Pirate advancement is not a 30-marble variant. Kyrin `1090000` in Navigation Room `120000101` starts one mutually exclusive quest and warps directly to a trial:

| Branch | Quest | Trial | Required attack | Required item | Count | Result job |
|---|---:|---:|---|---:|---:|---:|
| Brawler | `2191` | `108000502` | Flash Fist | Potent Power Crystal `4031856` | 15 | `510` |
| Gunslinger | `2192` | `108000501` | Double Shot | Potent Wind Crystal `4031857` | 15 | `520` |

The plan must temporarily constrain the combat loadout to the required skill and verify MP/ammunition readiness before entry. NPC `1072008` consumes the crystals and returns the agent to Kyrin. Kyrin commits the selected job after the corresponding quest is complete.

Both Pirate crystals also use database chance `700000` (70% base), for about
21.4 expected kills to obtain 15 before drop-rate multipliers.

## Resume model

The checkpoint key is `(family, branch, live job, trial quest states, letter count, proof/crystal/marble counts, map)`. The reconciler chooses the furthest safe phase whose postconditions hold:

- already second job: complete without replay;
- proof owned: return to leader;
- inside trial with incomplete item debt: resume combat and looting;
- trial quest started outside trial: return to instructor and re-enter;
- letter owned: travel to field instructor;
- leader quest started but letter missing: reacquire through the leader dialogue;
- no advancement state: start at leader.

Suspension exits combat, moves to a reachable safe point when possible, and retains the exact phase. Death or disconnect must not grant trial items or skip scripted transitions.

## Required implementation sequence

1. Add a typed `second-job-advancement` plan request with family and branch.
2. Add a live-state reconciler and phase journal; do not infer phase from an in-memory step counter.
3. Reuse the NPC interaction capability for leader/instructor/examiner dialogue and scripted warps.
4. Add a private-trial visit contract that admits only the owning agent and survives reconnect reconciliation.
5. Add trial-specific objective descriptors: ordinary Dark Marble collection and Pirate skill-restricted crystal collection.
6. Add irreversible branch confirmation and post-change verification.
7. Add resource/readiness remediation before trial entry and bounded failure reasons inside trials.
8. Test every branch from clean start, each resume boundary, death, full ETC inventory, missing weapon/ammo, and server restart.

## Acceptance criteria

- All twelve second-job branches complete through real scripts with no forced job change or granted trial item.
- Every phase is resumable and idempotent.
- The plan emits decision-level journal entries but does not log individual attacks.
- Failures name the blocking contract: navigation, NPC reachability, inventory capacity, resource readiness, trial combat, scripted transition, or final job verification.
