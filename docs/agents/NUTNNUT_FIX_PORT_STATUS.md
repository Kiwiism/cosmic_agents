# NuTNNuT Fix Port Status

Branch: `port/nutnnut-agent-correctness-population`

## Quest-status placeholder persistence

Source reference: `85249c8f0f` from
`source/fix-queststatus-notstarted-bloat`.

The current immutable persistence-snapshot pipeline now omits only an empty
`NOT_STARTED` placeholder. Records with forfeits, progress, medal-map state, or
a `STARTED`/`COMPLETED` status remain persisted. This reduces save pressure and
database growth without changing meaningful quest state.
