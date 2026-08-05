package server.agents.capabilities.recovery;

/**
 * Boundary between normal navigation and optional route-loop recovery.
 *
 * <p>The pathfinder and authored route overlays remain authoritative. This policy may record
 * route progress or, in conservative mode, temporarily reject one proven-bad ordinary ground
 * edge. Movement-producing recovery remains available only in explicit legacy-aggressive mode.
 * Authored route overlays and structural rope, ladder, and portal traversal stay authoritative.</p>
 */
public final class AgentNavigationRecoveryPolicy {
    static final int OFF = 0;
    static final int OBSERVE_ONLY = 1;
    static final int CONSERVATIVE = 2;
    static final int LEGACY_AGGRESSIVE = 3;

    private static final int MODE = validate(config.AgentTuning.intValue(
            "server.agents.capabilities.recovery.AgentNavigationRecoveryPolicy.MODE"));

    private AgentNavigationRecoveryPolicy() {
    }

    public static boolean recordsRouteProgress() {
        return recordsRouteProgress(MODE);
    }

    public static boolean mayRejectRouteEdge(boolean authoredRouteOverlay) {
        return mayRejectRouteEdge(MODE, authoredRouteOverlay);
    }

    public static String modeName() {
        return switch (MODE) {
            case OFF -> "OFF";
            case OBSERVE_ONLY -> "OBSERVE_ONLY";
            case CONSERVATIVE -> "CONSERVATIVE";
            case LEGACY_AGGRESSIVE -> "LEGACY_AGGRESSIVE";
            default -> throw new IllegalStateException("unreachable navigation recovery mode " + MODE);
        };
    }

    static boolean recordsRouteProgress(int mode) {
        return mode >= OBSERVE_ONLY;
    }

    static boolean mayRejectRouteEdge(int mode, boolean authoredRouteOverlay) {
        return mode >= CONSERVATIVE && !authoredRouteOverlay;
    }

    public static boolean mayPerformMovementRecovery() {
        return mayPerformMovementRecovery(MODE);
    }

    public static boolean mayPerformSoftTeleport() {
        return mayPerformSoftTeleport(MODE);
    }

    static boolean mayPerformMovementRecovery(int mode) {
        return mode >= LEGACY_AGGRESSIVE;
    }

    static boolean mayPerformSoftTeleport(int mode) {
        return mode >= LEGACY_AGGRESSIVE;
    }

    private static int validate(int mode) {
        if (mode < OFF || mode > LEGACY_AGGRESSIVE) {
            throw new IllegalArgumentException(
                    "Agent navigation recovery mode must be 0 (off), 1 (observe-only), "
                            + "2 (conservative), or 3 (legacy-aggressive): "
                            + mode);
        }
        return mode;
    }
}
