package server.agents.capabilities.build.profiles;

import java.util.Map;

/** Exact-job defaults for the source-attributed MapleRoyals 2026 Explorer builds. */
public final class AgentSpBuildDefaultCatalog {
    public static final String SOURCE_ID = "mapleroyals-optimal-2026";

    private static final Map<Integer, String> BY_JOB = Map.ofEntries(
            entry(110, "fighter"), entry(111, "crusader"), entry(112, "hero"),
            entry(120, "page"), entry(121, "white-knight"), entry(122, "paladin"),
            entry(130, "spearman"), entry(131, "dragon-knight-hybrid"), entry(132, "dark-knight-hybrid"),
            entry(210, "fp-wizard"), entry(211, "fp-mage"), entry(212, "fp-arch-mage"),
            entry(220, "il-wizard"), entry(221, "il-mage"), entry(222, "il-arch-mage"),
            entry(230, "cleric"), entry(231, "priest"), entry(232, "bishop"),
            entry(310, "hunter"), entry(311, "ranger"), entry(312, "bowmaster"),
            entry(320, "crossbowman"), entry(321, "sniper"), entry(322, "marksman"),
            entry(410, "assassin"), entry(411, "hermit"), entry(412, "night-lord"),
            entry(420, "bandit"), entry(421, "chief-bandit"), entry(422, "shadower"),
            entry(510, "brawler"), entry(511, "marauder"), entry(512, "buccaneer"),
            entry(520, "gunslinger"), entry(521, "outlaw"), entry(522, "corsair"));

    private AgentSpBuildDefaultCatalog() {
    }

    public static String profileIdFor(int exactJobId) {
        return BY_JOB.get(exactJobId);
    }

    public static String nextProfileId(String currentProfileId, int exactJobId) {
        if ((SOURCE_ID + "-dragon-knight-spear").equals(currentProfileId)
                && exactJobId == 132) {
            return SOURCE_ID + "-dark-knight-spear";
        }
        return profileIdFor(exactJobId);
    }

    private static Map.Entry<Integer, String> entry(int jobId, String branch) {
        return Map.entry(jobId, SOURCE_ID + "-" + branch);
    }
}
