# Free Market Convenience Shop and Gachapon Design

Status: approved product design with implementation gaps called out below. This document records the decisions made for the stock-Cosmic alpha; it does not claim that every described feature is implemented.

## Release content policy

- Keep every map shipped with stock Cosmic v83, including World Tour, Showa, Mushroom Shrine, New Leaf City, Rein, the Cygnus area, Temple of Time, Ellin Forest, and the route through Horntail.
- Do not delete seasonal or event maps. Make seasonal destinations inaccessible while their event is inactive by disabling their entry NPC options, portal scripts, commands, and other warp paths.
- NPCs located inside seasonal or event maps remain untouched.
- Retain all 12 stock Gachapon machines. None of them is located in a seasonal map.

## Free Market convenience shop

### Placement and presentation

The new convenience vendor replaces Inkwell (`9000069`) in the Free Market Entrance (`910000000`). Inkwell is currently placed at `(97, -332)` on foothold `108`. Fredrick (`9030000`, Store Banker) and Scrooge (`9030100`, Storage Keeper) remain unchanged.

The replacement NPC appearance and name have not been selected. Reusing a stock NPC asset is preferred so the feature does not require an NPC asset port.

The v83 NPC shop interface has no native tab control. The vendor therefore presents this ordered category menu before opening a category-specific shop page:

1. Market & Merchant Tools
2. Communication
3. Travel & Delivery
4. Character Services
5. Equipment Enhancement

Prices and per-purchase limits remain undecided. Items should be ordered within each category as listed below.

### 1. Market & Merchant Tools

| Order | Item ID | Stock name | Planned behavior |
|---:|---:|---|---|
| 1 | `5230000` | The Owl of Minerva | Search Free Market listings; consumed on use. |
| 2 | `5140000` | Regular Store Permit | Open a player-controlled store; custom one-day expiration. |
| 3 | `5030001` | Mushroom House Elf | One-day hired merchant permit. |
| 4 | `5030003` | Cashier: Teddy Bear Clerk | One-day hired merchant permit. |
| 5 | `5030005` | The Robot Stand | One-day hired merchant permit. |

The Donkey merchant is explicitly excluded. It is also not present as a suitable clean-v83 item in the current assets.

### 2. Communication

| Order | Item ID | Stock name | Planned behavior |
|---:|---:|---|---|
| 1 | `5090000` | Note | Send a message to another player, including an offline recipient. |
| 2 | `5370000` | Chalkboard | Display player-entered text above the character. |
| 3 | `5071000` | Megaphone | Send a message to the current map. |
| 4 | `5072000` | Super Megaphone | Send a message to the current world. |
| 5 | `5076000` | Item Megaphone | Broadcast a message with an item attachment/display. |
| 6 | `5077000` | Triple Megaphone | Broadcast three lines to the current world. |

The Note, Chalkboard, and megaphone handlers must validate ownership, quantity, message length, and recipient or attachment data on the server. Packet-provided item IDs or slots are not authoritative.

### 3. Travel & Delivery

| Order | Item ID | Stock name | Planned behavior |
|---:|---:|---|---|
| 1 | `5040000` | The Teleport Rock | Store up to five maps and teleport within its stock restrictions. |
| 2 | `5041000` | VIP Teleport Rock | Store up to ten maps and support cross-continent travel where allowed. |
| 3 | `5450000` | Miu Miu the Traveling Merchant | Open the portable general store where permitted. |
| 4 | `5330000` | Quick Delivery Ticket | Immediately send a Duey package and message. |

Quick Delivery is a package-delivery ticket; it is not an instant item-retrieval ticket.

### 4. Character Services

| Order | Item ID | Stock name | Planned behavior |
|---:|---:|---|---|
| 1 | `5050000` | AP Reset | Move one eligible AP per use. This is not a full-character AP reset. |
| 2 | `5050001` | SP Reset (1st job) | Reset one eligible first-job SP. |
| 3 | `5050002` | SP Reset (2nd job) | Reset one eligible second-job SP. |
| 4 | `5050003` | SP Reset (3rd job) | Reset one eligible third-job SP. |
| 5 | `5050004` | SP Reset (4th job) | Reset one eligible fourth-job SP. |
| 6 | `5130000` | Safety Charm | Prevent EXP loss on one death under stock rules. |

Wheel of Destiny (`5510000`) is explicitly excluded.

### 5. Equipment Enhancement

| Order | Item ID | Stock name | Planned behavior |
|---:|---:|---|---|
| 1 | `5520000` | Scissors of Karma | Make an eligible bound item tradeable once. |
| 2 | `5570000` | Vicious' Hammer | Add one upgrade slot, subject to the stock per-item limit and eligibility rules. |
| 3 | `5610000` | Vega's Spell (10%) | Raise an eligible 10% scroll to a 30% success chance. |
| 4 | `5610001` | Vega's Spell (60%) | Raise an eligible 60% scroll to a 90% success chance. |

Item Guard is explicitly excluded.

White Scroll (`2340000`) is not presently included in the convenience shop. It prevents an upgrade slot from being lost when a normal or Dark Scroll fails, but it does not prevent Dark Scroll destruction. Clean Slate Scrolls restore previously lost slots and can themselves destroy the equipment on failure. Clean v83 has no stock Shield/Protection Scroll that prevents cursed-scroll destruction.

### Expiration and stacking

- Store and hired-merchant permits listed above must expire one day after purchase.
- Other convenience consumables are intended to be permanent until consumed unless a later balance decision assigns a duration.
- Cosmic's ordinary NPC shop purchase path currently grants items with `expiration = -1`; it cannot produce the requested one-day permits without a targeted server change.
- A non-equipment item without a WZ `slotMax` override defaults to 100 per inventory slot in Cosmic. Purchase limits may still be set lower for balance and packet-safety reasons.
- Stock Cash Shop commodity periods, commonly 90 days, do not automatically apply to an item granted by an NPC shop.
- Expiring permits should not merge in a way that silently replaces or extends an existing permit's expiration.

### Runtime requirements

- Use a scripted category menu backed by separate shop pages or an equivalent server-owned category selection.
- Validate the selected shop, item ID, quantity, price, available mesos, inventory capacity, and purchase limit server-side.
- Grant the one-day permits with a server-computed expiration; never accept an expiration supplied by the client.
- Preserve the existing restrictions for Teleport Rocks, Miu Miu, AP/SP resets, Scissors, Hammer, Vega's Spell, Chalkboard, Note, and megaphones.
- Keep item catalog and agent-economy data synchronized with the final prices, restrictions, expiration, and availability.

## Gachapon V2

### Compatibility mode

`USE_GACHA_V2` remains off by default. When it is off, stock Cosmic must keep its original Java reward pools and normal `5220000` ticket behavior. Database-backed reward pools, tier tickets, legendary odds, and mob-level ticket eligibility apply only when Gachapon V2 is enabled.

The existing Gachapon V2 work currently adds the configuration switch, database-console workspace, and minimum/maximum mob-level columns for global drops. It does not yet implement database-backed Gachapon rewards or the three-ticket runtime described here.

### Ticket assets and names

| Tier | Item ID | Existing icon | Planned name | Asset action |
|---|---:|---|---|---|
| Common | `5220000` | Blue ticket | Common Gachapon Ticket | Reuse the stock icon and replace the String.wz name/description. |
| Uncommon | `5220010` | Silver/white ticket | Uncommon Gachapon Ticket | Reuse the stock icon, replace the String.wz text, and neutralize the original `pachinko` metadata. |
| Rare | `5220020` | Gold/yellow ticket | Rare Gachapon Ticket | Reuse the stock icon and replace the String.wz name/description. |

Remote Gachapon Ticket (`5451000`) remains a separate stock asset. Its exact relationship to the tier-ticket system is not yet decided; it must not silently act as a fourth rarity ticket.

### Reward pools and ticket odds

Every machine has Common, Uncommon, and Rare pools. Legendary is a shared global pool available to every ticket and every machine.

| Ticket | Common pool | Uncommon pool | Rare pool | Global Legendary pool |
|---|---:|---:|---:|---:|
| Common, blue `5220000` | 99.5% | 0% | 0% | 0.5% |
| Uncommon, silver `5220010` | 79% | 20% | 0% | 1% |
| Rare, gold `5220020` | 48% | 30% | 20% | 2% |

Each row totals exactly 100%. A roll first selects the reward tier using the table above, then selects an enabled reward from the selected machine and tier. Legendary selection instead uses the global Legendary pool.

Individual rewards need an explicit weight or probability within their pool. Pool-tier probability and item-within-pool weight are separate concepts and must be displayed separately in the console.

If the selected pool is empty or invalid, the transaction should fail without consuming the ticket rather than falling back to another tier and distorting the published odds.

### Stock machine locations

All stock machines are retained because the release keeps all stock Cosmic maps.

| Machine | NPC ID | Map | Map ID | Position `(x, y)` |
|---|---:|---|---:|---:|
| Henesys | `9100100` | Henesys Market | `100000100` | `(512, 146)` |
| Ellinia | `9100101` | Ellinia | `101000000` | `(384, -1888)` |
| Perion | `9100102` | Perion | `102000000` | `(1536, 576)` |
| Kerning City | `9100103` | Kerning City | `103000000` | `(-591, -21)` |
| Sleepywood | `9100104` | Sleepywood | `105040300` | `(950, 250)` |
| Mushroom Shrine | `9100105` | Mushroom Shrine | `800000000` | `(4660, 94)` |
| Showa Male | `9100106` | Spa (M) | `809000101` | `(-339, 204)` |
| Showa Female | `9100107` | Spa (F) | `809000201` | `(-348, 202)` |
| Ludibrium | `9100108` | Empty House I | `220000305` | `(156, 135)` |
| New Leaf City | `9100109` | New Leaf City - Town Center | `600000000` | `(3488, 482)` |
| El Nath | `9100110` | El Nath Department Store | `211000102` | `(357, 177)` |
| Nautilus | `9100117` | Mid Floor - Hallway | `120000200` | `(3515, 147)` |

There are no placed stock machines in Orbis, Aqua Road, Leafre, Omega Sector, Deep Ludibrium, Mu Lung, Herb Town, Ariant, Magatia, Ellin Forest, or Temple of Time. New machines may be added later and should receive their own database machine record and three location-specific pools.

NPC assets `9100111` (Gachapon) and `9100112` (EXP Gachapon) exist but are not placed on a map or connected to Cosmic's current Gachapon reward enum. They are candidates for a future machine only after their client behavior and intended placement are reviewed.

### Database and console requirements

The database becomes the source of truth for Gachapon V2 only. The console must support:

- listing every stock and custom machine;
- adding a new machine record without requiring a Java reward class;
- viewing and editing Common, Uncommon, Rare, and global Legendary rewards;
- adding, removing, disabling, reordering, and reweighting rewards;
- searching rewards by item ID, name, and category;
- showing item icons and enough item metadata to review compatibility;
- showing tier-selection odds separately from within-pool item weights;
- calculating effective per-ticket reward chances for review;
- warning about missing items, empty pools, duplicate rows, invalid weights, and rewards absent from the server/client catalog;
- previewing the eligible reward set for each ticket and machine;
- recording who changed a pool and when;
- keeping the page unavailable or read-only when Gachapon V2 is disabled.

Database migrations run through Liquibase during the first server start. Changesets must be append-only after they have run; editing an applied changeset causes checksum validation failure.

### Ticket global drops

Common, Uncommon, and Rare tickets are intended to be global monster drops. Each global-drop row must support configurable minimum and maximum monster levels as well as its drop chance. Example thresholds such as Common from all monsters, Uncommon from level 50, and Rare from level 90 were discussed but are not final balance values.

Runtime eligibility must use the actual monster level and apply only when Gachapon V2 is enabled. The database console should make the level range and effective chance visible. The final drop table must also be exported to the item/economy catalog used by agents.

### Secure roll transaction

The server-side roll must:

1. identify the selected machine from trusted NPC or remote-selection state;
2. identify and verify the owned ticket in the correct inventory slot;
3. map the ticket ID to its server-owned probability table;
4. select the tier, then select a valid weighted reward from the corresponding database pool;
5. verify reward data and inventory capacity before mutation;
6. consume exactly one ticket and grant exactly one reward as one coordinated transaction;
7. log the player, ticket, machine, selected tier, reward, quantity, and timestamp;
8. reject replayed, malformed, unsupported, or mismatched packets without granting a reward.

## Explicit exclusions and unresolved decisions

Excluded from the Free Market shop:

- Donkey merchant permit;
- Wheel of Destiny;
- Item Guard;
- destruction-protection scrolls that do not exist in clean v83.

Not yet decided:

- replacement NPC appearance and name;
- meso prices and purchase limits for every shop item;
- whether White Scroll should ever be sold by this vendor;
- exact global-drop rates and monster-level ranges for the three tickets;
- item weights and contents of every machine pool and the Legendary pool;
- how Remote Gachapon Ticket interacts with Gachapon V2 tiers;
- names, locations, and pools for any newly added machines.
