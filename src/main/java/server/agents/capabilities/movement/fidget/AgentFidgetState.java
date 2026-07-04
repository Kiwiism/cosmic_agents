package server.agents.capabilities.movement.fidget;

import java.awt.Point;

/**
 * Mutable fidget runtime state for one live Agent.
 */
public final class AgentFidgetState {
    private AgentFidgetMode mode = AgentFidgetMode.NONE;
    private AgentFidgetTrigger trigger = AgentFidgetTrigger.NONE;
    private long untilMs;
    private long nextActionAtMs;
    private long nextFidgetAtMs;
    private long nextIdleRollAtMs;
    private int airSteerDir;
    private int jumpDir;
    private int moveDir;
    private boolean spamAirSteer;
    private int actionBaseDelayMs;
    private long nextJumpAtMs;
    private Point originPos;
    private long nextVisualAtMs;

    public AgentFidgetMode mode() {
        return mode;
    }

    public boolean active() {
        return mode != AgentFidgetMode.NONE;
    }

    public AgentFidgetTrigger trigger() {
        return trigger;
    }

    public long untilMs() {
        return untilMs;
    }

    public long nextActionAtMs() {
        return nextActionAtMs;
    }

    public int airSteerDir() {
        return airSteerDir;
    }

    public int jumpDir() {
        return jumpDir;
    }

    public int moveDir() {
        return moveDir;
    }

    public boolean spamAirSteer() {
        return spamAirSteer;
    }

    public int actionBaseDelayMs() {
        return actionBaseDelayMs;
    }

    public long nextJumpAtMs() {
        return nextJumpAtMs;
    }

    public Point originPos() {
        return originPos == null ? null : new Point(originPos);
    }

    public long nextVisualAtMs() {
        return nextVisualAtMs;
    }

    public long nextFidgetAtMs() {
        return nextFidgetAtMs;
    }

    public long nextIdleRollAtMs() {
        return nextIdleRollAtMs;
    }

    public void clearActiveState() {
        mode = AgentFidgetMode.NONE;
        trigger = AgentFidgetTrigger.NONE;
        untilMs = 0L;
        nextActionAtMs = 0L;
        airSteerDir = 0;
        jumpDir = 0;
        moveDir = 0;
        spamAirSteer = false;
        actionBaseDelayMs = 0;
        nextJumpAtMs = 0L;
        originPos = null;
        nextVisualAtMs = 0L;
    }

    public void start(AgentFidgetMode mode,
                      AgentFidgetTrigger trigger,
                      long untilMs,
                      long nowMs,
                      int airSteerDir,
                      boolean spamAirSteer,
                      int actionBaseDelayMs,
                      Point originPos,
                      long nextVisualAtMs,
                      long nextFidgetAtMs) {
        this.mode = mode;
        this.trigger = trigger;
        this.untilMs = untilMs;
        this.nextActionAtMs = nowMs;
        this.airSteerDir = airSteerDir;
        this.jumpDir = airSteerDir == 0 ? 1 : airSteerDir;
        this.moveDir = airSteerDir;
        this.spamAirSteer = spamAirSteer;
        this.actionBaseDelayMs = actionBaseDelayMs;
        this.nextJumpAtMs = nowMs;
        this.originPos = originPos == null ? null : new Point(originPos);
        this.nextVisualAtMs = nextVisualAtMs;
        this.nextFidgetAtMs = nextFidgetAtMs;
    }

    public void setMode(AgentFidgetMode mode) {
        this.mode = mode == null ? AgentFidgetMode.NONE : mode;
    }

    public void setNextIdleRollAtMs(long nextIdleRollAtMs) {
        this.nextIdleRollAtMs = nextIdleRollAtMs;
    }

    public void setNextFidgetAtMs(long nextFidgetAtMs) {
        this.nextFidgetAtMs = nextFidgetAtMs;
    }

    public void setAirSteerDir(int airSteerDir) {
        this.airSteerDir = airSteerDir;
    }

    public void setNextActionAtMs(long nextActionAtMs) {
        this.nextActionAtMs = nextActionAtMs;
    }

    public void setJumpDir(int jumpDir) {
        this.jumpDir = jumpDir;
    }

    public void setNextJumpAtMs(long nextJumpAtMs) {
        this.nextJumpAtMs = nextJumpAtMs;
    }

    public void setMoveDir(int moveDir) {
        this.moveDir = moveDir;
    }

    public void setSpamAirSteer(boolean spamAirSteer) {
        this.spamAirSteer = spamAirSteer;
    }

    public void setActionBaseDelayMs(int actionBaseDelayMs) {
        this.actionBaseDelayMs = actionBaseDelayMs;
    }

    public void setUntilMs(long untilMs) {
        this.untilMs = untilMs;
    }

    public void setNextVisualAtMs(long nextVisualAtMs) {
        this.nextVisualAtMs = nextVisualAtMs;
    }
}
