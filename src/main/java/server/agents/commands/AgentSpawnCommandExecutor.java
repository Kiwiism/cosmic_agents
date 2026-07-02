package server.agents.commands;

import client.BotClient;
import client.Character;
import client.Client;
import client.DefaultDates;
import client.creator.BotCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentPartyLifecycleService;
import server.agents.auth.AgentOwnershipService;
import tools.BCrypt;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * Transitional Agent-facing spawn command executor that preserves legacy bot creation/spawn behavior
 * until lifecycle and registry logic move into Agent runtime modules.
 */
public final class AgentSpawnCommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(AgentSpawnCommandExecutor.class);

    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        AgentOwnershipService ownershipService = AgentOwnershipService.getInstance();
        if (params.length < 1) {
            player.yellowMessage("Syntax: @spawnbot <name> [confirm]");
            return;
        }

        String[] rawArgs = player.getLastCommandMessage().trim().split("[ ]", 2);
        String botName = rawArgs[0];
        boolean createRequested = params.length >= 2 && params[1].equals("confirm");

        AgentResolvedCharacter bot = ownershipService.resolveCharacterByName(botName);
        if (bot == null) {
            if (!createRequested) {
                player.yellowMessage("Bot '" + botName + "' does not exist. Run: @spawnbot " + botName + " confirm  to create it.");
                return;
            }

            BotAccountResolution account = resolveBotAccount(botName);
            if (!account.isSuccess()) {
                player.yellowMessage(account.failureMessage());
                return;
            }

            BotClient creationClient = new BotClient(c.getWorld(), c.getChannel());
            creationClient.setAccID(account.accountId());
            creationClient.setAccountName(botName);

            int createdCharId = BotCreator.createCharacter(creationClient, botName);
            if (createdCharId == -1) {
                player.yellowMessage("Failed to create bot character '" + botName + "'. Name may be invalid or already taken.");
                return;
            }

            ownershipService.registerOwner(createdCharId, player.getId());
            bot = ownershipService.resolveCharacterByName(botName);
            if (account.created()) {
                player.yellowMessage("Bot '" + botName + "' created. Login with: user=" + botName + " pw=botbot");
            } else {
                player.yellowMessage("Bot '" + botName + "' created on the existing empty account '" + botName + "'.");
            }
        }

        AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime.spawnAgentForLeader(player, botName);
        if (!result.success()) {
            player.yellowMessage(result.errorMessage());
            return;
        }
        AgentPartyLifecycleService.joinAgentToLeaderParty(player, result.agent());
        if (result.autoRegistered()) {
            player.yellowMessage("Bot '" + result.agent().getName() + "' auto-registered to " + player.getName() + " because it is on the same account.");
        }
        player.yellowMessage("Bot '" + result.agent().getName() + "' spawned. Say 'follow me' or 'stop' to control it.");
    }

    private BotAccountResolution resolveBotAccount(String name) {
        try (Connection con = DatabaseConnection.getConnection()) {
            Integer existingAccountId = findAccountId(con, name);
            if (existingAccountId != null) {
                if (countCharactersOnAccount(con, existingAccountId) == 0) {
                    return BotAccountResolution.reused(existingAccountId);
                }
                return BotAccountResolution.failure(
                        "Account '" + name + "' already exists and still has characters, so it cannot be reused for bot creation.");
            }

            int createdAccountId = createBotAccount(con, name);
            if (createdAccountId > 0) {
                return BotAccountResolution.created(createdAccountId);
            }
        } catch (SQLException e) {
            log.warn("Failed to create or reuse bot account '{}'", name, e);
        }
        return BotAccountResolution.failure("Failed to create or reuse the bot account for '" + name + "'.");
    }

    private Integer findAccountId(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM accounts WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }

    private int countCharactersOnAccount(Connection con, int accountId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) AS rowcount FROM characters WHERE accountid = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("rowcount");
                }
            }
        }
        return 0;
    }

    private int createBotAccount(Connection con, String name) throws SQLException {
        String hashedPw = BCrypt.hashpw("botbot", BCrypt.gensalt(12));
        try (PreparedStatement ps = con.prepareStatement(
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
        }
        return -1;
    }

    private record BotAccountResolution(int accountId, boolean created, String failureMessage) {
        static BotAccountResolution created(int accountId) {
            return new BotAccountResolution(accountId, true, null);
        }

        static BotAccountResolution reused(int accountId) {
            return new BotAccountResolution(accountId, false, null);
        }

        static BotAccountResolution failure(String failureMessage) {
            return new BotAccountResolution(-1, false, failureMessage);
        }

        boolean isSuccess() {
            return accountId > 0;
        }
    }
}
