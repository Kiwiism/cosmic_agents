package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.progression.VictoriaFirstJobMvpCommandService;

public final class VictoriaCommand extends Command {
    public VictoriaCommand() {
        setDescription("Reset and run the Lith Harbor to level-15 first-job Agent MVP.");
    }

    @Override
    public void execute(Client client, String[] params) {
        VictoriaFirstJobMvpCommandService.execute(client.getPlayer(), params);
    }
}
