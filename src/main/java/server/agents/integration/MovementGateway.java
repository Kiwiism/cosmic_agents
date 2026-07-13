package server.agents.integration;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.READ_ONLY_SNAPSHOT,
        rationale = "The movement gateway is an empty extension seam with no mutation operation.")
public interface MovementGateway {
}

