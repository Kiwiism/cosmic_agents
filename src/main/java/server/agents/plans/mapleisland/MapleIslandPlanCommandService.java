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
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.capabilities.quest.MapleIslandSouthperryBaseline;
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
import server.agents.profiles.AgentBehaviorProfileRuntime;

import java.awt.Point;
import java.io.IOException;

public final class MapleIslandPlanCommandService {
    private MapleIslandPlanCommandService() {
    }

    public static void execute(Character player, String[] params) {
        execute(player, params, Route.FULL_MAPLE_ISLAND);
    }

    public static void executeSouthperry(Character player, String[] params) {
        execute(player, params, Route.SOUTHPERRY);
    }

    private static void execute(Character player, String[] params, Route route) {
        if (player == null || params == null || params.length < 2) {
            usage(player, route);
            return;
        }
        String verb = params[0].toLowerCase();
        if (verb.equals("run")) {
            run(player, params[1], route);
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
            AmherstPlanCard card = route.card();
            switch (verb) {
                case "reset" -> reset(player, entry, agent, card, false, route);
                case "start" -> start(player, entry, agent, route);
                case "next" -> next(player, entry, agent, route);
                case "status" -> status(player, entry, agent, card, route);
                default -> usage(player, route);
            }
        } catch (IOException | AmherstPlanValidationException | IllegalArgumentException failure) {
            message(player, "Command failed: " + failure.getMessage());
        }
    }

    private static boolean reset(Character player,
                                 AgentRuntimeEntry entry,
                                 Character agent,
                                 AmherstPlanCard card,
                                 boolean showcase,
                                 Route route) throws IOException {
        AmherstTestResetResult result = (showcase
                ? AmherstTestResetService.showcaseHarness(true, agent.getName())
                : AmherstTestResetService.configuredHarness()).reset(
                new AmherstTestResetRequest(agent.getId(), agent.getName(),
                        route.resetMode, 0));
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
        if (route == Route.FULL_MAPLE_ISLAND) {
            message(player, "Clean level-1 Mushroom Town baseline restored. 0/"
                    + card.objectives().size() + " objectives satisfied.");
        } else {
            message(player, "Southperry baseline restored. 0/" + card.objectives().size()
                    + " objectives satisfied; "
                    + MapleIslandSouthperryBaseline.snapshot().completedQuestIds().size()
                    + " Amherst quests verified complete.");
        }
        return true;
    }

    private static void start(Character player,
                              AgentRuntimeEntry entry,
                              Character agent,
                              Route route) throws IOException, AmherstPlanValidationException {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        if (state.active()) {
            message(player, state.waitingForAdvance()
                    ? "Manual plan is paused. Use !" + route.command + " next " + agent.getName() + "."
                    : "A Maple Island objective is already active.");
            return;
        }
        route.startManual(entry, agent, event -> debugMessage(player, event));
        message(player, "Manual " + route.label + " plan started. Exactly one objective will execute.");
    }

    private static void next(Character player, AgentRuntimeEntry entry, Character agent, Route route) {
        if (AgentMapleIslandPlanRuntime.requestNext(entry)) {
            message(player, "Next objective authorized.");
            return;
        }
        if (entry.capabilityRuntimeState().hasActiveCapability()) {
            message(player, "The current objective is still running: "
                    + entry.capabilityRuntimeState().activeCapabilityId());
        } else {
            message(player, "No paused manual plan. Use !" + route.command
                    + " start " + agent.getName() + ".");
        }
    }

    private static void status(Character player,
                               AgentRuntimeEntry entry,
                               Character agent,
                               AmherstPlanCard card,
                               Route route) throws IOException {
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
        message(player, route.label + " plan: " + session + "; objectives=" + satisfied
                + "/" + card.objectives().size() + "; map=" + agent.getMapId() + ".");
        message(player, "Quest states: 1046=" + agent.getQuestStatus(1046)
                + " (must be active), 1028=" + agent.getQuestStatus(1028)
                + " (must not be complete). Lv" + agent.getLevel() + " EXP " + agent.getExp() + ".");
        AgentBehaviorProfileRuntime.current(entry).ifPresent(profile -> message(player,
                "Behavior profile: " + profile.profileId() + " v" + profile.profileVersion()
                        + "; NPC delay=" + format(profile.presentation().timing().beforeNpcInteractionMs())
                        + "ms; objective delay=" + format(
                        profile.presentation().timing().betweenObjectivesMs()) + "ms."));
        if (!state.lastError().isBlank()) {
            message(player, "Last error: " + state.lastError());
        }
    }

    private static void run(Character player, String agentName, Route route) {
        String allowedName = route.configuredAgentName();
        if (!route.showcaseEnabled()
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
                route.startMapId);
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
            AmherstPlanCard card = route.card();
            if (!reset(player, entry, agent, card, true, route)) {
                return;
            }
            int playerId = player.getId();
            int agentId = agent.getId();
            message(player, allowedName + " is ready in " + route.startMapName
                    + " and will begin in 3 seconds.");
            TimerManager.getInstance().schedule(
                    () -> startAfterDelay(player, playerId, agentId, allowedName, route), 3_000L);
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            message(player, "Showcase failed to prepare: " + failure.getMessage());
        }
    }

    private static void startAfterDelay(Character player,
                                        int playerId,
                                        int agentId,
                                        String agentName,
                                        Route route) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(playerId, agentId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null) {
            message(player, agentName + " is no longer spawned.");
            return;
        }
        try {
            route.startAuto(entry, agent, event -> debugMessage(player, event));
            message(player, route.label + " run started for " + agentName + ".");
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            message(player, "Showcase failed to start: " + failure.getMessage());
        }
    }

    private static void usage(Character player, Route route) {
        if (player != null) {
            message(player, "Usage: !" + route.command + " reset|start|next|status|run <AgentIGN>");
        }
    }

    private static void debugMessage(Character player, String event) {
        if (YamlConfig.config.server.AGENT_AMHERST_DEBUG_MESSAGES_ENABLED) {
            message(player, event);
        }
    }

    private static String format(server.agents.profiles.AgentBehaviorProfile.DelayRange range) {
        return range.min() + "-" + range.max();
    }

    private static void message(Character player, String text) {
        player.yellowMessage("[Maple Island] " + text);
    }

    private enum Route {
        FULL_MAPLE_ISLAND("Maple Island", "mapleisland", "Mushroom Town",
                AmherstTestResetMode.CLEAN_LV1_START, AmherstQuestCatalog.START_MAP_ID),
        SOUTHPERRY("Southperry", "southperry", "Amherst",
                AmherstTestResetMode.SOUTHPERRY_MVP_START, MapleIslandSouthperryQuestCatalog.START_MAP_ID);

        private final String label;
        private final String command;
        private final String startMapName;
        private final AmherstTestResetMode resetMode;
        private final int startMapId;

        Route(String label,
              String command,
              String startMapName,
              AmherstTestResetMode resetMode,
              int startMapId) {
            this.label = label;
            this.command = command;
            this.startMapName = startMapName;
            this.resetMode = resetMode;
            this.startMapId = startMapId;
        }

        private boolean showcaseEnabled() {
            return this == FULL_MAPLE_ISLAND
                    ? YamlConfig.config.server.AGENT_MAPLE_ISLAND_SHOWCASE_ENABLED
                    : YamlConfig.config.server.AGENT_SOUTHPERRY_SHOWCASE_ENABLED;
        }

        private String configuredAgentName() {
            return this == FULL_MAPLE_ISLAND
                    ? YamlConfig.config.server.AGENT_MAPLE_ISLAND_SHOWCASE_AGENT_NAME
                    : YamlConfig.config.server.AGENT_SOUTHPERRY_SHOWCASE_AGENT_NAME;
        }

        private AmherstPlanCard card() throws IOException, AmherstPlanValidationException {
            return this == FULL_MAPLE_ISLAND
                    ? AgentMapleIslandPlanRuntime.fullCard()
                    : AgentMapleIslandPlanRuntime.defaultCard();
        }

        private void startManual(AgentRuntimeEntry entry,
                                 Character agent,
                                 server.agents.plans.amherst.AmherstPlanObserver observer)
                throws IOException, AmherstPlanValidationException {
            if (this == FULL_MAPLE_ISLAND) {
                AgentMapleIslandPlanRuntime.startFullManual(
                        entry, agent, System.currentTimeMillis(), observer);
            } else {
                AgentMapleIslandPlanRuntime.startManual(
                        entry, agent, System.currentTimeMillis(), observer);
            }
        }

        private void startAuto(AgentRuntimeEntry entry,
                               Character agent,
                               server.agents.plans.amherst.AmherstPlanObserver observer)
                throws IOException, AmherstPlanValidationException {
            if (this == FULL_MAPLE_ISLAND) {
                AgentMapleIslandPlanRuntime.startFullAuto(
                        entry, agent, System.currentTimeMillis(), observer);
            } else {
                AgentMapleIslandPlanRuntime.startAuto(
                        entry, agent, System.currentTimeMillis(), observer);
            }
        }
    }
}
