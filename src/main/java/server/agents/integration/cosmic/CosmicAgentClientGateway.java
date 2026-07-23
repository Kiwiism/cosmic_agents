package server.agents.integration.cosmic;

import client.BotClient;
import client.Character;
import client.Client;
import client.creator.BotCreator;
import server.agents.integration.AgentClientGateway;

import java.sql.SQLException;

public enum CosmicAgentClientGateway implements AgentClientGateway {
    INSTANCE;

    @Override
    public Client createHeadlessClient(int world, int channel) {
        return new BotClient(world, channel);
    }

    @Override
    public int createBackingCharacter(Client client, String name) {
        return BotCreator.createCharacter(client, name);
    }

    @Override
    public Character loadBackingCharacter(int characterId, Client client) throws SQLException {
        return Character.loadCharFromDB(characterId, client, true);
    }

    @Override
    public boolean hasClient(Character character) {
        return character.getClient() != null;
    }

    @Override
    public boolean isRealPlayer(Character character) {
        return character != null && character.getClient() != null
                && !(character.getClient() instanceof BotClient);
    }

    @Override
    public boolean tryAcquire(Character character) {
        return character.getClient().tryacquireClient();
    }

    @Override
    public void release(Character character) {
        character.getClient().releaseClient();
    }

    @Override
    public int world(Character character) {
        return character == null || character.getClient() == null
                ? (character == null ? 0 : character.getWorld())
                : character.getClient().getWorld();
    }

    @Override
    public int channel(Character character) {
        return character == null || character.getClient() == null
                ? 0 : character.getClient().getChannel();
    }
}
