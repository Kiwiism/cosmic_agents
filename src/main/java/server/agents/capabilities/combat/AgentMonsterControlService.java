package server.agents.capabilities.combat;

import client.Character;
import server.life.Monster;

import java.util.Collection;

/**
 * Releases monster controller ownership that cannot be driven by headless Agents.
 */
public final class AgentMonsterControlService {
    private AgentMonsterControlService() {
    }

    public static void releaseControlledMonsters(Character agent) {
        Collection<Monster> controlled = agent.getControlledMonsters();
        if (controlled.isEmpty()) {
            return;
        }

        for (Monster monster : controlled) {
            monster.aggroRedirectController();
        }
    }
}
