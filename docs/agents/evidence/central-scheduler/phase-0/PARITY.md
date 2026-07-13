# Central Scheduler Phase 0 Parity

Automated parity anchors:

- legacy and central paths invoke the same guarded tick callback.
- both modes produce the same callback count over the required populations and
  cadences.
- paused, removed, despawning, stale, and invalid sessions remain skipped.
- one Agent failure does not stop later central-sequential callbacks.
- missed cadence does not replay multiple catch-up callbacks.
- mailbox actions still drain through the guarded tick path.
- Amherst capability runtime focused tests remain part of the baseline suite.

Live-client parity is not claimed. Movement, combat, loot, dialogue, map
transfer, death, recovery, and two-client packet consistency remain required
before any scheduler mode becomes the production default.
