package server.agents.capabilities.expedition.balrog;

import client.Character;
import server.agents.capabilities.expedition.AgentExpeditionLobbyService;

import java.util.List;

/** Easy Balrog command facade over the reusable Agent expedition lobby. */
public final class AgentBalrogTestService {
    private static final AgentExpeditionLobbyService LOBBY =
            new AgentExpeditionLobbyService(AgentEasyBalrogScenario::new);

    private AgentBalrogTestService() {
    }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        return LOBBY.execute(operator, params, nowMs);
    }
}
