package client.command.commands.gm6;

import client.Client;
import client.Character;
import client.command.Command;
import com.zaxxer.hikari.HikariDataSource;
import server.agents.economy.activity.LiveActivityCalibrationRuntime;
import server.agents.observation.commerce.CommerceScenarioRuntime;
import server.agents.economy.persistence.EconomyDatabaseVerifier;
import server.agents.economy.persistence.EconomyPostgresDataSource;
import server.agents.economy.persistence.JdbcActivityCalibrationStore;
import server.agents.economy.scenario.EconomyConfigLoader;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

/** Operator control only; economic decisions and market participation remain autonomous. */
public final class EconomyCommand extends Command {
    public EconomyCommand() {
        setDescription("Control economy runs and live activity calibration capture");
    }

    @Override
    public void execute(Client client, String[] params) {
        try {
            if (params.length == 0 || "status".equalsIgnoreCase(params[0])) {
                show(client, CommerceScenarioRuntime.status()); return;
            }
            if ("start".equalsIgnoreCase(params[0])) {
                if (params.length == 1) show(client, CommerceScenarioRuntime.start());
                else if (params.length == 3) show(client, CommerceScenarioRuntime.start(
                        java.util.UUID.fromString(params[1]), java.nio.file.Path.of(params[2])));
                else throw new IllegalArgumentException("start accepts no arguments or <run-uuid> <config-path>");
                return;
            }
            if ("resume".equalsIgnoreCase(params[0]) && params.length == 2) {
                show(client, CommerceScenarioRuntime.resume(java.util.UUID.fromString(params[1]))); return;
            }
            if ("preflight".equalsIgnoreCase(params[0])) {
                if (params.length == 1) preflight(client, CommerceScenarioRuntime.preflight());
                else if (params.length == 2) preflight(client, CommerceScenarioRuntime.preflight(
                        java.nio.file.Path.of(params[1])));
                else throw new IllegalArgumentException("preflight accepts an optional <config-path>");
                return;
            }
            if ("advance".equalsIgnoreCase(params[0]) && params.length == 2) {
                long days = Long.parseLong(params[1]);
                var result = CommerceScenarioRuntime.advanceDays(days);
                client.getPlayer().yellowMessage("Economy reached " + result.advance().reachedAt()
                        + "; events=" + result.advance().processedEvents() + "; status=" + result.status()
                        + (result.advance().waitingExternalAction()
                        ? "; waiting=" + result.advance().waitReason() : ""));
                return;
            }
            if ("audit".equalsIgnoreCase(params[0])) {
                evidence(client, "Audit", CommerceScenarioRuntime.audit()); return;
            }
            if ("complete".equalsIgnoreCase(params[0])) {
                evidence(client, "Completion", CommerceScenarioRuntime.complete()); return;
            }
            if ("fail".equalsIgnoreCase(params[0]) && params.length >= 2) {
                String reason = String.join(" ", java.util.Arrays.copyOfRange(params, 1, params.length));
                evidence(client, "Failure recorded", CommerceScenarioRuntime.fail(reason)); return;
            }
            if ("experiment".equalsIgnoreCase(params[0])) {
                experiment(client, params); return;
            }
            if ("stop".equalsIgnoreCase(params[0])) {
                CommerceScenarioRuntime.stop(); client.getPlayer().yellowMessage("Economy runtime stopped."); return;
            }
            if ("calibration".equalsIgnoreCase(params[0])) {
                calibration(client, params); return;
            }
            client.getPlayer().yellowMessage("Usage: !economy preflight [config-path] | start [run-uuid config-path] "
                    + "| resume <run-uuid> "
                    + "| advance <non-negative-days> | audit | complete | fail <reason> | status | stop "
                    + "| experiment plan <manifest-path> | experiment next <experiment-id> "
                    + "| calibration start|stop|status <agent-character-id> [died] "
                    + "| calibration start-all|stop-all <map-id> [died]");
        } catch (RuntimeException failure) {
            client.getPlayer().yellowMessage("Economy command failed: "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    private static void preflight(Client client, CommerceScenarioRuntime.Preflight value) {
        client.getPlayer().yellowMessage("Economy preflight " + (value.ready() ? "READY" : "BLOCKED")
                + ": roster=" + value.mappedCharacters() + '/' + value.requiredCharacters()
                + " initialFM=" + value.initialFmReady() + '/' + value.initialAgents()
                + " permits=" + value.realPermits() + '/' + value.configuredSellers()
                + " calibrationsMissing=" + value.missingCalibrations()
                + " database=" + (value.databaseReady() ? "READY" : "BLOCKED"));
        for (String blocker : value.blockers()) client.getPlayer().yellowMessage(" - " + blocker);
    }

    private static void show(Client client, CommerceScenarioRuntime.Status status) {
        client.getPlayer().yellowMessage(status.active()
                ? "Economy run=" + status.runId() + " logical=" + status.logicalTime()
                + " target=" + status.targetLogicalTime() + " state=" + status.state()
                + " mode=" + status.clockMode()
                + " admitted=" + status.admittedAgents() + "/" + status.reservedCharacters()
                : "Economy runtime is inactive.");
    }

    private static void evidence(Client client, String label,
                                 server.agents.economy.persistence.EconomyEvidencePipeline.Result result) {
        client.getPlayer().yellowMessage(label + ": relayed=" + result.relay().delivered()
                + " ingested=" + result.ingestion().ingested()
                + " quarantined=" + result.ingestion().quarantined()
                + " invariantClean=" + result.audit().clean()
                + " violations=" + result.audit().violations().size());
    }

    private static void experiment(Client client, String[] params) {
        if (params.length != 3)
            throw new IllegalArgumentException("experiment requires plan <manifest-path> or next <experiment-id>");
        var config = new EconomyConfigLoader().load().config();
        try (HikariDataSource database = EconomyPostgresDataSource.fromEnvironment()) {
            new EconomyDatabaseVerifier(database).verify(config.persistence.database);
            var planner = new server.agents.economy.experiment.EconomyExperimentPlanner(database);
            if ("plan".equalsIgnoreCase(params[1])) {
                var plan = planner.plan(java.nio.file.Path.of(params[2]));
                client.getPlayer().yellowMessage("Experiment planned: " + plan.experimentId()
                        + " pairs=" + plan.pairs().size());
                return;
            }
            if ("next".equalsIgnoreCase(params[1])) {
                var next = planner.next(params[2]);
                client.getPlayer().yellowMessage(next == null ? "Experiment complete: " + params[2]
                        : "Next " + next.side() + " pair=" + next.pairId() + " seed=" + next.seed()
                        + " run=" + next.runId() + " config=" + next.configPath()
                        + " current=" + (next.currentStatus() == null ? "NOT_STARTED" : next.currentStatus()));
                return;
            }
            throw new IllegalArgumentException("experiment action must be plan or next");
        }
    }

    private static void calibration(Client client, String[] params) {
        if (params.length < 3) {
            client.getPlayer().yellowMessage(
                    "Usage: !economy calibration start|stop|status <agent-character-id> [died] "
                            + "| start-all|stop-all <map-id> [died]");
            return;
        }
        if ("start-all".equalsIgnoreCase(params[1])) {
            startAllCalibrations(client, Integer.parseInt(params[2]));
            return;
        }
        if ("stop-all".equalsIgnoreCase(params[1])) {
            boolean died = params.length > 3 && Boolean.parseBoolean(params[3]);
            stopAllCalibrations(client, Integer.parseInt(params[2]), died);
            return;
        }
        int characterId = Integer.parseInt(params[2]);
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : entry.bot();
        if (agent == null) throw new IllegalStateException("live autonomous character not found: " + characterId);
        if ("start".equalsIgnoreCase(params[1])) {
            String build = new EconomyConfigLoader().load().config().activity.agentBuild;
            LiveActivityCalibrationRuntime.begin(agent, build, System.currentTimeMillis());
            client.getPlayer().yellowMessage("Calibration started for " + characterId + " on map "
                    + agent.getMapId() + " as " + build);
            return;
        }
        if ("status".equalsIgnoreCase(params[1])) {
            LiveActivityCalibrationRuntime.Status status = LiveActivityCalibrationRuntime.status(agent);
            client.getPlayer().yellowMessage(status == null ? "No active calibration for " + characterId
                    : "Calibration agent=" + characterId + " build=" + status.agentBuild()
                    + " map=" + status.mapId() + " level=" + status.level()
                    + " job=" + status.jobFamily() + " since=" + status.startedAt());
            return;
        }
        if ("stop".equalsIgnoreCase(params[1])) {
            boolean died = params.length > 3 && Boolean.parseBoolean(params[3]);
            var config = new EconomyConfigLoader().load().config();
            try (HikariDataSource database = EconomyPostgresDataSource.fromEnvironment()) {
                new EconomyDatabaseVerifier(database).verify(config.persistence.database);
                var sample = LiveActivityCalibrationRuntime.end(agent, died, System.currentTimeMillis(),
                        new JdbcActivityCalibrationStore(database));
                client.getPlayer().yellowMessage("Calibration saved: " + sample.sampleId()
                        + " kills=" + sample.killCounts().values().stream().mapToInt(Integer::intValue).sum());
            }
            return;
        }
        throw new IllegalArgumentException("calibration action must be start, stop, or status");
    }

    private static void startAllCalibrations(Client client, int mapId) {
        var config = new EconomyConfigLoader().load().config();
        var agents = liveAgentsOnMap(mapId);
        int started = 0;
        for (Character agent : agents) {
            if (LiveActivityCalibrationRuntime.status(agent) != null) continue;
            LiveActivityCalibrationRuntime.begin(agent, config.activity.agentBuild, System.currentTimeMillis());
            started++;
        }
        client.getPlayer().yellowMessage("Calibration batch started=" + started + " eligible="
                + agents.size() + " map=" + mapId + " build=" + config.activity.agentBuild);
    }

    private static void stopAllCalibrations(Client client, int mapId, boolean died) {
        var config = new EconomyConfigLoader().load().config();
        var agents = liveAgentsOnMap(mapId).stream()
                .filter(agent -> LiveActivityCalibrationRuntime.status(agent) != null).toList();
        int saved = 0;
        int failed = 0;
        try (HikariDataSource database = EconomyPostgresDataSource.fromEnvironment()) {
            new EconomyDatabaseVerifier(database).verify(config.persistence.database);
            JdbcActivityCalibrationStore store = new JdbcActivityCalibrationStore(database);
            for (Character agent : agents) {
                try {
                    LiveActivityCalibrationRuntime.end(agent, died, System.currentTimeMillis(), store);
                    saved++;
                } catch (RuntimeException failure) {
                    failed++;
                }
            }
        }
        client.getPlayer().yellowMessage("Calibration batch saved=" + saved + " failed=" + failed
                + " map=" + mapId + " died=" + died);
    }

    private static java.util.List<Character> liveAgentsOnMap(int mapId) {
        if (mapId <= 0) throw new IllegalArgumentException("map-id must be positive");
        return AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(AgentRuntimeEntry::bot).filter(java.util.Objects::nonNull)
                .filter(agent -> agent.getMapId() == mapId)
                .sorted(java.util.Comparator.comparingInt(Character::getId)).toList();
    }
}
