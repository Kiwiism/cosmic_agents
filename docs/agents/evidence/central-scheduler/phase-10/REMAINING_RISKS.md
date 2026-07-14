# Phase 10 Remaining Risks

- The removed Double Agent/profile-switch implementation remains absent.
- Canonical profile restoration before save/release is therefore not yet a
  production behavior that can be validated.
- Future profile operations must keep token-held mutation compact or dispatch
  blocking persistence through the bounded async lanes.
- Deterministic disconnect/unregistration and shutdown races are covered at
  the scheduler boundary; live-client race testing remains required when a
  profile consumer is implemented.
- Combined multi-shard, tick-slicing, simulation, load-shedding, and
  quiescence behavior still needs staged 500/1000/1500/2000-Agent tests.
- The production scheduler default remains legacy until Phase 11 gates pass.
- The repository-wide test suite is not green independently of Phase 10:
  dialogue/supply fixture expectations, one forbidden-quest expectation, and
  absent generated catalog fixtures fail in this worktree; one randomized
  movement assertion also failed once and passed on isolated rerun.

Rollback remains `agents.scheduler.mode=legacy`. The same quiescence guard is
retained in rollback mode so future profile consumers do not lose safety.
