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
            case SOCIAL -> variation < 3 ? AgentFidgetMode.DIAGONAL_JUMP
                    : variation < 9 ? AgentFidgetMode.SPAM_PRONE : AgentFidgetMode.WAIT;
            case NPC_PAUSE -> variation < 3 ? AgentFidgetMode.PRONE : AgentFidgetMode.WAIT;
            case ROAM -> state.role() == AgentTownLifeState.Role.WANDERER && variation < 3
                    ? AgentFidgetMode.JUMP : variation == 3
                    ? AgentFidgetMode.SPAM_SIDEWAYS : AgentFidgetMode.WAIT;
            case SHOP_VISIT, WEAPON_FLOURISH -> AgentFidgetMode.WAIT;
            default -> AgentFidgetMode.NONE;
        };
    }
}
