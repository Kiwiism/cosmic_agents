package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed movement physics flags.
 */
public final class AgentBotMovementPhysicsStateRuntime {
    private AgentBotMovementPhysicsStateRuntime() {
    }

    public static int jumpCooldownMs(BotEntry entry) {
        return entry.jumpCooldownMs();
    }

    public static void setJumpCooldownMs(BotEntry entry, int cooldownMs) {
        entry.setJumpCooldownMs(cooldownMs);
    }

    public static void clearJumpCooldown(BotEntry entry) {
        entry.setJumpCooldownMs(0);
    }

    public static boolean fixedAirArc(BotEntry entry) {
        return entry.fixedAirArc();
    }

    public static void setFixedAirArc(BotEntry entry, boolean fixed) {
        entry.setFixedAirArc(fixed);
    }

    public static int lastGroundFhId(BotEntry entry) {
        return entry.lastGroundFhId();
    }

    public static void setLastGroundFhId(BotEntry entry, int footholdId) {
        entry.setLastGroundFhId(footholdId);
    }

    public static Object groundTravelState(BotEntry entry) {
        return entry.groundTravelState();
    }
}
