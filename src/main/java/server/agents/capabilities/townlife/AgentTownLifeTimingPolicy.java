package server.agents.capabilities.townlife;

import client.Character;

final class AgentTownLifeTimingPolicy {
    private AgentTownLifeTimingPolicy() {
    }

    static long delay(Character agent,
                      AgentTownLifeState state,
                      int minimumInclusive,
                      int maximumExclusive) {
        return minimumInclusive + varied(
                agent, state, maximumExclusive - minimumInclusive, 157);
    }

    static int varied(Character agent, AgentTownLifeState state, int bound, int salt) {
        if (bound <= 1) {
            return 0;
        }
        long value = agent.getId() * 0x9E3779B97F4A7C15L
                + (long) state.sequence() * 0xBF58476D1CE4E5B9L
                + salt * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return Math.floorMod((int) value, bound);
    }
}
