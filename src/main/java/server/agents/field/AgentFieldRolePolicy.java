package server.agents.field;

import client.Character;

/** Runtime adapter from character capability family to generic field-formation preferences. */
public final class AgentFieldRolePolicy {
    private AgentFieldRolePolicy() {
    }

    public static AgentFieldCombatProfile resolve(Character agent) {
        if (agent == null || agent.getJob() == null) {
            return AgentFieldCombatProfile.roamer();
        }
        return switch (Math.floorMod(agent.getJob().getId() / 100, 10)) {
            case 1 -> new AgentFieldCombatProfile(AgentFieldRole.VANGUARD, 0, 4, 1, 0);
            case 2 -> new AgentFieldCombatProfile(AgentFieldRole.AOE_CLEARER, 3, 4, 1, 1);
            case 3 -> new AgentFieldCombatProfile(AgentFieldRole.RANGED_HOLDER, 5, 2, 2, 0);
            case 4 -> new AgentFieldCombatProfile(AgentFieldRole.ROAMER, 2, 3, 5, 0);
            case 5 -> new AgentFieldCombatProfile(AgentFieldRole.RANGED_HOLDER, 5, 2, 3, 0);
            default -> AgentFieldCombatProfile.roamer();
        };
    }
}
