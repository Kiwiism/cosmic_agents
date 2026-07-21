package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.capabilities.dialogue.AgentDialogueDiagnostics;

/** GM6 diagnostics and reversible topic controls for Agent dialogue. */
public final class AgentChatCommand extends Command {
    public AgentChatCommand() {
        setDescription("Inspect Agent dialogue and tune topic availability.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        try {
            for (String line : AgentDialogueDiagnostics.lines(params)) {
                player.dropMessage(6, line);
            }
        } catch (IllegalArgumentException failure) {
            player.dropMessage(6, "Agent dialogue command failed: " + failure.getMessage());
        }
    }
}
