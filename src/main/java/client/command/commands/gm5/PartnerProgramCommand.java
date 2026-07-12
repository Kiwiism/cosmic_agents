package client.command.commands.gm5;

import client.Client;
import client.command.Command;
import server.partner.PartnerRecoveryService;

public final class PartnerProgramCommand extends Command {
    {
        setDescription("Diagnose or recover the active Adventurer Partner session.");
    }

    @Override
    public void execute(Client client, String[] params) {
        if (params.length == 0 || "diag".equalsIgnoreCase(params[0])) {
            try {
                String result = params.length > 1
                        ? PartnerRecoveryService.getInstance().diagnoseCharacter(parseCharacterId(params[1]))
                        : PartnerRecoveryService.getInstance().diagnose(client.getPlayer());
                client.getPlayer().yellowMessage(result);
            } catch (IllegalArgumentException failure) {
                client.getPlayer().yellowMessage("Partner diagnostics failed: " + failure.getMessage());
            }
            return;
        }
        if ("recover".equalsIgnoreCase(params[0])) {
            try {
                String result = params.length > 1
                        ? PartnerRecoveryService.getInstance().recoverCharacter(
                                parseCharacterId(params[1]), "GM recovery command")
                        : PartnerRecoveryService.getInstance().recover(
                                client.getPlayer(), "GM recovery command");
                client.getPlayer().yellowMessage(result);
            } catch (RuntimeException failure) {
                client.getPlayer().yellowMessage("Partner recovery failed: " + failure.getMessage());
            }
            return;
        }
        client.getPlayer().yellowMessage("Syntax: !partnerprogram <diag|recover> [characterId]");
    }

    private static int parseCharacterId(String value) {
        try {
            int characterId = Integer.parseInt(value);
            if (characterId <= 0) {
                throw new NumberFormatException("not positive");
            }
            return characterId;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Character ID must be a positive number.");
        }
    }
}
