package client.command.commands.gm5;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.diagnostics.ClimbMovementCaptureRuntime;

/** GM command for bounded, opt-in native/Agent movement packet capture. */
public final class ClimbCaptureCommand extends Command {
    public ClimbCaptureCommand() {
        setDescription("Capture and decode a character's rope movement packets.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        String action = params.length == 0 ? "status" : params[0];
        switch (action) {
            case "start" -> start(client, player, params);
            case "stop" -> stop(player);
            case "clear" -> show(player, ClimbMovementCaptureRuntime.clear(player));
            case "status" -> status(player);
            default -> usage(player);
        }
    }

    private static void start(Client client, Character player, String[] params) {
        Character target = player;
        int packetLimit = ClimbMovementCaptureRuntime.DEFAULT_PACKET_LIMIT;

        if (params.length >= 2) {
            Integer possibleLimit = parseInteger(params[1]);
            if (possibleLimit != null) {
                packetLimit = possibleLimit;
            } else {
                target = resolveTarget(client, player, params[1]);
                if (target == null) {
                    player.yellowMessage("Character '" + params[1] + "' is not online or present on this map.");
                    return;
                }
            }
        }
        if (params.length >= 3) {
            Integer possibleLimit = parseInteger(params[2]);
            if (possibleLimit == null) {
                usage(player);
                return;
            }
            packetLimit = possibleLimit;
        }
        if (params.length > 3) {
            usage(player);
            return;
        }

        ClimbMovementCaptureRuntime.OperationResult result =
                ClimbMovementCaptureRuntime.start(player, target, packetLimit);
        show(player, result);
        if (result.success()) {
            player.dropMessage(6,
                    "Now perform: ground -> grab rope -> climb up/down -> detach -> land, then use !climbcapture stop.");
        }
    }

    private static Character resolveTarget(Client client, Character player, String name) {
        Character target = player.getMap() == null ? null : player.getMap().getCharacterByName(name);
        if (target != null) {
            return target;
        }
        return client.getWorldServer().getPlayerStorage().getCharacterByName(name);
    }

    private static void stop(Character player) {
        ClimbMovementCaptureRuntime.StopResult result = ClimbMovementCaptureRuntime.stop(player);
        player.yellowMessage(result.message());
        if (result.reportPath() != null) {
            player.dropMessage(6, "Movement capture report: " + result.reportPath());
        }
    }

    private static void status(Character player) {
        ClimbMovementCaptureRuntime.Status status = ClimbMovementCaptureRuntime.status(player);
        if (!status.active()) {
            player.yellowMessage("No active movement capture. Use !climbcapture start [character] [limit].");
            return;
        }
        player.yellowMessage("Capturing " + status.targetName() + ": " + status.packetCount()
                + "/" + status.packetLimit() + " packet(s), " + status.elapsedMillis() + "ms elapsed"
                + (status.limitReached() ? " (limit reached; stop to write report)." : "."));
    }

    private static void show(Character player, ClimbMovementCaptureRuntime.OperationResult result) {
        player.yellowMessage(result.message());
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void usage(Character player) {
        player.yellowMessage("Usage: !climbcapture start [character] [1-500] | stop | status | clear");
    }
}
