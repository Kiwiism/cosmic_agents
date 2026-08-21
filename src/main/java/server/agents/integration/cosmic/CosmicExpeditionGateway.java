package server.agents.integration.cosmic;

import client.Character;
import server.agents.integration.ExpeditionGateway;
import server.expeditions.Expedition;
import server.expeditions.ExpeditionType;

/** Cosmic channel adapter for expedition state. */
public enum CosmicExpeditionGateway implements ExpeditionGateway {
    INSTANCE;

    @Override
    public Expedition current(Character character, ExpeditionType type) {
        if (character == null || type == null || character.getClient() == null) return null;
        return character.getClient().getChannelServer().getExpedition(type);
    }

    @Override
    public void remove(Character character, Expedition expedition) {
        if (character != null && expedition != null && character.getClient() != null) {
            expedition.removeChannelExpedition(character.getClient().getChannelServer());
        }
    }
}
