package server.agents.integration;

import server.bots.BotEntry;
import server.agents.runtime.AgentModeState;

/**
 * Agent-owned adapter for temporary BotEntry-backed high-level movement mode state.
 */
public final class AgentBotModeStateRuntime {
    private AgentBotModeStateRuntime() {
    }

    public static boolean following(BotEntry entry) {
        return state(entry).following();
    }

    public static boolean grinding(BotEntry entry) {
        return state(entry).grinding();
    }

    public static int followTargetId(BotEntry entry) {
        return state(entry).followTargetId();
    }

    public static void setFollowing(BotEntry entry, boolean following) {
        state(entry).setFollowing(following);
    }

    public static void setGrinding(BotEntry entry, boolean grinding) {
        state(entry).setGrinding(grinding);
    }

    public static void setFollowTargetId(BotEntry entry, int followTargetId) {
        state(entry).setFollowTargetId(followTargetId);
    }

    public static void startFollowing(BotEntry entry, int followTargetId) {
        state(entry).startFollowing(followTargetId);
    }

    public static void startGrinding(BotEntry entry) {
        state(entry).startGrinding();
    }

    public static void stopFollowing(BotEntry entry) {
        state(entry).stopFollowing();
    }

    public static void stopMovementModes(BotEntry entry) {
        state(entry).stopMovementModes();
    }

    private static AgentModeState state(BotEntry entry) {
        return entry.modeState();
    }
}
