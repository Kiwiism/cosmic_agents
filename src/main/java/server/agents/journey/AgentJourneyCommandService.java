package server.agents.journey;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.List;

/** GM-facing operator surface for bounded autonomous progression experiments. */
public final class AgentJourneyCommandService {
    private AgentJourneyCommandService() {
    }

    public static void execute(Character player, String[] params) {
        if (player == null || params == null || params.length == 0) {
            usage(player);
            return;
        }
        switch (params[0].toLowerCase()) {
            case "run" -> run(player, params);
            case "status" -> status(player, params);
            case "agent" -> agent(player, params);
            case "report" -> report(player, params);
            case "stop" -> stop(player, params);
            default -> usage(player);
        }
    }

    private static void run(Character player, String[] params) {
        if (params.length < 3) {
            usage(player);
            return;
        }
        int count;
        try {
            count = Integer.parseInt(params[2]);
        } catch (NumberFormatException invalid) {
            player.yellowMessage("Journey Agent count must be an integer.");
            return;
        }
        if (count < 1) {
            player.yellowMessage("Journey Agent count must be positive.");
            return;
        }
        String mode = params.length >= 4 ? params[3] : "full";
        List<AgentRuntimeEntry> available = AgentRuntimeRegistry.agentEntriesForLeader(
                        player.getId()).stream()
                .filter(entry -> AgentRuntimeIdentityRuntime.bot(entry) != null)
                .limit(count)
                .toList();
        if (available.size() < count) {
            player.yellowMessage("Journey requested " + count + " Agents but only "
                    + available.size() + " are live in your Agent cohort. "
                    + "Spawn the reusable Agents first.");
            return;
        }
        AgentJourneyRuntime.StartResult result = AgentJourneyRuntime.start(
                params[1], available, mode, System.currentTimeMillis());
        if (!result.started()) {
            player.yellowMessage("Journey was rejected: " + result.reason());
            return;
        }
        player.yellowMessage("Journey " + result.runId() + " started with "
                + result.participants() + " Agents. Evidence: " + result.directory());
        player.yellowMessage("Careers rotate warrior, bowman, magician, thief-dagger, "
                + "pirate-knuckle; Lv15+ continues through the universal Victoria quest pool.");
    }

    private static void status(Character player, String[] params) {
        String runId = params.length >= 2 ? params[1] : "";
        AgentJourneyRuntime.StatusResult result = AgentJourneyRuntime.status(runId);
        if (!result.found()) {
            player.yellowMessage("Journey run was not found.");
            return;
        }
        long succeeded = result.agents().stream()
                .filter(agent -> "SUCCEEDED".equals(agent.status())).count();
        long failed = result.agents().stream()
                .filter(agent -> "FAILED".equals(agent.status())).count();
        player.yellowMessage("Journey " + result.runId() + " status=" + result.status()
                + " scenario=" + result.scenarioId() + " target=Lv" + result.targetLevel()
                + " agents=" + result.agents().size() + " succeeded=" + succeeded
                + " failed=" + failed + ".");
        result.agents().stream().limit(8).forEach(agent ->
                player.yellowMessage(agent.agentName() + " " + agent.career() + " Lv"
                        + agent.level() + " map=" + agent.mapId() + " plan=" + agent.planId()
                        + " objective=" + agent.objectiveId() + " stuck="
                        + agent.stuckEpisodes() + " recovery=" + agent.recoveries() + "."));
        if (result.agents().size() > 8) {
            player.yellowMessage("Showing 8/" + result.agents().size()
                    + "; use !journey agent " + result.runId() + " <ign> for one Agent.");
        }
    }

    private static void agent(Character player, String[] params) {
        if (params.length < 3) {
            usage(player);
            return;
        }
        AgentJourneyTraceView trace = AgentJourneyRuntime.agent(params[1], params[2]);
        if (trace == null) {
            player.yellowMessage("Journey Agent was not found.");
            return;
        }
        player.yellowMessage(trace.agentName() + " " + trace.career()
                + " status=" + trace.status() + " Lv" + trace.level()
                + " map=" + trace.mapId() + " plan=" + trace.planId()
                + " objective=" + trace.objectiveId() + ".");
        player.yellowMessage("kills=" + trace.kills()
                + " quests=" + trace.questsCompleted() + " recoveries=" + trace.recoveries()
                + " stuckEpisodes=" + trace.stuckEpisodes()
                + (trace.failureReason().isBlank() ? "" : " failure=" + trace.failureReason()));
    }

    private static void report(Character player, String[] params) {
        String runId = params.length >= 2 ? params[1] : "";
        AgentJourneyRuntime.ReportResult result =
                AgentJourneyRuntime.report(runId, System.currentTimeMillis());
        player.yellowMessage(result.written()
                ? "Journey report written to " + result.path()
                : "Journey report failed: " + result.reason());
    }

    private static void stop(Character player, String[] params) {
        String runId = params.length >= 2 ? params[1] : "";
        AgentJourneyRuntime.ReportResult result =
                AgentJourneyRuntime.stop(runId, System.currentTimeMillis());
        player.yellowMessage(result.written()
                ? "Journey stopped; final report written to " + result.path()
                : "Journey stop failed: " + result.reason());
    }

    private static void usage(Character player) {
        if (player == null) {
            return;
        }
        player.yellowMessage("!journey run victoria-lv10-20 <count> [off|light|full]");
        player.yellowMessage("!journey status [run-id]");
        player.yellowMessage("!journey agent <run-id> <ign>");
        player.yellowMessage("!journey report [run-id]");
        player.yellowMessage("!journey stop [run-id]");
    }
}
