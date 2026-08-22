package client.command.commands.gm1;

import client.Character;
import client.Client;
import client.command.Command;

public class ResetApCommand extends Command {
    {
        setDescription("Reset stats and return legally earned AP.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        if (params.length != 0) {
            player.yellowMessage("Syntax: @resetap");
            return;
        }
        int availableAp = player.resetAbilityPointsForCurrentLevel();
        player.message("AP reset complete. Available AP: " + availableAp + ".");
    }
}
