package server.agents.capabilities.presentation;

import client.Character;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackRoute;
import server.agents.integration.AgentPacketGatewayRuntime;

import java.awt.Point;
import java.util.Map;

/** Presentation-only weapon swing with no damage or target ownership. */
public final class AgentWeaponFlourishService {
    private AgentWeaponFlourishService() {
    }

    public static boolean flourish(Character agent, Point facingPoint) {
        if (agent == null || facingPoint == null || agent.getMap() == null) return false;
        AgentAttackExecutionProvider.BasicAttackData attack =
                AgentAttackExecutionProvider.buildBasicAttackData(agent, facingPoint);
        if (attack == null || attack.route() != AgentAttackRoute.CLOSE) return false;
        AgentPacketGatewayRuntime.packets().broadcastCloseRangeAttack(agent, 0, 0, attack.stance(), 0,
                Map.of(), attack.speed(), attack.direction(), attack.display());
        return true;
    }
}
