package server.agents.capabilities.combat;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed active grind target state.
 */
public final class AgentGrindTargetStateRuntime {
    private AgentGrindTargetStateRuntime() {
    }

    public static Monster target(AgentRuntimeEntry entry) {
        return entry.grindTargetState().target();
    }

    public static Monster activeTargetInMap(AgentRuntimeEntry entry, MapleMap map) {
        Monster target = target(entry);
        return target != null && target.isAlive() && target.getMap() == map ? target : null;
    }

    public static Monster targetInSeekRange(AgentRuntimeEntry entry, Character bot, Point botPos, double seekRangeSq) {
        if (bot == null || botPos == null) {
            return null;
        }
        Monster target = activeTargetInMap(entry, bot.getMap());
        if (target == null
                || !AgentCombatObjectiveTargetStateRuntime.allows(entry, target.getId())
                || target.getPosition().distanceSq(botPos) > seekRangeSq) {
            return null;
        }
        return target;
    }

    public static void setTarget(AgentRuntimeEntry entry, Monster target) {
        entry.grindTargetState().setTarget(target);
    }

    public static void commitTarget(AgentRuntimeEntry entry,
                                    Monster target,
                                    long nowMs,
                                    long commitmentDurationMs) {
        entry.grindTargetState().commitTarget(
                target, nowMs + Math.max(0L, commitmentDurationMs));
    }

    public static boolean committedTo(AgentRuntimeEntry entry, Monster target, long nowMs) {
        return entry != null && entry.grindTargetState().committedTo(target, nowMs);
    }

    public static int targetSwitchCount(AgentRuntimeEntry entry) {
        return entry == null ? 0 : entry.grindTargetState().targetSwitchCount();
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.grindTargetState().clearTarget();
    }
}
