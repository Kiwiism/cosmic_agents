package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.commands.AgentLegacyCommandBridge;

public class BotPerfDebugCommand extends Command {
    {
        setDescription("Toggle bot performance monitor: !botperfdebug [on|off]");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        boolean nowEnabled;
        if (params.length == 0) {
            nowEnabled = AgentLegacyCommandBridge.togglePerformanceMonitor();
        } else {
            String arg = params[0].toLowerCase();
            switch (arg) {
                case "on":
                    AgentLegacyCommandBridge.setPerformanceMonitorEnabled(true);
                    nowEnabled = true;
                    break;
                case "off":
                    AgentLegacyCommandBridge.setPerformanceMonitorEnabled(false);
                    nowEnabled = false;
                    break;
                default:
                    player.yellowMessage("Syntax: !botperfdebug [on|off]");
                    return;
            }
        }
        player.yellowMessage("bot performance monitor: " + (nowEnabled ? "ON" : "OFF"));
    }
}
