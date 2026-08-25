package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.hpq.AgentHpqTestService;

/** GM-only autonomous HPQ observation and checkpoint harness. */
public final class HpqTestCommand extends Command {
    {
        setDescription("Run and inspect autonomous Henesys Party Quest Agents.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentHpqTestService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
