package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.plans.mapleisland.MapleIslandPlanCommandService;

public final class MapleIslandCommand extends Command {
    public MapleIslandCommand() {
        setDescription("Run the guarded level-1 Mushroom Town-to-Southperry Maple Island plan.");
    }

    @Override
    public void execute(Client client, String[] params) {
        MapleIslandPlanCommandService.execute(client.getPlayer(), params);
    }
}
