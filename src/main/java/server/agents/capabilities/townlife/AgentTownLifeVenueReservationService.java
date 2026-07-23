package server.agents.capabilities.townlife;

import client.Character;
import server.maps.reservation.CharacterSpace;
import server.maps.reservation.CharacterSpaceOwner;
import server.maps.reservation.CharacterSpaceReservationRuntime;
import server.maps.reservation.CharacterSpaceScope;

import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Atomically presents a set of authored venue slots to one bounded encounter. */
final class AgentTownLifeVenueReservationService {
    private static final Object LOCK = new Object();

    private AgentTownLifeVenueReservationService() {
    }

    static Map<Integer, Point> reserveGroup(List<Character> participants,
                                            AgentTownLifeProfile.Venue venue,
                                            int sequence) {
        if (participants == null || participants.size() < 2 || venue == null
                || venue.spots().size() < participants.size()
                || participants.stream().anyMatch(java.util.Objects::isNull)) {
            return Map.of();
        }
        Character leader = participants.getFirst();
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(leader.getMapId());
        List<CharacterSpace> spaces = spaces(leader.getMapId(), venue).stream()
                .filter(space -> profile.allowsOccupancy(space.position()))
                .toList();
        if (spaces.size() < participants.size()) {
            return Map.of();
        }
        CharacterSpaceScope scope = scope(leader);
        synchronized (LOCK) {
            participants.forEach(AgentTownLifeDestinationService::release);
            Map<Integer, Point> reserved = new LinkedHashMap<>();
            int start = AgentTownLifeRolePolicy.variation(
                    leader.getId(), sequence, spaces.size(), 401);
            for (int participantIndex = 0; participantIndex < participants.size(); participantIndex++) {
                Character participant = participants.get(participantIndex);
                CharacterSpace candidate = spaces.get((start + participantIndex) % spaces.size());
                var reservation = CharacterSpaceReservationRuntime.reserveExact(
                        scope, CharacterSpaceOwner.character(participant.getId()),
                        spaces, candidate, 1);
                if (reservation.isEmpty()) {
                    participants.forEach(AgentTownLifeDestinationService::release);
                    return Map.of();
                }
                reserved.put(participant.getId(), reservation.orElseThrow().position());
            }
            return Map.copyOf(reserved);
        }
    }

    static void releaseGroup(List<Integer> participantAgentIds) {
        if (participantAgentIds == null) {
            return;
        }
        synchronized (LOCK) {
            participantAgentIds.forEach(agentId -> CharacterSpaceReservationRuntime.release(
                    CharacterSpaceOwner.character(agentId)));
        }
    }

    private static List<CharacterSpace> spaces(int mapId, AgentTownLifeProfile.Venue venue) {
        String catalog = "town-venue-" + venue.id();
        return java.util.stream.IntStream.range(0, venue.spots().size())
                .mapToObj(index -> {
                    Point point = venue.spots().get(index).point();
                    return new CharacterSpace(catalog, index + 1, mapId, 0,
                            index, point.x, point.y);
                })
                .toList();
    }

    private static CharacterSpaceScope scope(Character agent) {
        int channel = agent.getClient() == null ? 0 : Math.max(0, agent.getClient().getChannel());
        return new CharacterSpaceScope(Math.max(0, agent.getWorld()), channel, agent.getMapId());
    }
}
