package server.agents.plans.amherst;

import client.Character;
import config.YamlConfig;
import constants.id.ItemId;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.quest.AmherstTestResetMode;
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.capabilities.quest.AmherstTestResetRequest;
import server.agents.capabilities.quest.AmherstTestResetResult;
import server.agents.capabilities.quest.AmherstTestResetService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.profiles.AgentBehaviorProfileRuntime;

import java.awt.Point;
import java.io.IOException;
import java.util.List;

public final class AmherstPlanCommandService {
    private static final int PAGE_SIZE = 10;
    private static final int MAX_JOURNAL_LINES = 20;

    private AmherstPlanCommandService() {
    }

    public static void execute(Character player, String[] params) {
        if (player == null || params == null || params.length < 2) {
            usage(player);
            return;
        }
        String verb = params[0].toLowerCase();
        if (verb.equals("run")) {
            showcase(player, params[1]);
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

        if (mutatesAgent(verb)) {
            AgentMailboxRuntime.dispatch(entry, ignored -> {
                executeMutating(player, entry, agent, verb);
                return null;
            });
            return;
        }
        try {
            AmherstPlanCard card = AgentAmherstPlanRuntime.defaultCard();
            switch (verb) {
                case "status" -> status(player, entry, agent, card);
                case "list" -> list(player, agent, card, page(params));
                case "journal" -> journal(player, agent, card, journalCount(params));
                default -> usage(player);
            }
        } catch (IOException | AmherstPlanValidationException | IllegalArgumentException failure) {
            message(player, "Command failed: " + failure.getMessage());
        }
    }

    private static boolean mutatesAgent(String verb) {
        return switch (verb) {
            case "reset", "start", "resume", "next", "retry", "cancel", "sit", "stand" -> true;
            default -> false;
        };
    }

    private static void executeMutating(
            Character player,
            AgentRuntimeEntry entry,
            Character agent,
            String verb) {
        try {
            AmherstPlanCard card = AgentAmherstPlanRuntime.defaultCard();
            switch (verb) {
                case "reset" -> reset(player, entry, agent, card);
                case "start", "resume" -> start(player, entry, agent);
                case "next", "retry" -> next(player, entry);
                case "cancel" -> cancel(player, entry);
                case "sit" -> sit(player, entry, agent);
                case "stand" -> stand(player, entry, agent);
                default -> throw new IllegalArgumentException("Unsupported mutating command: " + verb);
            }
        } catch (IOException | AmherstPlanValidationException | IllegalArgumentException failure) {
            message(player, "Command failed: " + failure.getMessage());
        }
    }

    private static void reset(Character player,
                              AgentRuntimeEntry entry,
                              Character agent,
                              AmherstPlanCard card) throws IOException {
        AmherstTestResetResult result = AmherstTestResetService.configuredHarness().reset(
                new AmherstTestResetRequest(agent.getId(), agent.getName(),
                        AmherstTestResetMode.AMHERST_MVP_CLEAN, 0));
        if (!result.allowed()) {
            message(player, "Reset blocked [" + result.status() + "]: " + result.message());
            return;
        }
        if (!AgentAmherstPlanRuntime.clearSession(entry)) {
            message(player, "Reset completed, but an active capability is still closing. Try status again.");
            return;
        }
        AgentAmherstPlanRuntime.defaultStore().delete(card.planId(), agent.getId());
        message(player, "Plan and Agent reset complete. 0/" + card.objectives().size()
                + " objectives satisfied.");
        if (debugMessagesEnabled()) {
            printObjectives(player, card, AmherstPlanProgressSnapshot.empty(card.planId(), agent.getId()),
                    0, card.objectives().size());
        }
        message(player, "Use !amherst start " + agent.getName() + " to execute objective 1 only.");
    }

    private static void start(Character player,
                              AgentRuntimeEntry entry,
                              Character agent) throws IOException, AmherstPlanValidationException {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        if (state.active()) {
            message(player, state.waitingForAdvance()
                    ? "Manual plan is already paused. Use !amherst next " + agent.getName() + "."
                    : "An Amherst objective is already active.");
            return;
        }
        AgentAmherstPlanRuntime.startManual(entry, agent, System.currentTimeMillis(),
                event -> debugMessage(player, event));
        message(player, "Manual plan started. Exactly one objective will execute.");
    }

    private static void showcase(Character player, String agentName) {
        String allowedName = YamlConfig.config.server.AGENT_AMHERST_SHOWCASE_AGENT_NAME;
        if (!YamlConfig.config.server.AGENT_AMHERST_SHOWCASE_ENABLED
                || allowedName == null || !allowedName.equalsIgnoreCase(agentName)) {
            message(player, "Showcase run is disabled or '" + agentName
                    + "' is not the configured showcase Agent.");
            return;
        }
        String showcaseAgentName = allowedName;
        AgentOwnershipService ownership = AgentOwnershipService.getInstance();
        var resolved = ownership.resolveCharacterByName(showcaseAgentName);
        if (resolved == null) {
            message(player, "No character named '" + showcaseAgentName + "' exists.");
            return;
        }
        Integer registeredOwnerId = ownership.getRegisteredOwnerId(resolved.id());
        if (registeredOwnerId == null) {
            try {
                ownership.registerOwner(resolved.id(), player.getId());
            } catch (RuntimeException failure) {
                message(player, "Failed to register " + showcaseAgentName + " to " + player.getName() + ".");
                return;
            }
        }
        var startMap = AgentMapGatewayRuntime.map().resolveMap(
                player.getWorld(), player.getClient().getChannel(), AmherstQuestCatalog.START_MAP_ID);
        Point startPosition = startMap.getPortal(0) != null
                ? new Point(startMap.getPortal(0).getPosition())
                : new Point(startMap.getRandomPlayerSpawnpoint().getPosition());
        AgentLifecycleService.AgentSpawnResult spawn = AgentInteractionRuntime.spawnStationaryAgentForLeaderAt(
                player, showcaseAgentName, startMap, startPosition);
        if (!spawn.success()) {
            message(player, spawn.errorMessage());
            return;
        }
        Character agent = spawn.agent();
        int playerId = player.getId();
        int agentId = agent.getId();
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(playerId, agentId);
        if (entry == null) {
            message(player, "Showcase could not prepare because the Agent runtime is unavailable.");
            return;
        }
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            try {
                prepareShowcase(entry, agent);
                message(player, showcaseAgentName
                        + " spawned at the clean start and will begin in 3 seconds.");
                AgentSchedulerRuntime.schedule(entry,
                        () -> startShowcaseAfterDelay(player, playerId, agentId, showcaseAgentName), 3_000L);
            } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
                message(player, "Showcase failed to prepare: " + failure.getMessage());
            }
            return null;
        });
    }

    private static void prepareShowcase(AgentRuntimeEntry entry,
                                        Character agent) throws IOException, AmherstPlanValidationException {
        AmherstPlanCard card = AgentAmherstPlanRuntime.defaultCard();
        AmherstTestResetResult reset = AmherstTestResetService.showcaseHarness().reset(
                new AmherstTestResetRequest(agent.getId(), agent.getName(),
                        AmherstTestResetMode.AMHERST_MVP_CLEAN, 0));
        if (!reset.allowed()) {
            throw new IllegalStateException("reset blocked [" + reset.status() + "]: " + reset.message());
        }
        if (!AgentAmherstPlanRuntime.clearSession(entry)) {
            throw new IllegalStateException("the previous capability is still closing");
        }
        CosmicMapleIslandCohortIdentity.applyDefaultStarterWeapon(agent);
        agent.equipChanged();
        AgentAmherstPlanRuntime.defaultStore().delete(card.planId(), agent.getId());
        AgentMovementCommandRuntime.stop(entry);
    }

    private static void startShowcaseAfterDelay(Character player,
                                                int playerId,
                                                int agentId,
                                                String agentName) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(playerId, agentId);
        if (entry == null) {
            message(player, "Showcase could not start because " + agentName + " is no longer spawned.");
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        try {
            AgentAmherstPlanRuntime.startAuto(entry, agent, System.currentTimeMillis(),
                    event -> debugMessage(player, event));
            message(player, "Amherst showcase started for " + agentName + ".");
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            message(player, "Showcase failed to start: " + failure.getMessage());
        }
    }

    private static void next(Character player, AgentRuntimeEntry entry) {
        if (AgentAmherstPlanRuntime.requestNext(entry)) {
            message(player, "Next objective authorized. No later objective will start automatically.");
            return;
        }
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        if (!state.active()) {
            message(player, "No manual plan session is active. Use !amherst start <AgentIGN>.");
        } else if (entry.capabilityRuntimeState().hasActiveCapability()) {
            message(player, "The current objective is still running: "
                    + entry.capabilityRuntimeState().activeCapabilityId());
        } else {
            message(player, "The plan is not waiting for manual advance.");
        }
    }

    private static void cancel(Character player, AgentRuntimeEntry entry) {
        if (!entry.amherstPlanExecutionState().active()) {
            message(player, "No Amherst plan session is active.");
            return;
        }
        if (!entry.capabilityRuntimeState().hasActiveCapability()) {
            message(player, "The manual plan is already paused between objectives.");
            return;
        }
        AgentAmherstPlanRuntime.cancel(entry);
        message(player, "Cancellation requested. Progress remains resumable after the terminal result is recorded.");
    }

    private static void status(Character player,
                               AgentRuntimeEntry entry,
                               Character agent,
                               AmherstPlanCard card) throws IOException {
        AmherstPlanProgressSnapshot snapshot = snapshot(entry, agent, card);
        long satisfied = satisfied(snapshot);
        long completedQuests = card.requiredQuestIds().stream()
                .filter(questId -> agent.getQuestStatus(questId) == 2)
                .count();
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        String session = state.completed() ? "COMPLETE"
                : state.active() ? state.waitingForAdvance() ? "PAUSED" : "RUNNING"
                : "NOT STARTED";
        message(player, "Plan " + card.planId() + ": " + session + " mode=" + state.mode());
        message(player, "Overall: " + satisfied + "/" + card.objectives().size()
                + " objectives; " + completedQuests + "/" + card.requiredQuestIds().size()
                + " required quests; map=" + agent.getMapId() + ".");
        message(player, "Agent progress: Lv" + agent.getLevel() + " EXP " + agent.getExp() + ".");
        AgentBehaviorProfileRuntime.current(entry).ifPresent(profile -> message(player,
                "Behavior profile: " + profile.profileId() + " v" + profile.profileVersion()
                        + "; NPC delay=" + format(profile.presentation().timing().beforeNpcInteractionMs())
                        + "ms; objective delay=" + format(
                        profile.presentation().timing().betweenObjectivesMs()) + "ms."));
        if (state.assignedObjectiveId() != null) {
            AmherstPlanObjective current = objective(card, state.assignedObjectiveId());
            message(player, "Current: " + AmherstObjectiveFormatter.numbered(card, current));
            message(player, "Child: " + entry.capabilityRuntimeState().activeCapabilityId());
        } else {
            AmherstPlanObjective next = firstUnsatisfied(card, snapshot);
            message(player, next == null ? "Next: none." : "Next: " + AmherstObjectiveFormatter.numbered(card, next));
        }
        if (!state.lastError().isBlank()) {
            message(player, "Last error: " + state.lastError());
        }
    }

    private static void list(Character player,
                             Character agent,
                             AmherstPlanCard card,
                             int page) throws IOException {
        AmherstPlanProgressSnapshot snapshot = AgentAmherstPlanRuntime.defaultStore()
                .load(card.planId(), agent.getId());
        int pages = Math.max(1, (card.objectives().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int safePage = Math.max(1, Math.min(page, pages));
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, card.objectives().size());
        message(player, "Objectives page " + safePage + "/" + pages + ":");
        printObjectives(player, card, snapshot, from, to);
    }

    private static void journal(Character player,
                                Character agent,
                                AmherstPlanCard card,
                                int count) throws IOException {
        AmherstPlanProgressSnapshot snapshot = AgentAmherstPlanRuntime.defaultStore()
                .load(card.planId(), agent.getId());
        List<AmherstPlanJournalEvent> journal = snapshot.journal();
        int from = Math.max(0, journal.size() - count);
        message(player, "Last " + (journal.size() - from) + " plan journal event(s):");
        for (int i = from; i < journal.size(); i++) {
            AmherstPlanJournalEvent event = journal.get(i);
            message(player, event.type() + " " + event.objectiveId() + " [" + event.reasonCode()
                    + "] " + event.message());
        }
    }

    private static AmherstPlanProgressSnapshot snapshot(AgentRuntimeEntry entry,
                                                        Character agent,
                                                        AmherstPlanCard card) throws IOException {
        AmherstPlanProgressSnapshot live = entry.amherstPlanExecutionState().progress();
        return live == null ? AgentAmherstPlanRuntime.defaultStore().load(card.planId(), agent.getId()) : live;
    }

    private static void printObjectives(Character player,
                                        AmherstPlanCard card,
                                        AmherstPlanProgressSnapshot snapshot,
                                        int from,
                                        int to) {
        for (int i = from; i < to; i++) {
            AmherstPlanObjective objective = card.objectives().get(i);
            AmherstObjectiveProgress progress = snapshot.objectives().getOrDefault(
                    objective.objectiveId(), AmherstObjectiveProgress.pending(objective.objectiveId()));
            message(player, (i + 1) + "/" + card.objectives().size() + " " + progress.status()
                    + ": " + AmherstObjectiveFormatter.describe(objective));
        }
    }

    private static AmherstPlanObjective firstUnsatisfied(AmherstPlanCard card,
                                                         AmherstPlanProgressSnapshot snapshot) {
        return card.objectives().stream()
                .filter(objective -> snapshot.objectives().getOrDefault(objective.objectiveId(),
                        AmherstObjectiveProgress.pending(objective.objectiveId())).status()
                        != AmherstObjectiveProgressStatus.SATISFIED)
                .findFirst().orElse(null);
    }

    private static AmherstPlanObjective objective(AmherstPlanCard card, String objectiveId) {
        return card.objectives().stream()
                .filter(objective -> objective.objectiveId().equals(objectiveId))
                .findFirst().orElseThrow();
    }

    private static long satisfied(AmherstPlanProgressSnapshot snapshot) {
        return snapshot.objectives().values().stream()
                .filter(progress -> progress.status() == AmherstObjectiveProgressStatus.SATISFIED)
                .count();
    }

    private static int page(String[] params) {
        return params.length >= 3 ? Integer.parseInt(params[2]) : 1;
    }

    private static int journalCount(String[] params) {
        int requested = params.length >= 3 ? Integer.parseInt(params[2]) : 10;
        return Math.max(1, Math.min(requested, MAX_JOURNAL_LINES));
    }

    private static void sit(Character player, AgentRuntimeEntry entry, Character agent) {
        if (!AgentChairService.sit(entry, agent, ItemId.RELAXER)) {
            message(player, agent.getName() + " could not enter the Relaxer chair state.");
            return;
        }
        message(player, agent.getName() + " is sitting on the Relaxer (chair=" + agent.getChair() + ").");
    }

    private static void stand(Character player, AgentRuntimeEntry entry, Character agent) {
        boolean standing = AgentChairService.stand(entry, agent);
        message(player, agent.getName() + (standing ? " is standing." : " is still seated."));
    }

    private static void usage(Character player) {
        if (player != null) {
            message(player, "Usage: !amherst run|reset|start|resume|next|retry|status|list|cancel|journal|sit|stand "
                    + "<AgentIGN> [page/count]");
        }
    }

    private static void debugMessage(Character player, String message) {
        if (debugMessagesEnabled()) {
            message(player, message);
        }
    }

    private static boolean debugMessagesEnabled() {
        return YamlConfig.config.server.AGENT_AMHERST_DEBUG_MESSAGES_ENABLED;
    }

    private static void message(Character player, String message) {
        player.yellowMessage("[Amherst] " + message);
    }

    private static String format(server.agents.profiles.AgentBehaviorProfile.DelayRange range) {
        return range.min() + "-" + range.max();
    }
}
