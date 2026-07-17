# Production JVM Guardrails

The checked-in Windows and Docker launch paths enable the same bounded
diagnostics and fatal-error behavior:

- heap dump on `OutOfMemoryError`;
- process exit after `OutOfMemoryError`, allowing the service manager to restart it;
- fatal JVM error logs under `logs/crash`;
- GC and safepoint logs under `logs/gc`, capped at ten 20 MB files;
- heap dumps under `logs/heapdumps`;
- an in-process disk-space check every five minutes;
- refusal of manual `!heapdump` requests that would consume the critical disk reserve.

`launch.bat` creates the output directories and accepts additional JVM options
through `JAVA_OPTS`. Docker Compose persists `logs` and uses
`restart: unless-stopped`.

When running from IntelliJ, copy these VM options into the Server run
configuration so it has the same crash behavior:

```text
-Xmx2048m
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=logs/heapdumps
-XX:+ExitOnOutOfMemoryError
-XX:ErrorFile=logs/crash/hs_err_pid%p.log
-Xlog:gc*,safepoint:file=logs/gc/gc.log:time,uptime,level,tags:filecount=10,filesize=20M
-Dwz-path=wz
```

Startup logs and `!serverhealth` warn when heap-dump-on-OOM, exit-on-OOM, or
bounded GC logging is absent, making an unguarded IntelliJ run visible.

Disk thresholds are runtime settings, expressed in bytes:

- `cosmic.disk.path` / `COSMIC_DISK_PATH`, default `.`
- `cosmic.disk.warnFreeBytes` / `COSMIC_DISK_WARN_FREE_BYTES`, default 10 GiB
- `cosmic.disk.criticalFreeBytes` / `COSMIC_DISK_CRITICAL_FREE_BYTES`, default 2 GiB

Disk state appears in `!serverhealth`, the periodic scale-health log, and the
internal admin health response. A manual heap dump must leave the configured
critical reserve after allowing for a dump as large as the maximum JVM heap.

Heap dumps can contain account, character, chat, and runtime data. Restrict
access to `logs/heapdumps` and delete dumps after analysis.
