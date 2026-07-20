package server.agents.capabilities.presentation;

final class AgentPresentationDeterministicRandom {
    private AgentPresentationDeterministicRandom() {
    }

    static long sample(long seed, long sequence, long domain) {
        return mix(seed ^ (sequence * 0x9E3779B97F4A7C15L) ^ domain);
    }

    static int bounded(long sample, int bound) {
        return bound <= 1 ? 0 : (int) Long.remainderUnsigned(sample, bound);
    }

    static long range(long sample, long minimum, long maximum) {
        if (maximum <= minimum) {
            return minimum;
        }
        return minimum + Long.remainderUnsigned(sample, maximum - minimum + 1L);
    }

    private static long mix(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
