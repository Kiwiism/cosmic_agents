package server.agents.plans.mapleisland;

import client.Character;
import config.YamlConfig;
import server.TimerManager;
import server.agents.auth.AgentControlService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.presentation.AgentPresentationTelemetry;
import server.agents.capabilities.behavior.AgentBehaviorTelemetry;
import server.agents.capabilities.townlife.AgentTownLifeCommandService;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.capabilities.quest.AmherstTestResetMode;
import server.agents.capabilities.quest.AmherstTestResetRequest;
import server.agents.capabilities.quest.AmherstTestResetResult;
import server.agents.capabilities.quest.AmherstTestResetService;
import server.agents.plans.amherst.AmherstQuestCatalog;
import server.agents.capabilities.quest.MapleIslandSouthperryBaseline;
import server.agents.plans.amherst.MapleIslandSouthperryQuestCatalog;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.plans.amherst.AmherstObjectiveProgressStatus;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanExecutionState;
import server.agents.plans.amherst.AmherstPlanProgressSnapshot;
import server.agents.plans.amherst.AmherstPlanValidationException;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolRegistry;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortRealismMode;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortRunService;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortRuntime;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortTelemetryService;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.profiles.AgentBehaviorProfileRuntime;
import server.quest.Quest;

import java.awt.Point;
import java.io.IOException;
import java.util.TreeSet;

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
        if (player == null || params == null || params.length == 0) {
            usage(player, route);
            return;
        }
        String verb = params[0].toLowerCase();
        if (route == Route.FULL_MAPLE_ISLAND && executeCohortCommand(player, params, verb)) {
            return;
        }
        if (params.length < 2) {
            usage(player, route);
            return;
        }
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
                case "resume" -> resume(player, entry, agent, route);
                case "next" -> next(player, entry, agent, route);
                case "status" -> status(player, entry, agent, card, route);
                default -> usage(player, route);
            }
        } catch (IOException | AmherstPlanValidationException | IllegalArgumentException failure) {
            message(player, "Command failed: " + failure.getMessage());
        }
    }

    private static boolean executeCohortCommand(Character player, String[] params, String verb) {
        try {
            if (verb.equals("run") && params.length >= 3) {
                runCohort(player, params);
                return true;
            }
            if (verb.equals("status") && params.length == 1) {
                cohortStatus(player, MapleIslandCohortRuntime.instance());
                return true;
            }
            if (verb.equals("stats") && params.length == 1) {
                cohortStats(player, MapleIslandCohortRuntime.instance());
                return true;
            }
            if (verb.equals("radii")) {
                cohortNpcRadii(player, params);
                return true;
            }
            if (verb.equals("lithharbor") || verb.equals("lith")) {
                lithHarborTownLife(player, params);
                return true;
            }
            if (verb.equals("resetme") && params.length == 1) {
                resetPlayerMapleIslandQuests(player);
                return true;
            }
            if (verb.equals("pool")) {
                cohortPool(player, params);
                return true;
            }
            if (verb.equals("cancel") && params.length == 1) {
                MapleIslandCohortRuntime runtime = MapleIslandCohortRuntime.instance();
                MapleIslandCohortRunService.Status status = runtime.cancel(
                        player.getWorld(), player.getClient().getChannel());
                message(player, "Future cohort waves cancelled; already launched Agents keep running.");
                cohortStatus(player, runtime, status);
                return true;
            }
            if (verb.equals("stop") && params.length == 1) {
                MapleIslandCohortRuntime runtime = MapleIslandCohortRuntime.instance();
                MapleIslandCohortRunService.Status status = runtime.stop(
                        player.getWorld(), player.getClient().getChannel());
                message(player, "Cohort stop queued; Agents will disconnect and leases will be released.");
                cohortStatus(player, runtime, status);
                return true;
            }
            return false;
        } catch (IOException | RuntimeException failure) {
            message(player, "Cohort command failed: " + failure.getMessage());
            return true;
        }
    }

    private static void runCohort(Character player, String[] params) throws IOException {
        CohortRunArguments arguments = parseCohortRunArguments(params);
        MapleIslandCohortRuntime runtime = MapleIslandCohortRuntime.instance();
        MapleIslandCohortRunService.Status status = runtime.start(
                new MapleIslandCohortRunService.StartRequest(
                        player.getId(), player.getWorld(), player.getClient().getChannel(),
                        arguments.total(), arguments.batch(), arguments.intervalSeconds(),
                        arguments.seed(), arguments.realismMode()));
        message(player, "Cohort accepted: " + arguments.total() + " Agents in waves of "
                + arguments.batch() + " spread across each " + arguments.intervalSeconds()
                + "s; seed=" + status.runSeed()
                + "; realism=" + status.realismMode() + ".");
        cohortStatus(player, runtime, status);
    }

    static CohortRunArguments parseCohortRunArguments(String[] params) {
        if (params.length < 4 || params.length > 6) {
            throw new IllegalArgumentException(
                    "Usage: !mapleisland run <total> <batch> <intervalSeconds> [seed] [off|light|full]");
        }
        int total = parseInt(params[1], "total");
        int batch = parseInt(params[2], "batch");
        int intervalSeconds = parseInt(params[3], "intervalSeconds");
        Long seed = null;
        MapleIslandCohortRealismMode realismMode = MapleIslandCohortRealismMode.LIGHT;
        if (params.length == 5) {
            try {
                seed = Long.parseLong(params[4]);
            } catch (NumberFormatException ignored) {
                realismMode = MapleIslandCohortRealismMode.parse(params[4]);
            }
        } else if (params.length == 6) {
            seed = parseLong(params[4], "seed");
            realismMode = MapleIslandCohortRealismMode.parse(params[5]);
        }
        return new CohortRunArguments(total, batch, intervalSeconds, seed, realismMode);
    }

    record CohortRunArguments(int total,
                              int batch,
                              int intervalSeconds,
                              Long seed,
                              MapleIslandCohortRealismMode realismMode) {
    }

    private static void cohortStatus(Character player, MapleIslandCohortRuntime runtime) {
        MapleIslandCohortRunService.Status status = runtime.status(
                player.getWorld(), player.getClient().getChannel());
        if (status == null) {
            message(player, "No cohort session exists on this channel.");
        } else {
            cohortStatus(player, runtime, status);
            return;
        }
        cohortPoolStatus(player, runtime.poolStats());
    }

    private static void cohortStatus(Character player,
                                     MapleIslandCohortRuntime runtime,
                                     MapleIslandCohortRunService.Status status) {
        message(player, "Cohort " + status.state() + ": launched=" + status.launched()
                + "/" + status.requested() + ", pending=" + status.pending()
                + ", running=" + status.running() + ", complete=" + status.completed()
                + ", failed=" + (status.failedStarts() + status.failedRuns())
                + ", missing=" + status.missing() + ".");
        message(player, "Session=" + status.sessionId() + "; batch=" + status.batch()
                + "; interval=" + status.intervalSeconds() + "s; seed=" + status.runSeed()
                + "; realism=" + status.realismMode()
                + "; admissionDeferrals=" + status.admissionDeferrals() + ".");
        if (!status.lastError().isBlank()) {
            message(player, "Last cohort error: " + status.lastError());
        }
        MapleIslandCohortTelemetryService.Snapshot snapshot = runtime.telemetry(
                player.getWorld(), player.getClient().getChannel());
        if (snapshot != null && snapshot.trackedAgents() > 0) {
            message(player, "Milestones: Amherst=" + snapshot.amherst().samples()
                    + ", Southperry=" + snapshot.southperry().samples()
                    + ", full-run=" + snapshot.completion().samples()
                    + "/" + snapshot.trackedAgents() + ".");
            message(player, "Recovery signals: retries=" + snapshot.retries()
                    + ", timeouts=" + snapshot.timeouts() + ", blocked=" + snapshot.blocks()
                    + ", failures=" + snapshot.failures()
                    + ", unstucks=" + snapshot.movementUnstucks() + ".");
        }
        cohortPoolStatus(player, runtime.poolStats());
    }

    private static void cohortPoolStatus(Character player, MapleIslandCohortPoolRegistry.Stats stats) {
        message(player, "Reusable pool: accounts=" + stats.accounts() + ", total=" + stats.total()
                + ", available=" + stats.available() + ", leased=" + stats.leased()
                + ", active=" + stats.active() + ", broken=" + stats.broken() + ".");
    }

    private static void cohortPool(Character player, String[] params) {
        if (params.length > 2) {
            throw new IllegalArgumentException("Usage: !mapleisland pool [page]");
        }
        int requestedPage = params.length == 2 ? parseInt(params[1], "page") : 1;
        if (requestedPage < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        MapleIslandCohortRuntime runtime = MapleIslandCohortRuntime.instance();
        MapleIslandCohortPoolSnapshot snapshot = runtime.poolSnapshot();
        final int pageSize = 8;
        int pageCount = Math.max(1, (snapshot.agents().size() + pageSize - 1) / pageSize);
        if (requestedPage > pageCount) {
            throw new IllegalArgumentException("page must be between 1 and " + pageCount);
        }
        cohortPoolStatus(player, runtime.poolStats());
        message(player, "Pool roster page " + requestedPage + "/" + pageCount
                + " (revision " + snapshot.revision() + "):");
        int start = (requestedPage - 1) * pageSize;
        int end = Math.min(snapshot.agents().size(), start + pageSize);
        if (start == end) {
            message(player, "Pool roster is empty; the first cohort run provisions it.");
            return;
        }
        for (MapleIslandCohortPoolSnapshot.Agent agent : snapshot.agents().subList(start, end)) {
            String session = agent.leaseSessionId().isBlank() ? "-" : agent.leaseSessionId();
            String template = agent.characterTemplateOrdinal() == null
                    ? "-" : agent.characterTemplateOrdinal().toString();
            message(player, agent.name() + " | " + agent.accountName() + " (" + agent.accountId()
                    + ") | W" + agent.world() + " | look=" + template
                    + " | " + agent.leaseState() + " | session=" + session);
        }
    }

    private static void cohortNpcRadii(Character player, String[] params) {
        if (params.length > 2) {
            throw new IllegalArgumentException("Usage: !mapleisland radii [page]");
        }
        int requestedPage = params.length == 2 ? parseInt(params[1], "page") : 1;
        final int pageSize = 6;
        var entries = MapleIslandNpcInteractionRadiusCatalog.entries();
        int pageCount = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        if (requestedPage < 1 || requestedPage > pageCount) {
            throw new IllegalArgumentException("page must be between 1 and " + pageCount);
        }
        message(player, "NPC cohort spread radii page " + requestedPage + "/" + pageCount
                + " (generic non-cohort click allowance="
                + server.agents.capabilities.npc.AgentNpcInteractionPolicy.DEFAULT_CLICK_RANGE_PX + " px):");
        int start = (requestedPage - 1) * pageSize;
        int end = Math.min(entries.size(), start + pageSize);
        for (var entry : entries.subList(start, end)) {
            int anchors = MapleIslandNpcInteractionAnchorCatalog
                    .anchors(entry.mapId(), entry.npcId()).size();
            message(player, entry.npcName() + " | " + entry.mapName() + " (" + entry.mapId()
                    + ") | offset=(" + signed(entry.centerOffsetX()) + ","
                    + signed(entry.centerOffsetY()) + ") | radius=" + entry.radiusPx()
                    + " px | curated=" + anchors);
        }
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private static void resetPlayerMapleIslandQuests(Character player) {
        TreeSet<Integer> questIds = new TreeSet<>(AmherstQuestCatalog.requiredQuestIdSet());
        questIds.addAll(MapleIslandSouthperryQuestCatalog.requiredQuestIdSet());
        for (Integer questId : questIds) {
            Quest.getInstance(questId).reset(player);
        }
        AgentCharacterGatewayRuntime.characters().save(player, false);
        message(player, "Reset " + questIds.size() + " Maple Island run quests for "
                + player.getName() + ". Level, stats, equipment, inventory, position, and unrelated quests were preserved.");
    }

    private static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(label + " must be a whole number");
        }
    }

    private static long parseLong(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(label + " must be a signed 64-bit whole number");
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

    private static void cohortStats(Character player, MapleIslandCohortRuntime runtime) {
        MapleIslandCohortTelemetryService.Snapshot snapshot = runtime.telemetry(
                player.getWorld(), player.getClient().getChannel());
        if (snapshot == null) {
            message(player, "No cohort telemetry exists on this channel.");
            return;
        }
        message(player, "Cohort stats: tracked=" + snapshot.trackedAgents()
                + "; realism=" + snapshot.realismMode() + "; all times are relative to each Agent's spawn.");
        milestone(player, "Amherst", snapshot.amherst(), snapshot.trackedAgents());
        milestone(player, "Southperry", snapshot.southperry(), snapshot.trackedAgents());
        milestone(player, "Full run", snapshot.completion(), snapshot.trackedAgents());
        message(player, "Recovery: retries=" + snapshot.retries()
                + ", timeouts=" + snapshot.timeouts() + ", blocked=" + snapshot.blocks()
                + ", failures=" + snapshot.failures()
                + ", live-state recoveries=" + snapshot.liveStateRecoveries()
                + ", movement unstucks=" + snapshot.movementUnstucks() + ".");
        if (snapshot.longestActiveObjective() != null) {
            var active = snapshot.longestActiveObjective();
            message(player, "Longest current objective: " + active.agentName() + " on "
                    + active.objectiveId() + " for " + duration(active.elapsedMs()) + ".");
        }
        for (var objective : snapshot.slowestObjectives()) {
            message(player, "Slow objective: " + objective.objectiveId() + " avg="
                    + duration(objective.averageMs()) + " over " + objective.samples()
                    + "; max=" + duration(objective.slowestMs())
                    + " (" + objective.slowestAgent() + ").");
        }
        AgentPresentationTelemetry.Snapshot presentation = AgentPresentationTelemetry.snapshot();
        String intents = presentation.executedByIntent().entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .map(entry -> entry.getKey().name().toLowerCase() + "=" + entry.getValue())
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        message(player, "Personality presentation: triggers=" + presentation.triggers()
                + ", scheduled=" + presentation.scheduled()
                + ", executed=" + presentation.executed()
                + ", observer-suppressed=" + presentation.observerSuppressed()
                + ", unsafe-blocked=" + presentation.unsafeBlocked()
                + ", coalesced=" + presentation.coalesced()
                + "; intents=" + (intents.isEmpty() ? "none" : intents) + ".");
        AgentBehaviorTelemetry.Snapshot behavior = AgentBehaviorTelemetry.snapshot();
        message(player, "Behavior policy: response-deferred=" + behavior.responseDeferred()
                + ", claim-alternatives=" + behavior.claimAlternatives()
                + ", crowd-rests=" + behavior.crowdRestStarted()
                + ", crowd-resumes=" + behavior.crowdRestResumed()
                + ", idle-presentations=" + behavior.idlePresentation()
                + ", expressions=" + behavior.expressionsShown()
                + ", expression-suppressed=" + behavior.expressionsSuppressed() + ".");
    }

    private static void lithHarborTownLife(Character player, String[] params) {
        if (params.length >= 2 && params[1].equalsIgnoreCase("test")) {
            lithHarborTownLifeTest(player, params);
            return;
        }
        if (params.length == 1 || (params.length == 2 && params[1].equalsIgnoreCase("start"))) {
            AgentTownLifeCommandService.Result result =
                    AgentTownLifeCommandService.startCompletedSouthperryAgents(
                            player, System.currentTimeMillis());
            message(player, "Lith Harbor town life started for " + result.started()
                    + " completed Southperry Agents; already active=" + result.alreadyActive()
                    + ", not eligible=" + result.notEligible() + ".");
            return;
        }
        if (params.length == 2 && params[1].equalsIgnoreCase("stop")) {
            message(player, "Stopped Lith Harbor town life for "
                    + AgentTownLifeCommandService.stop(player) + " Agents.");
            return;
        }
        if (params.length == 2 && params[1].equalsIgnoreCase("status")) {
            AgentTownLifeCommandService.Status status = AgentTownLifeCommandService.status(player);
            message(player, "Lith Harbor town life: total=" + status.total()
                    + ", traveling=" + status.traveling() + ", in town=" + status.inTown()
                    + ", in shops=" + status.inShops() + ".");
            return;
        }
        throw new IllegalArgumentException("Usage: !mapleisland lithharbor [start|status|stop]");
    }

    private static void lithHarborTownLifeTest(Character player, String[] params) {
        MapleIslandCohortRuntime runtime = MapleIslandCohortRuntime.instance();
        if (params.length == 3 && params[2].equalsIgnoreCase("status")) {
            lithHarborTownLifeTestStatus(player, runtime.lithHarborTownLifeTestStatus(
                    player.getWorld(), player.getClient().getChannel()));
            return;
        }
        if (params.length == 3 && params[2].equalsIgnoreCase("stop")) {
            MapleIslandCohortRunService.Status status = runtime.stopLithHarborTownLifeTest(
                    player.getWorld(), player.getClient().getChannel());
            message(player, "Lith Harbor test stop queued; Agents will disconnect and leases will be released.");
            lithHarborTownLifeTestStatus(player, status);
            return;
        }
        LithHarborTestArguments arguments = parseLithHarborTestArguments(params);
        try {
            MapleIslandCohortRunService.Status status = runtime.startLithHarborTownLifeTest(
                    new MapleIslandCohortRunService.StartRequest(
                            player.getId(), player.getWorld(), player.getClient().getChannel(),
                            arguments.total(), arguments.batch(), arguments.intervalSeconds(),
                            arguments.seed(), MapleIslandCohortRealismMode.OFF));
            message(player, "Lith Harbor test accepted: " + arguments.total()
                    + " Lv9/10 Agents arriving at the ship in waves of " + arguments.batch()
                    + " across each " + arguments.intervalSeconds() + "s; seed=" + status.runSeed() + ".");
            lithHarborTownLifeTestStatus(player, status);
        } catch (IOException | RuntimeException failure) {
            message(player, "Lith Harbor test failed: " + failure.getMessage());
        }
    }

    static LithHarborTestArguments parseLithHarborTestArguments(String[] params) {
        if (params.length < 3 || params.length > 6 || !params[1].equalsIgnoreCase("test")) {
            throw new IllegalArgumentException("Usage: !mapleisland lithharbor test "
                    + "<total> [batch] [intervalSeconds] [seed]");
        }
        int total = parseInt(params[2], "total");
        int batch = params.length >= 4 ? parseInt(params[3], "batch") : Math.min(5, total);
        int intervalSeconds = params.length >= 5 ? parseInt(params[4], "intervalSeconds") : 10;
        Long seed = params.length == 6 ? parseLong(params[5], "seed") : null;
        return new LithHarborTestArguments(total, batch, intervalSeconds, seed);
    }

    private static void lithHarborTownLifeTestStatus(
            Character player, MapleIslandCohortRunService.Status status) {
        if (status == null) {
            message(player, "No Lith Harbor town-life test exists on this channel.");
            return;
        }
        message(player, "Lith Harbor test: state=" + status.state()
                + ", launched=" + status.launched() + "/" + status.requested()
                + ", active=" + status.running() + ", pending=" + status.pending()
                + ", failed-starts=" + status.failedStarts()
                + (status.lastError().isBlank() ? "." : ", error=" + status.lastError() + "."));
    }

    record LithHarborTestArguments(int total, int batch, int intervalSeconds, Long seed) {
    }

    private static void milestone(Character player,
                                  String label,
                                  MapleIslandCohortTelemetryService.DurationSummary summary,
                                  int trackedAgents) {
        if (summary.samples() == 0) {
            message(player, label + ": 0/" + trackedAgents + " reached.");
            return;
        }
        message(player, label + ": " + summary.samples() + "/" + trackedAgents
                + " reached; avg=" + duration(summary.averageMs())
                + ", p50=" + duration(summary.medianMs()) + ", p95=" + duration(summary.p95Ms())
                + ", fastest=" + duration(summary.fastestMs()) + " (" + summary.fastestAgent() + ")"
                + ", slowest=" + duration(summary.slowestMs()) + " (" + summary.slowestAgent() + ").");
    }

    private static String duration(long durationMs) {
        long seconds = Math.max(0L, durationMs) / 1_000L;
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        return minutes == 0L ? remainingSeconds + "s" : minutes + "m " + remainingSeconds + "s";
    }

    private static void resume(Character player,
                               AgentRuntimeEntry entry,
                               Character agent,
                               Route route) throws IOException, AmherstPlanValidationException {
        AmherstPlanExecutionState state = entry.amherstPlanExecutionState();
        if (state.active()) {
            message(player, "The " + route.label + " plan is already active.");
            return;
        }
        route.startAuto(entry, agent, event -> debugMessage(player, event));
        message(player, route.label + " resumed from live quest and inventory state; completed objectives were skipped.");
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
        AgentControlService control = AgentControlService.getInstance();
        var resolved = control.resolveCharacterByName(allowedName);
        if (resolved == null) {
            message(player, "No character named '" + allowedName + "' exists.");
            return;
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
            if (route == Route.FULL_MAPLE_ISLAND) {
                CosmicMapleIslandCohortIdentity.applyDefaultStarterWeapon(agent);
                agent.equipChanged();
            }
            int playerId = player.getId();
            int agentId = agent.getId();
            long startDelayMs = beginOpeningApproach(entry, agent, route) ? 1_000L : 3_000L;
            message(player, allowedName + " is ready in " + route.startMapName
                    + (startDelayMs == 1_000L
                    ? ", is walking toward Heena, and will begin shortly."
                    : " and will begin in 3 seconds."));
            TimerManager.getInstance().schedule(
                    () -> startAfterDelay(player, playerId, agentId, allowedName, route), startDelayMs);
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            message(player, "Showcase failed to prepare: " + failure.getMessage());
        }
    }

    private static boolean beginOpeningApproach(AgentRuntimeEntry entry, Character agent, Route route) {
        if (route != Route.FULL_MAPLE_ISLAND || agent.getMap() == null) {
            return false;
        }
        var heena = agent.getMap().getNPCById(2101);
        if (heena == null) {
            return false;
        }
        AgentMovementCommandRuntime.moveTo(entry, new Point(heena.getPosition()), false);
        return true;
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
            if (route == Route.FULL_MAPLE_ISLAND) {
                // Reassert the starter weapon after the opening map placement.
                // The initial look update can otherwise arrive while clients are
                // still rebuilding the freshly reset Agent's field presence.
                CosmicMapleIslandCohortIdentity.applyDefaultStarterWeapon(agent);
                agent.equipChanged();
            }
            route.startAuto(entry, agent, event -> debugMessage(player, event));
            message(player, route.label + " run started for " + agentName + ".");
        } catch (IOException | AmherstPlanValidationException | RuntimeException failure) {
            message(player, "Showcase failed to start: " + failure.getMessage());
        }
    }

    private static void usage(Character player, Route route) {
        if (player != null) {
            message(player, "Usage: !" + route.command + " reset|start|resume|next|status|run <AgentIGN>");
            if (route == Route.FULL_MAPLE_ISLAND) {
                message(player, "Cohort: !mapleisland run <total> <batch> <intervalSeconds> [seed] [off|light|full]");
                message(player, "Self reset: !mapleisland resetme (Maple Island run quests only)");
                message(player, "Cohort control: !mapleisland status|stats|pool [page]|radii [page]|cancel|stop");
                message(player, "Town life: !mapleisland lithharbor [start|status|stop]");
                message(player, "Town load test: !mapleisland lithharbor test "
                        + "<total> [batch] [intervalSeconds] [seed]");
            }
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
