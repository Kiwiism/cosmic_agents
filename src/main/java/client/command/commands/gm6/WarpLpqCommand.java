package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.partyquest.lpq.AgentLpqTestService;

/** Moves an LPQ test observer beside a stable party slot or active role. */
public final class WarpLpqCommand extends Command {
    { setDescription("Warp an LPQ spectator to a party slot or active role."); }

    @Override
    public void execute(Client client, String[] params) {
        Character operator = client.getPlayer();
        AgentLpqTestService.warpObserver(operator, params)
                .forEach(line -> operator.dropMessage(6, line));
    }
}
