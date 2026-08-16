package server.agents.commands.townlife;

import client.Character;
import server.agents.runtime.townlife.ambient.AgentTownLifeAmbientManifest;
import server.agents.runtime.townlife.ambient.AgentTownLifeAmbientManifestRepository;
import server.agents.runtime.townlife.ambient.AgentTownLifeAmbientRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Thin operator surface over the external ambient population owner. */
public final class AgentTownLifeAmbientCommandService {
    private AgentTownLifeAmbientCommandService() {
    }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || params == null || params.length < 2) {
            return usage();
        }
        return switch (params[1].toLowerCase(Locale.ROOT)) {
            case "start" -> start(operator, params, nowMs);
            case "stop" -> stop(operator, nowMs);
            case "status" -> status(operator);
            case "rebalance" -> rebalance(operator, nowMs);
            case "percent" -> percent(operator, params, nowMs);
            case "inspect" -> inspect(operator, params);
            default -> usage();
        };
    }

    private static List<String> start(Character operator, String[] params, long nowMs) {
        if (params.length > 5) {
            return usage();
        }
        Integer poolSize = params.length >= 3 ? integer(params[2], "pool-size") : null;
        Integer activePercent = params.length >= 4
                ? integer(params[3], "active-percent") : null;
        AgentTownLifeAmbientManifest.StandbyMode standby = params.length >= 5
                ? standby(params[4]) : null;
        AgentTownLifeAmbientRuntime.StartResult result = AgentTownLifeAmbientRuntime.start(
                operator, poolSize, activePercent, standby, nowMs);
        return List.of(result.message(), "deployment=" + result.deploymentId()
                + " pool=" + result.poolSize() + " targetActive=" + result.targetActive());
    }

    private static List<String> stop(Character operator, long nowMs) {
        AgentTownLifeAmbientRuntime.StopResult result =
                AgentTownLifeAmbientRuntime.stop(operator, nowMs);
        return List.of(result.message() + " draining=" + result.draining());
    }

    private static List<String> status(Character operator) {
        AgentTownLifeAmbientRuntime.Status status = AgentTownLifeAmbientRuntime.status(operator);
        if (status == null) {
            AgentTownLifeAmbientManifest manifest =
                    AgentTownLifeAmbientManifestRepository.defaultManifest();
            return List.of("No ambient TownLife deployment is active.",
                    "defaults pool=" + manifest.defaultPoolSize() + " active="
                            + manifest.targetActivePercent() + "% standby=" + manifest.standbyMode()
                            + " towns=" + manifest.towns().size());
        }
        List<String> lines = new ArrayList<>();
        lines.add("Ambient TownLife deployment=" + status.deploymentId()
                + " pool=" + status.poolSize() + " active=" + status.active() + '/'
                + status.targetActive() + " (" + status.activePercent() + "%) draining="
                + status.draining() + " standby=" + status.standbyMode()
                + " state=" + (status.terminated() ? "TERMINATED"
                : status.stopping() ? "DRAINING" : "RUNNING"));
        for (AgentTownLifeAmbientRuntime.TownStatus town : status.towns()) {
            lines.add(town.profileId() + " map=" + town.mapId() + " assigned="
                    + town.assigned() + " active=" + town.active() + " draining="
                    + town.draining() + " visibleStandby=" + town.visibleStandby()
                    + " failures=" + town.failures());
        }
        if (!status.releaseFailure().isBlank()) {
            lines.add("poolReleaseFailure=" + status.releaseFailure());
        }
        return List.copyOf(lines);
    }

    private static List<String> rebalance(Character operator, long nowMs) {
        return List.of(AgentTownLifeAmbientRuntime.rebalanceNow(operator, nowMs)
                ? "Ambient TownLife rebalance requested."
                : "No ambient TownLife deployment is active.");
    }

    private static List<String> percent(Character operator, String[] params, long nowMs) {
        if (params.length != 3) {
            return List.of("Usage: !townlife ambient percent <0-100>");
        }
        int percent = integer(params[2], "active-percent");
        return List.of(AgentTownLifeAmbientRuntime.setActivePercent(operator, percent, nowMs)
                ? "Ambient TownLife target updated to " + percent + "%."
                : "No running ambient TownLife deployment can be updated.");
    }

    private static List<String> inspect(Character operator, String[] params) {
        if (params.length != 3) {
            return List.of("Usage: !townlife ambient inspect <agent-name>");
        }
        AgentTownLifeAmbientRuntime.MemberStatus status =
                AgentTownLifeAmbientRuntime.inspect(operator, params[2]);
        if (status == null) {
            return List.of("That Agent is not assigned to this ambient TownLife deployment.");
        }
        return List.of(status.name() + " town=" + status.profileId() + " map=" + status.mapId()
                        + " live=" + status.live() + " active=" + status.active()
                        + " draining=" + status.draining() + " visits=" + status.visits(),
                "activity=" + status.activity() + " stage=" + status.stage()
                        + " transition=" + status.lastTransition()
                        + (status.failure().isBlank() ? "" : " failure=" + status.failure()));
    }

    private static int integer(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(label + " must be a whole number");
        }
    }

    private static AgentTownLifeAmbientManifest.StandbyMode standby(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "hidden", "unmaterialized", "off-map" ->
                    AgentTownLifeAmbientManifest.StandbyMode.UNMATERIALIZED;
            case "visible", "portal" -> AgentTownLifeAmbientManifest.StandbyMode.VISIBLE;
            default -> throw new IllegalArgumentException(
                    "standby must be visible or unmaterialized");
        };
    }

    private static List<String> usage() {
        return List.of("Usage: !townlife ambient start [pool-size] [active-percent]"
                + " [visible|unmaterialized]|stop|status|rebalance|percent <0-100>"
                + "|inspect <agent-name>");
    }
}
