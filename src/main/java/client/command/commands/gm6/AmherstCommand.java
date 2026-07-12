package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.plans.amherst.AmherstPlanCommandService;

public final class AmherstCommand extends Command {
    public AmherstCommand() {
        setDescription("Run the guarded Amherst MVP plan one objective at a time.");
    }

    @Override
    public void execute(Client client, String[] params) {
        AmherstPlanCommandService.execute(client.getPlayer(), params);
    }
}
