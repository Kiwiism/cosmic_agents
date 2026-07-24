package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.observer.AgentObserverCommandService;

public final class ObserverCommand extends Command {
    public ObserverCommand() {
        setDescription("Control the independent Kiwi observer showcase.");
    }

    @Override
    public void execute(Client client, String[] params) {
        AgentObserverCommandService.execute(client.getPlayer(), params);
    }
}
