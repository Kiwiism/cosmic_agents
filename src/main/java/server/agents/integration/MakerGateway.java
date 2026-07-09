package server.agents.integration;

import client.Character;

public interface MakerGateway {
    int makeLeftoverCrystal(Character agent, int leftoverItemId);

    int disassembleEquip(Character agent, short slot);

    boolean canDisassemble(int itemId);

    int getMakerSkillLevel(Character agent);
}
