package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;
import server.bots.llm.BotLlmConfig;

public class BotLlmCommand extends Command {
    {
        setDescription("Toggle bot LLM chat replies: !botllm <on|true|off|false|debug>");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.yellowMessage("Syntax: !botllm <on|true|off|false|debug>. Current: "
                    + (BotLlmConfig.enabled ? "ON" : "OFF")
                    + ", debug: " + (BotLlmConfig.debugLog ? "ON" : "OFF"));
            return;
        }

        switch (params[0]) {
            case "on", "true" -> {
                BotLlmConfig.enabled = true;
                BotLlmConfig.debugLog = false;
            }
            case "off", "false" -> {
                BotLlmConfig.enabled = false;
                BotLlmConfig.debugLog = false;
            }
            case "debug" -> {
                BotLlmConfig.enabled = true;
                BotLlmConfig.debugLog = true;
            }
            default -> {
                player.yellowMessage("Syntax: !botllm <on|true|off|false|debug>");
                return;
            }
        }

        player.yellowMessage("bot llm chat: " + (BotLlmConfig.enabled ? "ON" : "OFF")
                + ", debug: " + (BotLlmConfig.debugLog ? "ON" : "OFF"));
    }
}
