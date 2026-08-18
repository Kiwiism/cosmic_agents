package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.kpq.AgentKpqTestService;

/** GM-only autonomous KPQ observation and checkpoint harness. */
public final class KpqTestCommand extends Command {
    {
        setDescription("Run and inspect autonomous Kerning Party Quest Agents.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentKpqTestService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
