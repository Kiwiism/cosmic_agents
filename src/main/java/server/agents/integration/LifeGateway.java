package server.agents.integration;

import server.life.Monster;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.READ_ONLY_SNAPSHOT,
        rationale = "Life factory access supplies immutable/template data and performs no live-map mutation.")
public interface LifeGateway {
    Monster getMonster(int monsterId);
}
