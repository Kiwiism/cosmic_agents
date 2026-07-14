# Host Memory Preflight Guard - 2026-07-15

## Result

`PASS`

The read-only Agent scheduler live-gate preflight now prevents a 1,000-Agent
or larger stage from starting unless at least 8 GiB of physical memory is free.
An operator may request a stricter floor, but the effective minimum is always
the greater of the stage floor and the explicit value.

A follow-up 250-Agent rollback preflight observed host free memory fall to
1.36 GiB after the original checks passed. The server was not started. The
policy was therefore tightened to require 2 GiB for a basic live gate and
4 GiB for populated stages below 1,000 Agents. The 8 GiB floor for 1,000+
Agents remains unchanged.

The report exposes:

- `stageMinimumFreePhysicalMemoryGiB`;
- `minimumFreePhysicalMemoryGiB`;
- `freePhysicalMemoryGiB`;
- `totalVisibleMemoryGiB`;
- the `host:memory` check and failure id.

## Focused Verification

PowerShell parsing reported zero syntax errors.

A 1,000-Agent preflight with an explicit 4 GiB value retained an 8 GiB
effective floor and returned `host:memory` as a failure while the host was
below that threshold. This proves the stage requirement cannot be weakened by
the optional argument.

A follow-up threshold test verifies stage floors of 2 GiB without a managed
population, 4 GiB below 1,000 Agents, and 8 GiB at 1,000 Agents. Explicit
values are combined using the greater value and cannot weaken those floors.

Both invocations used the normal preflight's read-only JSON path. They did not
start a server, connect to the database, create runtime data, or alter
configuration.

The repository also passed:

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q -DskipTests package
```

## Runtime Behavior

No Agent scheduler, gameplay, persistence, schema, WZ, or production-default
behavior changed. The guard affects only operator admission before a live
soak process is started.
