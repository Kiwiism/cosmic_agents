package server.agents.integration;

import server.agents.runtime.AgentModeState;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed high-level movement mode state.
 */
public final class AgentBotModeStateRuntime {
    private AgentBotModeStateRuntime() {
    }

    public static boolean following(AgentRuntimeEntry entry) {
        return state(entry).following();
    }

    public static boolean grinding(AgentRuntimeEntry entry) {
        return state(entry).grinding();
    }

    public static int followTargetId(AgentRuntimeEntry entry) {
        return state(entry).followTargetId();
    }

    public static void setFollowing(AgentRuntimeEntry entry, boolean following) {
        state(entry).setFollowing(following);
    }

    public static void setGrinding(AgentRuntimeEntry entry, boolean grinding) {
        state(entry).setGrinding(grinding);
    }

    public static void setFollowTargetId(AgentRuntimeEntry entry, int followTargetId) {
        state(entry).setFollowTargetId(followTargetId);
    }

    public static void startFollowing(AgentRuntimeEntry entry, int followTargetId) {
        state(entry).startFollowing(followTargetId);
    }

    public static void startGrinding(AgentRuntimeEntry entry) {
        state(entry).startGrinding();
    }

    public static void stopFollowing(AgentRuntimeEntry entry) {
        state(entry).stopFollowing();
    }

    public static void stopMovementModes(AgentRuntimeEntry entry) {
        state(entry).stopMovementModes();
    }

    private static AgentModeState state(AgentRuntimeEntry entry) {
        return entry.modeState();
    }
}
