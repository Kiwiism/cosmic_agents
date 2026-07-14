package client.command.commands.gm6;

import client.Client;
import client.command.Command;
import server.maps.reservation.FreeMarketCharacterSpaceCatalog;
import server.maps.reservation.FreeMarketTestStallRuntime;

public final class FreeMarketSpotsCommand extends Command {
    public FreeMarketSpotsCommand() {
        setDescription("Visualize reserved Free Market stalls: !fmspots fill <0-100>|clear|status");
    }

    @Override
    public void execute(Client client, String[] params) {
        if (!FreeMarketCharacterSpaceCatalog.isRoom(client.getPlayer().getMapId())) {
            client.getPlayer().yellowMessage("Use !fmspots inside Free Market rooms 1-22.");
            return;
        }
        if (params.length == 0 || "status".equals(params[0])) {
            int occupied = FreeMarketTestStallRuntime.count(client.getPlayer());
            int capacity = FreeMarketCharacterSpaceCatalog.spaces(client.getPlayer().getMapId()).size();
            client.getPlayer().yellowMessage("FM test stalls: " + occupied + "/" + capacity + ".");
            return;
        }
        if ("clear".equals(params[0])) {
            int removed = FreeMarketTestStallRuntime.clear(client.getPlayer());
            client.getPlayer().yellowMessage("Removed " + removed + " runtime-only FM test stalls.");
            return;
        }
        if ("fill".equals(params[0]) && params.length >= 2) {
            try {
                int percentage = Integer.parseInt(params[1]);
                int created = FreeMarketTestStallRuntime.fill(client.getPlayer(), percentage);
                if (created < 0) {
                    throw new IllegalArgumentException();
                }
                client.getPlayer().yellowMessage("Created " + created
                        + " runtime-only FM test stalls at " + percentage + "% fill.");
                return;
            } catch (IllegalArgumentException ignored) {
                // Fall through to usage.
            }
        }
        client.getPlayer().yellowMessage("Usage: !fmspots fill <0-100> | clear | status");
    }
}
