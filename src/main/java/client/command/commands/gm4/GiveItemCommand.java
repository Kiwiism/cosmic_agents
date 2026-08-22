package client.command.commands.gm4;

import client.Character;
import client.Client;
import client.command.Command;
import client.command.CommandTargetPolicy;
import client.command.commands.gm2.ItemCommand;

import java.util.Arrays;

public class GiveItemCommand extends Command {
    {
        setDescription("Give an item to another character (Agents require GM6).");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character actor = client.getPlayer();
        if (params.length < 2) {
            actor.yellowMessage("Syntax: !giveitem <character> <itemid|name> [quantity]");
            return;
        }
        Character target = client.getWorldServer().getPlayerStorage().getCharacterByName(params[0]);
        if (target == null || target.getClient() == null) {
            actor.message("Character '" + params[0] + "' could not be found.");
            return;
        }
        if (!CommandTargetPolicy.canAffect(actor, target, true)) return;

        if (ItemCommand.grantItem(target.getClient(), actor, Arrays.copyOfRange(params, 1, params.length))) {
            actor.message("Item given to " + target.getName() + ".");
        }
    }
}
