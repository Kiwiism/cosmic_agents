package server.agents.field;

/** Immutable capability vector used only for formation scoring. */
public record AgentFieldCombatProfile(
        AgentFieldRole role,
        int rangePreference,
        int densityPreference,
        int mobilityPreference,
        int supportPreference) {
    public AgentFieldCombatProfile {
        role = role == null ? AgentFieldRole.ROAMER : role;
        if (rangePreference < 0 || densityPreference < 0
                || mobilityPreference < 0 || supportPreference < 0) {
            throw new IllegalArgumentException("field combat-profile preferences must be non-negative");
        }
    }

    public static AgentFieldCombatProfile roamer() {
        return new AgentFieldCombatProfile(AgentFieldRole.ROAMER, 1, 1, 3, 0);
    }
}
