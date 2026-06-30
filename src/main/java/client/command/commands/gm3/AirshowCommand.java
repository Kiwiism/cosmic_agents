package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.social.airshow.AgentAirshowService;

public class AirshowCommand extends Command {
    {
        setDescription("Make an active owned bot fly across the map: !airshow <botname>");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.yellowMessage("Syntax: !airshow <botname>");
            return;
        }

        String result = AgentAirshowService.start(player, params[0]);
        player.yellowMessage(result);
    }
}
