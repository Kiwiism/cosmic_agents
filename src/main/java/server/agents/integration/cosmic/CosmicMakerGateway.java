package server.agents.integration.cosmic;

import client.Character;
import client.processor.action.MakerProcessor;
import server.agents.integration.MakerGateway;

public enum CosmicMakerGateway implements MakerGateway {
    INSTANCE;

    @Override
    public int makeLeftoverCrystal(Character agent, int leftoverItemId) {
        return MakerProcessor.makeLeftoverCrystal(agent.getClient(), leftoverItemId);
    }

    @Override
    public int disassembleEquip(Character agent, short slot) {
        return MakerProcessor.disassembleEquip(agent.getClient(), slot);
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
