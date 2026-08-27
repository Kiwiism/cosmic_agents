package server.agents.progression;

/** Escalation policy for a Mushroom Kingdom objective whose durable state is not advancing. */
public final class AgentMushroomKingdomRecoveryPolicy {
    static final int RESET_STAGE = 1;
    static final int LOCAL_STAGE = 2;
    static final int CHECKPOINT_STAGE = 3;
    static final int RECONCILE_STAGE = 4;
    static final int BLOCK_STAGE = 5;

    public enum Action {
        NONE, RESET_TRANSIENT, STAGE_LOCAL, RESET_CHECKPOINT, RECONCILE, BLOCK
    }

    private AgentMushroomKingdomRecoveryPolicy() { }

    public static Action next(long elapsedMs, boolean hunting, int appliedStage,
                              int checkpointRecoveries) {
        long reset = hunting ? 3 * 60_000L : 30_000L;
        long local = hunting ? 10 * 60_000L : 90_000L;
        long checkpoint = hunting ? 20 * 60_000L : 3 * 60_000L;
        long reconcile = hunting ? 30 * 60_000L : 5 * 60_000L;
        long block = hunting ? 45 * 60_000L : 10 * 60_000L;
        if (elapsedMs >= block || checkpointRecoveries >= 3) return Action.BLOCK;
        if (elapsedMs >= reconcile && appliedStage < RECONCILE_STAGE) return Action.RECONCILE;
        if (elapsedMs >= checkpoint && appliedStage < CHECKPOINT_STAGE) return Action.RESET_CHECKPOINT;
        if (elapsedMs >= local && appliedStage < LOCAL_STAGE) return Action.STAGE_LOCAL;
        if (elapsedMs >= reset && appliedStage < RESET_STAGE) return Action.RESET_TRANSIENT;
        return Action.NONE;
    }

    static int stage(Action action) {
        return switch (action) {
            case RESET_TRANSIENT -> RESET_STAGE;
            case STAGE_LOCAL -> LOCAL_STAGE;
            case RESET_CHECKPOINT -> CHECKPOINT_STAGE;
            case RECONCILE -> RECONCILE_STAGE;
            case BLOCK -> BLOCK_STAGE;
            case NONE -> 0;
        };
    }
}
