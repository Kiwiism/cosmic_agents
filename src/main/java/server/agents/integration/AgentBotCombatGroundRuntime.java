package server.agents.integration;

import client.Character;
import server.bots.BotPhysicsEngine;
import server.maps.Foothold;

import java.awt.Point;

public final class AgentBotCombatGroundRuntime {
    private AgentBotCombatGroundRuntime() {
    }

    public static Foothold findGroundFoothold(Point position, Character bot) {
        if (position == null || bot == null || bot.getMap() == null) {
            return null;
        }

        return BotPhysicsEngine.findGroundFoothold(bot.getMap(), position);
    }
}
