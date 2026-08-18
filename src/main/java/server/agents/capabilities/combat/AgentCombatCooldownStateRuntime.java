package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.IntUnaryOperator;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed combat cooldown state.
 */
public final class AgentCombatCooldownStateRuntime {
    private AgentCombatCooldownStateRuntime() {
    }

    public static int attackCooldownMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).attackCooldownMs();
    }

    public static boolean hasAttackCooldown(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).hasAttackCooldown();
    }

    public static void clearAttackCooldown(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).clearAttackCooldown();
    }

    public static void tickAttackCooldown(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).tickAttackCooldown(tickDown);
    }

    public static void maxAttackCooldown(AgentRuntimeEntry entry, int cooldownMs) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).maxAttackCooldown(cooldownMs);
    }

    public static int moveWindowMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).moveWindowMs();
    }

    public static boolean hasMoveWindow(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).hasMoveWindow();
    }

    public static void clearMoveWindow(AgentRuntimeEntry entry) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).clearMoveWindow();
    }

    public static void tickMoveWindow(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).tickMoveWindow(tickDown);
    }

    public static void setMoveWindowMs(AgentRuntimeEntry entry, int windowMs) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).setMoveWindowMs(windowMs);
    }

    public static void maxMoveWindow(AgentRuntimeEntry entry, int windowMs) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).maxMoveWindow(windowMs);
    }

    public static boolean blocksGroundedAttack(AgentRuntimeEntry entry, boolean inAir) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).blocksGroundedAttack(inAir);
    }

    public static long alertedUntilMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).alertedUntilMs();
    }

    public static void setAlertedUntilMs(AgentRuntimeEntry entry, long untilMs) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).setAlertedUntilMs(untilMs);
    }

    public static boolean alertResetScheduled(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).alertResetScheduled();
    }

    public static void setAlertResetScheduled(AgentRuntimeEntry entry, boolean scheduled) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).setAlertResetScheduled(scheduled);
    }

    public static int mobHitCooldownMs(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).mobHitCooldownMs();
    }

    public static boolean hasMobHitCooldown(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).hasMobHitCooldown();
    }

    public static void tickMobHitCooldown(AgentRuntimeEntry entry, IntUnaryOperator tickDown) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).tickMobHitCooldown(tickDown);
    }

    public static void setMobHitCooldownMs(AgentRuntimeEntry entry, int cooldownMs) {
        entry.capabilityStates().require(AgentCombatCooldownState.STATE_KEY).setMobHitCooldownMs(cooldownMs);
    }
}
