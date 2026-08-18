package server.agents.capabilities.partyquest.kpq;

/** Time-based Kerning lobby policy. The live event currently permits three or four members. */
public final class AgentKpqRecruitmentPolicy {
    public static final int MIN_PARTY_SIZE = 3;
    public static final int MAX_PARTY_SIZE = 4;
    private static final long THREE_MEMBER_MIN_WAIT_MS = 20_000L;
    private static final long THREE_MEMBER_MAX_WAIT_MS = 45_000L;

    private AgentKpqRecruitmentPolicy() {
    }

    public static boolean shouldLaunch(int partySize, long recruitingForMs, long seed) {
        if (partySize >= MAX_PARTY_SIZE) return true;
        if (partySize < MIN_PARTY_SIZE) return false;
        long threshold = THREE_MEMBER_MIN_WAIT_MS + Math.floorMod(seed, 10_001L);
        return recruitingForMs >= Math.min(threshold, THREE_MEMBER_MAX_WAIT_MS);
    }

    /** 0-100 score used by a future population decision to fill an open seat. */
    public static int joinWillingness(int partySize, long recruitingForMs) {
        if (partySize < 1 || partySize >= MAX_PARTY_SIZE) return 0;
        int urgency = (int) Math.min(55L, Math.max(0L, recruitingForMs) / 750L);
        return Math.min(100, 20 + partySize * 8 + urgency);
    }

    public static long preparationDelayMs(int partySize, long seed) {
        long jitter = Math.floorMod(seed, 1_501L);
        return partySize == MIN_PARTY_SIZE ? 8_000L + jitter : 1_500L + jitter;
    }
}
