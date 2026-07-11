# NuTNNuT Fix Port Status

Branch: `port/nutnnut-agent-correctness-population`

## Quest-status placeholder persistence

Source reference: `85249c8f0f` from
`source/fix-queststatus-notstarted-bloat`.

The current immutable persistence-snapshot pipeline now omits only an empty
`NOT_STARTED` placeholder. Records with forfeits, progress, medal-map state, or
a `STARTED`/`COMPLETED` status remain persisted. This reduces save pressure and
database growth without changing meaningful quest state.

## Navigation: joined-fork foothold continuity

Source reference: `7c0d287015` from `source/dev`.

Ground-region sampling now prefers the standing foothold and its direct
`prev`/`next` chain before overlapping non-chain segments. This matches the
client's standing-foothold model and prevents a walk across a joined fork from
snapping onto a lower dead-end arm. Agent navigation graph cache version 47
invalidates graphs authored with the previous walk simulation.
