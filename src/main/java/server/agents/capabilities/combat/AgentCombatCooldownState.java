package server.agents.capabilities.combat;

import java.util.function.IntUnaryOperator;

/**
 * Mutable combat timing state for attack, movement, touch-damage, and alert reset gates.
 */
public final class AgentCombatCooldownState {
    private int attackCooldownMs = 0;
    private int moveWindowMs = 0;
    private int mobHitCooldownMs = 0;
    // Mirrors CharLook::alerted (TimedBool, 5000ms): absolute reset on each
    // attack/hit/heal/buff trigger, never additive.
    private long alertedUntilMs = 0L;
    // Debounces the scheduled stance-reset callback so observers receive a
    // fresh STAND broadcast once the visible alert pose expires.
    private boolean alertResetScheduled = false;

    public int attackCooldownMs() {
        return attackCooldownMs;
    }

    public void setAttackCooldownMs(int attackCooldownMs) {
        this.attackCooldownMs = attackCooldownMs;
    }

    public boolean hasAttackCooldown() {
        return attackCooldownMs > 0;
    }

    public void clearAttackCooldown() {
        attackCooldownMs = 0;
    }

    public void tickAttackCooldown(IntUnaryOperator tickDown) {
        setAttackCooldownMs(tickDown.applyAsInt(attackCooldownMs));
    }

    public void maxAttackCooldown(int cooldownMs) {
        setAttackCooldownMs(Math.max(attackCooldownMs, cooldownMs));
    }

    public int moveWindowMs() {
        return moveWindowMs;
    }

    public void setMoveWindowMs(int moveWindowMs) {
        this.moveWindowMs = moveWindowMs;
    }

    public boolean hasMoveWindow() {
        return moveWindowMs > 0;
    }

    public void clearMoveWindow() {
        moveWindowMs = 0;
    }

    public void tickMoveWindow(IntUnaryOperator tickDown) {
        setMoveWindowMs(tickDown.applyAsInt(moveWindowMs));
    }

    public void maxMoveWindow(int windowMs) {
        setMoveWindowMs(Math.max(moveWindowMs, windowMs));
    }

    public boolean blocksGroundedAttack(boolean inAir) {
        return hasAttackCooldown() || (hasMoveWindow() && !inAir);
    }

    public int mobHitCooldownMs() {
        return mobHitCooldownMs;
    }

    public void setMobHitCooldownMs(int mobHitCooldownMs) {
        this.mobHitCooldownMs = mobHitCooldownMs;
    }

    public boolean hasMobHitCooldown() {
        return mobHitCooldownMs > 0;
    }

    public void tickMobHitCooldown(IntUnaryOperator tickDown) {
        setMobHitCooldownMs(tickDown.applyAsInt(mobHitCooldownMs));
    }

    public long alertedUntilMs() {
        return alertedUntilMs;
    }

    public void setAlertedUntilMs(long alertedUntilMs) {
        this.alertedUntilMs = alertedUntilMs;
    }

    public boolean alertResetScheduled() {
        return alertResetScheduled;
    }

    public void setAlertResetScheduled(boolean alertResetScheduled) {
        this.alertResetScheduled = alertResetScheduled;
    }
}
