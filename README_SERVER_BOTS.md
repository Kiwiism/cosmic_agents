
# Bot System — Quick Start Guide

## Features

- follow, trade, loot
- fight, use skill, auto assign ap/sp, buffs (Only warrior 1st job configured/tested)
- auto complete any quest the owner turn in (with rewards)
- Party Quest Automation(currently only KPQ 1st stage + auto accept rewards 5th stage)
- Each bot is a real character you can log in as, can spawn your alts as bots

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

With multiple bots: prefix the bot's name to target one specifically

```
stop - party wide stop
jason follow - only Jason will follow
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
| `stop` / `stay` / `wait` / `hold on` / `idle` / `park here` | Bot stops moving |
| `move here` / `go here` / `move` / `here` | Bot moves to your exact position and stops (Handy for party quest puzzles) |
| `grind` / `farm` / `hunt` / `kill mobs` / `auto on` | Bot starts combat AI |

### Info

| Say | Effect |
|---|---|
| `stats` / `str` / `dex` / `int` / `luk` / `level` | Level and stat summary |
| `range` / `damage` / `dmg` / `watk` | Damage range |
| `build` / `ap` / `sp` | AP/SP allocation |
| `skills` | Skill tree summary |
| `inventory` / `inv` / `items` / `equips` | Inventory summary |
| `mesos` / `cash` | Meso count |
| `slots` | Free inventory slot count |
| `scrolls` | Scrolls on hand |
| `pots` / `potions` | HP/MP pot count |
| `buffs` | buffs preference
| `debug stats` / `attack cooldown` | Attack timing internals |
| `help` / `commands` | Prints command list |

### Support (buffs & heals for the party)

| Say | Effect |
|---|---|
| `support on` / `support off` | Toggle skill support (idk what this is, ai generated, not tested, probably try to use buff skill on party member) |
| `heals on` / `heals off` | Toggle HP heal casting on party members (cleric, not tested) |
| `buff on` / `buff off` | Toggle buff pot usage |
| `buff cheap` | Use worst available buff pots |
| `buff max` / `buff best` | Use best available buff pots |
| `buffs?` / `what buffs` / `buff list` | List active and available buffs |

### Gear

Bots auto equip best gear available (damage range * attack speed) > all stats sum as tie breaker
TODO: actually filter by class / type / skills they can use

| Say | Effect |
|---|---|
| `any upgrades?` / `better gear` / `recommended gear` | Check if bot has better gear available for you |
| `request?` / `do you need anything` / `what do you need` | Check if you have better gear available for the bot |
| `trade recommended gear` / `trade upgrades` | Bot trades its recommended gear to you |
| `trade <item name>` | can also do this

### Trading & Dropping Items

Verbs: `trade [me] <type/name>`, `give [me] <type/name>`, `drop <type/name>`, `pass me <type/name>`

| Say | Effect |
|---|---|
| `trade` | Open a trade window (you want to give them something) |
| `trade me scrolls` / `give me scrolls` | Trade all scrolls |
| `trade pots` | Trade all potions |
| `trade equips` | Trade all equips |
| `trade use` | Trade all use-tab items |
| `trade etc` | Trade etc/junk items |
| `trade buff` / `give me buff items` | Trade buff potions |
| `trade 100k` / `give mesos` | Trade mesos |
| `trade <item name>` | Trade a named item |
| `drop scrolls` / `drop equips` / etc | Drop category to ground |
| `unequip everything` | Unequip all gear |
| `unequip hat` / `unequip weapon` / etc | Unequip a specific slot |

### Build & Job

| Say | Effect |
|---|---|
| `respec` / `reset skills` / `reset sp` | Refund and re-assign SP |

## Notes
- Bot characters can be logged into as normal accounts (user = bot name, password = `botbot`) to manually equip or manage inventory.
