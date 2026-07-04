package server.agents.integration;

import server.agents.integration.AgentBotMovementStateRuntime;

import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetState;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed fidget state.
 */
public final class AgentBotFidgetStateRuntime {
    private AgentBotFidgetStateRuntime() {
    }

    public static AgentFidgetMode mode(BotEntry entry) {
        return state(entry).mode();
    }

    public static boolean modeIs(BotEntry entry, AgentFidgetMode mode) {
        return state(entry).mode() == mode;
    }

    public static boolean modeIsAny(BotEntry entry, AgentFidgetMode first, AgentFidgetMode second) {
        AgentFidgetMode mode = state(entry).mode();
        return mode == first || mode == second;
    }

    public static boolean active(BotEntry entry) {
        return state(entry).active();
    }

    public static boolean inactive(BotEntry entry) {
        return !active(entry);
    }

    public static AgentFidgetTrigger trigger(BotEntry entry) {
        return state(entry).trigger();
    }

    public static long untilMs(BotEntry entry) {
        return state(entry).untilMs();
    }

    public static boolean expired(BotEntry entry, long nowMs) {
        return nowMs >= state(entry).untilMs();
    }

    public static long nextActionAtMs(BotEntry entry) {
        return state(entry).nextActionAtMs();
    }

    public static boolean actionDue(BotEntry entry, long nowMs) {
        return nowMs >= state(entry).nextActionAtMs();
    }

    public static int airSteerDir(BotEntry entry) {
        return state(entry).airSteerDir();
    }

    public static void setAirSteerDir(BotEntry entry, int direction) {
        state(entry).setAirSteerDir(direction);
    }

    public static int jumpDir(BotEntry entry) {
        return state(entry).jumpDir();
    }

    public static void setJumpDir(BotEntry entry, int direction) {
        state(entry).setJumpDir(direction);
    }

    public static int moveDir(BotEntry entry) {
        return state(entry).moveDir();
    }

    public static void setMoveDir(BotEntry entry, int direction) {
        state(entry).setMoveDir(direction);
    }

    public static boolean spamAirSteer(BotEntry entry) {
        return state(entry).spamAirSteer();
    }

    public static int actionBaseDelayMs(BotEntry entry) {
        return state(entry).actionBaseDelayMs();
    }

    public static long nextJumpAtMs(BotEntry entry) {
        return state(entry).nextJumpAtMs();
    }

    public static boolean jumpDue(BotEntry entry, long nowMs) {
        return nowMs >= state(entry).nextJumpAtMs();
    }

    public static Point originPos(BotEntry entry) {
        return state(entry).originPos();
    }

    public static long nextVisualAtMs(BotEntry entry) {
        return state(entry).nextVisualAtMs();
    }

    public static boolean visualDue(BotEntry entry, long nowMs) {
        return nowMs >= state(entry).nextVisualAtMs();
    }

    public static long nextFidgetAtMs(BotEntry entry) {
        return state(entry).nextFidgetAtMs();
    }

    public static boolean nextFidgetDue(BotEntry entry, long nowMs) {
        return nowMs >= state(entry).nextFidgetAtMs();
    }

    public static long nextIdleRollAtMs(BotEntry entry) {
        return state(entry).nextIdleRollAtMs();
    }

    public static boolean idleRollNotScheduled(BotEntry entry) {
        return state(entry).nextIdleRollAtMs() == 0L;
    }

    public static boolean idleRollDue(BotEntry entry, long nowMs) {
        return nowMs >= state(entry).nextIdleRollAtMs();
    }

    public static boolean crouching(BotEntry entry) {
        return AgentBotMovementStateRuntime.crouching(entry);
    }

    public static void clear(BotEntry entry) {
        state(entry).clearActiveState();
    }

    public static void start(BotEntry entry,
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

    public static void setNextIdleRollAtMs(BotEntry entry, long nextRollAtMs) {
        state(entry).setNextIdleRollAtMs(nextRollAtMs);
    }

    public static void setNextFidgetAtMs(BotEntry entry, long nextFidgetAtMs) {
        state(entry).setNextFidgetAtMs(nextFidgetAtMs);
    }

    public static void setNextActionAtMs(BotEntry entry, long nextActionAtMs) {
        state(entry).setNextActionAtMs(nextActionAtMs);
    }

    public static void setNextJumpAtMs(BotEntry entry, long nextJumpAtMs) {
        state(entry).setNextJumpAtMs(nextJumpAtMs);
    }

    public static void setNextVisualAtMs(BotEntry entry, long nextVisualAtMs) {
        state(entry).setNextVisualAtMs(nextVisualAtMs);
    }

    private static AgentFidgetState state(BotEntry entry) {
        return entry.fidgetState();
    }
}
