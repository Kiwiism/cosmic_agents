# Phase 2 Remaining Risks

- `AgentNavigationGraphService.getGraph` still contains a blocking
  `CompletableFuture.join()`. Phase 5 must prevent scheduler workers from
  reaching it and deliver generation/request-stamped graph completions.
- Amherst reset/start operations can perform file or persistence work from a
  mailbox action. Phase 5 must move blocking work to bounded workload-specific
  executors before central modes are production candidates.
- `AgentTransferRuntime` evaluates part of its transfer operation on an async
  executor. Its delayed mutation is mailbox-delivered, but Phase 5 must make
  request identity and stale-completion rejection explicit.
- Formation and leader-away commands can mutate multiple sessions from one
  session action. This is single-writer-safe in legacy and
  central-sequential mode, but is `UNSAFE_PENDING_REFACTOR` for Phase 6
  multi-shard execution.
- Lifecycle admission/removal remains synchronous through the lifecycle facade.
  It is critical control work and requires a Phase 6 Cosmic thread-affinity
  classification before multi-shard mode.
- The focused suite does not prove live movement, combat, loot, dialogue,
  packet timing, or long-duration load parity.
- Legacy per-Agent scheduling remains the default and tested rollback.
