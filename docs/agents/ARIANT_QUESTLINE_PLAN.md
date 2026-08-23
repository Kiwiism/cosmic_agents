# Ariant questline implementation plan

## Scope and critical distinction

Ariant quests `3900`-`3955` are a collection of level-gated arcs, not one mandatory chain. The selector should offer eligible arcs independently, track their prerequisites, and let the Director interleave them with Hunting, supplies, or other quests. For the current level-30 milestone, only the arcs whose effective difficulty is at or below 30 are admitted; later arcs remain catalogued but deferred.

## Arc catalog

| Arc | Quests | Minimum level | Main work |
|---|---|---:|---|
| Ariant culture | `3900` | 15 | Talk to Byron `2101005`, interact with oasis `2103000`, return after quest progress becomes `5` |
| Palace access | `3901`, `3949`-`3950` | 10 / 48 | Tigun and later palace-pass dialogue; the latter is out of the level-30 rollout |
| Queen's tea | `3903`-`3905` | 23 | Delivery, kill 30 Jr. Cactus `5200000`, return Tea Box `4031577` |
| Jiyur and sister | `3906`-`3908` | 28 | Two-way delivery and 20 item `4000331` |
| Dancer and sword | `3909`-`3912` | 24 / 32 | 20 item `4000328`, then crafting/delivery; stop after `3909` below level 32 |
| Schegerazade | `3913`-`3916` | 36 | Cross-region Ludibrium library deliveries; deferred |
| Little Prince | `3917`-`3920` | 32 | Five roses, 20 item `4000333`, Ludibrium library deliveries; deferred |
| Red Scorpions | `3921`-`3926` | 27 | Collect `4000329` x30 and `4000330` x50, receive ring, password entry, steal and distribute four treasures |
| Sand Bandit admission | `3927`-`3937` | 29 | Three parallel sponsor branches: Sejan deliveries, Ardin hunt/duel, Eleska palace theft; join after all three |
| First Sand Bandit mission | `3938`-`3941` | 29 | Obtain Tigun hair, create/use morph, collect queen's silk order |
| Byron recommendations | `3942`-`3945`, `3947` | 30 | Job-specific long-distance delivery to the Explorer leader |
| Deo bounty | `3952`-`3954` | 38 | Sand Bandit prerequisite, crafting cost, boss `3220001`; deferred |
| Other high-level work | `3902`, `3948`, `3951`, `3955` | 40-53 | Keep catalogued but exclude from level-30 selector |

## Scripted mechanics that require typed support

1. `3900` completes only after oasis NPC `2103000` writes quest info progress `5`; simple start/end NPC routing is insufficient.
2. `3925` reveals the password. The hidden portal script `ThiefPassword.js` requires exact text `Open Sesame` before warping to Red Scorpion's Lair `260010402`.
3. `3926` grants four treasures `4031579` and requires depositing them at the four correct house NPCs `2103009`-`2103012`, producing progress `3333`. This is a finite interaction set, not an item delivery to one NPC.
4. `3929` similarly distributes four food items `4031580` through house NPCs `2103003`-`2103006` and requires progress `3333`.
5. `3933` uses start script `q3933s` and a scripted duel against Ardin's Other Self `9100013`.
6. `3935` enters the palace through hidden portal `skyrom`, obtains `4031574`, and returns it to Eleska. It requires hidden-portal and inventory postcondition handling.
7. `3938`/`3939` interleave two active quests: Tigun exchanges one Lidium ore `4010007` for hair `4031570`, then the parent mission consumes it.
8. `3940` produces the disguise; `3941` requires the Tigun morph while talking to merchant `2101013` in Tent of the Entertainers `260010600` and uses custom start/end scripts.

These mechanics should be declared as quest-step descriptors: `set-info-progress`, `password-portal`, `interaction-set`, `scripted-duel`, `hidden-portal-item`, and `morph-dialogue`. The descriptors call existing primitive capabilities and verify server state; they do not copy JavaScript quest logic into Agent code.

## Selector and effective-level policy

- Eligibility uses WZ minimum level, prerequisite quests, job restrictions, inventory/resource readiness, travel reachability, and combat hit-rate/survivability estimates.
- The recommended level is the maximum of the WZ minimum and the predicted level needed to maintain the configured hit rate and potion budget against the objective mobs.
- At level 25-30, prefer complete local arcs before expensive intercontinental delivery. Treat `3942`-`3947` as deliberate travel objectives, not random fillers.
- If an arc is partially complete, its resumable frontier receives priority over starting a new arc unless safety/resource policy says otherwise.
- Failure of a scripted mechanic suspends with the exact missing descriptor/capability instead of cycling maps or generic combat recovery.

## Resume and completion model

Each arc stores no private step counter. Reconciliation reads completed and active quests, WZ info progress, required items, morph state, current map, and live scripted-instance state. On restart it resumes the earliest unmet postcondition. Multi-quest interleaving such as `3938`/`3939` is represented as a dependency graph, allowing the child quest to complete before the parent resumes.

For the level-30 rollout, success means every selected eligible arc reaches its live terminal quest state; high-level deferred arcs are neither failures nor stalls.

## Implementation order

1. Import the arc/dependency catalog and effective-level metadata.
2. Add inspect-only eligibility and frontier diagnostics.
3. Enable ordinary NPC delivery, kill, and item-collection arcs.
4. Add quest-info progress interaction for `3900`.
5. Add password portal and finite house interaction sets for `3925`-`3929`.
6. Add scripted duel and hidden palace portal handling.
7. Add morph lifecycle and scripted merchant dialogue for `3938`-`3941`.
8. Add job-specific Byron deliveries, then later-level arcs.
9. Test every descriptor, every resume boundary, wrong password/missing morph/full inventory, server restart, and parallel agents using the same public interaction points.

