package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.registry.AgentResolvedCharacter;
import server.bots.BotOwnershipService;

public class TakeBotOwnerCommand extends Command {
    {
        setDescription("Force-assign bot ownership. Usage: @takebotowner <botName> [newOwner]");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.yellowMessage("Syntax: @takebotowner <botName> [newOwner]");
            return;
        }

        BotOwnershipService ownershipService = BotOwnershipService.getInstance();
        String[] rawArgs = player.getLastCommandMessage().trim().split("[ ]", 2);
        String botName = rawArgs[0];

        AgentResolvedCharacter bot = ownershipService.resolveCharacterByName(botName);
        if (bot == null) {
            player.yellowMessage("Bot '" + botName + "' could not be found.");
            return;
        }

        AgentResolvedCharacter newOwner;
        if (rawArgs.length >= 2 && !rawArgs[1].isBlank()) {
            newOwner = ownershipService.resolveCharacterByName(rawArgs[1].trim());
            if (newOwner == null) {
                player.yellowMessage("Character '" + rawArgs[1].trim() + "' could not be found.");
                return;
            }
        } else {
            newOwner = ownershipService.resolveCharacterByName(player.getName());
        }

        if (bot.id() == newOwner.id()) {
            player.yellowMessage("A character cannot own itself.");
            return;
        }

        ownershipService.registerOwner(bot.id(), newOwner.id());
        player.yellowMessage("Bot '" + bot.name() + "' ownership assigned to '" + newOwner.name() + "'.");
    }
}
