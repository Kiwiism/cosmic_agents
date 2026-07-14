# Phase 9 Summary

Baseline commit: `32e853f`

Phase 9 adds explicit load shedding and admission control behind
`agents.scheduler.loadShedding.enabled=false`:

- six ordered levels cover cosmetic suppression through new-session admission;
- scheduler backlog/lag/ingress and JVM CPU/heap/GC pressure are sampled once
  per configurable interval rather than in the hot dispatch loop;
- escalation and one-level-at-a-time recovery use separate hysteresis counts;
- background cadence reduction and idle-background deferral reuse scheduler
  period, priority, and ready-queue ownership;
- undirected cosmetic dialogue and selected LLM/catalog/economy submissions
  use the same strongest-shard policy;
- lifecycle-critical, presentation, navigation, and queued mailbox/completion
  work remain admitted;
- the registry mutation lock enforces the population ceiling atomically while
  allowing replacement sessions;
- transitions, suppressions, and admission rejections are reason-coded.

No gameplay outcome, database schema, WZ data, or visible behavior changes
while the feature remains disabled.
