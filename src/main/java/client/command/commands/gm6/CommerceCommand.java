package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.agents.observation.commerce.CommerceObservationCommandService;

/** Operator surface for detached Commerce observation scenarios. */
public final class CommerceCommand extends Command {
    public CommerceCommand() {
        setDescription("Control and inspect detached Commerce observation scenarios.");
    }

    @Override
    public void execute(Client client, String[] params) {
        try {
            CommerceObservationCommandService.execute(client.getPlayer(), params)
                    .forEach(line -> client.getPlayer().dropMessage(6, line));
        } catch (RuntimeException failure) {
            client.getPlayer().dropMessage(6, "Commerce observation failed: "
                    + (failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }
}
