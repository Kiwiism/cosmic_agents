package server.agents.capabilities.combat;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

/**
 * Remembers the last mob-touch sweep position for one Agent.
 */
public final class AgentMobTouchState {
    public static final AgentCapabilityStateKey<AgentMobTouchState> STATE_KEY =
            new AgentCapabilityStateKey<>("combat.mob-touch",
                    AgentMobTouchState.class, AgentMobTouchState::new);

    private Point lastCheckPosition = null;
    private int lastCheckMapId = -1;

    public Point lastCheckPosition() {
        return lastCheckPosition == null ? null : new Point(lastCheckPosition);
    }

    public int lastCheckMapId() {
        return lastCheckMapId;
    }

    public Point previousCheckPositionOnMap(int mapId) {
        if (lastCheckMapId != mapId) {
            return null;
        }
        return lastCheckPosition();
    }

    public void rememberCheck(Point position, int mapId) {
        lastCheckPosition = position == null ? null : new Point(position);
        lastCheckMapId = position == null ? -1 : mapId;
    }
}
