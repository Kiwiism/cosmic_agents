# Non-Agent Live Gameplay Test Results

Runbook: `docs/SERVER_LIVE_GAMEPLAY_TEST_RUNBOOK.md`
Branch: `port/nutnnut-agent-correctness-population`
Build commit observed after evidence submission: `a0f1fc640e8d25ee933567387351cbf2aae30a74`
Date: 2026-07-11 (Asia/Singapore)

The commit should be confirmed by the tester if the port advanced between
starting the server and submitting the screenshots.

## Batch 1

### PRE-01 - Clean startup: PASS

Evidence:

- Database pool started successfully in 948 ms.
- World 0 started.
- Channels 1, 2, and 3 listened on ports 7575, 7576, and 7577.
- Login listener started on port 8484.
- Server reported online after 9,468 ms.
- Database Console bridge listened on `127.0.0.1:8787`.
- Character `Kiwi` authenticated and entered world 0, channel 1.
- No startup exception, listener duplication, DB wait, or executor rejection
  was visible.

Notes:

- Runtime-feature warnings for tradeable untradeables, disabled one-of-a-kind
  checks, and max inventory normalization reflect active configuration.
- The first map-growth warning established the loaded-map high watermark and
  is not by itself a failure.
- Dressing Room completed loading after the server became available; this was
  not observed to block login.

### PRE-02 - Baseline health snapshot: PASS

Initial snapshot:

```text
players=1
maps loaded/active/idle-candidates=244/10/0
heap=393/8032 MB
DB active/idle/total/waiting=0/10/10/0
ThreadManager rejected: general=0, blocking=0, database=0
load=NORMAL
runtime failures=0
EXP logger persisted/dropped/failures=0/0/0
NPC pending/conversations/scripts/quest actions/quest scripts=0/0/0/0/0
transition buff/disease characters=0/0
families/messengers/player shops/merchants=0/0/0/0
login attempts/bypass/in-login-state=0/0/0
map unloads=0
character saves/failures=0/0
broadcast slow count=0
```

`accountViews=1` and `storages=1` are consistent with the one authenticated
character and are not leak signals at this point.

### AUTH-01 - Repeated logout/relogin: PASS

Observed behavior:

- Repeated login-server and channel-server connections completed successfully.
- Five requested relogin cycles plus the surrounding session produced six
  completed `LOGOUT` save checkpoints.
- No `already logged in` condition, failed login, exception, or manual DB
  cleanup was reported.
- Character inventory remained populated after the cycles; mesos displayed as
  `103,168,623`.
- HP remained `30,142/30,000`. MP moved from approximately `27,030` to `27,069`,
  which is consistent with normal in-map MP recovery rather than persistence
  loss.

Final health evidence:

```text
players=1
maps loaded/active/idle-candidates=245/7/0
heap=426/8032 MB
DB active/idle/total/waiting=0/10/10/0
ThreadManager rejected: general=0, blocking=0, database=0
load=NORMAL
runtime failures=0
EXP logger dropped/failures=0/0
NPC and quest runtime caches=0
transition buff/disease characters=0/0
families/messengers/player shops/merchants=0/0/0/0
login attempts/bypass/in-login-state=0/0/0
character saves total/failed/manual/autosave=6/0/6/0
save reason LOGOUT=6
save latency average/max=236/493 ms
map dormant ticks skipped=1264
map unloads=0
broadcast slow count=0
```

All six full logout saves wrote the expected sections, including skills,
inventory, quests, character row, keymap, locations, family/cash/storage, and
skill macros. No save exceeded the configured 1,000 ms warning threshold.

Heap increased by 33 MB and loaded maps by one during active login/map use.
This is a short functional sample, not evidence of a leak; both values remain
baseline observations for later Batch 1 and soak comparisons.

### AUTH-02 - Two simultaneous accounts: PASS

Evidence:

- `Kiwi` and `agent123` were present together in the same Henesys channel.
- Both clients rendered the other character and observed movement/chat.
- The server-health snapshot reported `players=2`.
- The ordinary character disappeared and re-entered without duplication.
- No player-shop, merchant, messenger, transition-buff, login-state, or NPC
  runtime entry remained unexpectedly active.

### AUTH-03 - Incorrect password and duplicate session: PASS (tester reported)

The tester completed the invalid-password and duplicate-account sequence and
continued using the original and subsequent valid sessions. The submitted log
shows later login/channel admission succeeding without a stuck account or
exception. No manual DB reset was reported.

### AUTH-04 - PIC/PIN routes: PASS (tester reported)

Enabled authentication routes completed and the character continued into one
world session. No duplicate character or stuck login-state entry appeared in
the later health snapshots. Any disabled route is outside this run's configured
surface rather than a failure.

### AUTH-05 - Abrupt disconnect recovery: PASS

Evidence:

- `agent123` was saved and later admitted through login and channel listeners
  after abrupt-disconnect scenarios.
- No `already logged in` state or manual DB cleanup was reported.
- Final `login attempts`, `bypass`, and `inLoginState` counts were all zero.
- NPC conversations/scripts/actions were all zero after the test.

Observation:

```text
Rejected invalid NPC response account=22 type=0 action=-1 selection=-1
```

This occurred around the NPC interruption test. The server rejected the stale
or invalid response and continued normally; it is evidence of the prompt guard
rather than a functional failure. Revisit only if ordinary NPC Cancel reliably
produces the same warning without an interrupted/stale conversation.

### MAP-01 - Same-map movement broadcast: PASS

Both clients rendered each other in Henesys. Submitted views show the ordinary
character at different positions while both clients remained synchronized.
Walking, jumping, sitting/chat presence, and equipment/presence updates were
reported working without a broadcast warning or exception.

### MAP-02 - Portal and multi-map travel: PASS

The submitted route covered town, field, indoor, training/PQ-office, and
multi-platform maps. Visible map evidence includes Perion (`102000000`), Perion
Weapon Store (`102000001`), Spiegelmann's Office (`980000000`), Ellinia
(`101000000`), and forest maps including `100040100`, plus the intervening
portal route. Taxi charges of 1,000 and 800 mesos were applied during travel.
No blank map, invalid destination, or portal exception was reported.

### MAP-03 - Channel-change round trip: PASS

Evidence:

- Logs show both test characters saving and connecting through channels 2 and
  3 before returning.
- Final save reasons include `SERVER_TRANSITION=4`.
- All four transition checkpoints completed; save failures remained zero.
- Both players remained online and usable after the round trip.
- Transition buff/disease cache counts returned to zero.

### MAP-04 - Death and respawn: PASS (tester reported)

The ordinary character reached the normal client death/revive prompt in a
monster field. The subsequent test sequence continued with valid authenticated
characters and no stuck player, map, or login state. No death-path exception
was visible in the submitted server log.

### MAP-05 - Dormant map wake-up: PASS

Evidence:

- Dormant ticks increased from approximately `9,162` to `10,923` while the
  quiet-map test proceeded.
- On return, monsters were present, animated, targetable, and killable.
- Quest kill progress advanced from 5/20 to 6/20 after re-entry, proving that
  combat and kill processing resumed.
- `unloaded=0`, consistent with idle map unloading remaining disabled.
- No map-update warning, frozen map, missing spawn population, or exception was
  reported.

### Batch 1 Final Health: PASS

```text
players=2
maps loaded/active/idle-candidates=258/8/0
heap=377/8032 MB
DB active/idle/total/waiting=0/10/10/0
ThreadManager rejected: general=0, blocking=0, database=0
load=NORMAL
runtime failures=0
EXP logger dropped/failures=0/0
NPC and quest runtime caches=0
transition buff/disease characters=0/0
families/messengers/player shops/merchants=0/0/0/0
login attempts/bypass/in-login-state=0/0/0
character saves total/failed/manual/autosave=13/0/13/0
save reasons LOGOUT=9, SERVER_TRANSITION=4
save latency average/max=149/493 ms
map dormant ticks skipped=10923
map unloads=0
broadcast slow count=0
```

The loaded-map increase from 244 to 258 is explained by the deliberate
multi-map route. Heap ended below the earlier AUTH-01 snapshot. Neither value
suggests a leak in this short run.

World account-view and storage caches ended at three while two players were
online. These are per-account world caches, and the run exercised more than two
account/login paths. Treat three as the current high-water observation and
monitor whether it rises monotonically across many unique-account
login/logout cycles; it is not a Batch 1 failure by itself.

## Next Tests

Batch 2:

```text
COMBAT-01 through COMBAT-05
DROP-01 through DROP-03
INV-01 through INV-05
```

## Batch 2 Findings

### HPMP-CAP-01 - Effective HP/MP exceeds displayed maximum: FIXED, RETEST PENDING

Observed client state:

```text
HP 30,976 / 30,000
MP 30,668 / 30,000
```

The matching HP and MP excesses strongly indicate equipment HP/MP bonuses over
a base pool already at 30,000. This is not an unconstrained heal: server
`setHp`/`setMp` clamp against `localmaxhp`/`localmaxmp`, and local maxima include
equipment bonuses before being capped at 300,000. The client-facing/base maxima
remain 30,000 in the current v83 client display.

Relevant implementation path:

```text
Character.reapplyLocalStats
  -> base maxhp/maxmp
  -> recalcEquipStats
  -> add equipmaxhp/equipmaxmp
  -> clamp localmaxhp/localmaxmp to 300,000

AbstractCharacterObject.setHp/setMp
  -> clamp current values against localmaxhp/localmaxmp
```

Impact:

- The health and mana bars can show current values above their displayed maxima.
- Players cannot reliably judge their true maximum pools.
- Potion, autopot, percent-HP logic, party HP display, and HP/MP buffs may use a
  different effective maximum than the client communicates.
- This is especially visible when base HP/MP is already at the v83 client cap
  and equipment adds more.

Implemented server policy:

- `PLAYER_HP_MP_CAP` now defines the shared base and final effective cap.
- The default remains 30,000 for the current v83 client.
- Equipment and Hyper Body cannot raise the effective pool above that cap.
- Current HP/MP, full healing, AP assignment, ratio updates, and `!maxstat`
  now use the same policy.
- Values above 30,000 remain opt-in and require a compatible client patch.

Live retest: restart the server, enter with the same equipment, run a full heal,
and confirm both current and displayed maximum HP/MP remain at or below 30,000.

Additional confirmation requested during Batch 2:

1. Record the HP and MP bonuses on all equipped items.
2. Verify whether they total `+976 HP` and `+668 MP`.
3. Unequip one HP/MP item and observe both current and displayed maximum.
4. Take damage, heal to full, relog, and report the resulting numerator and
   denominator.

### INV-05 - NPC sale at maximum mesos consumes item without payment: FIXED, RETEST PENDING

Observed:

- Character mesos displayed `2,147,483,647`.
- The NPC shop still accepted an item sale.
- The shop continued to display the character at the signed-int meso maximum.

Confirmed implementation behavior:

```text
Shop.sell
  -> validate item and quantity
  -> removeFromSlot
  -> calculate sale proceeds
  -> Character.gainMeso
       -> clamp overflow down to zero gain at Integer.MAX_VALUE
```

This prevents integer wraparound, but the validation occurs too late to
protect the sold item. At the cap, the item is removed and the player receives
zero mesos. This is a player-facing loss-of-value bug rather than a meso
duplication exploit.

Implemented correction:

1. Sale proceeds are calculated before inventory mutation.
2. `Character.canHoldMeso(recvMesos)` rejects proceeds that exceed the cap.
3. Rejected sales retain the item and meso balance, return the failed-sale shop
   response, and display `You cannot carry any more mesos.`
4. A focused boundary-policy regression test covers the rejected proceeds path.

Live retest: restart the server, set mesos to `2,147,483,647`, attempt to sell
one item, and confirm the item remains in its original slot and mesos remain
unchanged. Then lower mesos by exactly the sale price and confirm the sale
succeeds and reaches, but does not exceed, the cap.

Additional live confirmation requested:

1. Verify that the sold item or quantity actually disappeared.
2. Verify mesos remained exactly `2,147,483,647`.
3. Relog and confirm both the missing item and capped mesos persisted.

### Batch 2 Intermediate Health Check: PASS

Captured after Cash Shop activity, spawning, meso-boundary operations, and the
maximum-meso NPC sale test:

```text
players=1
maps loaded/active/idle-candidates=264/5/238
heap=295/8032 MB
DB active/idle/total/waiting=0/10/10/0
ThreadManager rejected: general=0, blocking=0, database=0
load=NORMAL
runtime failures=0
EXP logger dropped/failures=0/0
NPC, quest, login, and transition runtime caches=0
families/messengers/player shops/merchants=0/0/0/0
character saves total/failed/manual/autosave=18/0/15/3
save reasons AUTO_SAVE=1, CASHSHOP=1, LOGOUT=10, MERCHANT=2, SERVER_TRANSITION=4
save latency average/max=146/493 ms
map dormant ticks skipped=39313
map unloads=0
broadcasts total/slow=35985/0
```

The loaded-map count reflects the deliberate travel tests; 238 maps are now
recognized as idle candidates. Heap is below earlier Batch 1 samples. No
secondary runtime instability accompanied the NPC-sale correctness failure.
