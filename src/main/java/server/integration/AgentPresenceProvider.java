package server.integration;

import client.Character;
import server.life.Monster;
import server.maps.MapleMap;

public interface AgentPresenceProvider {
    boolean isAgent(Character chr);

    default void mapObservationChanged(MapleMap map, boolean observed) {
    }

    default void agentLeftMap(MapleMap map) {
    }

    default void mobHitAccepted(Character attacker, Monster monster,
                                int appliedDamage, long reactionDelayMs) {
    }

    default void mobHitAccepted(Character attacker, Monster monster,
                                int appliedDamage, MobHitReactionContext reactionContext) {
        mobHitAccepted(attacker, monster, appliedDamage,
                reactionContext == null ? 0L : reactionContext.delayMs());
    }
}
