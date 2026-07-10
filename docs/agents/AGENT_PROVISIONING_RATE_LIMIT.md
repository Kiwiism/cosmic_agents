# Agent Provisioning Rate Limit

Backing-account creation reserves a provisioning attempt atomically before any
account or character write. The default permits three attempts per controller
within ten minutes and prevents concurrent requests from bypassing that limit.

Expired attempt timestamps and empty controller buckets are removed during the
next provisioning check. Spawning an existing Agent does not enter this path.

Configuration remains available through:

```text
agents.provisioning.maxPerWindow
agents.provisioning.windowMs
```

Rollback is the preceding Phase 9 provisioning-policy commit. This hardening
intentionally affects only concurrent or excessive backing-account creation.
