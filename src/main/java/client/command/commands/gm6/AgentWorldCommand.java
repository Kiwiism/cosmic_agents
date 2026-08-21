package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.commands.AgentWorldCommandService;

/** Observation-only World Director preparation diagnostics. */
public final class AgentWorldCommand extends Command {
    {
        setDescription("Inspect shadow World Director proposals without changing Agent ownership.");
    }

    @Override
    public void execute(Client client, String[] params) {
        AgentWorldCommandService.execute(client.getPlayer(), params, System.currentTimeMillis())
                .forEach(line -> client.getPlayer().dropMessage(6, line));
    }
}
