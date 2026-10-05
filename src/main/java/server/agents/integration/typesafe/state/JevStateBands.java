package server.agents.integration.typesafe.state;

/**
 * Turns numbers the model cannot reason about into the words it can. Jev reads literally and is
 * "not a calculator", so the server compares, divides and orders first and sends the result.
 */
public final class JevStateBands {
    private JevStateBands() {
    }

    /** {@code "full"}, {@code "high"}, {@code "about half"}, {@code "low (below 30%)"}, ... */
    public static String percentBand(long current, long maximum) {
        if (maximum <= 0) {
            return "unknown";
        }
        long percent = Math.max(0L, Math.min(100L, current * 100L / maximum));
        if (percent >= 95) {
            return "full";
        }
        if (percent >= 70) {
            return "high";
        }
        if (percent >= 40) {
            return "about half";
        }
        if (percent >= 30) {
            return "getting low";
        }
        if (percent >= 15) {
            return "low (below 30%)";
        }
        if (percent > 0) {
            return "critical (below 15%)";
        }
        return "empty";
    }

    public static String mesoBand(long mesos) {
        if (mesos < 0) {
            return "unknown";
        }
        if (mesos < 1_000L) {
            return "almost none";
        }
        if (mesos < 50_000L) {
            return "a little";
        }
        if (mesos < 1_000_000L) {
            return "comfortable";
        }
        if (mesos < 50_000_000L) {
            return "wealthy";
        }
        return "very wealthy";
    }

    /** Describes {@code otherLevel} relative to {@code referenceLevel} (e.g. mob vs agent). */
    public static String levelGap(int referenceLevel, int otherLevel) {
        int gap = otherLevel - referenceLevel;
        if (gap <= -15) {
            return "far below";
        }
        if (gap <= -5) {
            return "below";
        }
        if (gap < 5) {
            return "about the same";
        }
        if (gap < 15) {
            return "above";
        }
        return "well above";
    }

    public static String relativeAge(long ageMs) {
        if (ageMs < 0) {
            return "unknown";
        }
        if (ageMs < 5_000L) {
            return "just now";
        }
        if (ageMs < 60_000L) {
            return "about " + Math.max(5L, (ageMs / 5_000L) * 5L) + " seconds ago";
        }
        if (ageMs < 600_000L) {
            return "about " + Math.max(1L, ageMs / 60_000L) + " minutes ago";
        }
        if (ageMs < 3_600_000L) {
            return "a while ago";
        }
        return "a long time ago";
    }

    public static String countBand(int count) {
        if (count <= 0) {
            return "none";
        }
        if (count == 1) {
            return "one";
        }
        if (count <= 3) {
            return "a few";
        }
        if (count <= 8) {
            return "several";
        }
        return "many";
    }
}
