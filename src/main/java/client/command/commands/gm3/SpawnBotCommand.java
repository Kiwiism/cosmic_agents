package client.command.commands.gm3;

import client.BotClient;
import client.Character;
import client.Client;
import client.DefaultDates;
import client.command.Command;
import client.creator.BotCreator;
import server.bots.BotManager;
import server.maps.MapleMap;
import tools.BCrypt;
import tools.DatabaseConnection;

import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Date;
import java.sql.Timestamp;

public class SpawnBotCommand extends Command {
    {
        setDescription("Spawn a bot companion at your position. Add 'confirm' to create a new bot.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.yellowMessage("Syntax: !spawnbot <name> [confirm]");
            return;
        }

        // params are lowercased by CommandsExecutor; use lastCommandMessage to preserve casing
        String[] rawArgs = player.getLastCommandMessage().trim().split("[ ]", 2);
        String botName = rawArgs[0];
        MapleMap adminMap = player.getMap();
        Point adminPos = player.getPosition();

        // Case 1: character is online somewhere — check all channels
        Character existingChr = null;
        outer:
        for (var world : net.server.Server.getInstance().getWorlds()) {
            for (var ch : world.getChannels()) {
                Character found = ch.getPlayerStorage().getCharacterByName(botName);
                if (found != null) { existingChr = found; break outer; }
            }
        }
        if (existingChr != null) {
            if (!(existingChr.getClient() instanceof BotClient)) {
                player.yellowMessage("'" + botName + "' is currently being played by a real player — cannot spawn as bot.");
                return;
            }
            teleportBotToPlayer(existingChr, adminMap, adminPos);
            player.yellowMessage("Bot '" + botName + "' teleported to your position.");
            return;
        }

        // Look up character by name in DB
        int charId = getCharacterId(botName);

        if (charId == -1) {
            // Bot doesn't exist yet — require explicit confirmation before creating
            if (params.length < 2 || !params[1].equals("confirm")) {
                player.yellowMessage("Bot '" + botName + "' does not exist. Run: !spawnbot " + botName + " confirm  to create it.");
                return;
            }

            int accountId = createBotAccount(botName);
            if (accountId == -1) {
                player.yellowMessage("Failed to create bot account for '" + botName + "'.");
                return;
            }

            BotClient creationClient = new BotClient(c.getWorld(), c.getChannel());
            creationClient.setAccID(accountId);
            creationClient.setAccountName(botName);

            charId = BotCreator.createCharacter(creationClient, botName);
            if (charId == -1) {
                player.yellowMessage("Failed to create bot character '" + botName + "'. Name may be invalid or already taken.");
                return;
            }

            player.yellowMessage("Bot '" + botName + "' created. Login with: user=" + botName + " pw=botbot");
        }

        // Load character from DB and spawn it into the current map
        try {
            BotClient botClient = new BotClient(c.getWorld(), c.getChannel());
            // Existing characters must be fully loaded before being used as bots.
            // A partial load skips keymap/quests/skills/macros/saved-locations, and a later
            // disconnect/save would then delete the persisted rows with empty in-memory state.
            Character botChar = Character.loadCharFromDB(charId, botClient, true);

            botClient.setPlayer(botChar);
            botClient.setAccID(botChar.getAccountID());

            // Override mapid before newClient so it initialises directly on the admin's map
            botChar.setMapId(adminMap.getId());
            botChar.newClient(botClient);
            botChar.recalcLocalStats(); // initialize localwatk/matk/etc from loaded equipment

            Point spawnPos = adminMap.getPointBelow(new Point(adminPos.x, adminPos.y - 1));
            if (spawnPos == null) {
                spawnPos = adminPos;
            }
            botChar.setPosition(spawnPos);

            c.getChannelServer().addPlayer(botChar);
            c.getChannelServer().getWorldServer().addPlayer(botChar);
            botChar.setEnteredChannelWorld();
            adminMap.addPlayer(botChar);
            botChar.broadcastStance(); // fix floating on spawn

            BotManager.getInstance().registerBot(player.getId(), player, botChar);
            player.yellowMessage("Bot '" + botName + "' spawned. Say 'follow me' or 'stop' to control it.");
        } catch (SQLException e) {
            player.yellowMessage("Failed to load bot character '" + botName + "'.");
            e.printStackTrace();
        }
    }

    private void teleportBotToPlayer(Character bot, MapleMap adminMap, Point adminPos) {
        if (bot.getMapId() != adminMap.getId()) {
            bot.forceChangeMap(adminMap, adminMap.findClosestPortal(adminPos));
        }
        Point snappedPos = adminMap.getPointBelow(new Point(adminPos.x, adminPos.y - 1));
        if (snappedPos == null) {
            snappedPos = adminPos;
        }
        bot.setPosition(snappedPos);
        bot.broadcastStance();
    }

    private int getCharacterId(String name) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id FROM characters WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private int createBotAccount(String name) {
        String hashedPw = BCrypt.hashpw("botbot", BCrypt.gensalt(12));
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO accounts (name, password, birthday, tempban) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, hashedPw);
            ps.setDate(3, Date.valueOf(DefaultDates.getBirthday()));
            ps.setTimestamp(4, Timestamp.valueOf(DefaultDates.getTempban()));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
}
