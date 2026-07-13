# Phase 5 Parity

- `LEGACY_PER_AGENT` remains the default and Amherst retains synchronous
  persistence in that mode.
- Navigation still builds and caches the same graph; normal Agent paths now
  continue with the existing fallback until the graph is ready instead of
  waiting on its future.
- LLM prompts, inference, memory updates, message splitting, and reply delays
  are unchanged; visible delivery now enters through the owning mailbox.
- Trade/item classification, random reply delay, and final mutation paths are
  unchanged; only background analysis and completion ownership moved.
- Central Amherst start waits in runtime state for its asynchronously loaded
  progress and resumes from the owning mailbox.
- One slow workload cannot block a scheduler worker or another workload lane.

No visible gameplay behavior change is intended. Live-client parity remains a
gate before changing the default scheduler mode.
