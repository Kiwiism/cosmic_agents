package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.ppq.AgentPpqTestService;

/** GM-only autonomous Pirate PQ observation harness. */
public final class PpqTestCommand extends Command {
    { setDescription("Run and inspect autonomous Pirate Party Quest Agents."); }
    @Override public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentPpqTestService.execute(operator, params, System.currentTimeMillis())
                .forEach(line -> operator.dropMessage(6, line));
    }
}
