package server.agents.integration;

import server.bots.BotEntry;
import server.bots.BotFidgetMode;
import server.bots.BotFidgetTrigger;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed fidget state.
 */
public final class AgentBotFidgetStateRuntime {
    private AgentBotFidgetStateRuntime() {
    }

    public static BotFidgetMode mode(BotEntry entry) {
        return entry.fidgetMode();
    }

    public static boolean modeIs(BotEntry entry, BotFidgetMode mode) {
        return entry.fidgetMode() == mode;
    }

    public static boolean modeIsAny(BotEntry entry, BotFidgetMode first, BotFidgetMode second) {
        BotFidgetMode mode = entry.fidgetMode();
        return mode == first || mode == second;
    }

    public static boolean active(BotEntry entry) {
        return entry.hasActiveFidgetMode();
    }

    public static boolean inactive(BotEntry entry) {
        return !active(entry);
    }

    public static BotFidgetTrigger trigger(BotEntry entry) {
        return entry.fidgetTrigger();
    }

    public static long untilMs(BotEntry entry) {
        return entry.fidgetUntilMs();
    }

    public static boolean expired(BotEntry entry, long nowMs) {
        return nowMs >= entry.fidgetUntilMs();
    }

    public static long nextActionAtMs(BotEntry entry) {
        return entry.nextFidgetActionAtMs();
    }

    public static boolean actionDue(BotEntry entry, long nowMs) {
        return nowMs >= entry.nextFidgetActionAtMs();
    }

    public static int airSteerDir(BotEntry entry) {
        return entry.fidgetAirSteerDir();
    }

    public static void setAirSteerDir(BotEntry entry, int direction) {
        entry.setFidgetAirSteerDir(direction);
    }

    public static int jumpDir(BotEntry entry) {
        return entry.fidgetJumpDir();
    }

    public static void setJumpDir(BotEntry entry, int direction) {
        entry.setFidgetJumpDir(direction);
    }

    public static int moveDir(BotEntry entry) {
        return entry.fidgetMoveDir();
    }

    public static void setMoveDir(BotEntry entry, int direction) {
        entry.setFidgetMoveDir(direction);
    }

    public static boolean spamAirSteer(BotEntry entry) {
        return entry.fidgetSpamAirSteer();
    }

    public static int actionBaseDelayMs(BotEntry entry) {
        return entry.fidgetActionBaseDelayMs();
    }

    public static long nextJumpAtMs(BotEntry entry) {
        return entry.nextFidgetJumpAtMs();
    }

    public static boolean jumpDue(BotEntry entry, long nowMs) {
        return nowMs >= entry.nextFidgetJumpAtMs();
    }

    public static Point originPos(BotEntry entry) {
        return entry.fidgetOriginPos();
    }

    public static long nextVisualAtMs(BotEntry entry) {
        return entry.nextFidgetVisualAtMs();
    }

    public static boolean visualDue(BotEntry entry, long nowMs) {
        return nowMs >= entry.nextFidgetVisualAtMs();
    }

    public static long nextFidgetAtMs(BotEntry entry) {
        return entry.nextFidgetAtMs();
    }

    public static boolean nextFidgetDue(BotEntry entry, long nowMs) {
        return nowMs >= entry.nextFidgetAtMs();
    }

    public static long nextIdleRollAtMs(BotEntry entry) {
        return entry.nextIdleFidgetRollAtMs();
    }

    public static boolean idleRollNotScheduled(BotEntry entry) {
        return entry.nextIdleFidgetRollAtMs() == 0L;
    }

    public static boolean idleRollDue(BotEntry entry, long nowMs) {
        return nowMs >= entry.nextIdleFidgetRollAtMs();
    }

    public static boolean crouching(BotEntry entry) {
        return entry.crouching();
    }

    public static void clear(BotEntry entry) {
        entry.clearFidgetState();
    }

    public static void start(BotEntry entry,
                             BotFidgetMode mode,
                             BotFidgetTrigger trigger,
                             long untilMs,
                             long nowMs,
                             int airSteerDir,
                             boolean spamAirSteer,
                             int actionBaseDelayMs,
                             Point originPos,
                             long nextVisualAtMs,
                             long nextFidgetAtMs) {
        entry.startFidgetState(
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
        entry.setNextIdleFidgetRollAtMs(nextRollAtMs);
    }

    public static void setNextFidgetAtMs(BotEntry entry, long nextFidgetAtMs) {
        entry.setNextFidgetAtMs(nextFidgetAtMs);
    }

    public static void setNextActionAtMs(BotEntry entry, long nextActionAtMs) {
        entry.setNextFidgetActionAtMs(nextActionAtMs);
    }

    public static void setNextJumpAtMs(BotEntry entry, long nextJumpAtMs) {
        entry.setNextFidgetJumpAtMs(nextJumpAtMs);
    }

    public static void setNextVisualAtMs(BotEntry entry, long nextVisualAtMs) {
        entry.setNextFidgetVisualAtMs(nextVisualAtMs);
    }
}
