package client.command.commands.gm3;

import client.Character;
import client.Client;
import client.command.Command;

public class MesoCommand extends Command {
    {
        setDescription("Give mesos to yourself.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        if (params.length != 1) {
            player.yellowMessage("Syntax: !meso <amount|max|min>");
            return;
        }
        Integer amount = parseAmount(params[0]);
        if (amount == null) {
            player.yellowMessage("Invalid meso amount.");
            return;
        }
        player.gainMeso(amount, true);
    }

    public static Integer parseAmount(String value) {
        if ("max".equalsIgnoreCase(value)) return Integer.MAX_VALUE;
        if ("min".equalsIgnoreCase(value)) return Integer.MIN_VALUE;
        try {
            long amount = Long.parseLong(value);
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, amount));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
