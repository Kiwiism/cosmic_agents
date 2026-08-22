package client.command.commands.gm4;

import client.Character;
import client.Client;
import client.command.Command;
import client.command.CommandTargetPolicy;
import client.command.commands.gm3.MesoCommand;

public class GiveMesoCommand extends Command {
    {
        setDescription("Give mesos to another character (Agents require GM6).");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character actor = client.getPlayer();
        if (params.length != 2) {
            actor.yellowMessage("Syntax: !givemeso <character> <amount|max|min>");
            return;
        }
        Character target = client.getWorldServer().getPlayerStorage().getCharacterByName(params[0]);
        if (target == null) {
            actor.message("Character '" + params[0] + "' could not be found.");
            return;
        }
        if (!CommandTargetPolicy.canAffect(actor, target, true)) return;

        Integer amount = MesoCommand.parseAmount(params[1]);
        if (amount == null) {
            actor.yellowMessage("Invalid meso amount.");
            return;
        }
        target.gainMeso(amount, true);
        actor.message("MESO given to " + target.getName() + ".");
    }
}
