package client.command.commands.gm0;

import client.Client;
import client.command.Command;
import server.doubleagent.DoubleAgentService;

public class DoubleAgentRestoreCommand extends Command {
    {
        setDescription("Restore your active Double Agent session.");
    }

    @Override
    public void execute(Client c, String[] params) {
        if (DoubleAgentService.restoreActive(c.getPlayer(), "COMMAND")) {
            c.getPlayer().yellowMessage("Double Agent restored.");
        } else {
            c.getPlayer().yellowMessage("No active Double Agent session.");
        }
    }
}
