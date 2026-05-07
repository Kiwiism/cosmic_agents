
# Bot System — Quick Start Guide

## Features

- follow, trade, loot
- auto fight/grind, use skill, auto assign ap/sp, buffs (Only 1st jobs + select 2nd job configured/tested)
- auto buy/resupply potions/ammo if shop available within the same map
- auto share potions/ammo among themselves and to owner(if requested) when running low while farming
- auto equip and optimize own gear loadout - including chaining stats bonus to unlock higher requirement equips and weapons, allowing dexless/strless/lukless builds
- auto share equipment upgrades with owner and other sibling bots (auto compare stats directly against recipient's inventory)
- auto sort equipment from junk equip so selling trash equips become manageable. (auto sell may come later when I trust it enough to not sell something useful)
- auto complete any quest the owner turn in (with rewards)
- Party Quest Automation(currently only KPQ 1st stage + auto accept rewards 5th stage)
- Each bot is a real character you can log in as, can spawn your alts as bots

## Bot Maintenance Guide

1. Give them mesos, take them shopping to resupply pots/ammo - can also manually give a bot some excess and they will auto distribute among themselves.
2. use `inv?` command to check inventory spaces, `trade <equip/use/etc/...>` or `trade trash` to get those items(no auto sell for now)
3. use `brb` or `logout` command to send bot to town safely or despawn

## Creating a Bot

### Option 1: create default 
`@spawnbot <characterName> confirm`

### Option 2: register existing character
log in on the target character and run: `@registerbot <characterName>`

## Spawning a Bot

### Option 1: `@spawnbot <name>`

### Option 2: Buddy invite shortcut
Add bots as friends. Bots will always show up as online. Invite a bot to party or chatroom through buddy menu to spawn.

## Bot commands

All commands are sent via **general map chat** (normal typing).
* Currently bot will also accept command across map even if you do normal chat (using follow command will teleport all bots you have spawned from other map to your location)

With multiple bots: target one specifically with a unique name-start prefix, full name, or slot number

```
stop - party wide stop
jason follow - only Jason will follow
jas come - only the unique bot whose name starts with "jas" will follow
1 stop - only bot slot 1 will stop
grind - party wide auto grind
jason stop - only jason will stop
```

---

## Management Commands


| Command | Effect |
|---|---|
| `transfer <botName> to <newOwner>` | Hand bot ownership to another player
| `!takebotowner <botName> [newOwner]` | GM force-reassign bot ownership

## Commands Available (chat with the bot)

### Movement & Combat

| Say | Effect |
|---|---|
| `follow` / `follow me` / `come here` / `come` | Bot follows you |
| `follow <name>` | follows party member or sibling bot |
| `stop` / `stay` / `wait` / `hold on` / `idle` / `park here` | Bot stops moving |
| `move here` / `go here` / `move` / `here` | Bot moves to your exact position and stops (Handy for party quest puzzles) |
| `grind` / `farm` / `hunt` / `kill mobs` / `auto on` / `go get exp` | Bot starts combat AI |
| `farm here` / `grind here` / `sentry` / `camp` / `guard mode` / `post up` / `anchor here` | Bot grinds from your current spot instead of following |
| `fidget` | Trigger a small idle/social fidget |

### Info

| Say | Effect |
|---|---|
| `stats` / `str` / `dex` / `int` / `luk` / `level` | Level and stat summary |
| `range` / `damage` / `dmg` / `watk` | Damage range and hardest-mob hit chance |
| `speed` / `jump` / `movement stats` | Movement stat breakdown |
| `build` / `ap` / `sp` | AP/SP allocation |
| `skills` | Skill tree summary |
| `inventory` / `inv` / `items` / `equips` | Inventory summary |
| `mesos` / `cash` | Meso count |
| `exp` / `xp` / `experience` | EXP progress |
| `slots` | Free inventory slot count |
| `scrolls` | Scrolls on hand |
| `pots` / `potions` | HP/MP pot count |
| `buffs?` / `buff list` | Buff-pot mode and active/available buff summary |
| `debug stats` / `attack cooldown` | Attack timing internals |
| `crit` / `crit debug` | Crit rate / multiplier breakdown |
| `pot debug` / `autopot debug` | Auto-pot selection/debug info |
| `buff debug` / `active buffs` | Buff consumable debug state |
| `skill buff debug` | Skill-buff debug state |
| `help` / `commands` | Prints command list |

### Support (buffs & heals for the party)

| Say | Effect |
|---|---|
| `support on` / `support off` | Toggle party support/heal behavior |
| `heals on` / `heals off` | Toggle HP heal casting on party members |
| `buff on` / `buff off` | Toggle buff pot usage |
| `buff cheap` | Use worst available buff pots |
| `buff max` / `buff best` | Use best available buff pots |
| `buffs?` / `what buffs` / `buff list` | List active and available buffs |

### Supplies

| Say | Effect |
|---|---|
| `need hp pot` / `need health pot` | Ask bots/owner flow for HP pot help |
| `need mp pot` / `need mana pot` | Ask bots/owner flow for MP pot help |
| `need pot` / `running low on pots` | Ask for whichever pot type is needed more |
| `need ammo` / `low on arrows` / `low on bolts` | Ask for ammo help when using bow/crossbow |

### Social

| Say | Effect |
|---|---|
| `fame me` | Bot fames you (subject to daily & monthly limits) |
| `fame <name>` | Bot fames the named player or bot on the map |

### Gear

Bots auto-equip the best available gear they can use. They can also recommend gear for you, request current upgrades from you, and trade away non-reserved spare gear.

| Say | Effect |
|---|---|
| `any upgrades?` / `better gear` / `recommended gear` | Check if bot has better gear available for you |
| `request?` / `need anything?` / `what do you need` | Check if you have better gear available for the bot |
| `trade recommended gear` / `trade upgrades` | Bot trades its recommended gear to you |
| `autoequip` / `optimize gear` | Force a gear optimization pass |
| `autoequip debug` / `optimize gear debug` | Explain optimizer picks and dump verbose output |
| `unequip everything` | Unequip all non-cash gear |
| `unequip hat` / `unequip weapon` / `unequip ring` / etc | Unequip a specific slot |

### Trading & Dropping Items

Verbs: `trade [me] <type/name>`, `give [me] <type/name>`, `drop <type/name>`, `pass me <type/name>`

| Say | Effect |
|---|---|
| `trade` | Open a trade window (you want to give them something) |
| `trade me scrolls` / `give me scrolls` | Trade all scrolls |
| `trade pots` | Trade all potions |
| `trade buff` | Trade buff potions |
| `trade equips` | Trade all equips |
| `trade trash` / `trade junk` | Trade only unreserved/disposable equips |
| `trade use` | Trade all use-tab items |
| `trade etc` | Trade etc/junk items |
| `trade 100k` / `give mesos` | Trade mesos |
| `trade <item name>` | Trade a named item |
| `trade <slot>` | Unequip then trade the item ex. `trade hat` |
| `show me your <slot>` / `can I see your <slot>` | Unequip then trade the item ex. `show me your hat` |
| `show me your junk` / `show your junk` | Alias for `trade trash` |
| `drop scrolls` / `drop equips` / etc | Drop category to ground |
| `give me <item name>` / `pass me <item name>` | Same named-item trade flow with alternate verbs |

### Build & Job

| Say                                       | Effect                  |
|-------------------------------------------|-------------------------|
| `respec sp` / `reset skills` / `reset sp` | Refund and re-assign SP |
| `respec ap` / `reset ap`                  | Refund and re-assign AP |
| `change build`                            | Re-prompt AP build selection |
| job name in chat while prompted           | Pick the requested advancement/build option |

## Notes
- Bot characters can be logged into as normal accounts (user = bot name, password = `botbot`) to manually equip or manage inventory.
