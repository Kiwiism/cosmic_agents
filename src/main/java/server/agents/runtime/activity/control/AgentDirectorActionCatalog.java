package server.agents.runtime.activity.control;

import client.Character;
import server.agents.capabilities.contracts.AgentSupplyUrgency;
import server.agents.capabilities.supplies.AgentResourcePlanningState;
import server.agents.capabilities.supplies.AgentSupplyProcurementState;
import server.agents.plans.AgentPlanRepository;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.progression.AgentVictoriaTrainingCatalog;
import server.agents.progression.AgentVictoriaTrainingCatalogRepository;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.binding.AgentStandardWorldActivityBindingResolver;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityIntent;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldBaselineProposalProvider;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldDirectorPhase;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;
import server.agents.runtime.activity.world.AgentWorldMilestoneEvaluator;
import server.agents.runtime.activity.world.AgentWorldMilestone;
import server.agents.runtime.activity.world.AgentWorldMilestoneSnapshot;
import server.agents.runtime.activity.world.AgentWorldProposalProvider;
import server.agents.integration.AgentEconomyRuntime;
import server.agents.progression.AgentCareerBuildBundleRepository;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.progression.AgentVictoriaIndividualQuestCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Produces explainable actions without mutating any child system. */
public final class AgentDirectorActionCatalog {
    private final AgentWorldProposalProvider proposals;

    public AgentDirectorActionCatalog() {
        this(new AgentWorldBaselineProposalProvider());
    }

    public AgentDirectorActionCatalog(AgentWorldProposalProvider proposals) {
        if (proposals == null) throw new IllegalArgumentException("proposal provider is required");
        this.proposals = proposals;
    }

    public List<AgentDirectorAction> actions(
            AgentRuntimeEntry entry,
            Character agent,
            AgentWorldContext context,
            AgentWorldDirectorSession session) {
        List<AgentDirectorAction> result = new ArrayList<>();
        boolean operatorEnabled = session.mode().acceptsOperatorDirectives();
        AgentWorldMilestoneSnapshot milestones = AgentWorldMilestoneEvaluator.evaluate(context);
        for (AgentWorldActivityIntent intent : proposals.propose(context, milestones)) {
            result.add(activity(intent, operatorEnabled));
        }
        for (String successor : AgentUniversalPlanRuntime.availableSuccessors(entry)) {
            boolean known = AgentPlanRepository.defaultRepository().find(successor).isPresent();
            result.add(new AgentDirectorAction(
                    "quest-plan:" + successor, "Start quest plan " + successor,
                    availability(operatorEnabled && known, true),
                    !operatorEnabled ? "Director is not in an operator-controlled mode"
                            : known ? "available successor from the retained quest session"
                            : "successor is not present in the executable plan repository",
                    AgentWorldDirectiveType.START_ACTIVITY, AgentActivityKind.QUESTING,
                    AgentWorldActivityRequestType.AUTHORED_PLAN, successor, Map.of(),
                    AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                    AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 700, false));
        }
        boolean victoriaReached = milestones.achieved(AgentWorldMilestone.VICTORIA_REACHED);
        if (victoriaReached && context.jobId() == 0) {
            addCareerBuilds(result, entry, operatorEnabled);
        }
        if (victoriaReached) {
            addProgressionChoices(result, context, operatorEnabled);
            addIndividualQuests(result, agent, context, operatorEnabled);
            addTrainingMaps(result, context, operatorEnabled);
            addTownLife(result, operatorEnabled);
            addCommerce(result, context, operatorEnabled);
        }
        if (victoriaReached && context.level() >= 21 && context.level() <= 30) {
            result.add(new AgentDirectorAction(
                    "party-quest:kpq", "Enter Kerning Party Quest lobby",
                    operatorEnabled
                            ? (context.ownsSquishyShoes()
                            ? AgentDirectorActionAvailability.AVAILABLE
                            : AgentDirectorActionAvailability.RECOMMENDED)
                            : AgentDirectorActionAvailability.UNAVAILABLE,
                    operatorEnabled ? "normal travel to the KPQ lobby; the KPQ aggregate forms its own party"
                            : "Director is not in an operator-controlled mode",
                    AgentWorldDirectiveType.START_ACTIVITY, AgentActivityKind.PARTY_QUEST,
                    AgentWorldActivityRequestType.PARTY_QUEST_VISIT, "kpq",
                    Map.of("scenarioId", "kpq", "partySize", "4", "maximumRuns", "1"),
                    AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                    AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 650, false));
        }
        boolean active = context.currentActivityKind() != null
                && !context.currentSessionId().isEmpty();
        boolean suspended = session.phase() == AgentWorldDirectorPhase.PAUSED
                && session.observedActivityKind() != null
                && !session.observedSessionId().isEmpty();
        result.add(lifecycle("lifecycle:suspend", "Suspend after safe boundary",
                AgentWorldDirectiveType.SUSPEND_ACTIVITY,
                operatorEnabled && active && !suspended,
                suspended ? "an exact activity session is already suspended"
                        : active ? "retains the exact session and walks to a safe spot"
                        : "there is no active session to suspend",
                context.currentActivityKind(), false));
        result.add(lifecycle("lifecycle:resume", "Resume suspended activity",
                AgentWorldDirectiveType.RESUME_ACTIVITY,
                operatorEnabled && suspended,
                suspended ? "resumes the exact retained activity session"
                        : "there is no exact suspended session",
                session.observedActivityKind(), false));
        result.add(lifecycle("lifecycle:stop", "Finish and stop activity",
                AgentWorldDirectiveType.STOP_ACTIVITY,
                operatorEnabled && active,
                active ? "finishes protected work before terminating the activity"
                        : "there is no active session to stop",
                context.currentActivityKind(), false));
        result.add(lifecycle("lifecycle:abandon", "Abandon activity now",
                AgentWorldDirectiveType.ABANDON_ACTIVITY,
                operatorEnabled && active,
                active ? "destructive: discards the active activity when its owner permits it"
                        : "there is no active session to abandon",
                context.currentActivityKind(), true));
        boolean supplyReady = supplyReady(entry);
        result.add(lifecycle("support:resupply", "Resupply critical resources",
                AgentWorldDirectiveType.REQUEST_SUPPLY_MAINTENANCE,
                operatorEnabled && supplyReady,
                supplyReady ? "a route-aware NPC-shop procurement request is ready"
                        : "no critical NPC-shop procurement request is ready",
                null, false));
        return List.copyOf(result);
    }

    private static void addTrainingMaps(
            List<AgentDirectorAction> result,
            AgentWorldContext context,
            boolean operatorEnabled) {
        AgentVictoriaTrainingCatalogRepository repository =
                AgentVictoriaTrainingCatalogRepository.defaultRepository();
        for (AgentVictoriaTrainingCatalog.TrainingChoice choice :
                repository.choicesForLevel(context.level())) {
            AgentVictoriaTrainingCatalog.TrainingMap map =
                    repository.findMap(choice.mapId()).orElse(null);
            if (map == null) continue;
            String mobIds = map.spawns().stream()
                    .map(spawn -> Integer.toString(spawn.mobId()))
                    .distinct().collect(java.util.stream.Collectors.joining(","));
            result.add(new AgentDirectorAction(
                    "hunting-map:" + map.mapId(), "Hunt — " + map.mapName(),
                    availability(operatorEnabled, choice.rank() == 1),
                    operatorEnabled ? choice.rationale()
                            : "Director is not in an operator-controlled mode",
                    AgentWorldDirectiveType.START_ACTIVITY, AgentActivityKind.HUNTING,
                    AgentWorldActivityRequestType.FIELD_VISIT,
                    "training-map:" + map.mapId(),
                    Map.of("mapId", Integer.toString(map.mapId()),
                            "intent", "FREE_GRIND", "mobIds", mobIds,
                            "acceptingQuestVisitors", "true",
                            "maximumParticipants", Integer.toString(map.maximumAgents()),
                            "restAllowed", "true", "narration", "SUMMARY"),
                    AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                    AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                    Math.max(100, 600 - choice.rank()), false));
        }
    }

    private static void addProgressionChoices(
            List<AgentDirectorAction> result,
            AgentWorldContext context,
            boolean operatorEnabled) {
        if (context.jobId() == 0 || context.level() < 15 || context.level() >= 30) return;
        int targetLevel = Math.min(30, context.level() + 1);
        addProgressionChoice(result, operatorEnabled, targetLevel, true,
                "Progress one level — quests and hunting",
                "the quest scheduler selects eligible Victoria work and falls back to "
                        + "level-appropriate Hunting");
        addProgressionChoice(result, operatorEnabled, targetLevel, false,
                "Progress one level — hunting only",
                "skips quest selection and uses the level-appropriate Hunting catalog");
    }

    private static void addIndividualQuests(
            List<AgentDirectorAction> result,
            Character agent,
            AgentWorldContext context,
            boolean operatorEnabled) {
        int targetLevel = Math.min(30, context.level() + 1);
        for (AgentVictoriaIndividualQuestCatalog.Option quest :
                AgentVictoriaIndividualQuestCatalog.available(agent)) {
            boolean active = quest.status() == client.QuestStatus.Status.STARTED.getId();
            String reason = active ? "resume this active quest from authoritative progress"
                    : quest.rationale().isBlank()
                    ? "supported individual Victoria quest at the current level"
                    : quest.rationale();
            result.add(new AgentDirectorAction(
                    "individual-quest:" + quest.questId(),
                    (active ? "Resume quest — " : "Start quest — ") + quest.questName(),
                    availability(operatorEnabled, true),
                    operatorEnabled ? reason
                            : "Director is not in an operator-controlled mode",
                    AgentWorldDirectiveType.START_ACTIVITY, AgentActivityKind.QUESTING,
                    AgentWorldActivityRequestType.INDIVIDUAL_QUEST,
                    "quest:" + quest.questId(),
                    Map.of("questId", Integer.toString(quest.questId()),
                            "targetLevel", Integer.toString(targetLevel)),
                    AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                    AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, active ? 680 : 630, false));
        }
    }

    private static void addProgressionChoice(
            List<AgentDirectorAction> result,
            boolean operatorEnabled,
            int targetLevel,
            boolean questsEnabled,
            String label,
            String availableReason) {
        result.add(new AgentDirectorAction(
                "progression:level-" + targetLevel + ':'
                        + (questsEnabled ? "mixed" : "hunting"),
                label, availability(operatorEnabled, questsEnabled),
                operatorEnabled ? availableReason
                        : "Director is not in an operator-controlled mode",
                AgentWorldDirectiveType.START_ACTIVITY, AgentActivityKind.QUESTING,
                AgentWorldActivityRequestType.AUTHORED_PLAN, "victoria-training",
                Map.of("input.targetLevel", Integer.toString(targetLevel),
                        "input.questsEnabled", Boolean.toString(questsEnabled)),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                questsEnabled ? 640 : 620, false));
    }

    private static void addCareerBuilds(
            List<AgentDirectorAction> result,
            AgentRuntimeEntry entry,
            boolean operatorEnabled) {
        AgentCareerProgressionState state = entry.capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY);
        String current = state.bundle() == null ? "" : state.bundle().bundleId();
        boolean safeStage = state.stage()
                == AgentCareerProgressionState.Stage.WAITING_FOR_MAPLE_ISLAND;
        for (var bundle : AgentCareerBuildBundleRepository.defaultRepository().all()) {
            boolean selected = bundle.bundleId().equals(current);
            boolean executable = operatorEnabled && safeStage && !selected;
            String reason = !operatorEnabled ? "Director is not in an operator-controlled mode"
                    : !safeStage ? "first-job progression has already begun"
                    : selected ? "this is the currently selected career build"
                    : "selects durable AP, SP, equipment, and instructor policy before the run";
            result.add(new AgentDirectorAction(
                    "career-build:" + bundle.bundleId(),
                    "Choose " + bundle.career().replace('-', ' '),
                    availability(executable, false), reason,
                    AgentWorldDirectiveType.CONFIGURE_CAREER_BUILD, null, null,
                    bundle.bundleId(), Map.of(),
                    AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                    AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 880, false));
        }
    }

    private static void addTownLife(
            List<AgentDirectorAction> result, boolean operatorEnabled) {
        Map<Integer, String> towns = new java.util.LinkedHashMap<>();
        towns.put(constants.id.MapId.LITH_HARBOUR, "Lith Harbor");
        towns.put(constants.id.MapId.HENESYS, "Henesys");
        towns.put(constants.id.MapId.ELLINIA, "Ellinia");
        towns.put(constants.id.MapId.PERION, "Perion");
        towns.put(constants.id.MapId.KERNING_CITY, "Kerning City");
        towns.put(constants.id.MapId.NAUTILUS_HARBOR, "Nautilus Harbor");
        towns.put(constants.id.MapId.SLEEPYWOOD, "Sleepywood");
        towns.forEach((mapId, name) -> result.add(new AgentDirectorAction(
                "town-life:" + mapId, "Visit " + name,
                availability(operatorEnabled, false),
                operatorEnabled ? "bounded leisure visit using the shared TownLife baseline"
                        : "Director is not in an operator-controlled mode",
                AgentWorldDirectiveType.START_ACTIVITY, AgentActivityKind.TOWN_LIFE,
                AgentWorldActivityRequestType.TOWN_LIFE_VISIT,
                "town-life:" + mapId,
                Map.of("mapId", Integer.toString(mapId), "purpose", "LEISURE",
                        "freeTimeBudgetMs", "300000"),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 200, false)));
    }

    private static void addCommerce(
            List<AgentDirectorAction> result,
            AgentWorldContext context,
            boolean operatorEnabled) {
        boolean inFreeMarket = context.mapId() >= constants.id.MapId.FM_ENTRANCE
                && context.mapId() <= constants.id.MapId.FM_ENTRANCE + 22;
        boolean installed = AgentEconomyRuntime.sessionPort().isPresent();
        boolean executable = operatorEnabled && installed && inFreeMarket;
        String reason = !operatorEnabled ? "Director is not in an operator-controlled mode"
                : !installed ? "a managed Commerce runtime is not installed"
                : !inFreeMarket ? "normal Free Market entry is not yet connected to Director travel"
                : "bounded market visit through the installed Commerce session owner";
        result.add(new AgentDirectorAction(
                "commerce:periodic-market-visit", "Visit the Free Market",
                availability(executable, false), reason,
                AgentWorldDirectiveType.START_ACTIVITY, AgentActivityKind.COMMERCE,
                AgentWorldActivityRequestType.COMMERCE_VISIT, "commerce:periodic-market-visit",
                Map.ofEntries(
                        Map.entry("jobFamily", Integer.toString(context.jobId() / 100)),
                        Map.entry("dailyActivityFraction", "0.50"),
                        Map.entry("riskTolerance", "0.50"),
                        Map.entry("liquidityPreference", "0.50"),
                        Map.entry("upgradeAggressiveness", "0.35"),
                        Map.entry("shoppingPatience", "0.60"),
                        Map.entry("stallWillingness", "0.40"),
                        Map.entry("priceMemoryHours", "72"),
                        Map.entry("negotiationAggressiveness", "0.35"),
                        Map.entry("chairInterest", "0.20"),
                        Map.entry("purpose", "PERIODIC_MARKET_VISIT"),
                        Map.entry("maximumDurationMs", "300000"),
                        Map.entry("maximumIdleMs", "60000")),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 200, false));
    }

    private AgentDirectorAction activity(
            AgentWorldActivityIntent intent, boolean operatorEnabled) {
        var proposal = intent.proposal();
        boolean supported = AgentStandardWorldActivityBindingResolver.supportedTargets()
                .contains(proposal.kind());
        boolean concrete = concrete(intent);
        boolean executable = operatorEnabled && proposal.eligible() && supported && concrete;
        String reason;
        if (!operatorEnabled) reason = "Director is not in an operator-controlled mode";
        else if (!proposal.eligible()) reason = proposal.evidence();
        else if (!supported) reason = "activity admission is not connected to Director execution";
        else if (!concrete) reason = "proposal needs a concrete quest, map, or visit selection";
        else reason = proposal.evidence();
        return new AgentDirectorAction(
                "proposal:" + proposal.proposalId(), label(proposal.kind(), intent.requestId()),
                availability(executable, proposal.priority() >= 500), reason,
                AgentWorldDirectiveType.START_ACTIVITY, proposal.kind(), intent.requestType(),
                intent.requestId(), Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                proposal.priority(), false);
    }

    private static boolean concrete(AgentWorldActivityIntent intent) {
        if (intent.requestType() != AgentWorldActivityRequestType.AUTHORED_PLAN) return false;
        return AgentPlanRepository.defaultRepository().find(intent.requestId()).isPresent();
    }

    private static AgentDirectorAction lifecycle(
            String id,
            String label,
            AgentWorldDirectiveType type,
            boolean executable,
            String reason,
            AgentActivityKind kind,
            boolean destructive) {
        return new AgentDirectorAction(id, label,
                executable ? AgentDirectorActionAvailability.AVAILABLE
                        : AgentDirectorActionAvailability.UNAVAILABLE,
                reason, type, kind, null, "", Map.of(),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                destructive ? 1_000 : 800, destructive);
    }

    private static AgentDirectorActionAvailability availability(
            boolean executable, boolean recommended) {
        if (!executable) return AgentDirectorActionAvailability.UNAVAILABLE;
        return recommended ? AgentDirectorActionAvailability.RECOMMENDED
                : AgentDirectorActionAvailability.AVAILABLE;
    }

    private static boolean supplyReady(AgentRuntimeEntry entry) {
        if (entry.capabilityStates().find(AgentSupplyProcurementState.STATE_KEY)
                .map(AgentSupplyProcurementState::isActive).orElse(false)) return true;
        return entry.capabilityStates().find(AgentResourcePlanningState.STATE_KEY)
                .map(state -> state.procurementSnapshot().values().stream()
                        .anyMatch(request -> request.urgency().ordinal()
                                >= AgentSupplyUrgency.CRITICAL.ordinal()))
                .orElse(false);
    }

    private static String label(AgentActivityKind kind, String requestId) {
        String readable = kind.name().toLowerCase().replace('_', ' ');
        return "Start " + readable + " — " + requestId;
    }
}
