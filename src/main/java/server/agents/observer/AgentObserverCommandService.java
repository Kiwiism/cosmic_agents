package server.agents.observer;

import client.Character;

/** Small command adapter for starting and inspecting the independent observer showcase. */
public final class AgentObserverCommandService {
    private AgentObserverCommandService() {
    }

    public static void execute(Character issuer, String[] params) {
        if (issuer == null) {
            return;
        }
        if (params == null || params.length == 0) {
            issuer.dropMessage(5, "Usage: !observer start <watched IGN> | status | stop");
            return;
        }
        switch (params[0].toLowerCase()) {
            case "start" -> start(issuer, params);
            case "status" -> issuer.dropMessage(5, AgentObserverRuntime.status());
            case "stop" -> issuer.dropMessage(5,
                    AgentObserverRuntime.stop()
                            ? "Observer showcase stopped."
                            : "No observer showcase was active.");
            default -> issuer.dropMessage(5,
                    "Usage: !observer start <watched IGN> | status | stop");
        }
    }

    private static void start(Character issuer, String[] params) {
        if (params.length < 2 || params[1].isBlank()) {
            issuer.dropMessage(5, "Usage: !observer start <watched IGN>");
            return;
        }
        AgentObserverRuntime.StartResult result =
                AgentObserverRuntime.start(issuer, params[1], System.currentTimeMillis());
        issuer.dropMessage(result.started() ? 5 : 6, result.message());
    }
}
