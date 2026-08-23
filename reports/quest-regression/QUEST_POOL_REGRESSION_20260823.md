# Victoria random quest-pool regression — 2026-08-23

## Scope

The regression exercised 23 distinct quest IDs with level 15, 20, and 25 Agents, plus
additional pre-existing Agents positioned across Victoria Island to reduce travel bias.
Each attempt was submitted through the World Director's individual-quest action and was
given a bounded live budget. The harness recorded quest admission, map transitions,
activity state, terminal outcome, and timeout phase; it did not log individual attacks.

This is a breadth regression of the generic quest runtime, not evidence that every quest
in the WZ data is autonomous. The current generic executor supports ordinary NPC
interaction, monster kills, monster-drop collection, navigation, quest-aware looting,
and returning to the completion NPC. Scripted reactors, passwords, instances, morphs,
item-use steps, scripted duels, and non-shop scripted item acquisition still require
typed capabilities. Cataloged Victoria NPC-shop items now have a typed procurement path.

## Validated results

| Quest | Objective shape | Result | Evidence / reason |
|---|---|---|---|
| 2090 — I'm Bored 1 | interaction-only | **COMPLETED** | Completed locally in 56.3 s. A separate remote attempt remained in legitimate travel for eight minutes. |
| 28279 — Red Ribbons Around the Pig's Neck | collection/reconciliation | **COMPLETED** | Completed in 64.3 s after authoritative inventory reconciliation. |
| 2010 — Jane and the Wild Boar | collection | TIMEOUT_ACTIVE | Accepted and crossed four maps; objective/return remained active at 496.6 s. The same-map watchdog no longer falsely suspended it. |
| 2106 — DANGER! 2-H. Mushroom | collection | TIMEOUT_TRAVEL | Still traveling to the start NPC after four map transitions at 496.6 s. |
| 2017 — Arwen and the Glass Shoe | collection | TIMEOUT_TRAVEL | Still traveling to the start NPC at 496.7 s. |
| 2122 — Manji and the Secret Group | collection | TIMEOUT_TRAVEL | Still traveling to the start NPC at 498.7 s. |
| 28274 — Why Are Dark Stumps So Dark? | kill | TIMEOUT_TRAVEL | Still traveling to the start NPC at 496.7 s; the formerly blocked melee route was separately validated after the route-blocker fix. |
| 28267 — I Need an Umbrella! | multi-collection | TIMEOUT_ACTIVE | Accepted; no drop progress in-budget, then used bounded exhaustion fallback rather than false failure. |
| 28268 — The Pigs Are Ruining the Produce! | kill | TIMEOUT_ACTIVE | Accepted and reached 3/30 kills before a no-progress map fallback; still active at 496.5 s. |
| 2179 — Protect The Nautilus' Emergency Food Supply | kill | TIMEOUT_ACTIVE | Accepted and selected a valid pig map; objective remained active at 496.2 s. |
| 28271 — That Red Isn't For Everyone! | kill | TIMEOUT_ACTIVE | Accepted and used ribbon-pig fallback maps; objective remained active at 496.0 s. |
| 2207 — Lazy Little Calico | collection | TIMEOUT_ACTIVE | Accepted, crossed Nautilus and three Victoria field maps, and remained resumable at 496.2 s. |
| 2204 — Strange Dish 1 | collection | TIMEOUT_ACTIVE | Accepted, crossed eight maps, and remained resumable at 496.2 s. |
| 28278 — Destructively Strong Pigs | kill | TIMEOUT_ACTIVE | Accepted, crossed five maps, reached the selected pig field, and remained active at 496.2 s. |
| 2165 — The Prince's Request | item acquisition | HISTORICAL CAPABILITY GAP | The run exposed a high-level monster route for one shop-bought Mana Elixir. The follow-up implementation now routes to the nearest reachable cataloged NPC vendor, buys the exact shortfall, verifies inventory, and resumes the quest. |
| 28281 — Preparations for the Traditional Ceremony | collection | NOT_AVAILABLE | Correctly absent for the selected Agent's live quest history/context. |

## Additional breadth probes

Seven more distinct IDs were exercised while diagnosing earlier infrastructure and
catalog defects: 2065, 2108, 28257, 28258, 28260, 28262, and 28280. The four seal-chain
quests were advertised even though their authoritative start/script requirements were
not satisfied. Quests 28257-28261 each require possession of Devil Hunter's Necklace
`4032496`, which is awarded only by the final class-story boss quests 28179, 28198,
28219, 28238, or 28256. Quest 28262 additionally requires all five seal quests to be
complete. The initial conservative catalog also omitted the Bowman seal quest 28259 and
Magician seal quest 28261; both are now present, so the final prerequisite set is no
longer structurally impossible. The generated facts and runtime catalog preserve those
item producers and all prerequisite states; the Director and live scheduler use the
same typed evidence, and explicit rejected requests identify either the missing necklace
and producer quest IDs or the incomplete seal quest. The
producer story chains themselves are outside the current conservative individual-quest
runtime, so these seal quests remain intentionally unavailable rather than being counted
as failures. Runs of 2065, 2108, and 28280 predated the final navigation/watchdog and
publication-grace fixes, so they are retained as diagnostics rather than counted as
current pass/fail verdicts.

## Defects corrected during the regression

1. Same-map navigation progress now includes region changes and meaningful position
   movement; a long field traversal no longer looks like 180 seconds of inactivity.
2. Melee route blockers are acquired using mechanically actionable melee/jump reach,
   not ranged projectile distance.
3. Explicit individual-quest requests reset stale attempt evidence and preempt a prior
   scheduler cursor without abandoning authoritative quest progress.
4. Director quest options are filtered against live start requirements, while active
   quests remain resumable.
5. Shop-sourced collection objectives use typed NPC procurement instead of incidental
   high-level monster routes. This re-enables quests 2165 (Mana Elixir), 2209 (Lemon),
   and five other cataloged shop-item quests through the existing shop capability.
6. Terminal diagnostics preserve the advisor/scheduler reason, and timeout results are
   separated into travel, active, unavailable, and unresolved classes.
7. All five class seal quests are represented in the conservative catalog, and the live
   runtime carries the five prerequisite quest states required by `Revealed Identity`.

## Assessment

The generic runtime is now safe enough to avoid the two worst false behaviors observed:
declaring legitimate navigation stuck, and routing low-level Agents to inappropriate
high-level mobs for shop items. It is **not yet a generic “complete any quest” engine**.
Only 2 of the 16 current, directly classifiable live probes completed within the short
budget; 12 were still making legitimate travel/objective progress, 1 exposed the shop
procurement gap corrected immediately afterward, and 2 were unavailable for the selected
Agent state.

The dominant remaining problem is throughput: eight minutes is frequently consumed by
cross-island travel, and accepted collection/kill objectives often do not finish before
the fixed wall-clock budget. The next test should use phase budgets (start travel,
objective work, return travel) and start some Agents near the quest NPC so quest logic can
be measured separately from full-island navigation. A follow-up live run should validate
the new shop-item path with both sufficient and insufficient mesos.

## Forward plans

- [Explorer second job advancement](../../docs/agents/EXPLORER_SECOND_JOB_ADVANCEMENT_PLAN.md)
- [Mushroom Kingdom questline](../../docs/agents/MUSHROOM_KINGDOM_QUESTLINE_PLAN.md)
- [Ariant questline](../../docs/agents/ARIANT_QUESTLINE_PLAN.md)

These are implementation plans, not live runtime claims. Second job advancement covers
all twelve Explorer branch choices. Mushroom Kingdom and Ariant explicitly identify the
typed scripted capabilities that must be added rather than forcing them through the
ordinary kill/collect executor.
