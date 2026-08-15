package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.capabilities.townlife.AgentTownLifeDiagnostics;
import server.agents.commands.townlife.AgentTownLifeTestService;

/** Thin GM6 adapter for TownLife diagnostics and explicitly requested bounded tests. */
public final class TownLifeCommand extends Command {
    {
        setDescription("Show TownLife diagnostics or run a bounded local Agent test.");
    }

    @Override
    public void execute(Client client, String[] params) {
        try {
            java.util.List<String> lines = params != null && params.length > 0
                    && "test".equalsIgnoreCase(params[0])
                    ? AgentTownLifeTestService.execute(
                            client.getPlayer(), params, System.currentTimeMillis())
                    : AgentTownLifeDiagnostics.lines(params);
            for (String line : lines) {
                client.getPlayer().dropMessage(6, line);
            }
        } catch (IllegalArgumentException failure) {
            client.getPlayer().dropMessage(6,
                    "TownLife diagnostics unavailable: " + failure.getMessage());
        }
    }
}
