package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.capabilities.townlife.AgentTownLifeDiagnostics;

/** Thin GM6 adapter for read-only TownLife diagnostics. */
public final class TownLifeCommand extends Command {
    {
        setDescription("Show TownLife profiles, state, encounters, and metrics.");
    }

    @Override
    public void execute(Client client, String[] params) {
        try {
            for (String line : AgentTownLifeDiagnostics.lines(params)) {
                client.getPlayer().dropMessage(6, line);
            }
        } catch (IllegalArgumentException failure) {
            client.getPlayer().dropMessage(6,
                    "TownLife diagnostics unavailable: " + failure.getMessage());
        }
    }
}
