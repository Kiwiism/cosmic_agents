package server.agents.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import constants.game.ExpTable;
import server.agents.events.AgentEvent;
import server.agents.events.AgentEventContext;
import server.agents.operations.events.AgentMapTransitionedEvent;
import server.agents.operations.events.AgentMobKilledEvent;
import server.agents.operations.events.AgentRecoveryPerformedEvent;
import server.agents.operations.events.AgentStuckDetectedEvent;
import server.agents.progression.events.AgentQuestStateChangedEvent;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory projection for one bounded cohort experiment. */
final class AgentJourneyRun {
    private static final Set<String> DECISION_MODE_DETAIL_EVENTS = Set.of(
            "combat.attack-resolved",
            "combat.mob-damaged",
            "combat.mob-killed",
            "combat.posture-changed",
            "combat.target-changed");
    enum Status {
        RUNNING,
        COMPLETED,
        STOPPED
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AgentJourneyManifest manifest;
    private final AgentJourneyConfig config;
    private final AgentJourneyStore store;
    private final Map<Integer, Trace> traces = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile Status status = Status.RUNNING;
    private volatile long finishedAtMs;

    AgentJourneyRun(AgentJourneyManifest manifest,
                    AgentJourneyConfig config,
                    AgentJourneyStore store) {
        this.manifest = manifest;
        this.config = config;
        this.store = store;
        for (AgentJourneyManifest.Participant participant : manifest.participants()) {
            traces.put(participant.characterId(), new Trace(participant));
        }
    }

    AgentJourneyManifest manifest() {
        return manifest;
    }

    Status status() {
        return status;
    }

    void onEvent(AgentEvent event) {
        Trace trace = traces.get(event.agentId());
        if (trace == null || trace.terminal() || status != Status.RUNNING) {
            return;
        }
        AgentEventContext context = event.context();
        Map<String, Object> attributes;
        try {
            attributes = MAPPER.convertValue(event, Map.class);
        } catch (IllegalArgumentException failure) {
            attributes = Map.of("dedupeKey", event.dedupeKey());
        }
        boolean critical = critical(event);
        AgentJourneyEventRecord record = record(trace, event.occurredAtMs(),
                category(event.type()), event.type(), context.objectiveId(),
                context.mapId(), critical, attributes);
        trace.onEvent(event, record, config);
        if (!decisionOnly() || retainDecisionEvent(event)) {
            store.append(record);
        }
    }

    void sample(AgentJourneySnapshot snapshot) {
        Trace trace = traces.get(snapshot.agentId());
        if (trace == null || trace.terminal() || status != Status.RUNNING) {
            return;
        }
        SampleAnalysis analysis = trace.sample(snapshot, config);
        AgentJourneyEventRecord sample = record(trace, snapshot.capturedAtMs(),
                "snapshot", "journey.agent-snapshot", snapshot.objectiveId(),
                snapshot.mapId(), false, MAPPER.convertValue(snapshot, Map.class));
        trace.retain(sample, config.flightRecorderWindowMs());
        if (!decisionOnly()) {
            store.append(sample);
        }
        for (AgentJourneyDerivedEvidence evidence : analysis.evidence()) {
            AgentJourneyEventRecord event = record(trace, snapshot.capturedAtMs(),
                    evidence.category(), evidence.eventType(), snapshot.objectiveId(),
                    snapshot.mapId(), evidence.critical(), evidence.attributes());
            trace.retain(event, config.flightRecorderWindowMs());
            if (!decisionOnly() || retainDecisionEvidence(evidence)) {
                store.append(event);
            }
        }
        for (Detection detection : analysis.detections()) {
            AgentJourneyEventRecord event = record(trace, snapshot.capturedAtMs(),
                    "diagnostic", "journey.stuck-detected", snapshot.objectiveId(),
                    snapshot.mapId(), true, Map.of(
                            "stuckType", detection.type().name(),
                            "detail", detection.detail(),
                            "lastProgressAtMs", trace.lastProgressAtMs));
            trace.retain(event, config.flightRecorderWindowMs());
            store.append(event);
            store.writeFlightRecorder(manifest.runId(), snapshot.agentId(),
                    snapshot.agentName(), event.sequence(), trace.flightSnapshot());
        }
    }

    void markAgentFailed(int agentId, String reason, long nowMs) {
        Trace trace = traces.get(agentId);
        if (trace == null || trace.terminal()) {
            return;
        }
        trace.markFailed(reason, nowMs);
        store.append(record(trace, nowMs, "lifecycle", "journey.agent-failed",
                trace.last == null ? "" : trace.last.objectiveId(),
                trace.last == null ? -1 : trace.last.mapId(), true,
                Map.of("reason", reason == null ? "" : reason)));
        completeWhenAllAgentsAreTerminal(nowMs);
    }

    void markAgentStalled(int agentId, String reason, long nowMs) {
        Trace trace = traces.get(agentId);
        if (trace == null || trace.terminal()) {
            return;
        }
        trace.markStalled(reason, nowMs);
        store.append(record(trace, nowMs, "lifecycle", "journey.agent-stalled",
                trace.last == null ? "" : trace.last.objectiveId(),
                trace.last == null ? -1 : trace.last.mapId(), true,
                Map.of("reason", reason == null ? "" : reason)));
        completeWhenAllAgentsAreTerminal(nowMs);
    }

    void markAgentCompleted(int agentId, long nowMs) {
        Trace trace = traces.get(agentId);
        if (trace == null || trace.terminal()) {
            return;
        }
        trace.markCompleted(nowMs);
        store.append(record(trace, nowMs, "lifecycle", "journey.agent-completed",
                trace.last == null ? "" : trace.last.objectiveId(),
                trace.last == null ? -1 : trace.last.mapId(), true,
                Map.of("targetLevel", manifest.targetLevel())));
        completeWhenAllAgentsAreTerminal(nowMs);
    }

    void stop(long nowMs) {
        if (status == Status.RUNNING) {
            status = Status.STOPPED;
            finishedAtMs = nowMs;
        }
    }

    boolean complete() {
        return status != Status.RUNNING;
    }

    private void completeWhenAllAgentsAreTerminal(long nowMs) {
        if (status == Status.RUNNING
                && traces.values().stream().allMatch(Trace::terminal)) {
            status = Status.COMPLETED;
            finishedAtMs = nowMs;
        }
    }

    AgentJourneyReport report(long nowMs) {
        long end = finishedAtMs > 0L ? finishedAtMs : nowMs;
        List<AgentJourneyReport.AgentSummary> agentSummaries = traces.values().stream()
                .sorted(Comparator.comparing(trace -> trace.participant.characterName()))
                .map(trace -> trace.agentSummary(end)).toList();
        List<AgentJourneyReport.QuestOutcome> quests = traces.values().stream()
                .flatMap(trace -> trace.questOutcomes.stream()).toList();
        List<AgentJourneyReport.MapOutcome> maps = traces.values().stream()
                .flatMap(trace -> trace.mapOutcomes(end).stream()).toList();
        List<AgentJourneyReport.ResourceOutcome> resources = traces.values().stream()
                .flatMap(trace -> trace.resourceOutcomes().stream()).toList();
        int succeeded = (int) agentSummaries.stream()
                .filter(summary -> "SUCCEEDED".equals(summary.status())).count();
        int failed = (int) agentSummaries.stream()
                .filter(summary -> "FAILED".equals(summary.status())
                        || "STALLED".equals(summary.status())).count();
        String agentsCsv = agentsCsv(agentSummaries);
        String questsCsv = questsCsv(quests);
        String mapsCsv = mapsCsv(maps);
        String resourcesCsv = resourcesCsv(resources);
        String markdown = markdown(agentSummaries, quests, maps, resources, succeeded, failed, end);
        return new AgentJourneyReport(
                1, manifest.runId(), manifest.scenarioId(), status.name(),
                manifest.startedAtMs(), end, manifest.targetLevel(), traces.size(),
                succeeded, failed, store.droppedSamples(manifest.runId()),
                agentSummaries, quests, maps,
                resources, agentsCsv, questsCsv, mapsCsv, resourcesCsv, markdown);
    }

    List<AgentJourneyTraceView> traceViews() {
        return traces.values().stream()
                .map(Trace::view)
                .sorted(Comparator.comparing(AgentJourneyTraceView::agentName))
                .toList();
    }

    private boolean decisionOnly() {
        return "decisions".equals(manifest.simulationMode());
    }

    static boolean retainDecisionEvent(AgentEvent event) {
        return event != null && !DECISION_MODE_DETAIL_EVENTS.contains(event.type());
    }

    private static boolean retainDecisionEvidence(AgentJourneyDerivedEvidence evidence) {
        return evidence.critical()
                || Set.of("plan", "progression", "quest", "diagnostic")
                .contains(evidence.category());
    }

    private AgentJourneyEventRecord record(Trace trace,
                                           long occurredAtMs,
                                           String category,
                                           String eventType,
                                           String objectiveId,
                                           int mapId,
                                           boolean critical,
                                           Map<String, Object> attributes) {
        return new AgentJourneyEventRecord(
                1, manifest.runId(), sequence.incrementAndGet(), occurredAtMs,
                trace.participant.characterId(), trace.participant.characterName(),
                category, eventType, objectiveId, mapId, critical, attributes);
    }

    private static boolean critical(AgentEvent event) {
        return event instanceof AgentStuckDetectedEvent
                || event instanceof AgentRecoveryPerformedEvent
                || event instanceof AgentQuestStateChangedEvent
                || event.type().contains("failed")
                || event.type().contains("blocked")
                || event.type().contains("death");
    }

    private static String category(String eventType) {
        int separator = eventType.indexOf('.');
        return separator < 1 ? "domain" : eventType.substring(0, separator);
    }

    private String markdown(
            List<AgentJourneyReport.AgentSummary> agents,
            List<AgentJourneyReport.QuestOutcome> quests,
            List<AgentJourneyReport.MapOutcome> maps,
            List<AgentJourneyReport.ResourceOutcome> resources,
            int succeeded,
            int failed,
            long end) {
        long duration = Math.max(0L, end - manifest.startedAtMs());
        return """
                # Agent Journey Report

                - Run: `%s`
                - Scenario: `%s`
                - Status: `%s`
                - Participants: %d
                - Reached target: %d
                - Failed: %d
                - Duration: %d ms
                - Dropped non-critical samples: %d

                ## Evidence

                - `agents.csv`: per-Agent completion, timing, stuck and recovery totals
                - `quests.csv`: per-Agent quest duration and resource delta
                - `maps.csv`: dwell, visits, kills and recoveries by map
                - `resources.csv`: starting, ending, acquired and consumed item quantities
                - `../agents/*.jsonl`: chronological full journey records
                - `../failures/*.jsonl`: pre-failure flight-recorder windows

                ## Interpretation

                A successful Agent reached level %d after the deterministic first-job path and
                the universal Victoria quest/training pool. A stuck episode is diagnostic evidence;
                it does not itself mutate or bypass the universal plan executor.
                """.formatted(
                manifest.runId(), manifest.scenarioId(), status.name(), agents.size(),
                succeeded, failed, duration, store.droppedSamples(manifest.runId()),
                manifest.targetLevel());
    }

    private static String agentsCsv(List<AgentJourneyReport.AgentSummary> values) {
        StringBuilder csv = new StringBuilder(
                "agentId,agentName,career,status,startLevel,endLevel,expGained,mesosGained,"
                        + "mesosSpent,mesosNet,durationMs,kills,questsCompleted,recoveries,"
                        + "recoveriesSucceeded,stuckEpisodes,mapsVisited,"
                        + "lastPlan,lastObjective,failureReason\n");
        for (AgentJourneyReport.AgentSummary value : values) {
            csv.append(value.agentId()).append(',').append(csv(value.agentName())).append(',')
                    .append(csv(value.career())).append(',').append(value.status()).append(',')
                    .append(value.startLevel()).append(',').append(value.endLevel()).append(',')
                    .append(value.experienceGained()).append(',').append(value.mesosGained()).append(',')
                    .append(value.mesosSpent()).append(',').append(value.mesosNet()).append(',')
                    .append(value.durationMs()).append(',').append(value.kills()).append(',')
                    .append(value.questsCompleted()).append(',').append(value.recoveries()).append(',')
                    .append(value.recoveriesSucceeded()).append(',')
                    .append(value.stuckEpisodes()).append(',').append(value.mapsVisited()).append(',')
                    .append(csv(value.lastPlan())).append(',').append(csv(value.lastObjective()))
                    .append(',').append(csv(value.failureReason())).append('\n');
        }
        return csv.toString();
    }

    private static String questsCsv(List<AgentJourneyReport.QuestOutcome> values) {
        StringBuilder csv = new StringBuilder(
                "agentId,agentName,questId,jobId,levelAtStart,startMapId,endMapId,"
                        + "durationMs,expGained,mesosNet,itemNet\n");
        for (AgentJourneyReport.QuestOutcome value : values) {
            csv.append(value.agentId()).append(',').append(csv(value.agentName())).append(',')
                    .append(value.questId()).append(',')
                    .append(value.jobId()).append(',').append(value.levelAtStart()).append(',')
                    .append(value.startMapId()).append(',').append(value.endMapId()).append(',')
                    .append(value.durationMs()).append(',')
                    .append(value.experienceGained()).append(',').append(value.mesosNet()).append(',')
                    .append(csv(value.itemNet().toString())).append('\n');
        }
        return csv.toString();
    }

    private static String mapsCsv(List<AgentJourneyReport.MapOutcome> values) {
        StringBuilder csv = new StringBuilder(
                "agentId,agentName,mapId,dwellMs,visits,kills,recoveries\n");
        for (AgentJourneyReport.MapOutcome value : values) {
            csv.append(value.agentId()).append(',').append(csv(value.agentName())).append(',')
                    .append(value.mapId()).append(',').append(value.dwellMs()).append(',')
                    .append(value.visits()).append(',').append(value.kills()).append(',')
                    .append(value.recoveries()).append('\n');
        }
        return csv.toString();
    }

    private static String resourcesCsv(List<AgentJourneyReport.ResourceOutcome> values) {
        StringBuilder csv = new StringBuilder(
                "agentId,agentName,itemId,startQuantity,endQuantity,netQuantity,acquired,consumedOrSold\n");
        for (AgentJourneyReport.ResourceOutcome value : values) {
            csv.append(value.agentId()).append(',').append(csv(value.agentName())).append(',')
                    .append(value.itemId()).append(',').append(value.startQuantity()).append(',')
                    .append(value.endQuantity()).append(',').append(value.netQuantity()).append(',')
                    .append(value.acquired()).append(',').append(value.consumedOrSold()).append('\n');
        }
        return csv.toString();
    }

    private static String csv(String value) {
        String normalized = value == null ? "" : value;
        return '"' + normalized.replace("\"", "\"\"") + '"';
    }

    private record Detection(AgentJourneyStuckType type, String detail) {
    }

    private record SampleAnalysis(
            List<Detection> detections,
            List<AgentJourneyDerivedEvidence> evidence) {
    }

    private static final class Trace {
        private final AgentJourneyManifest.Participant participant;
        private final ArrayDeque<AgentJourneyEventRecord> flight = new ArrayDeque<>();
        private final ArrayDeque<Integer> transitionMaps = new ArrayDeque<>();
        private final ArrayDeque<Point> positions = new ArrayDeque<>();
        private final ArrayDeque<Long> recoveryTimes = new ArrayDeque<>();
        private final Set<AgentJourneyStuckType> activeDetections = new HashSet<>();
        private final Map<Integer, QuestBaseline> questBaselines = new HashMap<>();
        private final List<AgentJourneyReport.QuestOutcome> questOutcomes = new ArrayList<>();
        private final Map<Integer, MutableMapOutcome> mapOutcomes = new LinkedHashMap<>();
        private final Map<Integer, Integer> acquiredItems = new HashMap<>();
        private final Map<Integer, Integer> consumedItems = new HashMap<>();
        private final Set<Integer> seenCompletedQuests = new HashSet<>();
        private AgentJourneySnapshot first;
        private AgentJourneySnapshot last;
        private long lastProgressAtMs;
        private long mapEnteredAtMs;
        private int kills;
        private int questsCompleted;
        private int recoveries;
        private int recoveriesSucceeded;
        private int stuckEpisodes;
        private int experienceGained;
        private int mesosGained;
        private int mesosSpent;
        private boolean recoveryPending;
        private String terminalStatus = "RUNNING";
        private String failureReason = "";
        private long terminalAtMs;

        private Trace(AgentJourneyManifest.Participant participant) {
            this.participant = participant;
        }

        synchronized void onEvent(AgentEvent event,
                                  AgentJourneyEventRecord record,
                                  AgentJourneyConfig config) {
            retain(record, config.flightRecorderWindowMs());
            if (event instanceof AgentMobKilledEvent killed) {
                kills++;
                progress(event.occurredAtMs());
                map(killed.mapId()).kills++;
            } else if (event instanceof AgentQuestStateChangedEvent quest) {
                progress(event.occurredAtMs());
                if (quest.status() == 1 && last != null) {
                    questBaselines.putIfAbsent(
                            quest.questId(), new QuestBaseline(event.occurredAtMs(), last));
                }
            } else if (event instanceof AgentMapTransitionedEvent transitioned) {
                addTransition(transitioned.mapId(), config.loopTransitionCount());
                progress(event.occurredAtMs());
            } else if (event instanceof AgentRecoveryPerformedEvent recovered) {
                recoveries++;
                recoveryPending = true;
                map(recovered.mapId()).recoveries++;
                recoveryTimes.addLast(event.occurredAtMs());
                trimRecoveries(event.occurredAtMs(), config.suspicionAfterMs());
            } else if (event instanceof AgentStuckDetectedEvent) {
                stuckEpisodes++;
            }
        }

        synchronized SampleAnalysis sample(
                AgentJourneySnapshot snapshot, AgentJourneyConfig config) {
            List<Detection> detections = new ArrayList<>();
            List<AgentJourneyDerivedEvidence> evidence = new ArrayList<>();
            if (first == null) {
                first = snapshot;
                lastProgressAtMs = snapshot.capturedAtMs();
                mapEnteredAtMs = snapshot.capturedAtMs();
                map(snapshot.mapId()).visits++;
                seenCompletedQuests.addAll(snapshot.completedQuestIds());
                snapshot.activeQuestProgress().keySet().forEach(questId ->
                        questBaselines.put(questId,
                                new QuestBaseline(snapshot.capturedAtMs(), snapshot)));
            } else {
                evidence.addAll(derivedEvidence(last, snapshot));
                accumulateResources(last, snapshot);
                experienceGained += experienceDelta(last, snapshot);
                if (meaningfulDelta(last, snapshot)) {
                    progress(snapshot.capturedAtMs());
                }
                if (last.mapId() != snapshot.mapId()) {
                    closeMap(snapshot.capturedAtMs());
                    mapEnteredAtMs = snapshot.capturedAtMs();
                    map(snapshot.mapId()).visits++;
                }
                snapshot.activeQuestProgress().keySet().forEach(questId ->
                        questBaselines.putIfAbsent(questId,
                                new QuestBaseline(snapshot.capturedAtMs(), snapshot)));
                for (int questId : snapshot.completedQuestIds()) {
                    if (!seenCompletedQuests.add(questId)) {
                        continue;
                    }
                    questsCompleted++;
                    QuestBaseline baseline = questBaselines.remove(questId);
                    if (baseline != null) {
                        questOutcomes.add(questOutcome(
                                questId, baseline, snapshot, snapshot.capturedAtMs()));
                    }
                }
            }
            last = snapshot;
            positions.addLast(new Point(snapshot.x(), snapshot.y()));
            while (positions.size() > 12) {
                positions.removeFirst();
            }
            trimRecoveries(snapshot.capturedAtMs(), config.suspicionAfterMs());
            evaluate(AgentJourneyStuckType.PLAN_BLOCKED,
                    "BLOCKED".equals(snapshot.planStatus()),
                    "universal plan is BLOCKED", detections);
            boolean activeWork = !snapshot.objectiveId().isBlank()
                    || "ACTIVE".equals(snapshot.planStatus());
            long stalledMs = snapshot.capturedAtMs() - lastProgressAtMs;
            evaluate(AgentJourneyStuckType.SEMANTIC_PROGRESS_STALL,
                    activeWork && stalledMs >= config.suspicionAfterMs(),
                    "no meaningful objective delta for " + stalledMs + "ms", detections);
            long dwellMs = snapshot.capturedAtMs() - mapEnteredAtMs;
            evaluate(AgentJourneyStuckType.MAP_DWELL,
                    activeWork && dwellMs >= config.mapDwellAfterMs()
                            && stalledMs >= config.suspicionAfterMs(),
                    "same map for " + dwellMs + "ms without objective progress", detections);
            evaluate(AgentJourneyStuckType.NAVIGATION_LOOP,
                    navigationLoop(config.loopTransitionCount()),
                    "recent map transitions repeat an A-B cycle", detections);
            evaluate(AgentJourneyStuckType.POSITION_OSCILLATION,
                    activeWork && stalledMs >= config.suspicionAfterMs()
                            && positionOscillation(),
                    "position samples oscillate inside a small area", detections);
            evaluate(AgentJourneyStuckType.RECOVERY_THRASH,
                    recoveryTimes.size() >= 3,
                    recoveryTimes.size() + " recoveries inside the stall window", detections);
            stuckEpisodes += detections.size();
            return new SampleAnalysis(List.copyOf(detections), List.copyOf(evidence));
        }

        synchronized void retain(AgentJourneyEventRecord record, long windowMs) {
            flight.addLast(record);
            long cutoff = record.occurredAtMs() - windowMs;
            while (!flight.isEmpty() && flight.peekFirst().occurredAtMs() < cutoff) {
                flight.removeFirst();
            }
        }

        synchronized List<AgentJourneyEventRecord> flightSnapshot() {
            return List.copyOf(flight);
        }

        synchronized void markCompleted(long nowMs) {
            terminalStatus = "SUCCEEDED";
            terminalAtMs = nowMs;
            closeMap(nowMs);
        }

        synchronized void markFailed(String reason, long nowMs) {
            terminalStatus = "FAILED";
            failureReason = reason == null ? "" : reason;
            terminalAtMs = nowMs;
            closeMap(nowMs);
        }

        synchronized void markStalled(String reason, long nowMs) {
            terminalStatus = "STALLED";
            failureReason = reason == null ? "" : reason;
            terminalAtMs = nowMs;
            closeMap(nowMs);
        }

        synchronized boolean terminal() {
            return !"RUNNING".equals(terminalStatus);
        }

        synchronized AgentJourneyTraceView view() {
            return new AgentJourneyTraceView(
                    participant.characterId(), participant.characterName(), participant.career(),
                    terminalStatus, last == null ? 0 : last.level(), last == null ? -1 : last.mapId(),
                    last == null ? "" : last.planId(), last == null ? "" : last.objectiveId(),
                    lastProgressAtMs, kills, questsCompleted, recoveries, stuckEpisodes, failureReason);
        }

        synchronized AgentJourneyReport.AgentSummary agentSummary(long endMs) {
            int startLevel = first == null ? 0 : first.level();
            int endLevel = last == null ? startLevel : last.level();
            int mesosNet = first == null || last == null ? 0 : last.mesos() - first.mesos();
            long end = terminalAtMs > 0L ? terminalAtMs : endMs;
            return new AgentJourneyReport.AgentSummary(
                    participant.characterId(), participant.characterName(), participant.career(),
                    terminalStatus, startLevel, endLevel, experienceGained,
                    mesosGained, mesosSpent, mesosNet,
                    first == null ? 0L : Math.max(0L, end - first.capturedAtMs()),
                    kills, questsCompleted, recoveries, recoveriesSucceeded,
                    stuckEpisodes, mapOutcomes.size(),
                    last == null ? "" : last.planId(), last == null ? "" : last.objectiveId(),
                    failureReason);
        }

        synchronized List<AgentJourneyReport.MapOutcome> mapOutcomes(long nowMs) {
            if (!terminal() && last != null) {
                MutableMapOutcome current = map(last.mapId());
                current.openDwellMs = Math.max(0L, nowMs - mapEnteredAtMs);
            }
            return mapOutcomes.values().stream()
                    .map(value -> new AgentJourneyReport.MapOutcome(
                            participant.characterId(), participant.characterName(), value.mapId,
                            value.closedDwellMs + value.openDwellMs, value.visits,
                            value.kills, value.recoveries))
                    .toList();
        }

        synchronized List<AgentJourneyReport.ResourceOutcome> resourceOutcomes() {
            if (first == null || last == null) {
                return List.of();
            }
            Set<Integer> itemIds = new LinkedHashSet<>(first.inventory().keySet());
            itemIds.addAll(last.inventory().keySet());
            itemIds.addAll(acquiredItems.keySet());
            itemIds.addAll(consumedItems.keySet());
            List<AgentJourneyReport.ResourceOutcome> result = new ArrayList<>();
            for (int itemId : itemIds) {
                int start = first.inventory().getOrDefault(itemId, 0);
                int end = last.inventory().getOrDefault(itemId, 0);
                int net = end - start;
                result.add(new AgentJourneyReport.ResourceOutcome(
                        participant.characterId(), participant.characterName(), itemId,
                        start, end, net, acquiredItems.getOrDefault(itemId, 0),
                        consumedItems.getOrDefault(itemId, 0)));
            }
            return result;
        }

        private void progress(long nowMs) {
            lastProgressAtMs = nowMs;
            activeDetections.clear();
            if (recoveryPending) {
                recoveriesSucceeded++;
                recoveryPending = false;
            }
        }

        private void evaluate(AgentJourneyStuckType type,
                              boolean detected,
                              String detail,
                              List<Detection> output) {
            if (!detected) {
                activeDetections.remove(type);
            } else if (activeDetections.add(type)) {
                output.add(new Detection(type, detail));
            }
        }

        private void addTransition(int mapId, int capacity) {
            transitionMaps.addLast(mapId);
            while (transitionMaps.size() > capacity) {
                transitionMaps.removeFirst();
            }
        }

        private boolean navigationLoop(int required) {
            return AgentJourneyStuckClassifier.hasAlternatingMapLoop(
                    List.copyOf(transitionMaps), required);
        }

        private boolean positionOscillation() {
            return AgentJourneyStuckClassifier.hasLocalPositionOscillation(
                    List.copyOf(positions));
        }

        private void trimRecoveries(long nowMs, long windowMs) {
            long cutoff = nowMs - windowMs;
            while (!recoveryTimes.isEmpty() && recoveryTimes.peekFirst() < cutoff) {
                recoveryTimes.removeFirst();
            }
        }

        private void closeMap(long nowMs) {
            if (last == null || mapEnteredAtMs <= 0L) {
                return;
            }
            MutableMapOutcome outcome = map(last.mapId());
            outcome.closedDwellMs += Math.max(0L, nowMs - mapEnteredAtMs);
            outcome.openDwellMs = 0L;
        }

        private MutableMapOutcome map(int mapId) {
            return mapOutcomes.computeIfAbsent(mapId, MutableMapOutcome::new);
        }

        private void accumulateResources(
                AgentJourneySnapshot previous, AgentJourneySnapshot current) {
            int mesosDelta = current.mesos() - previous.mesos();
            if (mesosDelta > 0) {
                mesosGained += mesosDelta;
            } else {
                mesosSpent += -mesosDelta;
            }
            Set<Integer> itemIds = new LinkedHashSet<>(previous.inventory().keySet());
            itemIds.addAll(current.inventory().keySet());
            for (int itemId : itemIds) {
                int delta = current.inventory().getOrDefault(itemId, 0)
                        - previous.inventory().getOrDefault(itemId, 0);
                if (delta > 0) {
                    acquiredItems.merge(itemId, delta, Integer::sum);
                } else if (delta < 0) {
                    consumedItems.merge(itemId, -delta, Integer::sum);
                }
            }
        }

        private static int experienceDelta(
                AgentJourneySnapshot previous, AgentJourneySnapshot current) {
            if (current.level() < previous.level()) {
                return 0;
            }
            if (current.level() == previous.level()) {
                return Math.max(0, current.experience() - previous.experience());
            }
            long gained = Math.max(0,
                    ExpTable.getExpNeededForLevel(previous.level()) - previous.experience());
            for (int level = previous.level() + 1; level < current.level(); level++) {
                gained += ExpTable.getExpNeededForLevel(level);
            }
            gained += Math.max(0, current.experience());
            return (int) Math.min(Integer.MAX_VALUE, gained);
        }

        private static List<AgentJourneyDerivedEvidence> derivedEvidence(
                AgentJourneySnapshot previous, AgentJourneySnapshot current) {
            List<AgentJourneyDerivedEvidence> result = new ArrayList<>();
            if (!previous.planId().equals(current.planId())
                    || !previous.planStatus().equals(current.planStatus())
                    || previous.planStepIndex() != current.planStepIndex()
                    || previous.planStepAttempt() != current.planStepAttempt()
                    || !previous.objectiveId().equals(current.objectiveId())) {
                result.add(new AgentJourneyDerivedEvidence(
                        "plan", "journey.plan-state-changed",
                        "FAILED".equals(current.planStatus())
                                || "BLOCKED".equals(current.planStatus()),
                        Map.of(
                                "fromPlan", previous.planId(),
                                "toPlan", current.planId(),
                                "fromStatus", previous.planStatus(),
                                "toStatus", current.planStatus(),
                                "fromStep", previous.planStepIndex(),
                                "toStep", current.planStepIndex(),
                                "attempt", current.planStepAttempt(),
                                "fromObjective", previous.objectiveId(),
                                "toObjective", current.objectiveId())));
            }
            int expDelta = experienceDelta(previous, current);
            int mesoDelta = current.mesos() - previous.mesos();
            if (expDelta != 0 || mesoDelta != 0 || previous.level() != current.level()
                    || previous.jobId() != current.jobId()) {
                result.add(new AgentJourneyDerivedEvidence(
                        "progression", "journey.character-progress-changed", false,
                        Map.of(
                                "fromLevel", previous.level(),
                                "toLevel", current.level(),
                                "experienceGained", expDelta,
                                "fromJobId", previous.jobId(),
                                "toJobId", current.jobId(),
                                "mesosDelta", mesoDelta)));
            }
            Set<Integer> itemIds = new LinkedHashSet<>(previous.inventory().keySet());
            itemIds.addAll(current.inventory().keySet());
            for (int itemId : itemIds) {
                int before = previous.inventory().getOrDefault(itemId, 0);
                int after = current.inventory().getOrDefault(itemId, 0);
                if (before != after) {
                    result.add(new AgentJourneyDerivedEvidence(
                            "inventory", "journey.item-quantity-reconciled", false,
                            Map.of("itemId", itemId, "before", before,
                                    "after", after, "delta", after - before)));
                }
            }
            Set<Integer> questIds = new LinkedHashSet<>(previous.activeQuestProgress().keySet());
            questIds.addAll(current.activeQuestProgress().keySet());
            for (int questId : questIds) {
                Map<Integer, String> before =
                        previous.activeQuestProgress().getOrDefault(questId, Map.of());
                Map<Integer, String> after =
                        current.activeQuestProgress().getOrDefault(questId, Map.of());
                if (!before.equals(after)) {
                    result.add(new AgentJourneyDerivedEvidence(
                            "quest", "journey.quest-counter-reconciled", false,
                            Map.of("questId", questId, "before", before, "after", after)));
                }
            }
            for (int questId : current.completedQuestIds()) {
                if (!previous.completedQuestIds().contains(questId)) {
                    result.add(new AgentJourneyDerivedEvidence(
                            "quest", "journey.quest-completion-reconciled", true,
                            Map.of("questId", questId)));
                }
            }
            return result;
        }

        private static boolean meaningfulDelta(
                AgentJourneySnapshot previous, AgentJourneySnapshot current) {
            if (previous == null) {
                return true;
            }
            return previous.level() != current.level()
                    || previous.experience() != current.experience()
                    || previous.jobId() != current.jobId()
                    || previous.mapId() != current.mapId()
                    || previous.planStepIndex() != current.planStepIndex()
                    || !previous.planId().equals(current.planId())
                    || !previous.planStatus().equals(current.planStatus())
                    || !previous.objectiveId().equals(current.objectiveId())
                    || previous.mesos() != current.mesos()
                    || !previous.inventory().equals(current.inventory())
                    || !previous.activeQuestProgress().equals(current.activeQuestProgress())
                    || !previous.completedQuestIds().equals(current.completedQuestIds());
        }

        private AgentJourneyReport.QuestOutcome questOutcome(
                int questId,
                QuestBaseline baseline,
                AgentJourneySnapshot current,
                long completedAtMs) {
            Map<Integer, Integer> itemNet = new TreeMap<>();
            Set<Integer> itemIds = new LinkedHashSet<>(baseline.snapshot.inventory().keySet());
            itemIds.addAll(current.inventory().keySet());
            for (int itemId : itemIds) {
                int delta = current.inventory().getOrDefault(itemId, 0)
                        - baseline.snapshot.inventory().getOrDefault(itemId, 0);
                if (delta != 0) {
                    itemNet.put(itemId, delta);
                }
            }
            return new AgentJourneyReport.QuestOutcome(
                    participant.characterId(), participant.characterName(), questId,
                    baseline.snapshot().jobId(), baseline.snapshot().level(),
                    baseline.snapshot().mapId(), current.mapId(),
                    Math.max(0L, completedAtMs - baseline.startedAtMs()),
                    experienceDelta(baseline.snapshot(), current),
                    current.mesos() - baseline.snapshot().mesos(), itemNet);
        }
    }

    private static final class MutableMapOutcome {
        private final int mapId;
        private long closedDwellMs;
        private long openDwellMs;
        private int visits;
        private int kills;
        private int recoveries;

        private MutableMapOutcome(int mapId) {
            this.mapId = mapId;
        }
    }

    private record QuestBaseline(long startedAtMs, AgentJourneySnapshot snapshot) {
    }
}
