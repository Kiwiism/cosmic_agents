package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;

public final class MapOwnerCommand extends Command {
    {
        setDescription("Show the current map owner.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character owner = client.getPlayer().getMap().getMapOwner();
        client.getPlayer().yellowMessage(owner == null
                ? "This map currently has no owner."
                : "Current map owner: " + owner.getName() + ".");
    }
}
