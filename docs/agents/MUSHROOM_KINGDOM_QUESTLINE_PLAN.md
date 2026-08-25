# Mushroom Kingdom questline runtime and test cohort

## Scope

Mushroom Kingdom is implemented as the executable `mushroom-kingdom-questline` universal plan. Questing owns ordering and reconciliation; existing Navigation, Hunting, looting, NPC interaction, survival, and inventory capabilities execute the work. The dedicated harness is fixture/provisioning code only and is not a competing gameplay runtime.

The production scope is Explorer second jobs at levels 30-38, beginning with the real job-instructor opener (`2300`-`2304`) and ending when `2336` completes. Optional extermination quest `2337` is outside the completion contract; `2338` and `2342` are recovery-only. Successful `victoria-second-job` execution automatically hands off to this plan.

Launch the complete 12-branch cohort with `!mushroomtest start [seed]`. Inspect it with `!mushroomtest status` and cleanly disconnect it with `!mushroomtest stop`.

For the shortened observation run, use `!mushroomtest start ten-percent [seed]`. All 12 level-30 second jobs begin at the Mushroom Kingdom entrance with `2312` active. Each Agent must personally obtain `ceil(authored requirement / 10)` items before the harness supplies only the missing turn-in items. One-item objectives, the three colored Yetis, and the Prime Minister remain real one-kill/one-item actions. At the top-up boundary the harness records only the EXP and mesos earned since that collection quest began, then grants nine times those gains after the ordinary quest submission succeeds; fixed quest rewards are not multiplied.

An authorized GM controlling a character can use `!mushroomtest fill`. It scans the ordered active Mushroom Kingdom frontier and supplies exactly the missing authored items or kill progress. Investigation flags can be filled, but dialogue, item-use, and portal-script stages still have to be performed normally; the command reports that next action rather than force-completing the quest.

The job-specific entry quests are `2300`-`2310`. For the five Explorer families the relevant starts are `2300` Warrior, `2301` Magician, `2302` Thief, `2303` Bowman, and `2304` Pirate. Their scripts grant recommendation letter `4032375`, optionally warp to Mushroom Forest Field `106020000`, and hand off to Head Security Officer `1300005`, who starts `2312`.

## Quest graph

| Stage | Quests | Work type and live postcondition |
|---|---|---|
| Entry | `2300`-`2310` | Scripted acceptance, recommendation letter, travel/warp to `106020000`, submit to `1300005` |
| Qualification | `2312` | Collect 50 Mutated Spores `4000499` |
| Briefing | `2313` | NPC handoff `1300005` -> `1300003` |
| Forest reconnaissance | `2314`-`2316` | Scripted map/portal visits; reconcile quest flags rather than inventing kill debt |
| Spore campaign | `2317`-`2319` | Collect 100 Poison Mushroom Caps `4000500` on the pre-barrier maps, produce the barrier-opening Killer Mushroom Spore from 50 Mutated Spores `4000499`, then obtain Antidote `4032389` |
| Optional Victoria delivery | `2320` | Deliver Antidote `4032389` to Bruce `1012111` in Henesys, route back to Henesys Pet Park `100000002`, and use its `TD_MC_first` portal to return without losing mainline context |
| Castle approach | `2321`-`2324` | Scripted reconnaissance plus 100 Intoxicated Pig Tails `4000501`; use the real Thorn Remover script and preserve portal state |
| James branch | `2325`-`2327` | Find James `1300008` at Central Castle Tower `106021201`, obtain required item `4001317`, complete scripted rescue |
| Provisions branch | `2328`-`2329` | Collect 200 Destroyed Helmets `4000502`, then 200 Broken Spears `4000503` |
| Wedding assault | `2330` | Enter a solo `KingPepeAndYetis` instance, receive one uniformly random `3300005`, `3300006`, or `3300007`, and re-enter through duplicate rolls until all three are credited |
| Royal seal | `2331`, `2342` | Recover seal `4001318`; `2342` is the explicit lost-seal recovery path |
| Princess and betrayal | `2332`-`2336` | Item-triggered start via `4032388`, scripted NPC sequence, defeat Prime Minister `3300008`, recover required truth items `4032387` and `4032386` |
| Epilogue/repeatable | `2337`, `2338` | Large optional extermination objective; repeatable spores kept outside mainline completion |

## Capability contracts

- **Scripted visit objective:** A quest with no WZ kill/item completion requirement may still be completed by a portal or quest script. Represent the exact visit trigger and verify the live quest flag after interaction.
- **Field collection:** Use pack-wide quest debt, density-aware map selection, quest-item-preserving loot, and bounded map fallback. Authored maps are preferences, not unconditional locks.
- **Instance objective:** `KingPepeAndYetis` and the Prime Minister event are solo owned instance visits. Entry, boss progress, death, timeout, and re-entry reconcile from live quest/map state without fabricating boss credit.
- **Item-triggered quest:** `2332` starts when `4032388` is present. Reconciliation checks the item and quest state before trying an NPC that does not own the start.
- **Recovery quest:** If the royal seal was not picked up, re-enter through the real Prime Minister portal recovery path. `2342` remains catalogued as a server-side recovery quest and is never fabricated by the plan runtime.
- **Branching:** James, provisions, Bruce, and repeatable work are selectable subgraphs. Mainline eligibility is computed from live prerequisites so agents can resume when some branches were done earlier.

## Safety and recovery

- Reserve ETC slots before entry, the three 100/200-item collection stages, and boss loot.
- Suspend for supply recovery outside an active private instance; inside an instance use bounded chair/potion/death policy and return a typed failure if completion becomes unsafe.
- On navigation failure, preserve the current quest node, suppress the failing edge temporarily, and replan. Do not force-warp except through the quest's own script.
- If a stale grounded state leaves an Agent physically below a Mushroom Kingdom map, recover it to the grounded entry portal, refresh navigation, and then use the authored route. Quest `2323` explicitly exits `106020401` through portal `4` to `106020400`.
- On low drop progress, damage to a relevant mob counts as progress; map changes occur only after spawn evidence and the bounded local lease are exhausted.
- On restart, derive the frontier from completed/active quests and required items. Never restart entry quests whose job-specific sibling is complete.

## Rollout and tests

1. Catalog the quest graph, job-specific aliases, NPC/map/item/mob references, and special script triggers.
2. Implement graph reconciliation and an inspect-only Director action.
3. Enable entry, qualification, and ordinary collection stages.
4. Add scripted visit triggers and the Bruce out-and-back branch.
5. Add James and provisions branches.
6. Add the King Pepe/Yeti instance, then Prime Minister and seal recovery.
7. Add optional epilogue/repeatable work.
8. Test five Explorer families, all resume boundaries, full inventory, death, disconnect, lost seal, already-completed branches, and concurrent agents occupying boss instances.

Completion requires the non-repeatable graph, including Bruce and James/provisions branches, ending at `2336`. Repeatable extermination `2337` and replacement-item recovery `2338` remain outside completion.

The disposable live runner also exposes `q2323-return`, `q2323-out-of-bounds`, and `q2325-entry` diagnostic snapshots. These are focused development checkpoints; release acceptance still requires a clean run from the five real job-family entry quests through `2336`.

## Cohort fixture

The harness creates one named Agent for each of Fighter, Page, Spearman, Fire/Poison Wizard, Ice/Lightning Wizard, Cleric, Hunter, Crossbowman, Assassin, Bandit, Brawler, and Gunslinger. It alternates the approved appearance catalog for an exact 6/6 gender split, resets each character to level 30, applies only a legal AP profile capable of equipping its selected level-25 equipment, and selects the branch's authored second-job SP profile.

Weapons receive five guaranteed applications of their actual 60% weapon-scroll stats. Shoes are recorded as `+5` with 10 Speed, and Old Raggedy Cape `1102053` is `+5` with 10 points in the branch primary stat. The fixture provides Power Elixirs, All Cures, Sniper Pills, and 30,000 compatible projectiles while leaving normal combat and loot behavior unchanged.

All twelve branches now use their complete level 30-70
`mapleroyals-optimal-2026-*` build. The earlier starter-only and experimental
Assassin/Spearman profiles remain selectable by ID, but are no longer fixture
defaults.
