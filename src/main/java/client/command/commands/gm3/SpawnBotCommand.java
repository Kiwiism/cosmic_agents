package client.command.commands.gm3;

import client.Client;
import client.command.Command;
import server.agents.commands.AgentSpawnCommandExecutor;

public class SpawnBotCommand extends Command {
    private final AgentSpawnCommandExecutor executor = new AgentSpawnCommandExecutor();

    {
        setDescription("Spawn an authorized character as a bot companion.");
    }

    @Override
    public void execute(Client c, String[] params) {
        executor.execute(c, params);
    }
}
