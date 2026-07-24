package server.agents.capabilities.townlife;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
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
                       String key,
                       String venueId) {
    }

    private AgentTownLifeDestinationService() {
    }

    static Destination select(AgentRuntimeEntry entry,
                              Character agent,
                              AgentTownLifeState state,
                              AgentTownLifeState.Activity requested,
                              long nowMs,
                              PrimitiveCapabilityGateway gateway) {
        return select(entry, agent, state, AgentTownLifeDecision.deterministic(requested), nowMs, gateway);
    }

    static Destination select(AgentRuntimeEntry entry,
                              Character agent,
                              AgentTownLifeState state,
                              AgentTownLifeDecision decision,
                              long nowMs,
                              PrimitiveCapabilityGateway gateway) {
        release(agent);
        long seed = ((long) agent.getId() << 32) ^ state.sequence();
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(state.townMapId());
        AgentTownLifeState.Activity requested = decision.activity();
        if (!decision.venueId().isBlank()) {
            Destination directed = directedVenueDestination(
                    agent, state, decision, profile, nowMs, seed);
            if (directed != null) {
                return directed;
            }
            if (!"default-policy".equals(decision.source())) {
                return null;
            }
        }
        if (requested == AgentTownLifeState.Activity.SHOP_VISIT) {
            int mapId = profile.shopMapId(
                    AgentTownLifeRolePolicy.variation(seed, state.sequence(), 103, 61));
            String key = "shop:" + mapId;
            return state.memory().destinationAvailable(key, nowMs)
                    ? new Destination(requested, null, 0, mapId, key, "") : null;
        }
        if (requested == AgentTownLifeState.Activity.SOCIAL
                || requested == AgentTownLifeState.Activity.WEAPON_FLOURISH) {
            Character peer = choosePeer(agent, state, seed, decision.targetAgentId(), nowMs);
            if (peer != null) {
                int side = AgentTownLifeRolePolicy.variation(seed, state.sequence(), 2, 73) == 0 ? -1 : 1;
                Point target = new Point(peer.getPosition().x + side * 52, peer.getPosition().y);
                String key = "peer:" + peer.getId() + ':' + side;
                Point reserved = reserveFirst(agent, state, nowMs,
                        List.of(space("town-social", agent.getMapId(), target, socialSpotNumber(target))), key);
                if (reserved != null) {
                    return new Destination(requested, reserved, peer.getId(), 0, key, "");
                }
            }
            requested = AgentTownLifeState.Activity.ROAM;
        }
        if (requested == AgentTownLifeState.Activity.REST) {
            List<CharacterSpace> spaces = spaces("town-rest", agent.getMapId(),
                    profile.restPoints());
            spaces = AgentTownLifeSpotSampler.orderAuthoredSpaces(agent, state, spaces, seed);
            return reservedDestination(agent, state, requested, spaces, nowMs, seed);
        }
        if (requested == AgentTownLifeState.Activity.NPC_PAUSE) {
            List<Point> points = new ArrayList<>();
            for (AgentTownLifeProfile.NpcSpot spot : profile.npcSpots()) {
                Point npc = gateway.npcPosition(agent, spot.npcId());
                if (npc != null) {
                    points.add(new Point(npc.x + spot.offsetX(), npc.y));
                }
            }
            List<CharacterSpace> npcSpaces = spaces("town-npc", agent.getMapId(), points);
            npcSpaces = AgentTownLifeSpotSampler.orderAuthoredSpaces(
                    agent, state, npcSpaces, seed);
            Destination result = reservedDestination(
                    agent, state, requested, npcSpaces, nowMs, seed);
            if (result != null) {
                return result;
            }
            requested = AgentTownLifeState.Activity.ROAM;
        }
        List<Point> anchors = new ArrayList<>(profile.restPoints());
        for (AgentTownLifeProfile.NpcSpot spot : profile.npcSpots()) {
            Point npc = gateway.npcPosition(agent, spot.npcId());
            if (npc != null) {
                anchors.add(npc);
            }
        }
        List<CharacterSpace> dynamic = AgentTownLifeSpotSampler.reachableSpaces(
                entry, agent, state, anchors, seed);
        if (dynamic.isEmpty()
                && AgentTownLifeFidelityPolicy.usesPhysicalNavigation(state.fidelity())
                && !AgentTownLifeSpotSampler.graphAvailable(entry, agent)) {
            return null;
        }
        if (dynamic.isEmpty()) {
            dynamic = spaces("town-roam", agent.getMapId(), profile.roamFallbackPoints());
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
                return new Destination(activity, reserved, 0, 0, key, "");
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
        AgentTownLifeMetrics.reservationAttempt(reservation.isPresent());
        return reservation.map(CharacterSpaceReservation::position).orElse(null);
    }

    private static CharacterSpaceScope scope(Character agent) {
        boolean hasClient = AgentClientGatewayRuntime.clients().hasClient(agent);
        int world = Math.max(0, hasClient
                ? AgentClientGatewayRuntime.clients().world(agent) : agent.getWorld());
        int channel = Math.max(0, hasClient
                ? AgentClientGatewayRuntime.clients().channel(agent) : 0);
        return new CharacterSpaceScope(world, channel, agent.getMapId());
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

    private static Destination directedVenueDestination(Character agent,
                                                        AgentTownLifeState state,
                                                        AgentTownLifeDecision decision,
                                                        AgentTownLifeProfile profile,
                                                        long nowMs,
                                                        long seed) {
        AgentTownLifeProfile.Venue venue = profile.venue(decision.venueId()).orElse(null);
        if (venue == null || !venue.supports(decision.activity())) {
            return null;
        }
        String key = "venue:" + venue.id();
        if (decision.activity() == AgentTownLifeState.Activity.SHOP_VISIT) {
            return venue.destinationMapId() > 0 && state.memory().destinationAvailable(key, nowMs)
                    ? new Destination(decision.activity(), null, 0, venue.destinationMapId(), key, venue.id())
                    : null;
        }
        List<CharacterSpace> spaces = new ArrayList<>();
        for (int index = 0; index < venue.spots().size(); index++) {
            Point point = venue.spots().get(index).point();
            if (profile.allowsOccupancy(point)) {
                spaces.add(space("town-venue-" + venue.id(), agent.getMapId(),
                        point, index + 1));
            }
        }
        spaces = AgentTownLifeSpotSampler.orderAuthoredSpaces(agent, state, spaces, seed);
        Destination reserved = reservedDestination(
                agent, state, decision.activity(), spaces, nowMs, seed, venue.id());
        if (reserved == null) {
            return null;
        }
        int targetId = decision.targetAgentId();
        if ((decision.activity() == AgentTownLifeState.Activity.SOCIAL
                || decision.activity() == AgentTownLifeState.Activity.WEAPON_FLOURISH)
                && targetId <= 0) {
            Character peer = choosePeer(agent, state, seed, 0, nowMs);
            targetId = peer == null ? 0 : peer.getId();
        }
        return new Destination(reserved.activity(), reserved.point(), targetId,
                reserved.destinationMapId(), reserved.key(), venue.id());
    }

    private static Destination reservedDestination(Character agent,
                                                   AgentTownLifeState state,
                                                   AgentTownLifeState.Activity activity,
                                                   List<CharacterSpace> spaces,
                                                   long nowMs,
                                                   long seed,
                                                   String venueId) {
        Destination result = reservedDestination(agent, state, activity, spaces, nowMs, seed);
        return result == null ? null : new Destination(result.activity(), result.point(),
                result.targetCharacterId(), result.destinationMapId(), result.key(), venueId);
    }

    private static Character choosePeer(Character agent,
                                        AgentTownLifeState state,
                                        long seed,
                                        int requestedAgentId,
                                        long nowMs) {
        List<Character> peers = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(peer -> peer != null && peer != agent)
                .filter(peer -> peer.getMapId() == agent.getMapId())
                .filter(peer -> AgentTownLifeRuntime.active(
                        AgentRuntimeRegistry.findByCharacterInstance(peer)))
                .filter(peer -> !AgentTownLifeEncounterCoordinator.active(
                        AgentRuntimeRegistry.findByCharacterInstance(peer)))
                .sorted(Comparator.comparingInt(Character::getId))
                .toList();
        if (peers.isEmpty()) {
            return null;
        }
        if (requestedAgentId > 0) {
            return peers.stream().filter(peer -> peer.getId() == requestedAgentId).findFirst().orElse(null);
        }
        List<Character> available = peers.stream()
                .filter(peer -> state.memory().peerAvailable(peer.getId(), nowMs))
                .toList();
        List<Character> candidates = available.isEmpty() ? peers : available;
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterInstance(agent);
        AgentPersonalityProfile.Traits traits = entry == null ? null : entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY)
                .map(AgentPersonalityState::profile)
                .map(AgentPersonalityProfile::traits)
                .orElse(null);
        return candidates.stream()
                .max(Comparator.comparingInt(peer ->
                        state.memory().peerPreferenceScore(peer.getId(), traits, nowMs)
                                + AgentTownLifeRolePolicy.variation(
                                seed ^ peer.getId(), state.sequence(), 20, 149)))
                .orElse(null);
    }
}
