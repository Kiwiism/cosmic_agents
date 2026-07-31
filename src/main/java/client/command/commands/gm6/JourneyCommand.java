package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.journey.AgentJourneyCommandService;

public final class JourneyCommand extends Command {
    public JourneyCommand() {
        setDescription("Run and inspect bounded Agent progression journeys.");
    }

    @Override
    public void execute(Client client, String[] params) {
        AgentJourneyCommandService.execute(client.getPlayer(), params);
    }
}
