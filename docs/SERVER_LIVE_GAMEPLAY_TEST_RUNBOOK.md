# Non-Agent Live Gameplay Test Runbook

Status: ready for manual execution
Scope: Cosmic server and normal player behavior only
Excluded: Agent spawning, Agent decisions, Agent capabilities, and Nutnnut bot behavior

This runbook turns the existing server hardening, persistence, and soak-test
checklists into repeatable live-client tests. It is intentionally documentation
only and does not change runtime behavior while
`port/nutnnut-agent-correctness-population` is active.

Related evidence and implementation notes:

- `docs/SOAK_TEST_CHECKLIST.md`
- `docs/SERVER_HARDENING_2026_07_IMPLEMENTATION.md`
- `docs/SERVER_HARDENING_DIAGNOSTICS.md`
- `docs/SERVER_REMAINING_COMPLETION_MATRIX.md`

## 1. Test Rules

### Accounts and clients

Prepare these disposable test identities:

| Identity | Purpose |
| --- | --- |
| `GM-A` | GM level 6; setup commands and `!serverhealth` |
| `PLAYER-B` | Ordinary player; validates real restrictions and two-client behavior |
| `PLAYER-C` | Optional ordinary player; party, guild, trade, messenger, and merchant contention |

Use test characters whose inventory, quests, buddy list, storage, and merchant
state may safely be changed. Do not run destructive tests on a valued character.

### Evidence to report for every test

Report results using this shape:

```text
Test ID:
Build commit:
Date/time and timezone:
Characters/channels/maps:
Steps completed:
Client-visible result:
Relevant chat/console/server-log text:
Relogin/restart result, if required:
PASS / FAIL / BLOCKED:
Screenshot or log excerpt:
```

Do not report only "worked." For persistence tests, write down the exact value
before saving and the exact value after relogin. For two-client tests, report
what both clients saw.

### Result meanings

- **PASS**: every expected client result and persistence checkpoint occurred.
- **FAIL**: an expected result was wrong, state was lost, an exception appeared,
  an account became stuck online, or manual DB repair was required.
- **BLOCKED**: the content or client feature is unavailable; explain why.
- **INCONCLUSIVE**: evidence was not captured or an unrelated interruption occurred.

### Stable GM commands used here

Run commands from `GM-A` unless a step says otherwise.

```text
!serverhealth
!saveall
!warp <map id or map name>
!spawn <mob id> [quantity]
!item <item id or item name> [quantity]
!startquest <quest id>
!completequest <quest id>
!resetquest <quest id>
!whereami
!pos
!online
!inmap
!shutdown
```

Useful stable fixtures:

```text
Henesys                 100000000
Free Market Entrance    910000000
Slime                    100100
Red Potion              2000000
Blue Snail Shell        4000000
Store Remote Controller 5470000
Fredrick NPC            9030000
```

If a fixture differs in the current WZ data, use `!search`, `!id`, or a known
equivalent and report the replacement ID.

## 2. Required Preflight

### PRE-01 Build identity and clean startup

1. Record `git rev-parse HEAD` from the server checkout.
2. Start MySQL and the packaged server normally.
3. Wait for login and every configured channel listener to report ready.
4. Log in `GM-A` and run `!serverhealth`.
5. Save the complete command output and startup log from process start through
   the first successful character entry.

Pass when all listeners start once, the character enters a map, load is
`NORMAL`, DB waiting is zero, and executor rejected counts are zero.

### PRE-02 Baseline health snapshot

Run `!serverhealth` and report at least:

```text
players and maps loaded/active/idle-candidate
heap used/max
DB active/idle/total/waiting
ThreadManager active/queued/completed/rejected by lane
Timer lane active/queued/completed
character save totals/failures/reasons/sections
broadcast totals/slow/max
world/cache/merchant/messenger/buff counts
load level
```

Repeat after each major batch. Any increasing save failure, DB waiter, rejected
task, stuck merchant, or stuck cache count is a failure signal even when the
visible gameplay passed.

## 3. Login, Session, and Character Lifecycle

### AUTH-01 Normal login/logout/relogin

1. Log `GM-A` into channel 1 and note map, HP, MP, and mesos.
2. Log out to character select, then to login.
3. Immediately log back into the same character.
4. Repeat five times, including one full client close.
5. Run `!serverhealth` before and after.

Pass when every login succeeds, no "already logged in" state remains, the
character state is unchanged, and login/session cache counts return to baseline.

### AUTH-02 Two simultaneous accounts

1. Log in `GM-A` and `PLAYER-B` at the same time.
2. Put both in Henesys channel 1.
3. Confirm each sees the other move, jump, sit, chat, and change equipment.
4. Log out only `PLAYER-B`; confirm `GM-A` sees the removal.
5. Immediately relog `PLAYER-B`.

Pass when both sessions remain independent and presence disappears/reappears
without stale characters or duplicate entries.

### AUTH-03 Incorrect password and duplicate session

1. Attempt one incorrect password for the disposable account, then log in correctly.
2. While `PLAYER-B` is online, attempt to log into the same account from another client.
3. Close the second attempt and verify the original session still works.
4. Properly log out the original, then log in from the second client.

Pass when invalid/duplicate access is rejected without disconnecting or wedging
the valid session.

### AUTH-04 Character select and PIC/PIN routes

Exercise every enabled route: normal character select, PIC/PIN registration,
wrong PIC/PIN, and View All Characters if enabled. Pass when invalid entries
are rejected and valid entries enter exactly one world/channel session.

### AUTH-05 Disconnect recovery

1. Enter the world with `PLAYER-B`.
2. Close the client process without logging out.
3. Wait 15 seconds and attempt relogin.
4. Repeat once while changing maps and once while an NPC dialogue is open.

Pass when relogin succeeds without DB cleanup and pending NPC/login state
returns to its pre-test baseline.

## 4. Map, Movement, Portal, and Channel Tests

### MAP-01 Same-map movement broadcast

With two clients in Henesys, walk, jump, climb a rope/ladder, drop through a
platform, sit, and use a portal. Both clients must see plausible movement and
the final positions must agree.

### MAP-02 Portal and multi-map travel

Travel through at least ten real portals across Victoria Island without using
GM warp. Include town, field, indoor, return-scroll, taxi, and one scripted
transport if available. Report every source/destination map ID using
`!whereami`. Pass when portals lead to the expected destination and return
travel works.

### MAP-03 Channel change round trip

1. Put both clients in the same map/channel.
2. Move `PLAYER-B` channel 1 -> 2 -> 3 -> 1.
3. Confirm disappearance and reappearance from `GM-A`.
4. Verify HP/MP, buffs, inventory, party, and map remain correct.
5. Run `!serverhealth`.

### MAP-04 Death and respawn

Allow `PLAYER-B` to die normally. Confirm death animation, EXP effect, return
map, HP restoration, active buff cleanup, and later relogin. Repeat in a field
with a non-default return map.

### MAP-05 Dormant map wake-up

1. Enter a quiet field and record mob/reactor state.
2. Leave it empty for longer than `cosmic.maps.dormantSkipMillis` (default 60 s).
3. Run `!serverhealth` and record dormant tick counters.
4. Re-enter normally and kill a mob.

Pass when mobs resume normal behavior/respawn and no map is blank or frozen.
Idle map unloading remains a separate opt-in canary and must not be enabled for
this baseline.

## 5. Combat, Skills, Buffs, Death, and Drops

### COMBAT-01 Basic attack and kill ownership

1. `!warp 100000000`, then move to a safe clear location.
2. Run `!spawn 100100 3`.
3. Kill one with a normal attack and one with a job skill.
4. Have `PLAYER-B` hit the third first, then let `GM-A` finish it.

Report damage, EXP recipient(s), kill credit, loot ownership, and exceptions.

### COMBAT-02 Physical and magic skill matrix

On representative characters, test at least one single-target and one
multi-target skill for warrior, magician, bowman, thief, and pirate families.
For each: record MP/ammo consumption, displayed animation, target count, damage,
cooldown if applicable, and whether nearby clients see it.

### COMBAT-03 Buff matrix

Test self, party, and map-visible buffs including Haste, Booster, Rage or an
equivalent party attack buff, Magic Guard, Invincible/damage reduction, Meso
Guard, Hyper Body, Bless, Shadow Partner, and stance/summon where available.
For each buff record:

```text
skill and level
before/after client stat or movement behavior
buff icon and duration
effect seen by second client
damage/HP/MP/meso delta where relevant
effect after map change, channel change, Cash Shop, and relogin
```

Pass only when the mechanical effect occurs; animation alone is insufficient.

### COMBAT-04 Debuff and abnormal status

Receive poison, darkness, weakness, seal, stun, slow, and curse where practical.
Verify icon, restriction/effect, expiry or cure, death behavior, and map/channel
transition cleanup. Report blocked statuses separately.

### COMBAT-05 Summons and pets during transitions

Cast a combat summon, change map/channel, enter/leave Cash Shop, then dismiss
it. Confirm no duplicate/stale summon and that summon damage/buffs work after
return.

### DROP-01 Item and meso drops

1. Spawn and kill ten low-level mobs normally.
2. Record meso and item drops, ownership timeout, pickup by owner, and pickup by
   the other player before/after ownership expiry.
3. Confirm stack quantities and mesos.
4. Run `!saveall`, relog, and verify both.

### DROP-02 Full inventory behavior

Fill the relevant inventory category, then kill mobs until an item of that
category drops. Attempt pickup manually and with a pet if available. Pass when
pickup is refused cleanly, the floor item remains as expected, no item is lost,
and no exception is logged.

### DROP-03 Quest-gated drop

Choose one known quest-gated drop. Compare kills before quest start, while the
quest is active, and after completion/reset. Record quest ID, mob ID, item ID,
and observed drop eligibility.

## 6. Inventory, Equipment, Mesos, and Item Semantics

### INV-01 Stack split/merge/drop/pickup

Use `!item 2000000 20`. Split the stack, merge it, drop part, pick it up, use
several, and trade part to `PLAYER-B`. Record each quantity. Save/relog and
verify the final exact quantity.

### INV-02 Equipment round trip

Obtain a non-cash equip, record every stat, upgrade slot, owner, flags, and
expiration. Equip/unequip it, change channel, `!saveall`, relog, and restart the
server. Pass when item identity, slot, and every persisted field remain exact.

### INV-03 Scroll success/failure and slot use

Apply one successful and one failed scroll using normal client behavior. Record
before/after stats, remaining slots, successful upgrade count, and item name
suffix. Save/relog and verify the result.

### INV-04 Cash and untradeable item restrictions

Try storage, trade, drop, merchant listing, and account transfer for cash,
untradeable, one-of-a-kind, and rechargeable items. Expected behavior must
match current configuration. Report config-sensitive results instead of
assuming Cosmic defaults.

### INV-05 Meso arithmetic boundaries

Buy, sell, trade, store, withdraw, and merchant-settle mesos around ordinary
values. Also test an operation that would exceed the current signed-int meso
limit. Pass when valid arithmetic is exact and overflow is rejected without
wrapping negative or duplicating mesos.

## 7. Persistence Matrix

Use one unique sentinel value per row. After mutation run `!serverhealth`, note
save counts, then perform the listed checkpoint and verify after relogin.

| ID | State to mutate | Suggested sentinel | Checkpoint routes |
| --- | --- | --- | --- |
| SAVE-01 | Stats/AP/SP/HP/MP/fame | unusual valid values | `!saveall`, logout |
| SAVE-02 | Inventory stack | Red Potion quantity 17 | autosave, logout |
| SAVE-03 | Equipped item | distinctive clean equip | channel change, restart |
| SAVE-04 | Skill level and unused SP | one non-max skill | Cash Shop, logout |
| SAVE-05 | Quest status/progress | known kill/item quest | autosave, logout |
| SAVE-06 | Monster Book | one card count | `!saveall`, restart |
| SAVE-07 | Key binding/quickslot/macro | unique layout | channel change, logout |
| SAVE-08 | Buddy capacity/group/visibility | group `LIVE_TEST` | autosave, logout |
| SAVE-09 | Saved locations/rock lists | three known maps | Cash Shop, logout |
| SAVE-10 | Pet state/ignores | name, closeness, fullness | map/channel/relogin |
| SAVE-11 | Mount | EXP, level, tiredness | autosave, relogin |
| SAVE-12 | Storage | item, slot count, mesos | close storage, restart |
| SAVE-13 | Cash Shop | NX, wishlist, CS inventory | exit CS, relogin |
| SAVE-14 | Family/event/area data | visible valid change | logout, restart |

For each row capture `!serverhealth` before mutation, after the checkpoint, and
after relogin. Pass when save failures remain zero and the relevant section
write count increases. A clean later autosave should increase the skipped-clean
counter rather than rewriting unrelated sections.

### SAVE-15 All full-checkpoint routes

Exercise these separately and report the save-reason delta after each:

```text
logout
channel/server transition
Cash Shop entry/exit
MTS entry/exit, if enabled
hired merchant save/close
!saveall
!warpworld, only on a disposable multi-world setup
graceful shutdown
```

### SAVE-16 Mutation during autosave

This is an evidence-gated advanced test. Only run when a naturally slow save is
visible in logs; do not inject delays into the port branch. Change a second
field while the save is in progress, then wait for the next autosave and
relogin. Pass when the later mutation survives.

### SAVE-17 SQL failure recovery

Do not sabotage the shared database. Run only in an isolated DB clone by
temporarily denying/failing one save transaction, then restoring DB access.
Verify the character remains usable and the next save persists the mutation.
Capture the contextual error and failed-save counter. Otherwise mark BLOCKED.

## 8. NPC, Quest, Shop, Storage, Cash Shop, and Scripts

### NPC-01 Dialogue types and replay protection

Open NPCs that use OK, Next/Prev, Yes/No, menu selection, text entry, number
entry, and style selection. Give valid and invalid values where the client
allows. Double-click responses and reopen after cancel/map change.

Pass when valid replies advance once, invalid/replayed replies do not execute
twice, and no conversation remains stuck. Check pending NPC/conversation cache
counts before and after.

### QUEST-01 Normal quest lifecycle

Use a reachable quest with prerequisite, kill or item progress, and reward:

1. Verify it is unavailable before requirements.
2. Meet requirements and start through the real NPC.
3. Advance progress naturally.
4. `!saveall`, relog, and verify exact progress.
5. Complete through the real NPC and verify EXP/items/mesos.
6. Relog and confirm completion.

Use `!startquest`, `!completequest`, and `!resetquest` only to reset a disposable
fixture or compare forced state; they do not replace the real-NPC path.

### QUEST-02 Forfeit and restart

Start a quest, make partial progress, forfeit in the client, relog, and restart
it. Pass when stale progress is gone and prerequisites/rewards behave normally.

### SHOP-01 NPC buy/sell/recharge

At a normal shop, buy one stackable item, buy an equip, sell part of a stack,
sell the equip, and recharge throwing stars/bullets. Record exact mesos and
quantities before/after. Attempt an invalid/full-inventory purchase. Pass when
all arithmetic and restrictions are correct.

### STORAGE-01 Deposit/withdraw/reorder/mesos

Deposit one equip and one stack, withdraw partial/full stacks, reorder slots,
deposit/withdraw mesos, close, relog, and restart. Verify exact slots, item
metadata, quantities, storage capacity, and mesos.

### CASH-01 Cash Shop transition

Record HP/MP, buffs, mesos, inventory, NX/RP, wishlist, and CS inventory. Enter
and exit Cash Shop, buy/move an item if safe, then relog. Verify state and that
the character is neither duplicated nor stuck online.

### MTS-01 MTS transition

If MTS is enabled, perform the equivalent enter/exit/relogin test and one safe
listing/cancel path. If disabled by design, report BLOCKED rather than enabling it.

### REACTOR-01 Reactor lifecycle

Use a known box/reactor map. Hit/open the reactor, collect its drop, leave and
return after respawn, then repeat after a channel change. Pass when reactor
state, drops, and respawn are correct with no duplicate trigger.

## 9. Player Interaction and Social Systems

### TRADE-01 Player trade commit and cancel

With `GM-A` and `PLAYER-B`, test item-only, meso-only, mixed trade, cancel
before confirm, inventory-full recipient, disconnect during trade, and normal
double-confirm. Record exact before/after inventories and mesos, then relog both.

### PARTY-01 Party lifecycle

Create, invite, accept, reject, change leader, leave, kick, disband, change
channel, and relog. Test party HP visibility and a shared kill. Run
`!serverhealth` after disband; party references must not retain absent players.

### BUDDY-01 Buddy lifecycle

Add, accept, reject, change group, toggle visibility if available, fill buddy
capacity, remove, change channel, and relog. Both clients must agree on online
state and group.

### MSG-01 Messenger lifecycle

Create a messenger, join with two clients, chat, leave/rejoin, disconnect one,
then have the last member leave. Record `!serverhealth` before create and after
final leave. Pass when messenger count returns to baseline.

### GUILD-01 Guild/alliance lifecycle

On disposable guilds: invite/join, rank change, guild chat, leave/kick,
channel change, relog, alliance join/chat/leave if available. Verify member
state and no exceptions during concurrent updates.

### FAMILY-01 Family lifecycle

Create/use a disposable family relationship, gain/spend reputation through a
normal supported action, save/relog, and verify relationship and reputation.

### MINI-01 Omok/match-card room

Create, join, play one round, forfeit/leave, disconnect a visitor, and close the
room. Verify both clients exit cleanly and the room disappears from the map.

## 10. Hired Merchant and Fredrick

There is currently no dedicated safe GM command to age or expire a merchant.
Do not add one during the active port merely for this runbook. Use normal owner
close, graceful shutdown, and an isolated-DB timestamp test for expiry.

### MERCH-01 Create/open/list/close normally

1. Give the owner a valid hired-merchant item through normal means or `!item`.
2. Enter a valid Free Market room and create the merchant.
3. List one stackable item and one equip; record quantity, price, bundle count,
   equip metadata, and owner mesos.
4. Open the store, enter with `PLAYER-B`, buy part of the stack, then leave.
5. Owner closes normally and retrieves remaining items/mesos.
6. `!saveall`, relog both characters, and verify exact state.

Pass when world/channel merchant count rises by one then returns to baseline,
sale arithmetic is exact, and no duplicate/orphan merchant remains.

### MERCH-02 Duplicate and invalid creation

While one merchant exists, try creating another for the same owner, changing
channel, and creating in an invalid map. Pass when attempts are rejected without
destroying or duplicating the original merchant.

### MERCH-03 Remote access

Attempt remote access without item `5470000`, then with it, from another map
and channel. Record whether the configured behavior requires or consumes the
item. Pass when unauthorized access is rejected and authorized access shows the
correct merchant once.

### MERCH-04 Shutdown with merchant online

1. Open a merchant containing known items and leave it active.
2. Capture `!serverhealth`.
3. Run `!shutdown` and save the complete shutdown log.
4. Restart, log in, inspect merchant/Fredrick state, and capture health again.

Pass when shutdown reaches every terminal channel state, restart needs no DB
repair, the merchant is not duplicated, and all goods/mesos are recoverable.

### FRED-01 Normal retrieval

After goods have been placed into Fredrick storage, record free slots, each
stored item, merchant balance, and character mesos. Talk to Fredrick in Free
Market Entrance and retrieve. Relog and restart.

Pass when all items and exact metadata arrive once, net mesos follow the
existing age fee, merchant/reminder rows are gone behaviorally, and a second
retrieval shows nothing rather than duplicating goods.

### FRED-02 Full inventory refusal

Fill one required inventory category before retrieval. Attempt retrieval,
confirm the clean refusal, free space, and retry. Pass when the first attempt
changes nothing and the second transfers everything exactly once.

### FRED-03 Disconnect timing

On a disposable fixture, disconnect immediately after confirming retrieval.
Relog and inspect inventory/Fredrick. Pass when committed items exist exactly
once or the untouched storage remains fully retryable; partial state fails.

### FRED-04 Expiry/age fee isolated test

Only in an isolated DB clone, create real merchant storage, stop the server,
move only that fixture's `fredstorage.timestamp` backward by known day counts,
restart, and retrieve. Test day 0, day 1, a later positive-fee day, future
timestamp, and expiry boundary. Report original balance and exact net result.
Never edit item or merchant rows manually.

## 11. Events, PQs, Bosses, and Timed Content

### EVENT-01 Event instance cleanup

Enter and leave one event normally, repeat by disconnecting, and repeat by
letting it timeout. Compare event-instance and timer counts before and after.

### PQ-01 Party quest lifecycle

With the minimum valid party, test entry requirements, stage progression,
member death/leave, leader disconnect, completion/reward, and re-entry cooldown.
Pass when the instance closes and all members return to valid maps.

### BOSS-01 Expedition lifecycle

Test signup, leader/member entry, duplicate-entry restriction, boss spawn/kill,
member disconnect, completion, and boss-log restriction on disposable data.

### TRANSPORT-01 Timed transport

Board, miss, ride, disconnect/relog during, and arrive using one ferry/ship.
Check timer queues and return maps. Pass when no rider is stranded or duplicated.

## 12. Graceful Shutdown and Restart Gate

### LIFE-01 Loaded shutdown

Before `!shutdown`, arrange:

```text
two authenticated players
different channels
one party or messenger
one open NPC conversation
one active merchant
one active summon/pet
one recently changed inventory/quest field
```

Capture `!serverhealth`, run `!shutdown`, and save the full log. Restart and
relog both accounts. Verify all durable state, merchant/Fredrick recovery,
absence of stuck login state, and terminal shutdown for all channels/listeners.

### LIFE-02 Occupied-port startup failure

In a test environment only, occupy one configured listener port and start the
server. Capture cleanup logs, stop the conflicting process, and start normally.
Pass when partial startup releases resources and the next startup succeeds.

## 13. Short Soak Before Agent Testing

Run a minimum non-Agent baseline of 24 hours before attributing later failures
to Agents.

Every five minutes collect the fields in `docs/SOAK_TEST_CHECKLIST.md`. Run
`!serverhealth` at start, hourly, after each gameplay batch, before shutdown,
and after restart. During the soak repeatedly cycle:

```text
login/logout and abrupt disconnect
channel/map travel
combat, drops, pickup, and death
NPC dialogue and quest progress
shop/storage/Cash Shop
trade/party/buddy/messenger
merchant open/sale/close and Fredrick retrieval
event/PQ enter/exit where available
saveall and clean autosave intervals
```

The baseline fails on any stuck account, continuous unexplained heap/map/cache
growth, repeated DB waiters, increasing executor rejections, save failures,
EXP logger drops/failures, orphan merchant/messenger/event state, or shutdown
that requires manual DB cleanup.

## 14. Recommended Execution Order

Run and report in batches so failures stay diagnosable:

1. `PRE`, `AUTH`, `MAP`
2. `COMBAT`, `DROP`, `INV`
3. `SAVE`
4. `NPC`, `QUEST`, `SHOP`, `STORAGE`, `CASH`, `REACTOR`
5. `TRADE`, `PARTY`, `BUDDY`, `MSG`, `GUILD`, `FAMILY`, `MINI`
6. `MERCH`, `FRED`
7. `EVENT`, `PQ`, `BOSS`, `TRANSPORT`
8. `LIFE`
9. 24-hour non-Agent soak

After the Nutnnut port is complete and this server baseline passes, create a
separate Agent behavior runbook. Do not mix Agent failures into these results.

## 15. First Report Requested

Start with Batch 1 and send back:

```text
PRE-01, PRE-02
AUTH-01 through AUTH-05
MAP-01 through MAP-05
```

Include the first and final `!serverhealth` output, startup log warnings/errors,
and exact map/channel identities. These results establish whether later
gameplay and persistence failures are trustworthy or are downstream of session
or lifecycle problems.
