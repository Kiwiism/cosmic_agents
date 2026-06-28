package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed high-level movement mode state.
 */
public final class AgentBotModeStateRuntime {
    private AgentBotModeStateRuntime() {
    }

    public static boolean following(BotEntry entry) {
        return entry.isFollowing();
    }

    public static boolean grinding(BotEntry entry) {
        return entry.isGrinding();
    }

    public static int followTargetId(BotEntry entry) {
        return entry.followTargetId();
    }

    public static void setFollowing(BotEntry entry, boolean following) {
        entry.setFollowing(following);
    }

    public static void setGrinding(BotEntry entry, boolean grinding) {
        entry.setGrinding(grinding);
    }

    public static void setFollowTargetId(BotEntry entry, int followTargetId) {
        entry.setFollowTargetId(followTargetId);
    }

    public static void startFollowing(BotEntry entry, int followTargetId) {
        setFollowTargetId(entry, followTargetId);
        setGrinding(entry, false);
        setFollowing(entry, true);
    }

    public static void startGrinding(BotEntry entry) {
        setFollowTargetId(entry, 0);
        setFollowing(entry, false);
        setGrinding(entry, true);
    }

    public static void stopFollowing(BotEntry entry) {
        setFollowTargetId(entry, 0);
        setFollowing(entry, false);
    }

    public static void stopMovementModes(BotEntry entry) {
        setFollowTargetId(entry, 0);
        setFollowing(entry, false);
        setGrinding(entry, false);
    }
}
