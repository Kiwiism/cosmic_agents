package server.integration;

import client.Character;
import server.maps.MapleMap;

public interface AgentPresenceProvider {
    boolean isAgent(Character chr);

    default void mapObservationChanged(MapleMap map, boolean observed) {
    }
}
