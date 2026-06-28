package server.agents.integration;

import server.bots.BotEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for temporary BotEntry-backed combat cooldown state.
 */
public final class AgentBotCombatCooldownStateRuntime {
    private AgentBotCombatCooldownStateRuntime() {
    }

    public static int attackCooldownMs(BotEntry entry) {
        return entry.attackCooldownMs();
    }

    public static boolean hasAttackCooldown(BotEntry entry) {
        return attackCooldownMs(entry) > 0;
    }

    public static void clearAttackCooldown(BotEntry entry) {
        entry.setAttackCooldownMs(0);
    }

    public static void tickAttackCooldown(BotEntry entry, IntUnaryOperator tickDown) {
        entry.setAttackCooldownMs(tickDown.applyAsInt(entry.attackCooldownMs()));
    }

    public static void maxAttackCooldown(BotEntry entry, int cooldownMs) {
        entry.setAttackCooldownMs(Math.max(entry.attackCooldownMs(), cooldownMs));
    }

    public static int moveWindowMs(BotEntry entry) {
        return entry.moveWindowMs();
    }

    public static boolean hasMoveWindow(BotEntry entry) {
        return moveWindowMs(entry) > 0;
    }

    public static void clearMoveWindow(BotEntry entry) {
        entry.setMoveWindowMs(0);
    }

    public static void tickMoveWindow(BotEntry entry, IntUnaryOperator tickDown) {
        entry.setMoveWindowMs(tickDown.applyAsInt(entry.moveWindowMs()));
    }

    public static void setMoveWindowMs(BotEntry entry, int windowMs) {
        entry.setMoveWindowMs(windowMs);
    }

    public static void maxMoveWindow(BotEntry entry, int windowMs) {
        entry.setMoveWindowMs(Math.max(entry.moveWindowMs(), windowMs));
    }

    public static boolean blocksGroundedAttack(BotEntry entry, boolean inAir) {
        return hasAttackCooldown(entry) || (hasMoveWindow(entry) && !inAir);
    }

    public static long alertedUntilMs(BotEntry entry) {
        return entry.alertedUntilMs();
    }

    public static void setAlertedUntilMs(BotEntry entry, long untilMs) {
        entry.setAlertedUntilMs(untilMs);
    }

    public static boolean alertResetScheduled(BotEntry entry) {
        return entry.alertResetScheduled();
    }

    public static void setAlertResetScheduled(BotEntry entry, boolean scheduled) {
        entry.setAlertResetScheduled(scheduled);
    }

    public static int mobHitCooldownMs(BotEntry entry) {
        return entry.mobHitCooldownMs();
    }

    public static boolean hasMobHitCooldown(BotEntry entry) {
        return mobHitCooldownMs(entry) > 0;
    }

    public static void tickMobHitCooldown(BotEntry entry, IntUnaryOperator tickDown) {
        entry.setMobHitCooldownMs(tickDown.applyAsInt(entry.mobHitCooldownMs()));
    }

    public static void setMobHitCooldownMs(BotEntry entry, int cooldownMs) {
        entry.setMobHitCooldownMs(cooldownMs);
    }
}
