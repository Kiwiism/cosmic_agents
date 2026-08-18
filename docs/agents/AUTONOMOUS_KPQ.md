# Autonomous Kerning Party Quest

## Scope and isolation

The autonomous KPQ implementation lives under
`server.agents.capabilities.partyquest.kpq`. It is a primary `PARTY_QUEST`
activity, so admission first drains Town Life, Hunting, Questing, or Commerce.
The incomplete compatibility KPQ hooks are bypassed only for members indexed in
a new KPQ session. Ordinary player KPQ scripts are unchanged.

Production admission is explicit through `AgentKpqAdmissionService`; no world
population decision automatically starts KPQ yet. The GM observation harness is
the only command entry point. Checkpoint property/map mutation exists only in
`AgentKpqCheckpointService` and rejects non-test sessions.

The server event script currently accepts three or four members, despite some
versions of KPQ allowing larger parties. The autonomous system follows the
authoritative local `KerningPQ.js` limit rather than pretending that five or six
will enter.

## Milestones 0-12

0. Isolated package, session aggregate, registry, and server gateway.
1. Authoritative map, NPC, item, question-answer, and puzzle rectangle catalog.
2. Exclusive activity admission and lifecycle release.
3. Time-based recruitment policy: four launches immediately; three waits longer.
4. Agent-led party creation, shuffled roster order, staggered preparation.
5. Stage 1 question lookup, exact coupon targets, excess redistribution drops,
   role-aware loot, pass delivery, and leader clear.
6. Stages 2-4 deterministic exhaustive combination paths with stable member
   numbers and exactly one changed assignment after the initial formation.
7. Stage 5 normal combat, leader-only pass collection, and seeded Agent shoe
   winner.
8. Normal reward NPC flow, immediate bonus-map exit, and outside waiting state.
9. Transition-only narration, seeded timing offsets, mixed jobs/genders/appearance,
   legal level-25 equipment, supplies, projectiles, and accuracy pills.
10. Human-member observation seam for puzzle positions and manual reward/exit.
    The autonomous event leader must currently be an Agent; a human can occupy a
    nonleader slot and is instructed but never moved or clicked by the system.
11. GM harness, stage checkpoints, pause/resume/status, rerun, stop, and random
    one- or two-member rotation.
12. Unit coverage for authoritative answers, recruitment thresholds, stable
    numbering, and the one-mover combination invariant.

## Observation commands

Run these while standing in Kerning City (`103000000`):

```text
!kpqtest start [3|4] [seed]
!kpqtest checkpoint <1-5> [3|4] [seed]
!kpqtest status
!kpqtest pause
!kpqtest resume
!kpqtest run
!kpqtest switch <1|2>
!kpqtest stop
```

`start` provisions missing Agent-only `KPQer01` through `KPQer24` backing
characters as needed. Names are selected in seeded shuffled order, so numeric
order is not spawn order. `switch` is accepted only after the party is waiting
outside. `run` starts the next full run with the current party.

## Verification boundary

Automated tests validate deterministic policy and state invariants. Maven compile
and focused KPQ tests must pass before use. A live channel smoke run is still
required to validate rope attachment, physical path reachability, event-instance
portal timing, actual drop rates, and every randomized event reward against the
server's current WZ/database data.
