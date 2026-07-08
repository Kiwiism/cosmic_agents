package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.combat.data.AgentMobHitboxProvider;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;
import java.awt.Rectangle;

public final class AgentMobTouchRuntime {
    private AgentMobTouchRuntime() {
    }

    public static boolean isMobTouchingAgent(AgentRuntimeEntry entry, Character agent, Monster mob, int sweepHeight) {
        Rectangle agentBounds = agentTouchBounds(entry, agent, sweepHeight);
        Rectangle mobBounds = AgentMobHitboxProvider.getInstance().getMobBounds(mob);
        if (mobBounds == null) {
            return false;
        }
        return AgentMobTouchPolicy.lowerHalfIntersects(mobBounds, agentBounds);
    }

    public static Rectangle agentTouchBounds(AgentRuntimeEntry entry, Character agent, int sweepHeight) {
        Point currentPos = agent.getPosition();
        Point previousPos = currentPos;
        Point rememberedPos = AgentMobTouchStateRuntime.previousCheckPositionOnMap(entry, agent.getMapId());
        if (rememberedPos != null) {
            previousPos = rememberedPos;
        }

        return AgentMobTouchPolicy.botTouchSweepBounds(previousPos, currentPos, sweepHeight);
    }

    public static void rememberMobTouchCheck(AgentRuntimeEntry entry, Character agent, Point position) {
        if (entry == null || agent == null || position == null) {
            return;
        }

        AgentMobTouchStateRuntime.rememberCheck(entry, position, agent.getMapId());
    }
}
