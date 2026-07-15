# Goal Prompt: Generic Autonomous Quest Capability

Use the following prompt as the implementation goal for the next quest-capability workstream.

```text
Implement a generic, production-safe autonomous quest capability for the reconstructed Agent runtime. Use NuTNNuT only as a behavioral reference. Do not port `server.bots` runtime code, static bot state, BotEntry dependencies, or donor-specific shortcuts into `server.agents`.

Primary donor references:

- `remotes/source/master:src/main/java/server/bots/BotQuestIndex.java`
- `remotes/source/master:src/main/java/server/bots/BotQuestScorer.java`
- the quest-selection portions of `remotes/source/master:src/main/java/server/bots/BotQuestManager.java`

Current Agent references:

- `server.agents.capabilities.primitive.AgentQuestStartPrimitiveCapability`
- `server.agents.capabilities.primitive.AgentQuestCompletePrimitiveCapability`
- `server.agents.capabilities.objective.NpcQuestObjectiveCapability`
- `server.agents.integration.PrimitiveCapabilityGateway`
- `server.agents.integration.cosmic.CosmicPrimitiveCapabilityGateway`
- `docs/agents/quest-objective-policy/QUEST_OBJECTIVE_POLICY_DESIGN_SPECIFICATION.md`
- `docs/agents/quest-objective-policy/QUEST_OBJECTIVE_POLICY_TECHNICAL_SPECIFICATION.md`
- existing profile, catalog, plan-runtime, capability-runtime, retry, and audit contracts

Outcome

An Agent can discover legally runnable quests from generic catalog data, decide whether a quest fits its active profile and plan, commit to one quest long enough to make progress, start and complete it through normal Cosmic rules, choose valid rewards, recover from bounded failures, suppress known-broken candidates, and optionally accept nearby worthwhile quests only when profile/config policy allows it.

Non-negotiable boundaries

1. Cosmic is authoritative for quest legality and mutation. Always use normal `Quest.canStart`, `Quest.start`, `Quest.canComplete`, and completion/reward paths through the existing gateway. Catalog classification and scoring are advisory, never authority.
2. Do not use force-start, force-complete, showcase reset, direct quest-status mutation, synthetic item grants, teleport cheats, or test fixtures in generic autonomous runtime.
3. Do not create a player shortcut. New server-side entry points must verify Agent identity or remain behind the existing Agent integration gateway.
4. Agents obey the same inventory capacity, one-of-a-kind, untradeable, item expiration, level, job, prerequisite, party, script, map, NPC, and reward rules as players.
5. Keep WZ and script parsing in catalog/build layers. Policy consumes immutable bounded snapshots and must not rescan WZ, scripts, maps, or global runtime collections per tick.
6. Preserve package ownership. Portable models and policies must not import Cosmic `Character`, `MapleMap`, packet handlers, `server.bots`, or donor runtime classes. Cosmic access belongs in integration adapters.
7. A policy returns decisions and reason codes. It does not execute combat, movement, NPC interaction, inventory mutation, or quest mutation itself.
8. All loops, retries, recovery attempts, candidate counts, travel budgets, and cache builds must be bounded and observable.
9. Default behavior must be conservative. Opportunistic acceptance is off unless an Agent profile and server configuration both permit it.
10. Do not modify WZ assets as part of this goal. Classify unsupported or broken quests and suppress them instead.

Recommended implementation order

Phase 1: Build the generic quest catalog and classifier from BotQuestIndex

- Reconstruct the useful parsing ideas from `BotQuestIndex` into Agent-owned catalog packages and schemas.
- Model quest ID, start/end NPCs, start/end maps when resolvable, level/job/prerequisite gates, scripted markers, auto-start/auto-complete, complete-requirement keys, required mobs and counts, required items and counts, reward EXP, reward mesos, fixed reward items, selectable reward groups, and inventory types affected by rewards.
- Classify at minimum: `MOB`, `TALK`, `FETCH`, `MOB_AND_FETCH`, `AUTO`, `SCRIPTED`, and `UNSUPPORTED`.
- Record explicit exclusion reason codes rather than silently dropping candidates.
- Preserve the useful reverse index from item ID to quests requiring that item, but give it a generic catalog contract.
- Use versioned cache output with deterministic ordering, atomic replacement, schema/version invalidation, and a cold-build/warm-load test seam.
- Treat script presence and WZ start/end script flags as unsupported by default. Add an allowlist only if an existing reconstructed capability proves a specific script safe.
- Unit-test classification with synthetic metadata. Add an integration/catalog test over real project data that reports counts by classification and exclusion reason without asserting fragile total counts.

Phase 2: Generalize and harden quest start/completion primitives

- Remove Amherst-specific scope from the generic primitive contract. Preserve Amherst behavior through a plan/profile scope policy adapter, not a boolean embedded in the generic command.
- Introduce typed preflight and terminal reason codes for already-started, already-completed, wrong NPC, missing prerequisite, wrong level/job, inventory blocked, reward choice unresolved, transient rejection, postcondition mismatch, and unsupported quest shape.
- Make start and completion idempotent. A retry after a successful mutation must verify live state and return success without repeating rewards or side effects.
- Capture pre-state, invoke the normal gateway once, then verify post-state on later ticks within a bounded deadline.
- Ensure completion cannot be attempted until live `canComplete` passes and required NPC/map interaction has been satisfied through normal capabilities.
- Keep auto-start/auto-complete as explicit catalog shapes with their own normal Cosmic path. Do not emulate them with force operations.
- Add focused tests for success, already-done idempotence, rejected preflight, transient retry, timeout, and postcondition mismatch.

Phase 3: Add reward-choice and inventory-space validation

- Build or consume the generated quest reward-choice catalog before completion.
- Determine every possible reward inventory type and required free space, including equipment quantity rules, stack capacity, one-of-a-kind policy, and simultaneous rewards.
- Resolve selectable rewards through a profile-aware reward policy. At minimum support equip usability/upgrades, class compatibility, consumable utility, inventory pressure, item restrictions, and deterministic fallback ordering.
- If a required choice cannot be resolved, return `REWARD_CHOICE_UNRESOLVED`; do not guess through a raw index.
- Revalidate the chosen option and inventory immediately before completion because live inventory may have changed.
- Verify completion status and actual reward outcome after the mutation. Never retry a completion mutation merely because a preferred reward was not observed if the quest is already complete.
- Add tests for each inventory type, stack merge, full inventory, one-of-a-kind collision, invalid choice, deterministic tie-break, and completion postcondition.

Phase 4: Implement quest commitment in Quest Objective Policy

- Implement the existing Quest Objective Policy specification against stable plan/capability contracts.
- Add persistent bounded focus state containing active quest, objective, start time, last progress time, retry counters, travel state, and last reason codes.
- Once a quest is selected, keep it committed until completion, an explicit exit criterion, a hard blocker, a profile/plan cancellation, or a bounded no-progress timeout occurs.
- Do not re-score every quest every tick. Re-evaluate only at defined events such as objective completion, map arrival, inventory change relevant to the quest, material profile change, hard blocker, or retry deadline.
- Prefer active quest requirements over generic grinding and unrelated loot, while allowing bounded recovery and configured filler actions.
- Emit deterministic decisions and audit events. Execution stays in movement, combat, loot, reactor, NPC, inventory, and quest capabilities.
- Test commitment stability, progress refresh, plan cancellation, timeout, recovery handoff, and no-thrashing behavior.

Phase 5: Port NuTNNuT's quest scorer as a profile-aware policy

- Reconstruct the useful value/cost model from `BotQuestScorer`; do not copy its static thresholds as universal truth.
- Use catalog and bounded runtime snapshots for reward value, mob EXP, objective overlap, already-needed kills/items, travel hops/time, off-route cost, estimated kill time, inventory pressure, danger, prerequisite chain value, and current grind baseline.
- Move weights and thresholds into typed profile/config policy with safe defaults. Support at least progression-focused, completionist, economy-aware, social/party, and conservative profiles where existing profile contracts permit them.
- Keep scoring pure and deterministic for the same input. Return component breakdown and reason codes for auditability.
- Filter illegal, unsupported, suppressed, unresolved-reward, and out-of-budget quests before scoring.
- Bound the candidate set using level range, region/travel budget, catalog shape, prerequisites, and profile policy.
- Test score monotonicity and tradeoffs, not just exact donor constants. Include overlap, travel, grind baseline, danger, reward utility, and profile-dependent ranking cases.

Phase 6: Add bounded recovery and broken-quest suppression

- Define failure categories: transient interaction failure, navigation failure, missing spawn/drop source, inventory blockage, unresolved reward, script/shape mismatch, repeated postcondition mismatch, and permanent catalog/data defect.
- Recovery may reposition, re-resolve an NPC/map, wait for spawn, free allowed inventory space, retry interaction, or postpone the quest, but every strategy has a maximum attempt count and elapsed-time budget.
- Maintain a suppression registry keyed by quest ID plus catalog version and relevant failure reason. Support session suppression and configurable persisted suppression where appropriate.
- Use cooldown/backoff for transient failures. Permanent failures remain suppressed until catalog version, configuration, or manual administration changes.
- Never suppress a quest merely because the Agent was interrupted by a plan/profile change.
- Expose diagnostics for selected quest, commitment state, last progress, failure counters, suppression reason, and next eligible retry time.
- Test retry budgets, backoff, suppression invalidation, persistence policy, and continued selection of healthy quests when one candidate is broken.

Phase 7: Enable opportunistic quest acceptance only through profile/config policy

- Add a server-level master switch defaulting off and a profile capability/permission defaulting off.
- Require both gates. Plan-defined quests remain available independently of opportunistic mode.
- Apply strict candidate, travel-hop, time, danger, inventory-pressure, active-quest-count, and score thresholds.
- Do not interrupt a committed higher-priority plan objective unless the plan explicitly permits sidetracks.
- Avoid repeated offers and acceptances using per-Agent history, cooldowns, completed/start-state checks, and suppression data.
- Prefer quests that overlap the Agent's current map, mobs, items, route, or active plan.
- Log the score breakdown and policy gates for every autonomous acceptance.
- Add tests proving default-off behavior, two-gate enforcement, commitment protection, overlap preference, cooldown, bounded acceptance rate, and profile-specific decisions.

Cross-cutting deliverables

- Agent-owned catalog models, cache builder/loader, classifier, and reason codes.
- Generalized quest start and completion primitives plus Cosmic adapter changes.
- Reward choice and inventory preflight policy.
- Quest commitment state and Quest Objective Policy implementation.
- Profile-aware scorer with auditable component breakdown.
- Recovery/suppression services and diagnostics.
- Config/profile schema additions with conservative defaults and migration notes.
- Focused unit, contract, integration, and architecture-boundary tests.
- Documentation updates to package registry, reconstruction map/rules, catalog schema, profile schema, capability docs, and operational diagnostics.

Definition of done

1. A generic test Agent can discover, select, start, progress, and complete representative non-scripted MOB, TALK, FETCH, MOB_AND_FETCH, and AUTO quests outside the hardcoded Amherst plan.
2. Every quest mutation passes through normal Cosmic legality checks and has an idempotent verified postcondition.
3. Reward choices and inventory capacity are resolved before completion, with typed blockers when unresolved.
4. A committed quest does not thrash between candidates during normal progress.
5. Broken quests recover within fixed budgets and become suppressible without blocking healthy candidates.
6. Opportunistic acceptance is disabled by default and requires both server and profile permission.
7. Agents receive no gameplay-rule bypass unavailable to players, apart from narrowly validated headless integration bridges.
8. No new `server.bots` dependency enters Agent runtime, and portable policy/catalog packages contain no Cosmic runtime imports.
9. Cold catalog build, warm cache load, policy evaluation, and per-tick behavior meet existing performance budgets and expose metrics.
10. Main compilation, focused new tests, existing Agent quest/capability tests, architecture tests, and the full test suite pass.

Execution approach

- Implement one phase at a time in the order above.
- Begin each phase with a source/contract audit and a small testable seam.
- Preserve current Amherst and Maple Island showcase behavior while replacing hardcoded scope coupling with adapters.
- Stop and document a blocker if an existing plan/profile/catalog contract is not stable enough; do not create a parallel subsystem to bypass it.
- At the end of each phase, update the implementation status and list remaining limitations before starting the next phase.
```
