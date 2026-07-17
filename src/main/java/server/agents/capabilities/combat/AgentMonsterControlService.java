package server.agents.capabilities.combat;

import client.Character;
import server.life.Monster;
import server.integration.AgentPresence;
import server.life.simulation.MobControlAuthority;
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
            // The channel MobPhysicsService owns the controller and lifecycle while
            // this authority is active.  The legacy per-Agent cleanup exists for
            // headless controllers that cannot simulate MOVE_LIFE; redirecting a
            // physics-owned monster here tears down its session before the first
            // server movement publication.
            if (monster.getControlAuthority() == MobControlAuthority.AGENT_PHYSICS) {
                continue;
            }
            monster.aggroRedirectController();
        }
    }

    /** Stops the hidden observer from driving mobs after the last Agent leaves its map. */
    public static void releaseHiddenSimulationControllers(MapleMap map) {
        if (map == null) {
            return;
        }
        for (Monster monster : server.agents.perception.AgentMapPerception.monsters(map)) {
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
