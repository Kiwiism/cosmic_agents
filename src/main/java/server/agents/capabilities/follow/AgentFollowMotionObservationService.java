package server.agents.capabilities.follow;

import server.agents.runtime.AgentOwnerMotionStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentFollowMotionObservationService {
    private AgentFollowMotionObservationService() {
    }

    public static void updateObservedLeaderMotion(AgentRuntimeEntry entry, Point leaderPosition) {
        if (entry == null || leaderPosition == null) {
            return;
        }
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, leaderPosition);
    }
}
