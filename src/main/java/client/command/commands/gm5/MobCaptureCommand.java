package client.command.commands.gm5;

import client.Character;
import client.Client;
import client.command.Command;
import server.agents.diagnostics.MobReactionCaptureRuntime;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** GM command for a bounded packet timeline around one monster's hit reaction. */
public final class MobCaptureCommand extends Command {
    private static final int LIST_LIMIT = 15;

    public MobCaptureCommand() {
        setDescription("Capture packets and state around a monster hit reaction.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character player = client.getPlayer();
        String action = params.length == 0 ? "status" : params[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> list(player, params);
            case "start" -> start(player, params);
            case "mark" -> mark(player, params);
            case "stop" -> stop(player, params);
            case "clear" -> clear(player, params);
            case "status" -> status(player, params);
            default -> usage(player);
        }
    }

    private static void list(Character player, String[] params) {
        if (params.length != 1) {
            usage(player);
            return;
        }
        MapleMap map = player.getMap();
        if (map == null) {
            player.yellowMessage("You are not currently on a map.");
            return;
        }
        List<Monster> monsters = map.getAllMonsters();
        Point playerPosition = player.getPosition();
        monsters.sort(Comparator.comparingDouble(monster -> distanceSquared(playerPosition, monster.getPosition())));
        if (monsters.isEmpty()) {
            player.yellowMessage("No monsters are present on this map.");
            return;
        }

        player.yellowMessage("Nearest monsters (use the OID with !mobcapture start):");
        for (int index = 0; index < Math.min(LIST_LIMIT, monsters.size()); index++) {
            Monster monster = monsters.get(index);
            Character controller = monster.getController();
            player.dropMessage(6, "OID " + monster.getObjectId()
                    + " | " + monster.getName() + " (ID " + monster.getId() + ")"
                    + " | pos " + formatPoint(monster.getPosition())
                    + " | controller " + (controller == null ? "none" : controller.getName()));
        }
        if (monsters.size() > LIST_LIMIT) {
            player.dropMessage(6, "...and " + (monsters.size() - LIST_LIMIT) + " more monster(s).");
        }
    }

    private static void start(Character player, String[] params) {
        if (params.length < 2 || params.length > 3) {
            usage(player);
            return;
        }
        Integer monsterOid = parseInteger(params[1]);
        Integer eventLimit = params.length == 3
                ? parseInteger(params[2])
                : MobReactionCaptureRuntime.DEFAULT_EVENT_LIMIT;
        if (monsterOid == null || eventLimit == null) {
            usage(player);
            return;
        }

        MapleMap map = player.getMap();
        Monster monster = map == null ? null : map.getMonsterByOid(monsterOid);
        if (monster == null) {
            player.yellowMessage("No live monster with OID " + monsterOid + " is on your current map.");
            player.dropMessage(6, "Use !mobcapture list to display nearby monster OIDs.");
            return;
        }

        MobReactionCaptureRuntime.OperationResult result =
                MobReactionCaptureRuntime.start(player, map, monster, eventLimit);
        player.yellowMessage(result.message());
        if (!result.success()) {
            return;
        }

        Character controller = monster.getController();
        player.dropMessage(6, "Current controller: "
                + (controller == null ? "none" : controller.getName())
                + ". The capture follows the mob if control changes.");
        player.dropMessage(6, "Test in order, using a marker immediately before each case:");
        player.dropMessage(6, "1) !mobcapture mark baseline — wait for ordinary mob movement.");
        player.dropMessage(6, "2) !mobcapture mark controller-hit — controller hits the mob.");
        player.dropMessage(6, "3) !mobcapture mark other-player-hit — another real player hits it.");
        player.dropMessage(6, "4) !mobcapture mark agent-hit — the Agent hits it; then wait about one second.");
        player.dropMessage(6, "Finish with !mobcapture stop to write the decoded report.");
    }

    private static void mark(Character player, String[] params) {
        if (params.length < 2) {
            usage(player);
            return;
        }
        String label = String.join(" ", Arrays.copyOfRange(params, 1, params.length));
        player.yellowMessage(MobReactionCaptureRuntime.mark(player, label).message());
    }

    private static void stop(Character player, String[] params) {
        if (params.length != 1) {
            usage(player);
            return;
        }
        MobReactionCaptureRuntime.StopResult result = MobReactionCaptureRuntime.stop(player);
        player.yellowMessage(result.message());
        if (result.reportPath() != null) {
            player.dropMessage(6, "Mob reaction capture report: " + result.reportPath());
        }
    }

    private static void clear(Character player, String[] params) {
        if (params.length != 1) {
            usage(player);
            return;
        }
        player.yellowMessage(MobReactionCaptureRuntime.clear(player).message());
    }

    private static void status(Character player, String[] params) {
        if (params.length > 1) {
            usage(player);
            return;
        }
        MobReactionCaptureRuntime.Status status = MobReactionCaptureRuntime.status(player);
        if (!status.active()) {
            player.yellowMessage("No active mob capture. Use !mobcapture list, then !mobcapture start <mobOid> [limit].");
            return;
        }
        player.yellowMessage("Capturing " + status.monsterName() + " (OID " + status.monsterOid()
                + ") on map " + status.mapId() + ": " + status.eventCount() + "/"
                + status.eventLimit() + " event(s), controller " + status.controllerName() + ", "
                + status.elapsedMillis() + "ms elapsed"
                + (status.limitReached() ? " (limit reached; stop to write the report)." : "."));
    }

    private static double distanceSquared(Point first, Point second) {
        return first == null || second == null ? Double.POSITIVE_INFINITY : first.distanceSq(second);
    }

    private static String formatPoint(Point point) {
        return point == null ? "null" : "(" + point.x + "," + point.y + ")";
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void usage(Character player) {
        player.yellowMessage("Usage: !mobcapture list | start <mobOid> [1-2000] | mark <label> | stop | status | clear");
    }
}
