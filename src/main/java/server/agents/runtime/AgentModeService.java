package server.agents.runtime;

import client.Character;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.integration.AgentDegenerateAttackStateRuntime;
import server.agents.runtime.AgentFarmAnchorStateRuntime;
import server.agents.integration.AgentGrindLootStateRuntime;
import server.agents.integration.AgentGrindSearchStateRuntime;
import server.agents.integration.AgentGrindTargetStateRuntime;
import server.agents.integration.AgentGrindWanderStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.capabilities.combat.AgentRetreatHoldStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.awt.Point;
import java.util.function.Consumer;

public final class AgentModeService {
    private AgentModeService() {
    }

    public static void startFollow(AgentRuntimeEntry entry, Character target) {
        Character leader = AgentRuntimeIdentityRuntime.owner(entry);
        AgentModeStateRuntime.setFollowTargetId(entry,
                leader != null && target != null && leader.getId() != target.getId()
                        ? target.getId()
                        : 0);
        AgentModeStateRuntime.setGrinding(entry, false);
        AgentMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentModeStateRuntime.setFollowing(entry, true);
    }

    public static void startGrind(AgentRuntimeEntry entry, Consumer<AgentRuntimeEntry> navigationClearer) {
        enterActiveMode(entry, navigationClearer);
    }

    public static void startStop(AgentRuntimeEntry entry) {
        clearMode(entry);
        AgentMoveTargetStateRuntime.clearMoveTarget(entry);
    }

    public static void startMoveTo(AgentRuntimeEntry entry, Point destination, boolean precise) {
        clearMode(entry);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, destination, precise);
    }

    public static void startFarmHere(AgentRuntimeEntry entry, Point destination, Consumer<AgentRuntimeEntry> navigationClearer) {
        enterActiveMode(entry, navigationClearer);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, destination, AgentRuntimeIdentityRuntime.botMapId(entry));
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, destination);
    }

    public static void startPatrol(AgentRuntimeEntry entry, int regionId, Consumer<AgentRuntimeEntry> navigationClearer) {
        enterActiveMode(entry, navigationClearer);
        AgentPatrolStateRuntime.startPatrol(entry, regionId, AgentRuntimeIdentityRuntime.botMapId(entry));
    }

    public static void enterActiveMode(AgentRuntimeEntry entry, Consumer<AgentRuntimeEntry> navigationClearer) {
        AgentModeStateRuntime.stopFollowing(entry);
        AgentMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentPatrolStateRuntime.clearPatrol(entry);
        AgentGrindTargetStateRuntime.clear(entry);
        AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
        AgentGrindSearchStateRuntime.clear(entry);
        AgentCombatCooldownStateRuntime.clearMoveWindow(entry);
        AgentDegenerateAttackStateRuntime.clear(entry);
        AgentRetreatHoldStateRuntime.clear(entry);
        AgentGrindWanderStateRuntime.clearWanderDirection(entry);
        navigationClearer.accept(entry);
        AgentModeStateRuntime.startGrinding(entry);
    }

    public static void clearMode(AgentRuntimeEntry entry) {
        AgentModeStateRuntime.stopMovementModes(entry);
        AgentFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentPatrolStateRuntime.clearPatrol(entry);
        AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
    }
}
