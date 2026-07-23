package server.agents.capabilities.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

public final class AgentTownLifeControlState {
    public static final AgentCapabilityStateKey<AgentTownLifeControlState> STATE_KEY =
            new AgentCapabilityStateKey<>("town-life.control", AgentTownLifeControlState.class,
                    AgentTownLifeControlState::new);

    private AgentTownLifeSupportLevel supportLevel = AgentTownLifeSupportLevel.DETERMINISTIC;

    public synchronized AgentTownLifeSupportLevel supportLevel() {
        return supportLevel;
    }

    public synchronized void setSupportLevel(AgentTownLifeSupportLevel next) {
        supportLevel = next == null ? AgentTownLifeSupportLevel.DETERMINISTIC : next;
    }
}
