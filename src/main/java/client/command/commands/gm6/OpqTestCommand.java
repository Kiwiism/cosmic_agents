package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.opq.AgentOpqTestService;

/** GM-only autonomous Orbis Party Quest observation harness. */
public final class OpqTestCommand extends Command {
    { setDescription("Run and inspect autonomous Orbis Party Quest Agents."); }
    @Override public void execute(Client client, String[] params) {
        AgentOpqTestService.execute(client.getPlayer(), params, System.currentTimeMillis())
                .forEach(line -> client.getPlayer().dropMessage(6, line));
    }
}
