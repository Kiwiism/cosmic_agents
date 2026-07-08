package server.agents.integration;

import server.agents.integration.AgentMovementStateRuntime;

import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetState;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed fidget state.
 */
public final class AgentFidgetStateRuntime {
    private AgentFidgetStateRuntime() {
    }

    public static AgentFidgetMode mode(AgentRuntimeEntry entry) {
        return state(entry).mode();
    }

    public static boolean modeIs(AgentRuntimeEntry entry, AgentFidgetMode mode) {
        return state(entry).mode() == mode;
    }

    public static boolean modeIsAny(AgentRuntimeEntry entry, AgentFidgetMode first, AgentFidgetMode second) {
        AgentFidgetMode mode = state(entry).mode();
        return mode == first || mode == second;
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return state(entry).active();
    }

    public static boolean inactive(AgentRuntimeEntry entry) {
        return !active(entry);
    }

    public static AgentFidgetTrigger trigger(AgentRuntimeEntry entry) {
        return state(entry).trigger();
    }

    public static long untilMs(AgentRuntimeEntry entry) {
        return state(entry).untilMs();
    }

    public static boolean expired(AgentRuntimeEntry entry, long nowMs) {
        return nowMs >= state(entry).untilMs();
    }

    public static long nextActionAtMs(AgentRuntimeEntry entry) {
        return state(entry).nextActionAtMs();
    }

    public static boolean actionDue(AgentRuntimeEntry entry, long nowMs) {
        return nowMs >= state(entry).nextActionAtMs();
    }

    public static int airSteerDir(AgentRuntimeEntry entry) {
        return state(entry).airSteerDir();
    }

    public static void setAirSteerDir(AgentRuntimeEntry entry, int direction) {
        state(entry).setAirSteerDir(direction);
    }

    public static int jumpDir(AgentRuntimeEntry entry) {
        return state(entry).jumpDir();
    }

    public static void setJumpDir(AgentRuntimeEntry entry, int direction) {
        state(entry).setJumpDir(direction);
    }

    public static int moveDir(AgentRuntimeEntry entry) {
        return state(entry).moveDir();
    }

    public static void setMoveDir(AgentRuntimeEntry entry, int direction) {
        state(entry).setMoveDir(direction);
    }

    public static boolean spamAirSteer(AgentRuntimeEntry entry) {
        return state(entry).spamAirSteer();
    }

    public static int actionBaseDelayMs(AgentRuntimeEntry entry) {
        return state(entry).actionBaseDelayMs();
    }

    public static long nextJumpAtMs(AgentRuntimeEntry entry) {
        return state(entry).nextJumpAtMs();
    }

    public static boolean jumpDue(AgentRuntimeEntry entry, long nowMs) {
        return nowMs >= state(entry).nextJumpAtMs();
    }

    public static Point originPos(AgentRuntimeEntry entry) {
        return state(entry).originPos();
    }

    public static long nextVisualAtMs(AgentRuntimeEntry entry) {
        return state(entry).nextVisualAtMs();
    }

    public static boolean visualDue(AgentRuntimeEntry entry, long nowMs) {
        return nowMs >= state(entry).nextVisualAtMs();
    }

    public static long nextFidgetAtMs(AgentRuntimeEntry entry) {
        return state(entry).nextFidgetAtMs();
    }

    public static boolean nextFidgetDue(AgentRuntimeEntry entry, long nowMs) {
        return nowMs >= state(entry).nextFidgetAtMs();
    }

    public static long nextIdleRollAtMs(AgentRuntimeEntry entry) {
        return state(entry).nextIdleRollAtMs();
    }

    public static boolean idleRollNotScheduled(AgentRuntimeEntry entry) {
        return state(entry).nextIdleRollAtMs() == 0L;
    }

    public static boolean idleRollDue(AgentRuntimeEntry entry, long nowMs) {
        return nowMs >= state(entry).nextIdleRollAtMs();
    }

    public static boolean crouching(AgentRuntimeEntry entry) {
        return AgentMovementStateRuntime.crouching(entry);
    }

    public static void clear(AgentRuntimeEntry entry) {
        state(entry).clearActiveState();
    }

    public static void start(AgentRuntimeEntry entry,
                             AgentFidgetMode mode,
                             AgentFidgetTrigger trigger,
                             long untilMs,
                             long nowMs,
                             int airSteerDir,
                             boolean spamAirSteer,
                             int actionBaseDelayMs,
                             Point originPos,
                             long nextVisualAtMs,
                             long nextFidgetAtMs) {
        state(entry).start(
                mode,
                trigger,
                untilMs,
                nowMs,
                airSteerDir,
                spamAirSteer,
                actionBaseDelayMs,
                originPos,
                nextVisualAtMs,
                nextFidgetAtMs);
    }

    public static void setNextIdleRollAtMs(AgentRuntimeEntry entry, long nextRollAtMs) {
        state(entry).setNextIdleRollAtMs(nextRollAtMs);
    }

    public static void setNextFidgetAtMs(AgentRuntimeEntry entry, long nextFidgetAtMs) {
        state(entry).setNextFidgetAtMs(nextFidgetAtMs);
    }

    public static void setNextActionAtMs(AgentRuntimeEntry entry, long nextActionAtMs) {
        state(entry).setNextActionAtMs(nextActionAtMs);
    }

    public static void setNextJumpAtMs(AgentRuntimeEntry entry, long nextJumpAtMs) {
        state(entry).setNextJumpAtMs(nextJumpAtMs);
    }

    public static void setNextVisualAtMs(AgentRuntimeEntry entry, long nextVisualAtMs) {
        state(entry).setNextVisualAtMs(nextVisualAtMs);
    }

    private static AgentFidgetState state(AgentRuntimeEntry entry) {
        return entry.fidgetState();
    }
}
