package server.agents.capabilities.townlife;

import client.Character;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;

final class AgentTownLifeFidgetPolicy {
    private AgentTownLifeFidgetPolicy() {
    }

    static AgentFidgetMode choose(Character agent, AgentTownLifeState state) {
        int variation = AgentTownLifeRolePolicy.variation(
                agent.getId(), state.sequence(), 12, 107);
        return switch (state.activity()) {
            case SOCIALIZE -> variation < 3 ? AgentFidgetMode.DIAGONAL_JUMP
                    : variation < 9 ? AgentFidgetMode.SPAM_PRONE : AgentFidgetMode.WAIT;
            case LINGER -> variation < 3 ? AgentFidgetMode.PRONE : AgentFidgetMode.WAIT;
            case STROLL -> state.role() == AgentTownLifeState.Role.WANDERER && variation < 3
                    ? AgentFidgetMode.JUMP : variation == 3
                    ? AgentFidgetMode.SPAM_SIDEWAYS : AgentFidgetMode.WAIT;
            case BROWSE, SHOW_OFF, LOCAL_ACTIVITY -> AgentFidgetMode.WAIT;
            default -> AgentFidgetMode.NONE;
        };
    }
}
