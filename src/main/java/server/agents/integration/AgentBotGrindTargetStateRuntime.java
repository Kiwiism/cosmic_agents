package server.agents.integration;

import client.Character;
import server.bots.BotEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed active grind target state.
 */
public final class AgentBotGrindTargetStateRuntime {
    private AgentBotGrindTargetStateRuntime() {
    }

    public static Monster target(BotEntry entry) {
        return entry.grindTargetState().target();
    }

    public static Monster activeTargetInMap(BotEntry entry, MapleMap map) {
        Monster target = target(entry);
        return target != null && target.isAlive() && target.getMap() == map ? target : null;
    }

    public static Monster targetInSeekRange(BotEntry entry, Character bot, Point botPos, double seekRangeSq) {
        if (bot == null || botPos == null) {
            return null;
        }
        Monster target = activeTargetInMap(entry, bot.getMap());
        if (target == null || target.getPosition().distanceSq(botPos) > seekRangeSq) {
            return null;
        }
        return target;
    }

    public static void setTarget(BotEntry entry, Monster target) {
        entry.grindTargetState().setTarget(target);
    }

    public static void clear(BotEntry entry) {
        entry.grindTargetState().clearTarget();
    }
}
