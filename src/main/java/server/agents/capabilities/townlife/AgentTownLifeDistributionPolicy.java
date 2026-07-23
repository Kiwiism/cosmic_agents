package server.agents.capabilities.townlife;

/** Stable fallback distribution until durable personality profiles own these preferences. */
final class AgentTownLifeDistributionPolicy {
    private AgentTownLifeDistributionPolicy() {
    }

    static AgentTownLifeState.District homeDistrict(int identitySeed,
                                                    AgentTownLifeProfile.Distribution distribution) {
        int total = distribution.upperWeight() + distribution.middleWeight()
                + distribution.lowerWeight() + distribution.anyWeight();
        int roll = Math.floorMod(mix(identitySeed), total);
        if (roll < distribution.upperWeight()) {
            return AgentTownLifeState.District.UPPER;
        }
        roll -= distribution.upperWeight();
        if (roll < distribution.middleWeight()) {
            return AgentTownLifeState.District.MIDDLE;
        }
        roll -= distribution.middleWeight();
        if (roll < distribution.lowerWeight()) {
            return AgentTownLifeState.District.LOWER;
        }
        return AgentTownLifeState.District.ANY;
    }

    static AgentTownLifeState.PlatformKind platformPreference(
            int identitySeed,
            AgentTownLifeProfile.Distribution distribution) {
        return Math.floorMod(mix(identitySeed ^ 0x51ED270B), 100)
                < distribution.miniPlatformPercent()
                ? AgentTownLifeState.PlatformKind.MINI
                : AgentTownLifeState.PlatformKind.ANY;
    }

    static boolean allowsCrossDistrictVisit(int sequence,
                                            AgentTownLifeProfile.Distribution distribution) {
        return sequence > 0
                && Math.floorMod(sequence, distribution.crossDistrictEveryActivities()) == 0;
    }

    private static int mix(int value) {
        int mixed = value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        return mixed ^ mixed >>> 16;
    }
}
