package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.monitoring.AgentSchedulerDiagnostics;

/** GM6 read-only centralized Agent scheduler diagnostics. */
public final class AgentSchedulerCommand extends Command {
    {
        setDescription("Show compact Agent scheduler diagnostics.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        try {
            for (String line : AgentSchedulerDiagnostics.lines(params)) {
                player.dropMessage(6, line);
            }
        } catch (IllegalArgumentException failure) {
            player.dropMessage(6, "Agent scheduler diagnostics unavailable: " + failure.getMessage());
        }
    }
}
