# Pre-Reconstruction Goal Prompt

Purpose:

```text
Provide a reusable Codex goal prompt for finishing all safe prep work before
the Nutnnut bot-to-Agent reconstruction is complete.
```

Use this prompt when starting or resuming a broad pre-reconstruction prep
thread. It intentionally preserves the same boundaries as the safe-prep audit:
do useful preparation now, but do not change live Agent runtime behavior until
the reconstruction boundary is stable.

## Prompt

```md
Complete all safe preparation work before the Nutnnut bot-to-Agent
reconstruction is finished.

Objective:

Prepare the Cosmic server, documentation, catalogs, contracts, package specs,
scaling plans, and future implementation plans so that once Agent
reconstruction is complete, the new Agent engine can immediately build toward:

- Maple Island questline MVP.
- 2000 concurrent Agent scaling.
- LLM-directed Agent control.
- economy simulation.
- profile-driven Agent behavior.
- modular capabilities.
- portable Agent platform installation.
- future Database Console / Server Console / Agent Console integration.

Hard Rules:

- Do not modify live Agent runtime behavior.
- Do not edit `src/main/java/server/agents` or `src/main/java/server/bots`
  unless explicitly requested.
- When explicitly requested, Java work under `server/agents` must be inactive
  prep only: contracts, static catalogs, scope policies, selectors, guarded
  test harnesses, or tests that are not wired into live ticks.
- Do not change BotClient behavior.
- Do not implement live Agent navigation, combat, looting, NPC, quest, shop,
  economy, LLM, or plan execution behavior yet.
- Do not change `config.yaml` unless explicitly requested.
- Preserve current server behavior by default.
- Server changes must be low-risk, default-preserving, and useful even without
  Agents.
- Agent-related work should stay as docs, TODOs, contracts, catalogs,
  inactive hooks, package specs, or future implementation plans.

Work Scope:

1. Server-Only Prep

Finish safe server-side hardening, diagnostics, crash prevention, memory-leak
prevention, and low-risk optimizations that do not touch Agent runtime.
Document remaining server-only TODOs and separate player scaling from Agent
scaling.

2. Agent Scaling Prep

Document the future 2000-Agent scaling model, including visible full
simulation, background/light simulation, abstract offscreen simulation, route
ETA travel, abstract combat/loot, direct loot-to-inventory when unseen,
reduced broadcasts, reduced physics, deferred persistence, Agent DB separation,
population spreading, and materialization when players enter view.

3. Catalog Prep

Prepare portable, fast-query catalog/index specs and data expectations for
maps, NPCs, quests, shops, mobs, drops, reactors, portals, regions, travel
links, spawn density, footholds, interact spots, dialogue delay metadata, quest
requirements/rewards, blocked quests, and review-needed quests.

4. Maple Island MVP Prep

Finalize implementation-ready docs for the Maple Island questline MVP: quest
sequence, NPC start/complete actions, maps, kill/loot/use objectives, required
capabilities, fallbacks, timeout handling, optional random interact spots,
optional dialogue delay simulation, and the first concrete Plan Card.

5. Portable Agent Platform Prep

Document future package boundaries: Agent runtime, capability runtime, plan
runtime, catalog provider, profile provider, economy provider, event bus,
recovery policy, observability, server adapter, optional LLM/MCP bridge, and
minimal Cosmic integration points.

6. Plan Card System Prep

Finalize design and technical specs for ordered objectives, unordered
objectives, focus mode, sidetracks, exit criteria, retries, postponement,
fallback actions, capability activation, and Maple Island as the first
implementation target.

7. Agent Profile Prep

Finalize specs for personality, job goals, playstyle, risk tolerance, patience,
market behavior, social behavior, quest/grind/farm preference, volatility,
Islanders, special archetypes, relationship memory, and decision journals.

8. Economy Engine Prep

Finalize specs for baseline item pricing, supply/demand, listing duration,
actual sale history, trade tax, inflation/deflation, manipulation resistance,
class-population demand, event-driven demand, rare item valuation, equip stat
valuation, scroll valuation, Agent buying/selling strategy, and self-learning
market adaptation.

9. Console Planning

Keep planning records for Database Console, Server Console, and Agent Console.
Clearly document which system owns database records, server policies/overrides,
and Agent profiles/plans/runtime controls.

10. Nutnnut Over Cosmic Review

Keep decision records updated for Nutnnut changes over Cosmic. Bot/Agent system
stays with reconstruction. WZ assets use Cosmic as base. Config values follow
current user-set values. Admin commands are kept for now. Economy, item,
quest, network, and database changes should follow recorded user preferences or
remain marked for review.

11. Verification

Before finishing any implementation work:

- Run `git diff --check`.
- Check no active Agent reconstruction files were modified or staged unless
  explicitly requested. Inactive capability prep under `server/agents` must be
  listed separately from live runtime wiring.
- Check `config.yaml` was not changed unless requested.
- Compile/test where feasible.
- If compile fails due to existing reconstruction work, document the blocker
  and do not fix Agent code unless requested.

Deliverables:

- Updated docs.
- Updated TODOs.
- Updated package registry.
- Updated implementation specs.
- Server-only safe prep where useful.
- No live Agent behavior changes.
- Clear list of post-reconstruction implementation packages.

Completion Criteria:

This goal is complete only when all safe pre-reconstruction prep is either
documented, specified, or safely implemented server-only, and all
Agent-dependent work is clearly deferred until after reconstruction.
```

## Usage Notes

- Read `docs/agents/PRE_RECONSTRUCTION_SAFE_PREP_STATUS.md` before choosing
  the next slice of work.
- Treat `docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md` as the evidence
  ledger, not as proof that runtime soak evidence already exists.
- Commit safe-prep slices separately from reconstruction commits.
- Do not stage dirty reconstruction files while committing docs, tooling, or
  server-only prep.
- If actual runtime evidence is collected, record the run id and summary in the
  completion audit.
