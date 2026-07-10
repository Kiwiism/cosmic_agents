# Remaining Server Work Completion Matrix

Audit date: 2026-07-11

Scope: non-Agent Cosmic server only. Agent reconstruction sources/tests and
`docs/agents` were not edited by this completion pass.

| Item | Status | Evidence / changed area | Residual risk and revisit condition |
| --- | --- | --- | --- |
| Dirty-section save selection, failure retry, and in-flight mutation | Implemented | `Character`, `DirtySectionTracker`; focused persistence suite and 2,012-test non-Agent run | Authenticated relogin matrix remains manual |
| Full save-route checkpoints | Implemented, manual runtime gate | Logout, transition, Cash Shop, MTS, save-all, world-warp reason routing exists; exact checklist in soak doc | Exercise every route with two v83 clients |
| `Pet.setUniqueId` mutation | Removed | No source/test caller found; production compile passes | Add only with an explicit identity migration design |
| Fredrick successful retrieval | Implemented | One transaction for inventory, age-adjusted mesos, merchant/equipment rows, and reminder; live rollback recovery | Test disconnect timing and full inventory in client |
| Merchant persistence snapshot | Implemented | Merchant rows and `fredstorage` written in one transaction | Client merchant open/close/restart smoke remains manual |
| Merchant duplicate/orphan cleanup | Implemented | Identity-checked world/channel removal and force-close cleanup | Monitor merchant counts during soak |
| Merchant load query pressure | Implemented | Joined item/bundle query replaces `1 + N`; real-MySQL test | No timing percentage claimed |
| Character deletion transaction | Implemented | DB transaction plus post-commit memory cleanup tests | Rich-character deletion is a manual gameplay gate |
| Messenger lifecycle | Implemented | Empty registry removal; synchronized immutable snapshots; focused test | Watch messenger count return to baseline |
| World/runtime cache ownership | Implemented diagnostics; ongoing soak gate | `!serverhealth` world and transition-cache counts; cache audit table | Add cleanup only for a reproduced monotonically growing owner cache |
| Idle-map dormant skipping | Implemented | Existing lifecycle tests and counters | Compare active gameplay under soak |
| Idle-map unloading | Evidence-gated, default off | `cosmic.maps.idleUnloadEnabled=false` default; canary checklist | Enable only after all map-class canaries pass |
| DB index migrations | Evidence-rejected for now | Schema review has no slow query or `EXPLAIN` proof | Revisit when diagnostics identify a query/table |
| Save batching beyond current snapshots | Evidence-rejected for now | No measured section hotspot in available logs | Revisit from section latency and DB-wait samples |
| Packet-byte/broadcast reuse | Evidence-rejected for now | Available logs contain no measured hot packet path | Revisit with packet identity and recipient evidence |
| Runtime raw logging cleanup | Implemented for runtime | Scan leaves `printStackTrace`/stdout only in offline `tools.mapletools` utilities | Revisit new runtime call sites only |
| Timer shutdown ownership | Implemented | All timer lanes are interrupted, then awaited under one 10-second deadline | Driver/native work may ignore interruption; check shutdown logs |
| Partial startup cleanup | Implemented | Failed world/channel setup releases world resources; absent login listener is null-safe | Repeat occupied-port smoke after future startup changes |
| Java build and non-Agent tests | Passed | 2,012 tests, zero failures/errors/skips; Java 21 package passes | Re-run after later merges |
| Real MySQL merchant tests | Passed | Explicit opt-in fixture tests prove load, validation, commit, and rollback | Does not replace full character relogin |
| Packaged startup | Passed | Login, three channels, and admin bridge ready in 8.6 seconds | Graceful authenticated shutdown remains manual |
| 24h/72h/7d/30d soak | Manual gate | Metrics, failure signals, controls, and exact commands documented | Required on deployment hardware before scale claims |

## Exact Automated Verification

```powershell
$root=(Resolve-Path 'src/test/java').Path
$tests=Get-ChildItem $root -Recurse -Filter '*Test.java' |
  Where-Object { $_.FullName -notmatch '\\server\\agents\\' -and $_.FullName -notmatch '\\server\\bots\\' } |
  ForEach-Object { $_.FullName.Substring($root.Length+1).Replace('\','.').Replace('.java','') }
.\mvnw.cmd -q ('-Dtest='+($tests -join ',')) test

.\mvnw.cmd -q "-Dcosmic.test.mysql=true" `
  "-Dtest=client.inventory.ItemFactoryMerchantMySqlIntegrationTest,client.processor.npc.FredrickRetrievalMySqlIntegrationTest" test

.\mvnw.cmd -q -DskipTests package
git diff --check
```

No performance percentage is claimed. The only optimization count proven
without a profiler is merchant loading changing from `1 + N` SQL statements to
one joined statement. All other optimization decisions remain tied to runtime
save, broadcast, DB-pool, executor, heap, map, and cache evidence.
