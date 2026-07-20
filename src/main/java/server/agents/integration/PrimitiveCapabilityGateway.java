package server.agents.integration;

import client.Character;
import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Reactor;

import java.awt.Point;
import java.util.Collection;
import java.util.Set;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Primitive actions are single-writer Agent operations using normal Cosmic validation paths.")
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

    default Set<Integer> configuredMonsterSpawnIds(Character agent) {
        return Set.of();
    }

    void navigate(AgentRuntimeEntry entry, Point destination, boolean precise);

    void grind(AgentRuntimeEntry entry, Set<Integer> allowedMobIds);

    void stop(AgentRuntimeEntry entry);

    boolean enterPortal(Character agent, int portalId);

    boolean useItem(Character agent, int itemId);

    boolean interactNpc(Character agent, int npcId, AgentNpcInteractionType type, Integer questId);

    /** Runs an ordinary NPC script with explicit menu selections for a headless Agent. */
    default boolean runNpcScript(Character agent, int npcId, int... selections) {
        return false;
    }

    boolean startQuest(Character agent, int questId, int npcId);

    boolean completeQuest(Character agent, int questId, int npcId);

    default boolean completeQuest(Character agent, int questId, int npcId, Integer rewardSelection) {
        return completeQuest(agent, questId, npcId);
    }

    default boolean forceCompleteQuest(Character agent, int questId, int npcId) {
        return false;
    }

    default boolean beginFieldAbsence(Character agent, long safetyRestoreDelayMs) {
        return false;
    }

    default boolean endFieldAbsence(Character agent) {
        return false;
    }

    boolean hitReactor(Character agent, int objectId);

    boolean lootNearby(Character agent, Set<Integer> itemIds);

    boolean sitChair(Character agent, int itemId);

    int chairItemId(Character agent);
}
