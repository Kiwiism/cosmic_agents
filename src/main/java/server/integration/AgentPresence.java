package server.integration;

import client.Character;
import server.maps.MapleMap;

public final class AgentPresence {
    private static volatile AgentPresenceProvider provider = new NoopAgentPresenceProvider();

    private AgentPresence() {
    }

    public static boolean isAgent(Character chr) {
        return provider.isAgent(chr);
    }

    public static boolean hasAgentInMap(MapleMap map) {
        for (Character chr : map.getAllPlayers()) {
            if (isAgent(chr)) {
                return true;
            }
        }
        return false;
    }

    public static void install(AgentPresenceProvider newProvider) {
        provider = newProvider == null ? new NoopAgentPresenceProvider() : newProvider;
    }
}
