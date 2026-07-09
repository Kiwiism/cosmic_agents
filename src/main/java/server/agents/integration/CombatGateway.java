package server.agents.integration;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.agents.capabilities.combat.AgentAttackRoute;

public interface CombatGateway {
    int currentTimestamp();

    boolean dispatchSyntheticPacket(Character agent, byte[] packetBytes);

    void applyAttackEffects(AgentAttackRoute route, AbstractDealDamageHandler.AttackInfo attack, Character agent);
}

