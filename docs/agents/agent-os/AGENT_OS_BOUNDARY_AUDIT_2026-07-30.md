# Agent OS Boundary Audit — 2026-07-30

## Scope

This audit covers production code under `src/main/java/server/agents`, its
architecture guard tests, runtime blocking hazards, generated workspace
artifacts, and the seams required by a future social-chat implementation.

It does not redesign a capability merely because its implementation is large.
Changes are limited to demonstrated boundary violations or misleading
guardrails. Larger extractions are recorded as debt with a migration target.

## Results

| Area | Result | Evidence or correction |
|---|---|---|
| Cosmic mutation boundary | No new regression; observer boundary restored | Observer shutdown no longer reaches through `Character.getClient()`. Reviewed compatibility surfaces listed below still require migration. |
| Scheduler blocking boundary | Restored | Objective checkpoint writes and Windows lock retries run through the bounded persistence executor. Retry delay is scheduled; no Agent scheduler thread sleeps. |
| Navigation to Movement | Accepted debt, frozen | Teleport and Flash Jump execution raised direct imports from 74 to 93. The reviewed ceiling is now 93 and may not grow. |
| Movement to Navigation | Within ceiling | Existing dependency remains below its migration ceiling. |
| Capability to Plan compatibility | Within ceiling | No new capability dependency on concrete plan implementations was introduced. |
| LLM mutation boundary | Clean | The model-provider seam receives strings and returns optional text. It cannot see runtime entries or Cosmic mutation objects. |
| Personality boundary | Clean | Profiles remain declarative inputs rather than owners of movement, combat, or plan execution. |
| Configuration boundary | Clean | Agent tuning remains in `agent-engine.yaml`; server configuration remains separate. |
| Generated workspace noise | Contained | Diagnostic text, rendered graph exports, and local .NET `bin`/`obj` output are ignored without deleting useful local evidence. |

## Corrections made

### Observer cleanup

Previous flow:

```text
observer runtime -> Character.getClient() -> forceDisconnect()
```

Corrected flow:

```text
observer runtime -> CharacterGateway.disconnect() -> Cosmic adapter
```

The observer controller retains lifecycle intent, while Cosmic client details
remain in the integration adapter.

The boundary guard still explicitly freezes older compatibility exceptions in
Maple Island command/cohort code, the Relaxer reservation runtime, synthetic
mob reaction diagnostics, and the Free Market stall service. Those exceptions
were not introduced by this change and are not evidence that direct Cosmic
access is generally allowed. In particular:

- cohort disconnect and Relaxer item mutation should move to lifecycle and
  inventory gateways;
- Free Market stall packet/client operations should move to a dedicated stall
  integration gateway;
- command adapters may read the issuing real player's channel, but should
  project it into immutable command context before entering plan policy;
- diagnostics may retain low-level packet access only while isolated from
  production decision paths.

### Objective checkpoint persistence

Previous flow:

```text
Agent objective transition
  -> synchronous JSON write
  -> Thread.sleep on AccessDeniedException
```

Corrected flow:

```text
Agent objective transition
  -> capture immutable checkpoint
  -> bounded PERSISTENCE lane
  -> atomic/non-atomic replacement
  -> mailbox completion
  -> scheduler-delayed retry only for AccessDeniedException
```

The live objective remains authoritative if persistence fails. A checkpoint
failure is logged and never rolls back or corrupts the live objective state.
Per-character write revisions suppress stale queued writes, so increasing the
persistence executor width cannot let an older checkpoint overwrite a newer
one.

## Reviewed architectural debt

### Navigation and Movement are still concretely coupled

Navigation owns route choice, graph edges, portal approach, traversal intent,
and edge execution. Movement owns ground, airborne, climb, pose, and movement
skill primitives. Teleport and Flash Jump are traversal actions: route
selection belongs to Navigation, while physical execution belongs to Movement.

The current implementation expresses that relationship with 93 direct
Navigation-to-Movement imports. This is understandable but too concrete for the
long-term Agent OS. The boundary test now freezes the reviewed value at 93 so
new dependencies cannot silently accumulate.

Recommended extraction:

1. Define a `TraversalCommand` value contract containing the selected edge,
   launch/landing constraints, locomotion kind, and evidence requirement.
2. Define a `TraversalExecutor` port owned by neither concrete package.
3. Have Navigation emit commands and consume immutable traversal results.
4. Have Movement implement the executor through an integration registration.
5. Move teleport/Flash Jump readiness and execution behind that port.
6. Reduce both package-to-package import ceilings only after parity tests pass.

Do not move files merely to make the import counter smaller. The desired result
is one explicit directional port, not a renamed cycle.

### Dialogue still contains command-era compatibility

The dialogue package currently combines several generations:

- deterministic command classification and owner-era wording;
- human-facing reply formatting;
- observer-gated event projection;
- pending actions and interaction sessions;
- a read-only dialogue-only LLM seam.

These remain operational, but they should not become the foundation for
Agent-to-Agent social behaviour. Structured coordination already exists and
must remain the machine protocol. The detailed target architecture is in
`AGENT_SOCIAL_CHAT_ARCHITECTURE.md`.

### Static runtime accessors reduce test substitutability

Several runtime facades expose process-wide static adapters. They are useful
during reconstruction, but make isolated tests and multiple-world simulation
harder. New capabilities should accept ports in constructors or registration
records. Existing static facades should be retired only when callers have
parity coverage; a sweeping service-container rewrite would be higher risk.

### Compatibility imports are migration debt

The capability-to-plan compatibility ceiling prevents new coupling but does not
claim the existing imports are ideal. Plans should request typed capability
commands through the objective kernel. Capabilities must not inspect concrete
Maple Island, Victoria, or TownLife runner state to decide top-level intent.

## Boundary ownership after this audit

| Layer | Owns | Must not own |
|---|---|---|
| Objective kernel | active goal, suspension stack, completion/failure result | Cosmic mutation mechanics |
| Plan runtime | ordered objective definitions and transition conditions | movement physics or packet creation |
| Capability policy | deterministic proposal from immutable perception/profile inputs | direct packets, database writes, global goal changes |
| Capability executor | one bounded domain action through gateways | choosing the Agent's next top-level goal |
| Scheduler/mailbox | fairness, ordering, generation safety, bounded delivery | gameplay policy |
| Integration gateways | Cosmic reads/mutations and thread-affinity enforcement | Agent intent |
| Event bus | facts that already happened | imperative mutation commands |
| Dialogue projection | optional human-facing presentation | Agent-to-Agent operational protocol |
| LLM provider | text or bounded proposals from immutable context | direct Cosmic or runtime mutation |

## Follow-up gates

Before further broad capability work:

1. Keep all architecture and blocking boundary tests green.
2. Reject any increase above the reviewed 93 Navigation-to-Movement imports.
3. Require new LLM integrations to pass the read-only provider boundary.
4. Require every social action proposal to pass deterministic policy and
   capability arbitration.
5. Keep public Maple chat optional; suppressing chat must never break a plan.
6. Add traversal-port parity tests before lowering the Navigation/Movement
   dependency ceilings.
