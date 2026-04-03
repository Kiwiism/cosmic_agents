package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import server.bots.BotNavigationProbe;
import server.maps.MapleMap;

import java.util.List;

public class RegenNavCommand extends Command {
    {
        setDescription("Force rebuild the current map bot navigation graph and print phase timings.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        MapleMap map = player.getMap();
        if (map == null) {
            player.yellowMessage("No current map.");
            return;
        }

        List<String> lines = BotNavigationProbe.rebuildGraphReport(map);
        for (String line : lines) {
            player.yellowMessage(line);
        }
    }
}
