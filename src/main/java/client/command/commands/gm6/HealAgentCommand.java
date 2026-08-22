package client.command.commands.gm6;

import client.Character;
import client.Client;
import client.command.Command;
import client.command.CommandTargetPolicy;

public class HealAgentCommand extends Command {
    {
        setDescription("Heal an online Agent's HP and MP.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character actor = client.getPlayer();
        if (params.length != 1) {
            actor.yellowMessage("Syntax: !healagent <agent>");
            return;
        }
        Character target = client.getWorldServer().getPlayerStorage().getCharacterByName(params[0]);
        if (target == null) {
            actor.message("Agent '" + params[0] + "' could not be found online.");
            return;
        }
        if (!CommandTargetPolicy.isAgent(target)) {
            actor.message("'" + target.getName() + "' is a player, not an Agent.");
            return;
        }
        target.healHpMp();
        actor.message("Healed Agent " + target.getName() + ".");
    }
}
