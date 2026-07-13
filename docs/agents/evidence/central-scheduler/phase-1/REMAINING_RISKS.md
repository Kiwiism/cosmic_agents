# Phase 1 Remaining Risks

- Central-sequential still scans and sorts every registration each cycle.
- The compatibility `ScheduledFuture` surface still has blocking `get`
  methods; scheduler code does not call them, but the contract is removed in a
  later phase.
- External Agent mutations are not yet universally mailbox-owned.
- Chat mailbox compatibility still waits for a result.
- Delayed callbacks are not yet fully classified or migrated.
- Central-sharded mode is deliberately unavailable.
- Live-client parity and sustained load evidence remain mandatory before any
  default-mode change.
- The repository-wide Maven suite is not a clean phase gate yet. Its latest
  attempt reported dialogue tests that assume chatter while
  `AGENT_LEGACY_DIALOGUE_ENABLED=false`, one Amherst quest-policy assertion,
  missing generated catalog fixtures under `tmp/game-catalog`, and a movement
  randomness assertion; the Surefire child then exited without releasing the
  Maven wrapper. None of those paths is changed by Phase 1.
