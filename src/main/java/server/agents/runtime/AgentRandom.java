package server.agents.runtime;

import java.util.concurrent.ThreadLocalRandom;

public final class AgentRandom {
    private AgentRandom() {
    }

    public static long randMs(int lo, int hi) {
        return lo + ThreadLocalRandom.current().nextInt(hi - lo);
    }
}
