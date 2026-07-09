package server.agents.integration;

import server.life.Monster;

public interface LifeGateway {
    Monster getMonster(int monsterId);
}
