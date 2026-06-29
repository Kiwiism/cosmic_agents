package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.registry.AgentResolvedCharacter;
import server.bots.BotOwnershipService;

public class RegisterBotCommand extends Command {
    {
        setDescription("Authorize another character to spawn this character as a bot.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.yellowMessage("Syntax: @registerbot <ownerName>");
            return;
        }

        String ownerName = player.getLastCommandMessage().trim();
        if (ownerName.isEmpty()) {
            player.yellowMessage("Syntax: @registerbot <ownerName>");
            return;
        }

        BotOwnershipService ownershipService = BotOwnershipService.getInstance();
        AgentResolvedCharacter owner = ownershipService.resolveCharacterByName(ownerName);
        if (owner == null) {
            player.yellowMessage("Character '" + ownerName + "' could not be found.");
            return;
        }
        if (owner.id() == player.getId()) {
            player.yellowMessage("You cannot register your current character to itself.");
            return;
        }

        ownershipService.registerOwner(player.getId(), owner.id());
        player.yellowMessage("Character '" + player.getName() + "' is now registered to '" + owner.name() + "' for bot spawning.");
    }
}
