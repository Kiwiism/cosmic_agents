package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.epq.AgentEpqTestService;

/** GM-only autonomous and mixed Ellin Forest Party Quest harness. */
public final class EpqTestCommand extends Command {
    { setDescription("Run and inspect Ellin Forest Party Quest Agents."); }

    @Override
    public void execute(Client client, String[] params) {
        AgentEpqTestService.execute(client.getPlayer(), params, System.currentTimeMillis())
                .forEach(line -> client.getPlayer().dropMessage(6, line));
    }
}
