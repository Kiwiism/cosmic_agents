package server.agents.runtime;

import server.agents.capabilities.combat.AgentGrindSearchStateRuntime;

import client.Character;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.capabilities.combat.AgentDegenerateAttackStateRuntime;
import server.agents.capabilities.movement.AgentFarmAnchorStateRuntime;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.combat.AgentGrindWanderStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.capabilities.combat.AgentRetreatHoldStateRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;

import java.awt.Point;
import java.util.function.Consumer;

public final class AgentModeService {
    private AgentModeService() {
    }

    public static void startFollow(AgentRuntimeEntry entry, Character target) {
        AgentRelationshipRuntime.setFollowTarget(entry, target);
        AgentModeStateRuntime.setFollowTargetId(entry, target == null ? 0 : target.getId());
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
        AgentRelationshipRuntime.setFollowTarget(entry, null);
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
        AgentRelationshipRuntime.setFollowTarget(entry, null);
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
