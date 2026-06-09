package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import server.bots.BotManager;

public class BotCfgCommand extends Command {
    {
        setDescription("Get/set bot combat config live: !botcfg [FIELD [value]]");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length == 0) {
            player.yellowMessage("bot combat config (live):");
            for (String line : BotManager.botCombatConfigLines()) {
                player.yellowMessage("  " + line);
            }
            player.yellowMessage("set: !botcfg <FIELD> <value>");
            return;
        }
        if (params.length == 1) {
            String line = BotManager.botCombatConfigLine(params[0]);
            player.yellowMessage(line != null ? line : "unknown field: " + params[0] + " (use !botcfg to list)");
            return;
        }
        player.yellowMessage(BotManager.setBotCombatConfig(params[0], params[1]));
    }
}
