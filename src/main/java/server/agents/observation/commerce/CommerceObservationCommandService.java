package server.agents.observation.commerce;

import client.Character;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Operator controls and client-visible diagnostics for the detached observation harness. */
public final class CommerceObservationCommandService {
    private CommerceObservationCommandService() {
    }

    public static List<String> execute(Character operator, String[] params) {
        if (operator == null || params == null || params.length < 2
                || !"observe".equalsIgnoreCase(params[0])) {
            return usage();
        }
        return switch (params[1].toLowerCase(Locale.ROOT)) {
            case "preflight" -> preflight();
            case "start" -> start(params);
            case "resume" -> resume(params);
            case "advance" -> advance(params);
            case "status", "report" -> status();
            case "population" -> population();
            case "rooms" -> rooms();
            case "stalls" -> stalls();
            case "agent" -> agent(params);
            case "audit", "checkpoint" -> audit();
            case "stop" -> stop();
            default -> usage();
        };
    }

    private static List<String> preflight() {
        CommerceScenarioRuntime.Preflight value = CommerceObservationRuntime.preflight();
        List<String> lines = new ArrayList<>();
        lines.add("Commerce observation preflight " + (value.ready() ? "READY" : "BLOCKED")
                + " roster=" + value.mappedCharacters() + '/' + value.requiredCharacters()
                + " currentlyInFM=" + value.initialFmReady() + '/' + value.initialAgents()
                + " permits=" + value.realPermits() + '/' + value.configuredSellers()
                + " calibrationsMissing=" + value.missingCalibrations()
                + " database=" + (value.databaseReady() ? "READY" : "BLOCKED"));
        value.blockers().forEach(blocker -> lines.add(" - " + blocker));
        return List.copyOf(lines);
    }

    private static List<String> start(String[] params) {
        if (params.length > 3) return usage();
        UUID runId = params.length == 3 ? UUID.fromString(params[2]) : UUID.randomUUID();
        CommerceScenarioRuntime.Status status = CommerceObservationRuntime.start(runId);
        return List.of("Commerce observation started run=" + status.runId()
                + " logical=" + status.logicalTime() + " target=" + status.targetLogicalTime());
    }

    private static List<String> resume(String[] params) {
        if (params.length != 3) return List.of("Usage: !commerce observe resume <run-uuid>");
        CommerceScenarioRuntime.Status status =
                CommerceObservationRuntime.resume(UUID.fromString(params[2]));
        return List.of("Commerce observation resumed run=" + status.runId()
                + " logical=" + status.logicalTime());
    }

    private static List<String> advance(String[] params) {
        if (params.length != 3) return List.of("Usage: !commerce observe advance <days>");
        long days = Long.parseLong(params[2]);
        var result = CommerceObservationRuntime.advanceDays(days);
        return List.of("Commerce observation reached " + result.advance().reachedAt()
                + " events=" + result.advance().processedEvents() + " status=" + result.status()
                + (result.advance().waitingExternalAction()
                ? " waiting=" + result.advance().waitReason() : ""));
    }

    private static List<String> status() {
        CommerceScenarioRuntime.Status status = CommerceObservationRuntime.status();
        if (!status.active()) return List.of("Commerce observation is inactive.");
        CommerceScenarioRuntime.ObservationSnapshot snapshot =
                CommerceObservationRuntime.snapshot();
        long stalls = snapshot.agents().stream()
                .filter(CommerceScenarioRuntime.ObservedAgent::openStall).count();
        long offscreen = snapshot.agents().stream()
                .filter(agent -> "OFFSCREEN_ACTIVITY".equals(agent.state())).count();
        long elapsedDays = Duration.between(
                status.targetLogicalTime().minus(Duration.ofDays(30)),
                status.logicalTime()).toDays();
        return List.of("Commerce observation run=" + status.runId() + " day="
                        + (elapsedDays + 1L) + "/30 logical=" + status.logicalTime()
                        + " state=" + status.state(),
                "admitted=" + status.admittedAgents() + "/100 reserved="
                        + status.reservedCharacters() + " staged=" + snapshot.stagedCharacters()
                        + " offscreen=" + offscreen
                        + " openStalls=" + stalls + " occupiedRooms=" + snapshot.rooms().size());
    }

    private static List<String> population() {
        CommerceScenarioRuntime.ObservationSnapshot snapshot =
                CommerceObservationRuntime.snapshot();
        List<String> lines = new ArrayList<>();
        lines.add("Commerce population admitted=" + snapshot.agents().size());
        snapshot.agents().stream().collect(java.util.stream.Collectors.groupingBy(
                        CommerceScenarioRuntime.ObservedAgent::state,
                        java.util.TreeMap::new, java.util.stream.Collectors.counting()))
                .forEach((state, count) -> lines.add(" - " + state + '=' + count));
        return List.copyOf(lines);
    }

    private static List<String> rooms() {
        List<String> lines = new ArrayList<>();
        lines.add("Occupied Commerce observation rooms:");
        CommerceObservationRuntime.snapshot().rooms().stream()
                .sorted(Comparator.comparingInt(CommerceScenarioRuntime.RoomObservation::mapId))
                .forEach(room -> lines.add(" - map=" + room.mapId() + " agents="
                        + room.presentAgents() + " stalls=" + room.openStalls()
                        + " trades=" + room.activeTrades()));
        if (lines.size() == 1) lines.add(" - none");
        return List.copyOf(lines);
    }

    private static List<String> stalls() {
        List<String> lines = new ArrayList<>();
        lines.add("Open Commerce observation stalls:");
        CommerceObservationRuntime.snapshot().agents().stream()
                .filter(CommerceScenarioRuntime.ObservedAgent::openStall)
                .forEach(agent -> lines.add(" - " + display(agent)));
        if (lines.size() == 1) lines.add(" - none");
        return List.copyOf(lines);
    }

    private static List<String> agent(String[] params) {
        if (params.length != 3) return List.of("Usage: !commerce observe agent <logical-id|IGN>");
        String wanted = params[2];
        return CommerceObservationRuntime.snapshot().agents().stream()
                .filter(agent -> agent.logicalAgentId().equalsIgnoreCase(wanted)
                        || agent.characterName().equalsIgnoreCase(wanted))
                .findFirst().map(agent -> List.of(display(agent),
                        "state=" + agent.state() + " job=" + agent.jobFamily()
                                + " level=" + agent.level() + " session=" + agent.sessionId()))
                .orElseGet(() -> List.of("Commerce observation agent not found: " + wanted));
    }

    private static List<String> audit() {
        var result = CommerceObservationRuntime.audit();
        return List.of("Commerce audit relayed=" + result.relay().delivered()
                + " ingested=" + result.ingestion().ingested()
                + " quarantined=" + result.ingestion().quarantined()
                + " invariantClean=" + result.audit().clean()
                + " violations=" + result.audit().violations().size());
    }

    private static List<String> stop() {
        CommerceObservationRuntime.stop();
        return List.of("Commerce observation stopped and checkpointed.");
    }

    private static String display(CommerceScenarioRuntime.ObservedAgent agent) {
        return agent.logicalAgentId() + '/' + agent.characterName() + " map=" + agent.mapId()
                + " pos=" + agent.x() + ',' + agent.y()
                + " stall=" + agent.openStall() + " trade=" + agent.activeTrade();
    }

    private static List<String> usage() {
        return List.of("Usage: !commerce observe preflight|start [run-uuid]|resume <run-uuid>"
                + "|advance <days>|status|population|rooms|stalls|agent <id|IGN>"
                + "|audit|checkpoint|report|stop");
    }
}
