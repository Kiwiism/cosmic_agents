package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.field.AgentFieldCommandService;

/** GM6 field-coordination exercise harness. */
public final class AgentFieldCommand extends Command {
    {
        setDescription("Run and inspect 1-6 Agent farming-coverage exercises.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentFieldCommandService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
