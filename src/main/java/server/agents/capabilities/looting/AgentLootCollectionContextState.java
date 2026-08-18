package server.agents.capabilities.looting;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** System-owned loot cadence context; eligibility and pickup authority remain unchanged. */
public final class AgentLootCollectionContextState {
    public static final AgentCapabilityStateKey<AgentLootCollectionContextState> STATE_KEY =
            new AgentCapabilityStateKey<>("looting.collection-context",
                    AgentLootCollectionContextState.class,
                    AgentLootCollectionContextState::new);

    public enum Mode { STANDARD, FIELD_GRIND }

    private Mode mode = Mode.STANDARD;
    private int batchKills;

    public synchronized void fieldGrind(int agentId) {
        mode = Mode.FIELD_GRIND;
        int minimum = AgentLootCollectionPolicyConfig.fieldBatchMinKills();
        int maximum = Math.max(minimum, AgentLootCollectionPolicyConfig.fieldBatchMaxKills());
        batchKills = minimum + Math.floorMod(agentId, maximum - minimum + 1);
    }

    public synchronized void standard() {
        mode = Mode.STANDARD;
        batchKills = 0;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(mode, batchKills);
    }

    public record Snapshot(Mode mode, int batchKills) {
        public boolean fieldGrinding() {
            return mode == Mode.FIELD_GRIND;
        }
    }
}
