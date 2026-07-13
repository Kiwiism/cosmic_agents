package server.agents.integration;

import client.Character;
import net.server.channel.handlers.AbstractDealDamageHandler;
import server.agents.capabilities.combat.AgentAttackRoute;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Synthetic attacks use normal packet handlers with one writer per Agent session.")
public interface CombatGateway {
    int currentTimestamp();

    boolean dispatchSyntheticPacket(Character agent, byte[] packetBytes);

    boolean dispatchSupportSpecialMove(Character agent, int skillId, int skillLevel, int packetTimestamp);

    void applyAttackEffects(AgentAttackRoute route, AbstractDealDamageHandler.AttackInfo attack, Character agent);
}

