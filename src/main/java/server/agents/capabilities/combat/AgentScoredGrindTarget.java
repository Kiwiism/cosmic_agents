package server.agents.capabilities.combat;

import server.life.Monster;

record AgentScoredGrindTarget(Monster monster, long routeCost, long localScore, double distanceSq) {
}
