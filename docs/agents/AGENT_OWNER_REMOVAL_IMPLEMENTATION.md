# Agent owner removal implementation

## Outcome

Agent identity and lifecycle no longer depend on a human owner or on the
`bot_owners` table. Human control is configuration-backed authority. Following,
interaction, party membership, cohort membership, and formation membership are
separate relationships and may all differ or be absent.

The migration deliberately keeps deprecated owner/leader-shaped Java aliases
for one compatibility window. They delegate to explicit interaction/cohort
state and are not backed by ownership persistence. New code must not use them.

## Implemented removal order

1. **Authority first.** `AgentAuthorityService` provides observer, operator,
   and administrator roles from `config.yaml`. `AgentControlService` resolves
   characters and checks command authority without registering ownership.
2. **Agent-centric registry and lifecycle.** Active sessions are indexed by
   agent character ID. Cohort is a secondary grouping key. Lifecycle phases are
   `ACTIVE`, `SUSPENDED`, `RELOGIN_BACKOFF`, `QUARANTINED`, `STOPPING`, and
   `OFFLINE`.
3. **Relationship separation.** `AgentRelationshipState` stores optional
   follow and interaction targets plus independent cohort and formation IDs.
   Party identity remains canonical Cosmic party state through
   `AgentPartyContextRuntime`.
4. **Capability detachment.** Combat support, supplies, inventory, equipment,
   loot, trade routing, dialogue, navigation, and formation use the relevant
   explicit relationship. Passive agent loot goes to the agent rather than a
   former owner.
5. **Structured cooperation.** Supply needs publish
   `AgentSupplyNeedMessage`; donor selection remains cohort-scoped. Maple chat
   is presentation and can later subscribe only when a real observer is
   present. Agents never need to parse chat to understand the request.
6. **Lifecycle state replaces owner presence.** A missing/offline follow target
   stops follow mode and uses the agent itself as its activity anchor. It does
   not stop autonomous capability ticks. Relog captures map, position, cohort,
   formation, follow target, and interaction target before disconnecting.
   Repeated tick failure quarantines only the failing agent.
7. **Commands use authority.** Spawn, plan, chat/whisper control, equipment
   debug actions, and trusted human trade ingress use authority/trust lists.
   Agent lifecycle registration itself has no owner write.
8. **Legacy behavior gate.** `AGENT_LEGACY_OWNER_COMPATIBILITY_ENABLED` is
   `false` by default. It can temporarily restore the old owner-away dialogue
   flow without restoring database ownership.
9. **Verification.** Automated boundaries cover authority hierarchy,
   agent/cohort registry behavior, owner-free relog, typed coordination events,
   lifecycle failure isolation, follow/session compatibility, and supply
   sharing. The manual soak matrix is below.
10. **Persistence removal.** `AgentOwnershipService`, registration/take-owner
    commands, and ownership gateway SQL are removed. Liquibase changeset 25 is
    retained byte-for-byte because applied changesets are immutable. Forward
    changeset `26-remove-bot-owners` drops the table safely on both upgraded and
    fresh databases.

## Configuration

Names are case-insensitive comma-separated values.

```yaml
AGENT_AUTHORITY_ADMINISTRATOR_NAMES: Kiwi
AGENT_AUTHORITY_OPERATOR_NAMES: ""
AGENT_AUTHORITY_OBSERVER_NAMES: ""
AGENT_TRUSTED_TRADE_PLAYER_NAMES: Kiwi
AGENT_LEGACY_OWNER_COMPATIBILITY_ENABLED: false
```

- Administrator includes operator and observer permissions.
- Operator can run normal agent control/test commands.
- Observer is reserved for read-only diagnostics.
- Trusted trade names may initiate human-to-agent trades. Agent-to-agent trade
  remains governed by agent policy and cohort/party context.
- Authority is administrative only: it never makes the actor a follow target,
  party leader, cohort identity, or formation identity.

## Behavior retention map

| Previous owner behavior | Owner-free source of truth |
|---|---|
| Command permission | Authority role |
| Spawn/recruit lifecycle | Agent character ID and active session |
| Follow movement | Optional follow target |
| Whisper/trade reply | Optional interaction target |
| Supply sharing | Cohort ID and structured supply message |
| Formation offsets | Formation ID |
| Party combat/support | Cosmic party ID/membership |
| Logout/relog/failure | Agent lifecycle phase |
| Group cleanup | Cohort ID; individual failures remove/quarantine one agent |

## Manual soak gates

Run with `AGENT_LEGACY_OWNER_COMPATIBILITY_ENABLED: false`:

1. Complete the full Maple Island cohort run with no human follow target.
2. Assign and clear a follow target while questing; autonomy must resume after
   the target logs out.
3. Form/dissolve parties independently of cohorts and confirm support targets
   party members rather than an administrator.
4. Exercise agent-to-agent supplies and trusted/untrusted human trade ingress.
5. Relog an agent while its interaction target is offline; it must return to
   the captured map and position.
6. Disconnect the administrator; unrelated agents must keep ticking.
7. Inject one agent tick failure until quarantine and confirm the cohort and
   real players remain unaffected.
8. Restart the server and confirm Liquibase validates history and
   `bot_owners` is absent.

The rollback switch restores only owner-era dialogue behavior. If an issue
requires the removed ownership table or service, roll back the whole migration
commit instead of reconstructing partial ownership state.

## Physics branch integration

This migration should reach `master` as one logical commit. The physics branch
should merge that master commit rather than cherry-picking individual files,
because movement now depends on the relationship and lifecycle foundations.

Recommended sequence on the physics branch:

```powershell
git status --short
git fetch origin
git merge origin/master
.\mvnw.cmd -q -DskipTests test-compile
.\mvnw.cmd -q "-Dtest=Agent*Movement*Test,Agent*Physics*Test,AgentTickCoreRuntimeTest" test
```

Resolve conflicts semantically in these likely hotspots:

- `AgentRuntimeEntry`: preserve physics state fields and the new identity,
  relationship, and lifecycle state fields.
- `AgentTickCoreRuntime`: preserve the self activity-anchor behavior; do not
  restore leader/owner lookup as a precondition for physics ticks.
- movement/follow code: read `AgentRelationshipRuntime.followTarget`; do not
  infer it from cohort, formation, party, authority, or interaction target.
- cleanup: retain physics cache cleanup and lifecycle `OFFLINE` transitions.

Do not merge with a dirty physics worktree. Commit or stash physics experiments
first, merge master, resolve and test, then resume the experiment. This keeps a
clean rollback point and avoids mixing behavior changes with conflict fixes.
