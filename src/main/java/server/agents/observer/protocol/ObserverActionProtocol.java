package server.agents.observer.protocol;

public final class ObserverActionProtocol {
    public static final int VERSION = 1;

    public static final int ACTION_WARP_MAP = 1;
    public static final int ACTION_WARP_CHARACTER = 2;
    public static final int ACTION_REJOIN_TARGET = 3;

    public static final int STATUS_OK = 0;
    public static final int STATUS_INVALID_REQUEST = 1;
    public static final int STATUS_TARGET_NOT_FOUND = 2;
    public static final int STATUS_WRONG_CHANNEL = 3;
    public static final int STATUS_BLOCKED = 4;

    private ObserverActionProtocol() {
    }

    public static boolean validAction(int action) {
        return action == ACTION_WARP_MAP
                || action == ACTION_WARP_CHARACTER
                || action == ACTION_REJOIN_TARGET;
    }
}
