package client.command.commands.gm6;

import client.Client;
import client.Character;
import client.command.Command;
import com.zaxxer.hikari.HikariDataSource;
import server.agents.economy.activity.LiveActivityCalibrationRuntime;
import server.agents.economy.integration.cosmic.EconomySimulationRuntime;
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
                show(client, EconomySimulationRuntime.status()); return;
            }
            if ("start".equalsIgnoreCase(params[0])) {
                show(client, EconomySimulationRuntime.start()); return;
            }
            if ("advance".equalsIgnoreCase(params[0]) && params.length == 2) {
                long days = Long.parseLong(params[1]);
                var result = EconomySimulationRuntime.advanceDays(days);
                client.getPlayer().yellowMessage("Economy reached " + result.advance().reachedAt()
                        + "; events=" + result.advance().processedEvents() + "; status=" + result.status()
                        + (result.advance().waitingExternalAction()
                        ? "; waiting=" + result.advance().waitReason() : ""));
                return;
            }
            if ("stop".equalsIgnoreCase(params[0])) {
                EconomySimulationRuntime.stop(); client.getPlayer().yellowMessage("Economy runtime stopped."); return;
            }
            if ("calibration".equalsIgnoreCase(params[0])) {
                calibration(client, params); return;
            }
            client.getPlayer().yellowMessage("Usage: !economy start | advance <non-negative-days> | status | stop "
                    + "| calibration start|stop|status <agent-character-id> [died]");
        } catch (RuntimeException failure) {
            client.getPlayer().yellowMessage("Economy command failed: "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    private static void show(Client client, EconomySimulationRuntime.Status status) {
        client.getPlayer().yellowMessage(status.active()
                ? "Economy run=" + status.runId() + " logical=" + status.logicalTime()
                + " admitted=" + status.admittedAgents() + "/" + status.reservedCharacters()
                : "Economy runtime is inactive.");
    }

    private static void calibration(Client client, String[] params) {
        if (params.length < 3) {
            client.getPlayer().yellowMessage(
                    "Usage: !economy calibration start|stop|status <agent-character-id> [died]");
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
}
