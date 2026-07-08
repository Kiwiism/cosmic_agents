package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed combat cooldown state.
 */
public final class AgentBotCombatCooldownStateRuntime {
    private AgentBotCombatCooldownStateRuntime() {
    }

    public static int attackCooldownMs(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().attackCooldownMs();
    }

    public static boolean hasAttackCooldown(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().hasAttackCooldown();
    }

    public static void clearAttackCooldown(AgentRuntimeEntry entry) {
        entry.combatCooldownState().clearAttackCooldown();
    }

    public static void tickAttackCooldown(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.combatCooldownState().tickAttackCooldown(tickDown);
    }

    public static void maxAttackCooldown(AgentRuntimeEntry entry, int cooldownMs) {
        entry.combatCooldownState().maxAttackCooldown(cooldownMs);
    }

    public static int moveWindowMs(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().moveWindowMs();
    }

    public static boolean hasMoveWindow(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().hasMoveWindow();
    }

    public static void clearMoveWindow(AgentRuntimeEntry entry) {
        entry.combatCooldownState().clearMoveWindow();
    }

    public static void tickMoveWindow(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.combatCooldownState().tickMoveWindow(tickDown);
    }

    public static void setMoveWindowMs(AgentRuntimeEntry entry, int windowMs) {
        entry.combatCooldownState().setMoveWindowMs(windowMs);
    }

    public static void maxMoveWindow(AgentRuntimeEntry entry, int windowMs) {
        entry.combatCooldownState().maxMoveWindow(windowMs);
    }

    public static boolean blocksGroundedAttack(AgentRuntimeEntry entry, boolean inAir) {
        return entry.combatCooldownState().blocksGroundedAttack(inAir);
    }

    public static long alertedUntilMs(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().alertedUntilMs();
    }

    public static void setAlertedUntilMs(AgentRuntimeEntry entry, long untilMs) {
        entry.combatCooldownState().setAlertedUntilMs(untilMs);
    }

    public static boolean alertResetScheduled(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().alertResetScheduled();
    }

    public static void setAlertResetScheduled(AgentRuntimeEntry entry, boolean scheduled) {
        entry.combatCooldownState().setAlertResetScheduled(scheduled);
    }

    public static int mobHitCooldownMs(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().mobHitCooldownMs();
    }

    public static boolean hasMobHitCooldown(AgentRuntimeEntry entry) {
        return entry.combatCooldownState().hasMobHitCooldown();
    }

    public static void tickMobHitCooldown(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.combatCooldownState().tickMobHitCooldown(tickDown);
    }

    public static void setMobHitCooldownMs(AgentRuntimeEntry entry, int cooldownMs) {
        entry.combatCooldownState().setMobHitCooldownMs(cooldownMs);
    }
}
