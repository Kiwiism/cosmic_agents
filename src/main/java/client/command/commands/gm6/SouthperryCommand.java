package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.plans.mapleisland.MapleIslandPlanCommandService;

public final class SouthperryCommand extends Command {
    public SouthperryCommand() {
        setDescription("Run the guarded post-Amherst-to-Southperry Maple Island plan.");
    }

    @Override
    public void execute(Client client, String[] params) {
        MapleIslandPlanCommandService.executeSouthperry(client.getPlayer(), params);
    }
}
