package server.agents.capabilities.townlife;

/** Fixed lock stripes isolate encounter mutations between unrelated towns. */
final class AgentTownLifeScopeLocks {
    private static final int STRIPE_COUNT = config.AgentTuning.intValue(
            "server.agents.capabilities.townlife.AgentTownLifeScopeLocks.STRIPE_COUNT");
    private static final Object[] LOCKS = new Object[STRIPE_COUNT];

    static {
        for (int index = 0; index < LOCKS.length; index++) {
            LOCKS[index] = new Object();
        }
    }

    private AgentTownLifeScopeLocks() {
    }

    static Object forTown(int townMapId) {
        return LOCKS[Math.floorMod(townMapId, STRIPE_COUNT)];
    }
}
