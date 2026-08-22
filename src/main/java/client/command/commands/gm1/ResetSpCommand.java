package client.command.commands.gm1;

import client.Character;
import client.Client;
import client.command.Command;

public class ResetSpCommand extends Command {
    {
        setDescription("Reset job skills and return legally earned SP.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        if (params.length != 0) {
            player.yellowMessage("Syntax: @resetsp");
            return;
        }
        int availableSp = player.resetSkillPointsForCurrentLevel();
        player.message("SP reset complete. Available SP: " + availableSp + ".");
    }
}
