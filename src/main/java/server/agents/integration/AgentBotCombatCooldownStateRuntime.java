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
        return entry.combatCooldownState().attackCooldownMs();
    }

    public static boolean hasAttackCooldown(BotEntry entry) {
        return entry.combatCooldownState().hasAttackCooldown();
    }

    public static void clearAttackCooldown(BotEntry entry) {
        entry.combatCooldownState().clearAttackCooldown();
    }

    public static void tickAttackCooldown(BotEntry entry, IntUnaryOperator tickDown) {
        entry.combatCooldownState().tickAttackCooldown(tickDown);
    }

    public static void maxAttackCooldown(BotEntry entry, int cooldownMs) {
        entry.combatCooldownState().maxAttackCooldown(cooldownMs);
    }

    public static int moveWindowMs(BotEntry entry) {
        return entry.combatCooldownState().moveWindowMs();
    }

    public static boolean hasMoveWindow(BotEntry entry) {
        return entry.combatCooldownState().hasMoveWindow();
    }

    public static void clearMoveWindow(BotEntry entry) {
        entry.combatCooldownState().clearMoveWindow();
    }

    public static void tickMoveWindow(BotEntry entry, IntUnaryOperator tickDown) {
        entry.combatCooldownState().tickMoveWindow(tickDown);
    }

    public static void setMoveWindowMs(BotEntry entry, int windowMs) {
        entry.combatCooldownState().setMoveWindowMs(windowMs);
    }

    public static void maxMoveWindow(BotEntry entry, int windowMs) {
        entry.combatCooldownState().maxMoveWindow(windowMs);
    }

    public static boolean blocksGroundedAttack(BotEntry entry, boolean inAir) {
        return entry.combatCooldownState().blocksGroundedAttack(inAir);
    }

    public static long alertedUntilMs(BotEntry entry) {
        return entry.combatCooldownState().alertedUntilMs();
    }

    public static void setAlertedUntilMs(BotEntry entry, long untilMs) {
        entry.combatCooldownState().setAlertedUntilMs(untilMs);
    }

    public static boolean alertResetScheduled(BotEntry entry) {
        return entry.combatCooldownState().alertResetScheduled();
    }

    public static void setAlertResetScheduled(BotEntry entry, boolean scheduled) {
        entry.combatCooldownState().setAlertResetScheduled(scheduled);
    }

    public static int mobHitCooldownMs(BotEntry entry) {
        return entry.combatCooldownState().mobHitCooldownMs();
    }

    public static boolean hasMobHitCooldown(BotEntry entry) {
        return entry.combatCooldownState().hasMobHitCooldown();
    }

    public static void tickMobHitCooldown(BotEntry entry, IntUnaryOperator tickDown) {
        entry.combatCooldownState().tickMobHitCooldown(tickDown);
    }

    public static void setMobHitCooldownMs(BotEntry entry, int cooldownMs) {
        entry.combatCooldownState().setMobHitCooldownMs(cooldownMs);
    }
}
