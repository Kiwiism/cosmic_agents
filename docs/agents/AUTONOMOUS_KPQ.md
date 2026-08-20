# Autonomous Kerning Party Quest

## Scope and isolation

The KPQ stage implementation lives under `server.agents.capabilities.partyquest.kpq`.
The reusable parent engagement and lobby live under
`server.agents.capabilities.partyquest`. A parent engagement owns Agents from
activity acquisition through lobby, one fresh event session, exit, and recovery.
Lobby and event therefore share the primary `PARTY_QUEST` activity instead of
creating a controller-less gap between systems. The incomplete compatibility KPQ
hooks are bypassed for the full parent engagement, including lobby and bounded
recovery, so they cannot reactivate while an Agent is between event phases.
Ordinary player outcomes remain authoritative; Cloto has one
observation hook that reports a human leader's real puzzle verdict to the Agent
coordinator without exposing the hidden answer.

Production admission is transactional through `AgentKpqAdmissionService`: it
freezes the authoritative lobby roster, validates every member, publishes the
complete event session/member indexes together, then closes the lobby. A failed
handoff restores the lobby rather than leaving a partial KPQ session.
`AgentKpqPopulationRuntime` may select only explicitly managed, live level-21-30
population Agents, releases their current primary activities, creates an owned
party, moves it to Kerning, and admits it through that same boundary. Checkpoint
property/map mutation exists only in `AgentKpqCheckpointService` and rejects
non-test sessions.

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
5. Stage 1 question lookup, exact coupon targets, shared grind/combat execution,
   seeded target/platform variation, ordinary shared hunting pickup for nearby
   coupons, and a 90-second map-wide sweep that assigns one collector for up to
   30 seconds before rotating, excess redistribution drops, pass delivery, leader combat
   participation, an 80% base Ligator coupon chance, and ordinary leader clear.
6. Stages 2-4 deterministic exhaustive combination paths with stable member
   numbers and exactly one changed assignment after the initial formation. Puzzle
   chat contains only `IGN -> number` for members whose assignment changed; an
   already-correct formation produces no redundant movement instruction.
7. Stage 5 preferred normal-mob then King Slime combat, obsolete platform-anchor
   release, leader-only pass collection, return navigation, missing-pass recovery,
   and a seeded shoe winner that prefers an Agent without Squishy Shoes. If a human
   is present, the human party has the first seven seconds to loot the drop. Only
   the assigned Agent waits; everyone else may advance and claim rewards.
8. Normal reward NPC flow, immediate bonus-map exit, event-session release, and
   parent engagement outside waiting state.
9. Transition-only narration, coupon milestones, per-pass leader progress, seeded
   timing/formation fidgets, mixed jobs/genders/appearance, legal level-25 equipment,
   supplies, projectiles, and accuracy pills. Required KPQ coordination chat is
   visible even when optional ambient Agent dialogue is globally disabled. Stage 1
   announces each Agent's coupon target after Cloto, then reports the crossed
   20%, 50%, 90%, and completion milestones only as collected/required counts.
   Shared combat-posture switching narration
   is suppressed for active KPQ members without changing combat decisions.
10. Human leader/member seams. A human leader keeps every authoritative NPC and
    pass action while an Agent coordinator narrates prompts and observes Cloto's
    real puzzle verdict. A human member can occupy a puzzle position and receives
    a named assignment before the Agent assignments. Humans are never moved,
    clicked, looted for, or made to chat by the system.
11. GM harness, stage checkpoints, pause/resume/status, rerun, stop, and random
    one- or two-member rotation.
12. Unit coverage for authoritative answers, recruitment thresholds, stable
    numbering, and the one-mover combination invariant.

## Observation commands

Run these while standing in Kerning City (`103000000`):

```text
!kpqtest start [3|4] [balanced] [seed]
!kpqtest withme [seed]
!kpqtest invite [seed]
!kpqtest wait [seed]
!kpqtest party [seed]
!kpqtest checkpoint <1-5> [3|4] [balanced] [seed]
!kpqtest status
!kpqtest pause
!kpqtest resume
!kpqtest run
!kpqtest switch <1|2>
!kpqtest stop
!kpqtest complete [1-5]
```

`start` provisions missing Agent-only `KPQer01` through `KPQer24` backing
characters as needed. Names are selected in seeded shuffled order, so numeric
order is not spawn order. Every start/checkpoint/mixed command creates a lobby
engagement first; the KPQ event session does not exist until the live party is
complete. The optional `balanced` preset gives a four-Agent party two distinct
melee careers (warrior, dagger thief, or knuckle pirate) and two distinct ranged
careers (bowman, magician, claw thief, or gun pirate); a three-Agent party gets
the nearest split. Replacements inherit the vacated career while this preset is
active. `switch` is accepted only after the party is waiting outside. `run`
creates a new lobby and a new event-session ID for the next full run with the
current party. `complete` does
not set a stage-clear flag: Stage 1 receives its missing coupons and submits them,
Stages 2-4 form the live answer and let Cloto validate it, and Stage 5 receives
the missing passes after removing test monsters before navigating back to Cloto.

`withme` creates a human-led party and adds three Agents automatically. `invite`
creates an Agent-led party of three that periodically advertises nearby; the
operator requests the real party invitation through matching map chat and then
accepts it. `wait`
spawns three named, unpartied Agents for manual invitations. Those waiters accept
only the invoking character while that character is a level-21-30 party leader
at the Kerning entrance on the same world and channel. Once all three have joined,
the mixed party is admitted and the human leader receives the ordinary Lakelis
prompt. All three modes require the operator to begin without a party.

## Reusable party-quest lobby

Recruitment state, chat, and ambient waiting behavior live in the generic
`server.agents.capabilities.partyquest.lobby` capability. A PQ supplies a profile
containing its map, NPC, level and party limits, lobby geometry, phrases, and
chat variants. A durable lobby session owns roster revision, leader, party ID,
member type/role, readiness, reservation, pause, failure, and handoff state. The
shared runtime owns normalization, longest-substring intent selection, invitation
cooldowns, authoritative party reconciliation, periodic narration, and seeded
move/fidget/sit/idle variation. Any roster change cancels readiness. Future PQs
should add a profile and admission adapter rather than another chat hook or
recruitment loop.

KPQ currently recognizes these normalized substrings; punctuation, capitalization,
and repeated spaces do not matter:

| Intent | Recognized substrings |
|---|---|
| Human wants to join | `looking for kpq`, `looking for pq`, `looking for kerning pq`, `looking to join kpq`, `looking to join pq`, `want to join kpq`, `want to join pq`, `can i join kpq`, `can i join pq`, `lf kpq`, `lf pq`, `join kpq`, `join pq`, `joining kpq`, `joining pq`, `kerning pq`, `kerning party quest`, `any kpq`, `anyone doing kpq`, `kpq anyone`, `doing kpq`, `kpq run`, `need kpq`, `need a kpq`, `kpq party please`, `kpq pls` |
| Human is recruiting | `recruiting for kpq`, `recruiting kpq`, `kpq recruiting`, `recruiting for pq`, `recruiting pq`, `pq recruiting`, `recruiting for kerning pq`, `recruiting kerning pq`, `lfm kpq`, `lfm pq`, `lfm kerning pq`, `looking for kpq members`, `looking for pq members`, `looking for members kpq`, `looking for members pq`, `looking for kerning pq members`, `need members for kpq`, `need people for kpq`, `kpq need members`, `kpq need one`, `kpq need 1`, `need one for kpq`, `need 1 for kpq`, `need one for pq`, `need 1 for pq`, `forming kpq`, `forming pq` |

The longest matching substring wins, so `looking for kpq members` is recruiting,
not a request to join. Single vague words such as `party`, `join`, or `pq` are not
accepted on their own.

Recruiter narration is also shared and roster-aware. Its default distribution is
70% party count (`KPQ 3/4, looking for 1 more`), 20% current job composition, and
10% eligibility or missing-requirement reminders. KPQ has no job or skill
requirement, so its reminder is `Looking for lv21-30 for KPQ`. A future LPQ
profile may declare requirements such as `a thief with Dark Sight` and
`a magician with Teleport`; the generic lobby reports only requirements not yet
satisfied by the live roster.

`party` adopts the operator's current online level-21-30 party without spawning,
disconnecting, or controlling its human members. The party may contain one to
three humans in any leader/member arrangement, but must contain at least one
active Agent to coordinate. Total party size remains three or four. This is the
mixed-party smoke-test entry point; automatic `switch` is intentionally disabled
for adopted parties. If another KPQ test is active, it must be stopped before
`party`; this prevents cleanup of the old engagement from disbanding the party
being adopted.

Human puzzle-role tuning uses three non-negative ratios for least, middle, and
most movement. Ratios do not need to sum to one: `1/1/1` is uniform, while
`1/0/0` gives the first human the least-moving role and, when a second human is
present, the next available least-moving role. Multiple humans are assigned
without replacement. Deterministic combination order and one-mover instructions
remain unchanged.

## Hunting reuse boundary

KPQ does not create a second combat implementation. Stage 1 and Stage 5 enter the
same `PrimitiveCapabilityGateway.grind` path used by ordinary hunting, including
target selection, target leases, route finding, attacks, skills, recovery, and
combat variation. Changes to that shared combat pipeline therefore apply to KPQ.
KPQ intentionally does not join the long-lived field population allocator because
it owns an instanced event map and party lifecycle; its test fixtures instead use
the shared local-target lease and seeded platform-anchor variation to avoid moving
and attacking in unison.

Stage 1 additionally gives registered Agent members a configurable 50% chance to
ignore simulated mob knockback. This does not reduce HP damage, does not apply in
other maps or stages, and never applies to a human player. Configure it with
`AgentKpqKnockbackResistancePolicy.STAGE_1_RESISTANCE_PERCENT` (0-100).
Non-resisted Stage 1 knockback also chooses left or right with a configurable
probability instead of always moving directly away from the attacking mob. The
default `AgentKpqKnockbackDirectionPolicy.RANDOM_DIRECTION_PERCENT` is 100, with
left and right equally likely. This variation has the same Agent/member/map/phase
boundary as the resistance policy.

Fixture AP and SP allocation is validated as complete before a test Agent may
spawn. Accuracy-aware weapon selection breaks equal-accuracy ties by actual
weapon attack, preventing a level-25 knuckle Pirate from receiving the weakest
level-10 knuckle merely because it has the lowest item ID.

## Recovery and background execution

KPQ owns one idempotent termination path that stops Agent actions, exits event
participants, disposes an uncleared event instance, releases registry indexes,
and disbands only parties explicitly owned by KPQ. Successful observation runs
retain their owned party and move the parent engagement to `POST_RUN_HOLD`; stop
or a failed owned run may clean/rebuild that party safely. Production completion
keeps `PARTY_QUEST` ownership until Town Life at the Kerning exit has accepted
every live Agent. A failed fallback remains in bounded, diagnostic recovery and
is never released into a controller-less state. A separate one-second watchdog
renews or transfers the coordinator lease, so no session depends on one fixed
Agent tick. Missing coordinators, disconnected members, exhausted puzzle
combinations, failed NPC interactions, portal stalls, inventory blocks, and exit
stalls all have bounded recovery and structured diagnostics.

Lobby reconciliation uses server party snapshots rather than assuming invite
success. Dynamic human replacement atomically removes the departed human and
indexes the replacement before readiness can be restored. Missing owned Agents are rejoined when safe, vanished test Agent runtimes
close the observation engagement after a bounded timeout, and a long human-driven
lobby emits periodic diagnostics without forcing or moving the human player.

Puzzle formations remain deterministic, but an Agent leader waits a tunable
`2000 +/- 650 ms` after the formation is stable before checking Cloto. Human
leaders continue to decide when to click normally.

Puzzle fidget is presentation-only. Rope-height and platform-position adjustments
are clamped to the member's assigned answer rectangle and never reset formation
stability, postpone the Cloto check, or register as a KPQ blocker. Only leaving
the assigned rectangle (or losing ground contact on a grounded stage) invalidates
readiness; stage transitions clear any unfinished fidget state.

`KerningPQ.js` reads its lobby limit from `AgentKpqLobbyPolicy`. The rollout
default is two isolated event instances per channel, with at most one background
party on a channel and one lobby reserved for humans. The global background cap
is two parties.

Unobserved KPQ remains exact `BACKGROUND_ACTIVE` gameplay at the scheduler's
background cadence. Movement, combat, physics, NPC scripts, drops, rewards, and
event time all remain authoritative, and a player may begin observing the same
instance without materialization. Outcome-only `BACKGROUND_ABSTRACT` KPQ is
deliberately unsupported until measured load demonstrates a need for it.

Server-owned ground-mob physics uses 18 px left and 10 px right foothold safety
insets. A supported mob keeps its foothold guard during the delayed hit handoff,
so inherited client velocity cannot carry it off the platform before grounded
knockback begins. The edge limiter also recovers mobs whose physics takeover
begins inside that margin, instead of requiring a fresh boundary crossing before
it turns them.

## Verification boundary

Automated tests validate deterministic policy and state invariants. Maven compile
and focused KPQ tests must pass before use. A live channel smoke run is still
required to validate rope attachment, physical path reachability, event-instance
portal timing, actual drop rates, and every randomized event reward against the
server's current WZ/database data.
