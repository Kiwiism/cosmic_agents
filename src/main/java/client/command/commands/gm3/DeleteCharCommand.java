package client.command.commands.gm3;

import client.BotClient;
import client.Character;
import client.CharacterDeletionService;
import client.Client;
import client.command.Command;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeCleanupService;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeleteCharCommand extends Command {
    private static final Logger log = LoggerFactory.getLogger(DeleteCharCommand.class);

    {
        setDescription("Delete a character after confirmation.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.yellowMessage("Syntax: !deletechar <name> [confirm]");
            return;
        }

        String[] rawArgs = player.getLastCommandMessage().trim().split("[ ]+", 2);
        String targetName = rawArgs[0];
        boolean confirmed = rawArgs.length > 1 && rawArgs[1].equalsIgnoreCase("confirm");

        ResolvedTarget target = resolveTarget(targetName);
        if (target == null) {
            player.yellowMessage("Character '" + targetName + "' could not be found.");
            return;
        }

        if (target.id == player.getId()) {
            player.yellowMessage("You cannot delete the character currently executing this command.");
            return;
        }

        if (target.gmLevel > player.gmLevel()) {
            player.yellowMessage("You do not have permission to delete '" + target.name + "'.");
            return;
        }

        CharacterDeletionService.Result eligibility = CharacterDeletionService.checkDeletionEligibility(target.id);
        if (!eligibility.isSuccess()) {
            player.yellowMessage("Cannot delete '" + target.name + "': " + eligibility.getCommandMessage());
            return;
        }

        if (!confirmed) {
            String onlineState = target.onlineCharacter != null ? "online" : "offline";
            player.yellowMessage("Delete '" + target.name + "' (id " + target.id + ", acc " + target.accountId + ", " + onlineState + ").");
            player.yellowMessage("Run: !deletechar " + target.name + " confirm");
            return;
        }

        if (target.onlineCharacter != null) {
            if (target.onlineCharacter.getClient() instanceof BotClient) {
                AgentRuntimeCleanupService.removeAgentByCharacterId(target.id);
            }
            target.onlineCharacter.getClient().forceDisconnect();
        }

        CharacterDeletionService.Result result = CharacterDeletionService.deleteCharacter(target.id, target.accountId);
        if (!result.isSuccess()) {
            player.yellowMessage("Delete failed for '" + target.name + "': " + result.getCommandMessage());
            return;
        }

        player.yellowMessage("Character '" + target.name + "' deleted.");
        log.info("Admin {} deleted chrId {} ({}) from account {}", player.getName(), target.id, target.name, target.accountId);
    }

    private ResolvedTarget resolveTarget(String name) {
        for (var world : Server.getInstance().getWorlds()) {
            Character online = world.getPlayerStorage().getCharacterByName(name);
            if (online != null) {
                return new ResolvedTarget(
                        online.getId(),
                        online.getName(),
                        online.getAccountID(),
                        online.gmLevel(),
                        online);
            }
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT id, name, accountid, gm FROM characters WHERE LOWER(name) = LOWER(?)")) {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return new ResolvedTarget(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("accountid"),
                        rs.getInt("gm"),
                        null);
            }
        } catch (SQLException e) {
            log.error("Failed to resolve character '{}'", name, e);
            return null;
        }
    }

    private static final class ResolvedTarget {
        private final int id;
        private final String name;
        private final int accountId;
        private final int gmLevel;
        private final Character onlineCharacter;

        private ResolvedTarget(int id, String name, int accountId, int gmLevel, Character onlineCharacter) {
            this.id = id;
            this.name = name;
            this.accountId = accountId;
            this.gmLevel = gmLevel;
            this.onlineCharacter = onlineCharacter;
        }
    }
}
