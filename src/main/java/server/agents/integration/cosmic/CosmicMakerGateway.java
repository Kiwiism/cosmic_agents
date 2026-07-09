package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import client.processor.action.MakerProcessor;
import server.agents.integration.MakerGateway;

public enum CosmicMakerGateway implements MakerGateway {
    INSTANCE;

    @Override
    public int makeLeftoverCrystal(Client client, int leftoverItemId) {
        return MakerProcessor.makeLeftoverCrystal(client, leftoverItemId);
    }

    @Override
    public int disassembleEquip(Client client, short slot) {
        return MakerProcessor.disassembleEquip(client, slot);
    }

    @Override
    public boolean canDisassemble(int itemId) {
        return MakerProcessor.canDisassemble(itemId);
    }

    @Override
    public int getMakerSkillLevel(Character agent) {
        return MakerProcessor.getMakerSkillLevel(agent);
    }
}
