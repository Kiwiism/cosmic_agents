package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import net.server.Server;

public class ServerHealthCommand extends Command {

    {
        setDescription("Show compact server runtime diagnostics.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        for (String line : Server.getInstance().diagnosticLines()) {
            player.dropMessage(6, line);
        }
    }
}
