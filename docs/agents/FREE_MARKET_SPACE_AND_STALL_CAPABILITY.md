# Character Space Reservation and Free Market Stall Capability

Status: implementation MVP, July 2026

## Purpose

One reservation model now covers character-sized positions instead of keeping
Amherst, Southperry, and Free Market placement as unrelated coordinate lists.
The model is intended for:

- Relaxer/rest positions in Amherst and Southperry;
- larger chairs that need two or more adjacent character slots;
- deterministic Free Market stall rows;
- future social, queue, event, and gathering positions.

The allocator is runtime state. It does not persist occupancy across a server
restart.

## Reservation Model

A `CharacterSpace` has a stable catalog id, map id, spot number, row id, slot
index, and map coordinate. A reservation is scoped by:

- world;
- channel;
- map;
- owner type and owner id.

This means FM room 1 spot 5 can be occupied independently in channels 1 and 2.
Allocation and release are synchronized and atomic. A footprint may reserve
one slot or several contiguous slots on the same row. For a two-slot chair,
the returned character coordinate is the midpoint of both reserved slots.

The existing `AgentRelaxerSpotCatalog` and
`AgentRelaxerSpotReservationRuntime` remain compatibility adapters. Amherst
and Southperry plans therefore keep their current plan-card contract while
using the generic allocator underneath.

## Current Catalogs

### Maple Island

- Amherst: all currently approved foothold positions.
- Southperry: all positions plus stable left-half and right-half views.
- The full Maple Island run continues to choose from the Southperry right-half
  view.

### Free Market

Rooms 1 through 22 are cataloged from the v83 room layouts:

| Rooms | Layout | Spots per room |
|---|---|---:|
| 1-6 | Henesys | 23 |
| 7-12 | Ludibrium | 28 |
| 13-17 | Perion | 26 |
| 18-22 | El Nath | 27 |

The coordinates were checked against the local `Map.wz` XML. The initial
layout reference was the artificial Free Market implementation in
[MadaraGameDev/SoloMapling](https://github.com/MadaraGameDev/SoloMapling).

## Player Store Placement

When a player creates a Player Shop or Hired Merchant in an FM room:

1. Find only the nearest catalog spot on the player's left and the nearest on
   the player's right.
2. Discard candidates farther than 125 pixels, near a portal, already
   reserved, or colliding with an uncataloged legacy store.
3. Reserve the nearer valid candidate atomically.
4. Assign the shop object to the reserved coordinate.
5. Reject creation with the normal occupied-store error plus a short message
   when neither candidate is available.
6. Release the reservation when the shop map object is removed.

Hired Merchants are independent map objects and visibly use the snapped
coordinate. A v83 Player Shop box is rendered on its owner character. The MVP
records the exact reserved shop coordinate but does not forcibly warp a real
player, because a server-only position mutation would desynchronize the local
client. Exact visible snapping for Player Shops needs either a supported
same-map reposition packet or a small client hook; until then the 125-pixel
limit keeps the player close to the assigned slot.

## FM Visualization Command

Use the GM 6 command while standing in FM rooms 1-22:

```text
!fmspots fill 50
!fmspots status
!fmspots clear
```

`fill` clears the previous test population in that channel/room and fills the
requested percentage of randomly selected catalog spots. Every stall:

- uses a random v83 Hired Merchant permit skin;
- is named `CH<channel> FM<room> S<spot>`;
- contains 1-16 random non-cash listings;
- prices each bundle at the server's NPC sell price.

These stalls are runtime-only and deliberately non-purchasable. They test
layout, density, labels, packets, and client rendering without creating fake
characters, fake merchant balances, or ownerless economy transactions.

## Agent Capability

`AgentFreeMarketStallCapability` is the production path. Its command contains:

- target FM map id;
- stall description;
- real Player Shop permit item id;
- 1-16 explicit listings, each with inventory type, slot, units per bundle,
  number of bundles, and price. Price `0` means NPC sell price.

Behavior:

1. Require the agent to already be in the requested FM room. Cross-map travel
   remains a separate plan objective.
2. Reserve one of the two nearest left/right catalog spots.
3. Hand off to the normal navigation capability until the agent reaches the
   spot.
4. Revalidate the real inventory and permit.
5. Remove listed quantities through normal inventory APIs.
6. Open a normal `PlayerShop` owned by the database character and register it
   in the world shop registry.

Successful end state:

- the agent remains at its reserved spot;
- its real inventory owns the removal;
- its real Player Shop owns the listings;
- buyer mesos and sale proceeds follow normal Cosmic behavior;
- the reservation remains held until the shop closes.

Failure before opening releases the reservation. A plan using this capability
should stop navigation/combat after success and transition the agent to a
merchant-idle state.

## Next Steps

Before autonomous market trading is enabled broadly:

1. Add an economy policy that chooses listings, bundle sizes, and prices from
   the agent's actual inventory and goals.
2. Add a plan objective that navigates through the FM entrance and room portal
   before invoking the stall capability.
3. Add store lifecycle objectives for repricing, withdrawing proceeds,
   closing, and unsold-item recovery.
4. Decide whether exact real-player Player Shop snapping warrants a client
   hook; do not emulate it with an unsynchronized server position write.
5. Add optional persistent catalog occupancy only if reservations must survive
   process restarts. Normal active shops still remain the authoritative durable
   merchant state.
