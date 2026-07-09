package server.agents.integration;

import client.Character;
import client.Client;

public interface MakerGateway {
    int makeLeftoverCrystal(Client client, int leftoverItemId);

    int disassembleEquip(Client client, short slot);

    boolean canDisassemble(int itemId);

    int getMakerSkillLevel(Character agent);
}
