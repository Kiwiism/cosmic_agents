# Agent Gameplay Track

This track contains Agent gameplay capability, correctness, and autonomy work.
It is intentionally separated from the scaling track so the post-reconstruction
focus can be 2000-Agent runtime stability first.

## Gameplay Scope

Gameplay packages include:

- plan runtime.
- capability runtime.
- NPC quest interaction.
- Maple Island MVP.
- recovery policy.
- combat and loot objective behavior.
- inventory/item policy.
- skill and buff parity.
- shop/trade/economy behavior.
- profile-driven plan selection.
- LLM control.
- population behavior.

## First Gameplay Milestone

After scaling foundations are in place, the first gameplay milestone remains:

```text
Maple Island questline MVP
```

Required package cut:

- plan card loader/progress/objective runner.
- capability command/result/audit model.
- Maple Island catalog runtime slice.
- quest read/start/complete capability.
- NPC quest interaction validator/executor.
- navigation objective adapters.
- portal verification and Shanks/Lith Harbor guard.
- inventory count/free-slot/protected quest item reads.
- Roger apple use-item objective.
- combat kill stop condition.
- loot item stop condition.
- recovery retry/block policy.
- Pio reactor support or clean blocker/debug-only resolver.
- Yoona scripted item support.
- resume/reconcile.
- objective journal.
- full one-Agent integration test.

## Gameplay Backlog

### Maple Island MVP

Primary docs:

- `docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md`
- `docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md`
- `docs/agents/MAPLE_ISLAND_MVP_AGENT_TODO_ALIGNMENT.md`

### Damage And Buff Parity

Includes:

- Magic Guard.
- Meso Guard.
- Power Guard.
- Combo Barrier.
- Achilles.
- High Defense.
- Mana Reflection.
- Battleship HP routing.
- Invincible review.

### Skill Capability Matrix

Includes:

- active attacks.
- support buffs.
- summons/puppets.
- debuffs/control skills.
- utility skills.
- movement skills such as Teleport and Flash Jump.

### NPC / Quest / Shop

Includes:

- generalized quest capability.
- generalized NPC action validation.
- shop interaction.
- reward choice policy.
- script-sensitive review flags.

### Inventory / Item Policy

Includes:

- protected item policy.
- inventory capacity planning.
- acquisition option service.
- sell/drop/trade safety.

### Economy / LLM / Population

Includes:

- economy engine runtime.
- LLM gateway.
- profile adaptation.
- relationship memory.
- population director.

## Gameplay Work Deferred During Scaling Track

While focusing on 2000-Agent scaling, avoid starting:

- generalized shop automation.
- full economy runtime.
- LLM gateway.
- full spell matrix.
- advanced defensive buff parity.
- relationship/social graph split.
- population director.

Allowed exceptions:

- minimal gameplay hooks needed to test scaling modes.
- bug fixes that prevent Agent cleanup or cause runaway loops.
- small validation hooks needed for safe background simulation.
