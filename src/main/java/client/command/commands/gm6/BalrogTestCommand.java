package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.expedition.balrog.AgentBalrogTestService;

/** GM-only twelve-Agent Easy Balrog expedition harness. */
public final class BalrogTestCommand extends Command {
    {
        setDescription("Run or quick-stage a twelve-Agent second-job Easy Balrog expedition.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentBalrogTestService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
