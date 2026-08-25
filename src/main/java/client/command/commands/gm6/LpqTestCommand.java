package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.lpq.AgentLpqTestService;

/** GM-only autonomous LPQ observation and mixed-party harness. */
public final class LpqTestCommand extends Command {
    { setDescription("Run and inspect autonomous Ludibrium Party Quest Agents."); }
    @Override public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentLpqTestService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
