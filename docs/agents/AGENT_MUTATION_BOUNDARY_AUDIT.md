# Agent Mutation Boundary Audit

## Allowed mutation owners

| Layer | May mutate Agent/Cosmic state? | Rule |
|---|---:|---|
| Executable capability | Yes | Only through its declared command and held resource leases |
| Cosmic integration gateway | Yes | Adapts a capability operation to server internals |
| Plan executor / step orchestration | No | Starts, ticks, suspends, resumes, or cancels capability commands |
| Policy / personality | No | Returns scored or versioned decisions |
| Foreground arbiter / scheduler | No | Selects ownership and supplies time/budget |
| Memory / coordination / interaction session | No | Stores immutable facts and protocol state |
| LLM provider/gateway | No | Returns dialogue text only |

## Audited direct mutation signatures

The architecture gate scans orchestration roots for direct calls including
`setPosition`, `changeMap`, `gainItem`, `setHp`, `setMp`, `setJob`, `setLevel`,
`addItem`, `removeItem`, and `updateSingleStat`. Any required new mechanical
operation must first become an executable capability command or integration
gateway operation, declare its resource set, and add terminal cleanup tests.

Existing low-level capability and integration packages remain the migration
surface. This milestone prevents new orchestration mutations from being added;
future slices can shrink the existing low-level gateway allow-list without
changing plan, policy, personality, memory, TownLife, or LLM contracts.
