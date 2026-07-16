package server.integration;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.life.Monster;
import server.maps.MapleMap;

public final class AgentPresence {
    private static final Logger log = LoggerFactory.getLogger(AgentPresence.class);
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

    public static void mapObservationChanged(MapleMap map, boolean observed) {
        try {
            provider.mapObservationChanged(map, observed);
        } catch (RuntimeException failure) {
            log.warn("Agent map-observation hook failed for map {} observed={}",
                    map == null ? -1 : map.getId(), observed, failure);
        }
    }

    public static void agentLeftMap(MapleMap map) {
        try {
            provider.agentLeftMap(map);
        } catch (RuntimeException failure) {
            log.warn("Agent map-departure hook failed for map {}",
                    map == null ? -1 : map.getId(), failure);
        }
    }

    public static void mobHitAccepted(Character attacker, Monster monster,
                                      int appliedDamage, long reactionDelayMs) {
        try {
            if (attacker == null || !provider.isAgent(attacker)) {
                return;
            }
            provider.mobHitAccepted(attacker, monster, appliedDamage, reactionDelayMs);
        } catch (RuntimeException | LinkageError failure) {
            log.warn("Agent accepted-mob-hit hook failed for mob {} attacker {}",
                    monster == null ? -1 : monster.getObjectId(),
                    attacker == null ? -1 : attacker.getId(), failure);
        }
    }

    public static void install(AgentPresenceProvider newProvider) {
        provider = newProvider == null ? new NoopAgentPresenceProvider() : newProvider;
    }
}
