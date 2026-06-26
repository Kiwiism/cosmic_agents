package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.commands.AgentLegacyCommandBridge;

public class BotNavCommand extends Command {
    {
        setDescription("Show bot navigation overlays: !botnav graph | path [botName] | pathlog [botName] [note...] | clear");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.yellowMessage("Syntax: !botnav graph | path [botName] | pathlog [botName] [note...] | clear");
            return;
        }

        String message;
        switch (params[0]) {
            case "graph":
                message = AgentLegacyCommandBridge.showNavigationGraph(player);
                break;
            case "path":
                message = AgentLegacyCommandBridge.showNavigationPath(player, params.length >= 2 ? params[1] : null);
                break;
            case "pathlog": {
                String botName = params.length >= 2 ? params[1] : null;
                String note = null;
                if (params.length >= 3) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 2; i < params.length; i++) {
                        if (i > 2) sb.append(' ');
                        sb.append(params[i]);
                    }
                    note = sb.toString();
                }
                message = AgentLegacyCommandBridge.writeNavigationPathLog(player, botName, note);
                break;
            }
            case "clear":
                message = AgentLegacyCommandBridge.clearNavigationOverlay(player);
                break;
            default:
                player.yellowMessage("Syntax: !botnav graph | path [botName] | pathlog [botName] [note...] | clear");
                return;
        }

        player.yellowMessage(message);
    }
}
