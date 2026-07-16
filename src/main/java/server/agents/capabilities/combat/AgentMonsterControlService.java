package server.agents.capabilities.combat;

import client.Character;
import server.life.Monster;
import server.integration.AgentPresence;
import server.maps.MapleMap;

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

    /** Stops the hidden observer from driving mobs after the last Agent leaves its map. */
    public static void releaseHiddenSimulationControllers(MapleMap map) {
        if (map == null) {
            return;
        }
        for (Monster monster : map.getAllMonsters()) {
            Character controller = monster.getController();
            if (controller != null
                    && controller.isHidden()
                    && !AgentPresence.isAgent(controller)
                    && !map.shouldAllowHiddenMobSimulation(controller)) {
                monster.aggroRedirectController();
            }
        }
    }
}
