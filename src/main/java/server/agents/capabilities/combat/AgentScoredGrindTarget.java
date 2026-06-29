package server.agents.capabilities.combat;

import server.life.Monster;

public record AgentScoredGrindTarget(Monster monster, long graphCost, long localScore, double distanceSq) {
}
