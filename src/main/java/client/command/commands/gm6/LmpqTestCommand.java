package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.lmpq.AgentLmpqTestService;

/** GM-only autonomous Ludibrium Maze Party Quest observation harness. */
public final class LmpqTestCommand extends Command {
    { setDescription("Run and inspect autonomous Ludibrium Maze PQ Agents."); }
    @Override public void execute(Client client, String[] params) {
        AgentLmpqTestService.execute(client.getPlayer(), params, System.currentTimeMillis())
                .forEach(line -> client.getPlayer().dropMessage(6, line));
    }
}
