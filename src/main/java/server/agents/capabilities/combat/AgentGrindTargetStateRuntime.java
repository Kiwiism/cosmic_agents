package server.agents.capabilities.combat;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.maps.MapleMap;
import server.agents.events.AgentEventPriority;
import server.agents.operations.events.AgentCombatTargetChangedEvent;
import server.agents.operations.events.AgentOperationalEventPublisher;

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
        Monster previous = entry.grindTargetState().target();
        entry.grindTargetState().setTarget(target);
        publishTargetChange(entry, previous, target);
    }

    public static void commitTarget(AgentRuntimeEntry entry,
                                    Monster target,
                                    long nowMs,
                                    long commitmentDurationMs) {
        Monster previous = entry.grindTargetState().target();
        entry.grindTargetState().commitTarget(
                target, nowMs + Math.max(0L, commitmentDurationMs));
        publishTargetChange(entry, previous, target);
    }

    public static boolean committedTo(AgentRuntimeEntry entry, Monster target, long nowMs) {
        return entry != null && entry.grindTargetState().committedTo(target, nowMs);
    }

    public static int targetSwitchCount(AgentRuntimeEntry entry) {
        return entry == null ? 0 : entry.grindTargetState().targetSwitchCount();
    }

    public static void clear(AgentRuntimeEntry entry) {
        Monster previous = entry.grindTargetState().target();
        entry.grindTargetState().clearTarget();
        publishTargetChange(entry, previous, null);
    }

    private static void publishTargetChange(AgentRuntimeEntry entry, Monster previous, Monster target) {
        if (previous == target) {
            return;
        }
        int previousObjectId = previous == null ? 0 : Math.max(0, previous.getObjectId());
        int targetObjectId = target == null ? 0 : Math.max(0, target.getObjectId());
        if (target != null && targetObjectId == 0) {
            return;
        }
        int targetMobId = target == null ? 0 : Math.max(0, target.getId());
        AgentOperationalEventPublisher.publish(entry,
                objectiveId -> new AgentCombatTargetChangedEvent(
                        entry.bot().getId(), System.currentTimeMillis(), previousObjectId,
                        targetObjectId, targetMobId, targetSwitchCount(entry), objectiveId),
                AgentEventPriority.NORMAL);
    }
}
