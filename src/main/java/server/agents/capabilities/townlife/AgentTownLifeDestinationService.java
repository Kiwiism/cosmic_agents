package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.reservation.CharacterSpace;
import server.maps.reservation.CharacterSpaceOwner;
import server.maps.reservation.CharacterSpaceReservation;
import server.maps.reservation.CharacterSpaceReservationRuntime;
import server.maps.reservation.CharacterSpaceScope;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class AgentTownLifeDestinationService {
    record Destination(AgentTownLifeState.Activity activity,
                       Point point,
                       int targetCharacterId,
                       int destinationMapId,
                       String key) {
    }

    private AgentTownLifeDestinationService() {
    }

    static Destination select(AgentRuntimeEntry entry,
                              Character agent,
                              AgentTownLifeState state,
                              AgentTownLifeState.Activity requested,
                              long nowMs,
                              PrimitiveCapabilityGateway gateway) {
        release(agent);
        long seed = ((long) agent.getId() << 32) ^ state.sequence();
        if (requested == AgentTownLifeState.Activity.SHOP_VISIT) {
            int mapId = LithHarborTownLifeCatalog.shopMapId(
                    AgentTownLifeRolePolicy.variation(seed, state.sequence(), 103, 61));
            String key = "shop:" + mapId;
            return state.memory().destinationAvailable(key, nowMs)
                    ? new Destination(requested, null, 0, mapId, key) : null;
        }
        if (requested == AgentTownLifeState.Activity.SOCIAL
                || requested == AgentTownLifeState.Activity.WEAPON_FLOURISH) {
            Character peer = choosePeer(agent, state, seed);
            if (peer != null) {
                int side = AgentTownLifeRolePolicy.variation(seed, state.sequence(), 2, 73) == 0 ? -1 : 1;
                Point target = new Point(peer.getPosition().x + side * 52, peer.getPosition().y);
                String key = "peer:" + peer.getId() + ':' + side;
                Point reserved = reserveFirst(agent, state, nowMs,
                        List.of(space("town-social", agent.getMapId(), target, socialSpotNumber(target))), key);
                if (reserved != null) {
                    return new Destination(requested, reserved, peer.getId(), 0, key);
                }
            }
            requested = AgentTownLifeState.Activity.WANDER;
        }
        if (requested == AgentTownLifeState.Activity.REST) {
            List<CharacterSpace> spaces = spaces("town-rest", agent.getMapId(),
                    LithHarborTownLifeCatalog.restSpots());
            return reservedDestination(agent, state, requested, spaces, nowMs, seed);
        }
        if (requested == AgentTownLifeState.Activity.NPC_PAUSE) {
            List<Point> points = new ArrayList<>();
            for (LithHarborTownLifeCatalog.NpcSpot spot : LithHarborTownLifeCatalog.npcSpots()) {
                Point npc = gateway.npcPosition(agent, spot.npcId());
                if (npc != null) {
                    points.add(new Point(npc.x + spot.offsetX(), npc.y));
                }
            }
            Destination result = reservedDestination(agent, state, requested,
                    spaces("town-npc", agent.getMapId(), points), nowMs, seed);
            if (result != null) {
                return result;
            }
            requested = AgentTownLifeState.Activity.WANDER;
        }
        List<Point> anchors = new ArrayList<>(LithHarborTownLifeCatalog.restSpots());
        for (LithHarborTownLifeCatalog.NpcSpot spot : LithHarborTownLifeCatalog.npcSpots()) {
            Point npc = gateway.npcPosition(agent, spot.npcId());
            if (npc != null) {
                anchors.add(npc);
            }
        }
        List<CharacterSpace> dynamic = AgentTownLifeSpotSampler.reachableSpaces(
                entry, agent, anchors, seed);
        if (dynamic.isEmpty()) {
            dynamic = spaces("town-wander", agent.getMapId(), LithHarborTownLifeCatalog.wanderSpots());
        }
        return reservedDestination(agent, state, requested, dynamic, nowMs, seed);
    }

    static void release(Character agent) {
        if (agent != null && agent.getId() > 0) {
            CharacterSpaceReservationRuntime.release(CharacterSpaceOwner.character(agent.getId()));
        }
    }

    private static Destination reservedDestination(Character agent,
                                                   AgentTownLifeState state,
                                                   AgentTownLifeState.Activity activity,
                                                   List<CharacterSpace> spaces,
                                                   long nowMs,
                                                   long seed) {
        if (spaces.isEmpty()) {
            return null;
        }
        int start = spaces.get(0).catalogId().startsWith("town-nav-") ? 0
                : AgentTownLifeRolePolicy.variation(seed, state.sequence(), spaces.size(), 257);
        for (int offset = 0; offset < spaces.size(); offset++) {
            CharacterSpace space = spaces.get((start + offset) % spaces.size());
            String key = space.catalogId() + ':' + space.spotNumber();
            Point reserved = reserveFirst(agent, state, nowMs, spaces, key, space);
            if (reserved != null) {
                return new Destination(activity, reserved, 0, 0, key);
            }
        }
        return null;
    }

    private static Point reserveFirst(Character agent,
                                      AgentTownLifeState state,
                                      long nowMs,
                                      List<CharacterSpace> spaces,
                                      String key) {
        return reserveFirst(agent, state, nowMs, spaces, key, spaces.get(0));
    }

    private static Point reserveFirst(Character agent,
                                      AgentTownLifeState state,
                                      long nowMs,
                                      List<CharacterSpace> spaces,
                                      String key,
                                      CharacterSpace candidate) {
        if (!state.memory().destinationAvailable(key, nowMs)) {
            return null;
        }
        Optional<CharacterSpaceReservation> reservation = CharacterSpaceReservationRuntime.reserveExact(
                scope(agent), CharacterSpaceOwner.character(agent.getId()), spaces, candidate, 1);
        return reservation.map(CharacterSpaceReservation::position).orElse(null);
    }

    private static CharacterSpaceScope scope(Character agent) {
        int channel = agent.getClient() == null ? 0 : Math.max(0, agent.getClient().getChannel());
        return new CharacterSpaceScope(Math.max(0, agent.getWorld()), channel, agent.getMapId());
    }

    private static List<CharacterSpace> spaces(String catalogId, int mapId, List<Point> points) {
        List<CharacterSpace> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            result.add(space(catalogId, mapId, points.get(i), i + 1));
        }
        return result;
    }

    private static CharacterSpace space(String catalogId, int mapId, Point point, int spotNumber) {
        return new CharacterSpace(catalogId, Math.max(1, spotNumber), mapId, 0,
                Math.max(0, spotNumber - 1), point.x, point.y);
    }

    private static int socialSpotNumber(Point point) {
        return Math.max(1, Math.floorDiv(point.x + 20_000, 24) + 1);
    }

    private static Character choosePeer(Character agent, AgentTownLifeState state, long seed) {
        List<Character> peers = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(peer -> peer != null && peer != agent)
                .filter(peer -> peer.getMapId() == agent.getMapId())
                .filter(peer -> AgentTownLifeRuntime.active(
                        AgentRuntimeRegistry.findByCharacterInstance(peer)))
                .sorted(Comparator.comparingInt(Character::getId))
                .toList();
        if (peers.isEmpty()) {
            return null;
        }
        return peers.get(AgentTownLifeRolePolicy.variation(seed, state.sequence(), peers.size(), 149));
    }
}
