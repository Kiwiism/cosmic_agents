package client;

import constants.game.GameConstants;

/** Keeps permanent HP/MP arithmetic lossless while exposing client-safe values. */
public final class HpMpPoolPolicy {
    private HpMpPoolPolicy() {
    }

    public static int normalizeRaw(int value, int minimum) {
        return Math.max(minimum, value);
    }

    public static int visibleValue(int rawValue, int minimum) {
        return Math.min(GameConstants.getPlayerHpMpCap(), normalizeRaw(rawValue, minimum));
    }

    public static int applyDelta(int rawValue, int delta, int minimum) {
        long result = (long) rawValue + delta;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return normalizeRaw((int) Math.max(Integer.MIN_VALUE, result), minimum);
    }
}
