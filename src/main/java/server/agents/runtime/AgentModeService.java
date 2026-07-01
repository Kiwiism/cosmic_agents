package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindSearchStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotGrindWanderStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotRetreatHoldStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.function.Consumer;

public final class AgentModeService {
    private AgentModeService() {
    }

    public static void startFollow(BotEntry entry, Character target) {
        Character leader = AgentBotRuntimeIdentityRuntime.owner(entry);
        AgentBotModeStateRuntime.setFollowTargetId(entry,
                leader != null && target != null && leader.getId() != target.getId()
                        ? target.getId()
                        : 0);
        AgentBotModeStateRuntime.setGrinding(entry, false);
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentBotFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentBotModeStateRuntime.setFollowing(entry, true);
    }

    public static void startGrind(BotEntry entry, Consumer<BotEntry> navigationClearer) {
        enterActiveMode(entry, navigationClearer);
    }

    public static void startStop(BotEntry entry) {
        clearMode(entry);
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
    }

    public static void startMoveTo(BotEntry entry, Point destination, boolean precise) {
        clearMode(entry);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, destination, precise);
    }

    public static void startFarmHere(BotEntry entry, Point destination, Consumer<BotEntry> navigationClearer) {
        enterActiveMode(entry, navigationClearer);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, destination, AgentBotRuntimeIdentityRuntime.botMapId(entry));
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, destination);
    }

    public static void startPatrol(BotEntry entry, int regionId, Consumer<BotEntry> navigationClearer) {
        enterActiveMode(entry, navigationClearer);
        AgentBotPatrolStateRuntime.startPatrol(entry, regionId, AgentBotRuntimeIdentityRuntime.botMapId(entry));
    }

    public static void enterActiveMode(BotEntry entry, Consumer<BotEntry> navigationClearer) {
        AgentBotModeStateRuntime.stopFollowing(entry);
        AgentBotMoveTargetStateRuntime.clearMoveTarget(entry);
        AgentBotFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentBotPatrolStateRuntime.clearPatrol(entry);
        AgentBotGrindTargetStateRuntime.clear(entry);
        AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
        AgentBotGrindSearchStateRuntime.clear(entry);
        AgentBotCombatCooldownStateRuntime.clearMoveWindow(entry);
        AgentBotDegenerateAttackStateRuntime.clear(entry);
        AgentBotRetreatHoldStateRuntime.clear(entry);
        AgentBotGrindWanderStateRuntime.clearWanderDirection(entry);
        navigationClearer.accept(entry);
        AgentBotModeStateRuntime.startGrinding(entry);
    }

    public static void clearMode(BotEntry entry) {
        AgentBotModeStateRuntime.stopMovementModes(entry);
        AgentBotFarmAnchorStateRuntime.clearFarmAnchor(entry);
        AgentBotPatrolStateRuntime.clearPatrol(entry);
        AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
    }
}
