# Host Memory Preflight Guard - 2026-07-15

## Result

`PASS`

The read-only Agent scheduler live-gate preflight now prevents a 1,000-Agent
or larger stage from starting unless at least 8 GiB of physical memory is free.
An operator may request a stricter floor, but the effective minimum is always
the greater of the stage floor and the explicit value.

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

A 500-Agent preflight with no explicit value reported stage and effective
floors of zero and returned `host:memory` as passing. Smaller stages therefore
retain their previous admission behavior.

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
