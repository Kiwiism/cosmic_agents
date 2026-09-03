package server.agents.capabilities.expedition.balrog;

import client.Character;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per reward-map assignment gate shared by active and passive Agent pickup paths. */
public final class AgentEasyBalrogRewardClaimRegistry {
    private static final Map<ClaimKey, Integer> claims = new ConcurrentHashMap<>();

    private AgentEasyBalrogRewardClaimRegistry() {
    }

    public static void replace(MapleMap map, Map<Integer, Integer> assignments) {
        if (map == null) return;
        clear(map);
        assignments.forEach((objectId, characterId) ->
                claims.put(new ClaimKey(map, objectId), characterId));
    }

    public static boolean isAssignedTo(Character agent, MapItem drop) {
        if (agent == null || agent.getMap() == null || drop == null) return false;
        Integer assignedCharacterId = claims.get(
                new ClaimKey(agent.getMap(), drop.getObjectId()));
        return assignedCharacterId != null && assignedCharacterId == agent.getId();
    }

    public static void collected(MapleMap map, int objectId) {
        if (map != null) claims.remove(new ClaimKey(map, objectId));
    }

    public static void clear(MapleMap map) {
        if (map != null) claims.keySet().removeIf(key -> key.map == map);
    }

    private record ClaimKey(MapleMap map, int objectId) {
        @Override
        public boolean equals(Object other) {
            return other instanceof ClaimKey key && map == key.map && objectId == key.objectId;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(map) + objectId;
        }
    }
}
