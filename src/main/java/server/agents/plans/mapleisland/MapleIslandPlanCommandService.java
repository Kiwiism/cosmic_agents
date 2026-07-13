package server.agents.plans.mapleisland;

import client.Character;
import config.YamlConfig;
import server.TimerManager;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.capabilities.quest.AmherstTestResetMode;
import server.agents.capabilities.quest.AmherstTestResetRequest;
import server.agents.capabilities.quest.AmherstTestResetResult;
import server.agents.capabilities.quest.AmherstTestResetService;
import server.agents.capabilities.quest.MapleIslandSouthperryQuestCatalog;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.amherst.AmherstObjectiveProgressStatus;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanExecutionState;
import server.agents.plans.amherst.AmherstPlanProgressSnapshot;
import server.agents.plans.amherst.AmherstPlanValidationException;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.io.IOException;

public final class MapleIslandPlanCommandService {
    private MapleIslandPlanCommandService() {
    }

    public static void execute(Character player, String[] params) {
        if (player == null || params == null || params.length < 2) {
            usage(player);
            return;
        }
        String verb = params[0].toLowerCase();
        if (verb.equals("run")) {
            run(player, params[1]);
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(player.getId(), params[1]);
        if (entry == null) {
            message(player, "No spawned Agent named '" + params[1] + "' is controlled by you.");
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            message(player, "The selected Agent has no live character.");
            return;
        }
        try {
            AmherstPlanCard card = AgentMapleIslandPlanRuntime.defaultCard();
            switch (verb) {
                case "reset" -> reset(player, entry, agent, card, false);
                case "start" -> start(player, entry, agent);
                case "next" -> next(player, entry, agent);
                case "status" -> status(player, entry, agent, card);
                default -> usage(player);
            }
        } catch (IOException | AmherstPlanValidationException | IllegalArgumentException failure) {
            message(player, "Command failed: " + failure.getMessage());
        }
    }

    private static boolean reset(Character player,
                                 AgentRuntimeEntry entry,
                                 Character agent,
                                 AmherstPlanCard card,
                                 boolean showcase) throws IOException {
        AmherstTestResetResult result = (showcase
                ? AmherstTestResetService.showcaseHarness(true, agent.getName())
                : AmherstTestResetService.configuredHarness()).reset(
                new AmherstTestResetRequest(agent.getId(), agent.getName(),
                        AmherstTestResetMode.SOUTHPERRY_MVP_START, 0));
        if (!result.allowed()) {
            message(player, "Reset blocked [" + result.status() + "]: " + result.message());
            return false;
        }
        if (!AgentMapleIslandPlanRuntime.clearSession(entry)) {
            message(player, "Reset completed, but an active capability is still closing.");
            return false;
        }
        AgentMapleIslandPlanRuntime.defaultStore().delete(card.planId(), agent.getId());
        AgentMovementCommandRuntime.stop(entry);
        message(player, "Southperry baseline restored. 0/" + card.objectives().size()
                + " objectives satisfied; Amherst quests remain complete.");
        return true;
    }

    private static void start(Character player,
                              AgentRuntimeEntry entry,
                              Character agent) throws IOException, AmherstPlanValidationException {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        if (state.active()) {
            message(player, state.waitingForAdvance()
                    ? "Manual plan is paused. Use !mapleisland next " + agent.getName() + "."
                    : "A Maple Island objective is already active.");
            return;
        }
        AgentMapleIslandPlanRuntime.startManual(entry, agent, System.currentTimeMillis(),
                event -> debugMessage(player, event));
        message(player, "Manual Southperry plan started. Exactly one objective will execute.");
    }

    private static void next(Character player, AgentRuntimeEntry entry, Character agent) {
        if (AgentMapleIslandPlanRuntime.requestNext(entry)) {
            message(player, "Next objective authorized.");
            return;
        }
        if (entry.capabilityRuntimeState().hasActiveCapability()) {
            message(player, "The current objective is still running: "
                    + entry.capabilityRuntimeState().activeCapabilityId());
        } else {
            message(player, "No paused manual plan. Use !mapleisland start " + agent.getName() + ".");
        }
    }

    private static void status(Character player,
                               AgentRuntimeEntry entry,
                               Character agent,
                               AmherstPlanCard card) throws IOException {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        AmherstPlanProgressSnapshot snapshot = state.progress() == null
                ? AgentMapleIslandPlanRuntime.defaultStore().load(card.planId(), agent.getId())
                : state.progress();
        long satisfied = snapshot.objectives().values().stream()
                .filter(progress -> progress.status() == AmherstObjectiveProgressStatus.SATISFIED)
                .count();
        String session = state.completed() ? "COMPLETE"
                : state.active() ? state.waitingForAdvance() ? "PAUSED" : "RUNNING"
                : "NOT STARTED";
        message(player, "Southperry plan: " + session + "; objectives=" + satisfied
                + "/" + card.objectives().size() + "; map=" + agent.getMapId() + ".");
        message(player, "Quest states: 1046=" + agent.getQuestStatus(1046)
                + " (must be active), 1028=" + agent.getQuestStatus(1028)
                + " (must not be complete). Lv" + agent.getLevel() + " EXP " + agent.getExp() + ".");
        if (!state.lastError().isBlank()) {
            message(player, "Last error: " + state.lastError());
        }
    }

    private static void run(Character player, String agentName) {
        String allowedName = YamlConfig.config.server.AGENT_MAPLE_ISLAND_SHOWCASE_AGENT_NAME;
        if (!YamlConfig.config.server.AGENT_MAPLE_ISLAND_SHOWCASE_ENABLED
                || allowedName == null || !allowedName.equalsIgnoreCase(agentName)) {
            message(player, "Showcase run is disabled or '" + agentName
                    + "' is not the configured Agent.");
            return;
        }
        AgentOwnershipService ownership = AgentOwnershipService.getInstance();
        var resolved = ownership.resolveCharacterByName(allowedName);
        if (resolved == null) {
            message(player, "No character named '" + allowedName + "' exists.");
            return;
        }
        if (ownership.getRegisteredOwnerId(resolved.id()) == null) {
            try {
                ownership.registerOwner(resolved.id(), player.getId());
            } catch (RuntimeException failure) {
                message(player, "Failed to register " + allowedName + " to " + player.getName() + ".");
                return;
            }
        }
        var startMap = AgentMapGatewayRuntime.map().resolveMap(
                player.getWorld(), player.getClient().getChannel(),
                MapleIslandSouthperryQuestCatalog.START_MAP_ID);
        Point startPosition = startMap.getPortal(0) == null
                ? new Point(startMap.getRandomPlayerSpawnpoint().getPosition())
                : new Point(startMap.getPortal(0).getPosition());
        AgentLifecycleService.AgentSpawnResult spawn = AgentInteractionRuntime.spawnStationaryAgentForLeaderAt(
                player, allowedName, startMap, startPosition);
        if (!spawn.success()) {
            message(player, spawn.errorMessage());
            return;
        }
        Character agent = spawn.agent();
        AgentPartyLifecycleService.leaveAgentParty(agent);
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(player.getId(), agent.getId());
        if (entry == null) {
            message(player, "The Agent runtime is unavailable.");
            return;
        }
        try {
            AmherstPlanCard card = AgentMapleIslandPlanRuntime.defaultCard();
            if (!reset(player, entry, agent, card, true)) {
                return;
            }
            int playerId = player.getId();
            int agentId = agent.getId();
            message(player, allowedName + " is ready in Amherst and will begin in 3 seconds.");
            TimerManager.getInstance().schedule(
                    () -> startAfterDelay(player, playerId, agentId, allowedName), 3_000L);
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            message(player, "Showcase failed to prepare: " + failure.getMessage());
        }
    }

    private static void startAfterDelay(Character player,
                                        int playerId,
                                        int agentId,
                                        String agentName) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(playerId, agentId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null) {
            message(player, agentName + " is no longer spawned.");
            return;
        }
        try {
            AgentMapleIslandPlanRuntime.startAuto(entry, agent, System.currentTimeMillis(),
                    event -> debugMessage(player, event));
            message(player, "Maple Island Southperry run started for " + agentName + ".");
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            message(player, "Showcase failed to start: " + failure.getMessage());
        }
    }

    private static void usage(Character player) {
        if (player != null) {
            message(player, "Usage: !mapleisland reset|start|next|status|run <AgentIGN>");
        }
    }

    private static void debugMessage(Character player, String event) {
        if (YamlConfig.config.server.AGENT_AMHERST_DEBUG_MESSAGES_ENABLED) {
            message(player, event);
        }
    }

    private static void message(Character player, String text) {
        player.yellowMessage("[Maple Island] " + text);
    }
}
