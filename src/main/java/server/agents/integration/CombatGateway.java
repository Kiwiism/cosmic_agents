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

    /** Casts a summon skill at the Agent's position (special move with a position payload). */
    boolean dispatchSummonSpecialMove(Character agent, int skillId, int skillLevel, int packetTimestamp);

    /** Monster Magnet: the special move carries the pulled monsters' object ids. */
    boolean dispatchMonsterMagnet(Character agent, int skillId, int skillLevel, int packetTimestamp, java.util.List<Integer> monsterOids);

    CombatAttackApplicationResult applyAttackEffects(
            AgentAttackRoute route,
            AbstractDealDamageHandler.AttackInfo attack,
            Character agent);
}

