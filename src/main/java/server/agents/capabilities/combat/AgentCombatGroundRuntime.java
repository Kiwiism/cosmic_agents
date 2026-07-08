package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.movement.AgentGroundingService;
import server.maps.Foothold;

import java.awt.Point;

public final class AgentCombatGroundRuntime {
    private AgentCombatGroundRuntime() {
    }

    public static Foothold findGroundFoothold(Point position, Character bot) {
        if (position == null || bot == null || bot.getMap() == null) {
            return null;
        }

        return AgentGroundingService.findGroundFoothold(bot.getMap(), position);
    }
}
