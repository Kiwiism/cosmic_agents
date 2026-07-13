# Phase 8 Summary

Baseline commit: `797f9ab322`

Phase 8 adds bounded guarded-tick slicing behind
`agents.scheduler.tickSlicing.enabled=false`:

- the existing guarded tick is represented as four ordered frame slices;
- the compact path still runs that same frame to completion;
- a central turn runs a configurable number of slices and requests a
  coalesced immediate continuation when work remains;
- a frame has a hard continuation limit and uses the existing per-Agent
  failure path when the limit is exceeded;
- mailbox drain occurs once per frame, and movement settlement plus failure
  reset occur only after completion;
- lifecycle cancellation clears partial frame state;
- bounded duration metrics are available by slice kind.

Legacy scheduling remains the default and never enables slicing. No gameplay
outcome, database schema, WZ data, or visible behavior is intentionally
changed.
