package server.agents.integration;

import client.Character;
import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Reactor;

import java.awt.Point;
import java.util.Collection;
import java.util.Set;

public interface PrimitiveCapabilityGateway {
    int mapId(Character agent);

    Point position(Character agent);

    boolean alive(Character agent);

    default boolean grounded(Character agent) {
        return true;
    }

    AgentCharacterStateSnapshot characterState(Character agent);

    int stuckDurationMs(AgentRuntimeEntry entry);

    int questStatus(Character agent, int questId);

    int questProgress(Character agent, int questId, int progressId);

    boolean canStartQuest(Character agent, int questId, int npcId);

    boolean canCompleteQuest(Character agent, int questId, int npcId);

    int itemCount(Character agent, int itemId);

    int freeSlots(Character agent, int itemId);

    boolean questItem(int itemId);

    boolean portalPresent(Character agent, int portalId);

    Point portalPosition(Character agent, int portalId);

    Integer directPortalIdTo(Character agent, int destinationMapId);

    Point npcPosition(Character agent, int npcId);

    default void facePosition(Character agent, Point targetPosition) {
    }

    Collection<Reactor> reactors(Character agent);

    default Point nearestActiveReactorPosition(Character agent, Integer reactorId, String reactorName) {
        return null;
    }

    int liveMonsterCount(Character agent, Set<Integer> mobIds);

    void navigate(AgentRuntimeEntry entry, Point destination, boolean precise);

    void grind(AgentRuntimeEntry entry, Set<Integer> allowedMobIds);

    void stop(AgentRuntimeEntry entry);

    boolean enterPortal(Character agent, int portalId);

    boolean useItem(Character agent, int itemId);

    boolean interactNpc(Character agent, int npcId, AgentNpcInteractionType type, Integer questId);

    boolean startQuest(Character agent, int questId, int npcId);

    boolean completeQuest(Character agent, int questId, int npcId);

    boolean hitReactor(Character agent, int objectId);

    boolean lootNearby(Character agent, Set<Integer> itemIds);

    boolean sitChair(Character agent, int itemId);
}
