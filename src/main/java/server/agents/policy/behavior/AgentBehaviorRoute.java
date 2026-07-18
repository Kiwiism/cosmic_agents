package server.agents.policy.behavior;

public record AgentBehaviorRoute(
        AgentBehaviorCapability capability,
        AgentBehaviorMode mode,
        String primaryVersion,
        String shadowVersion) {

    public AgentBehaviorRoute {
        if (capability == null || mode == null || primaryVersion == null || primaryVersion.isBlank()) {
            throw new IllegalArgumentException("Behavior capability, mode, and primary version are required");
        }
        primaryVersion = primaryVersion.trim();
        shadowVersion = shadowVersion == null ? "" : shadowVersion.trim();
        if (mode == AgentBehaviorMode.SHADOW_COMPARE && shadowVersion.isEmpty()) {
            throw new IllegalArgumentException("Shadow comparison requires a shadow behavior version");
        }
    }

    public static AgentBehaviorRoute reconstructed(AgentBehaviorCapability capability, String version) {
        return new AgentBehaviorRoute(capability, AgentBehaviorMode.RECONSTRUCTED, version, "");
    }

    public static AgentBehaviorRoute legacy(AgentBehaviorCapability capability) {
        return new AgentBehaviorRoute(capability, AgentBehaviorMode.LEGACY, "legacy-v1", "");
    }
}
