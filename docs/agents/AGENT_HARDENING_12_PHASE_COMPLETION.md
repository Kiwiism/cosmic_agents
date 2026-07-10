# Agent Hardening 12-Phase Completion

## Result

The 12 audited Agent reliability, security, memory, and scheduling phases are
implemented on `reconstruction/source-master-agent-base`. Automated validation
uses an isolated snapshot of the current combined worktree so IDE compilation
cannot alter Maven's output while tests are running.

## Phase commits

1. Unused scaffolding: `6d6072ec1e`
2. Bounded command parsing: `3b5d1b6722`
3. Concurrent attack-profile cache: `c756a2fefe`
4. Lifecycle initialization ordering: `456adc0752`
5. Safe navigation-cache persistence: `cb91698d82`
6. Lifecycle state cleanup: `4c7ddf5c21`
7. Session-scoped delayed callbacks: `5d7e9c65b5`, `11360f1e23`
8. Weighted navigation cache: `f04c5fb2f5`
9. Provisioning security: `c3a614f309`, `5211117e5d`, `1cfa1b3ce5`
10. Bounded async queues and metrics: `562910ae15`, `9969592c0f`, `ac3a353ca5`
11. Per-Agent mailbox foundation: `8ee3d75ed1`
12. Optional central scheduler: `4ede72a240`

Runtime regression alignment is in `1d0e68b2b4` and `d53617a6d1`. Random
dialogue assertions that could fail despite valid production output were made
deterministic in `d6a93d9d6a` and `fbc30b169d`.

## Automated evidence

- Full Maven suite: 4,392 tests, 0 failures, 0 errors, 0 skipped.
- Package: `target/Cosmic.jar` built successfully.
- Production and test `server.bots` directories: absent.
- `import server.bots` in production/tests: zero.
- `server.bots` production text dependencies: zero.
- Agent `Executors.new*`, `LinkedBlockingQueue`, and `ConcurrentLinkedQueue`
  allocations: zero.
- Agent `TimerManager` access: only `CosmicSchedulerGateway`.
- Central scheduler default: disabled through
  `agents.scheduler.central.enabled=false` when unspecified.
- Mailbox compatibility path default: disabled through
  `agents.mailbox.enabled=false` when unspecified.

## Behavior and rollback

Valid movement, combat, navigation, dialogue, trade, inventory, quest, NPC,
spawn, and command behavior is intended to remain unchanged. Intentional
changes are limited to malformed-number rejection, stale callback suppression,
bounded overload rejection/coalescing, restricted provisioning, and player
login lockout for newly provisioned Agent backing accounts.

The legacy per-Agent scheduler remains the default rollback path. The central
scheduler and mailbox can each be enabled independently through their system
properties after live parity validation.

## Remaining manual validation

Automated scheduler parity and a deterministic 500-session scheduler soak pass.
Live-client movement/combat/dialogue parity and a long-duration production soak
remain operational validation tasks; they do not block the completed code and
automated-test scope, but central scheduling must remain disabled by default
until that evidence is collected.
