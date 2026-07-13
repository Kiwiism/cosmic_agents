package server.agents.integration;

import client.Character;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Maker actions mutate only the owning Agent inventory through Cosmic validation.")
public interface MakerGateway {
    int makeLeftoverCrystal(Character agent, int leftoverItemId);

    int disassembleEquip(Character agent, short slot);

    boolean canDisassemble(int itemId);

    int getMakerSkillLevel(Character agent);
}
