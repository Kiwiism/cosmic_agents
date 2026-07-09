package server.agents.integration.cosmic;

import server.agents.integration.LifeGateway;
import server.life.LifeFactory;
import server.life.Monster;

public enum CosmicLifeGateway implements LifeGateway {
    INSTANCE;

    @Override
    public Monster getMonster(int monsterId) {
        return LifeFactory.getMonster(monsterId);
    }
}
