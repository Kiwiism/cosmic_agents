package server.agents.integration;

import client.Character;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Reactor;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
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

    /** True when at least one real player can currently observe the Agent's map. */
    default boolean observedByPlayer(Character agent) {
        return false;
    }

    /** Characters already occupying a map, used by exclusive activity admission. */
    default int characterCount(Character agent, int mapId) {
        return 0;
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

    default Point portalPosition(Character agent, String portalName) {
        return null;
    }

    Integer directPortalIdTo(Character agent, int destinationMapId);

    Point npcPosition(Character agent, int npcId);

    default void facePosition(Character agent, Point targetPosition) {
    }

    /** Places an Agent for an explicit route-arrival ceremony and broadcasts the authoritative pose. */
    default void stagePosition(AgentRuntimeEntry entry, Character agent, Point position) {
    }

    /** Resolves an authored arrival point to its supporting foothold. */
    default Point groundPoint(MapleMap map, Point candidate) {
        return candidate == null ? null : new Point(candidate);
    }

    /** Warms navigation data without exposing movement-profile implementation details to callers. */
    default void prepareNavigation(AgentRuntimeEntry entry, Character agent) {
    }

    /** Invalidates one failed local route and warms a replacement without changing the objective. */
    default void refreshNavigation(AgentRuntimeEntry entry, Character agent) {
        prepareNavigation(entry, agent);
    }

    /**
     * Advances cross-map travel without exposing a progression-specific route
     * catalog to the requesting capability.
     */
    default AgentRouteOutcome travelTo(AgentRuntimeEntry entry,
                                       Character agent,
                                       int destinationMapId,
                                       long nowMs) {
        return AgentRouteOutcome.unavailable(mapId(agent), destinationMapId);
    }

    Collection<Reactor> reactors(Character agent);

    default Point nearestActiveReactorPosition(Character agent, Integer reactorId, String reactorName) {
        return null;
    }

    int liveMonsterCount(Character agent, Set<Integer> mobIds);

    default Map<Integer, Integer> liveMonsterCounts(Character agent) {
        return Map.of();
    }

    default Set<Integer> configuredMonsterSpawnIds(Character agent) {
        return Set.of();
    }

    default Map<Integer, Integer> configuredMonsterSpawnCounts(Character agent) {
        return Map.of();
    }

    void navigate(AgentRuntimeEntry entry, Point destination, boolean precise);

    void grind(AgentRuntimeEntry entry, Set<Integer> allowedMobIds);

    default void grind(AgentRuntimeEntry entry,
                       Set<Integer> preferredMobIds,
                       Set<Integer> fallbackMobIds) {
        Set<Integer> allowedMobIds = new LinkedHashSet<>();
        if (preferredMobIds != null) {
            allowedMobIds.addAll(preferredMobIds);
        }
        if (fallbackMobIds != null) {
            allowedMobIds.addAll(fallbackMobIds);
        }
        grind(entry, Set.copyOf(allowedMobIds));
    }

    /**
     * Grinds from an authored formation point. Combat remains subject to the normal
     * target hitbox and skill-range checks, while movement returns to the anchor.
     */
    default void grindFromAnchor(AgentRuntimeEntry entry,
                                 Point anchor,
                                 Set<Integer> preferredMobIds,
                                 Set<Integer> fallbackMobIds) {
        grind(entry, preferredMobIds, fallbackMobIds);
    }

    void stop(AgentRuntimeEntry entry);

    boolean enterPortal(Character agent, int portalId);

    boolean useItem(Character agent, int itemId);

    boolean interactNpc(Character agent, int npcId, AgentNpcInteractionType type, Integer questId);

    /** Runs an ordinary NPC script with explicit menu selections for a headless Agent. */
    default boolean runNpcScript(Character agent, int npcId, int... selections) {
        return false;
    }

    /** Runs an NPC dialogue opened by a nearby scripted portal whose NPC is not a map life object. */
    default boolean runPortalNpcScript(Character agent, int portalId, int npcId, int... selections) {
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

    /** Evidence-backed recovery start that retains the quest's normal start actions. */
    default boolean forceStartQuest(Character agent, int questId, int npcId) {
        return false;
    }

    /** Supplies one proven deterministic quest reward during recovery. */
    default boolean grantItem(Character agent, int itemId, int quantity) {
        return false;
    }

    /** Repairs a scripted investigation marker after the authored portal was attempted. */
    default boolean setQuestProgress(Character agent, int questId, int progressId, int value) {
        return false;
    }

    /** Clears transient movement state and moves the Agent to a durable recovery checkpoint. */
    default boolean recoverToMap(AgentRuntimeEntry entry, Character agent, int mapId) {
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

    default boolean sitMapSeat(Character agent, int seatId, Point seatPosition) {
        return sitChair(agent, seatId);
    }

    int chairItemId(Character agent);
}
