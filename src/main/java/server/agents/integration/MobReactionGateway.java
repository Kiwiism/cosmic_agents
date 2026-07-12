package server.agents.integration;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.life.Monster;

public interface MobReactionGateway {
    void prepareObservedAttack(AbstractDealDamageHandler.AttackInfo attack, Character agent);

    String describe(Monster monster);
}
