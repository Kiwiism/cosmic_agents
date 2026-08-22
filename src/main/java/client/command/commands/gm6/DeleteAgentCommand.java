package client.command.commands.gm6;

import client.Character;
import client.CharacterDeletionService;
import client.Client;
import client.command.Command;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentIdentityGatewayRuntime;
import server.agents.runtime.AgentRuntimeCleanupService;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DeleteAgentCommand extends Command {
    private static final Logger log = LoggerFactory.getLogger(DeleteAgentCommand.class);

    {
        setDescription("Permanently delete an Agent after confirmation.");
    }

    @Override
    public void execute(Client client, String[] params) {
        Character actor = client.getPlayer();
        if (params.length < 1) {
            actor.yellowMessage("Syntax: !deleteagent <name> [confirm]");
            return;
        }

        String[] rawArgs = actor.getLastCommandMessage().trim().split("[ ]+", 2);
        String targetName = rawArgs[0];
        boolean confirmed = rawArgs.length > 1 && rawArgs[1].equalsIgnoreCase("confirm");
        ResolvedTarget target = resolveTarget(targetName);
        if (target == null) {
            actor.yellowMessage("Character '" + targetName + "' could not be found.");
            return;
        }
        if (!isActiveAgent(target.id)) {
            actor.yellowMessage("'" + target.name + "' is not an active Agent identity.");
            return;
        }

        CharacterDeletionService.Result eligibility = CharacterDeletionService.checkDeletionEligibility(target.id);
        if (!eligibility.isSuccess()) {
            actor.yellowMessage("Cannot delete Agent '" + target.name + "': " + eligibility.getCommandMessage());
            return;
        }
        if (!confirmed) {
            actor.yellowMessage("Delete Agent '" + target.name + "' (id " + target.id + ", account "
                    + target.accountId + ", " + (target.onlineCharacter == null ? "offline" : "online") + ").");
            actor.yellowMessage("Run: !deleteagent " + target.name + " confirm");
            return;
        }

        AgentRuntimeCleanupService.removeAgentByCharacterId(target.id);
        if (target.onlineCharacter != null && target.onlineCharacter.getClient() != null) {
            target.onlineCharacter.getClient().forceDisconnect();
        }
        CharacterDeletionService.Result result = CharacterDeletionService.deleteCharacter(target.id, target.accountId);
        if (!result.isSuccess()) {
            actor.yellowMessage("Delete failed for Agent '" + target.name + "': " + result.getCommandMessage());
            return;
        }
        actor.yellowMessage("Agent '" + target.name + "' deleted.");
        log.info("Owner {} deleted agent chrId {} ({}) from account {}",
                actor.getName(), target.id, target.name, target.accountId);
    }

    private static boolean isActiveAgent(int characterId) {
        try {
            return AgentIdentityGatewayRuntime.identities().isActiveAgent(characterId);
        } catch (SQLException exception) {
            log.error("Failed to resolve Agent identity for chrId {}", characterId, exception);
            return false;
        }
    }

    private static ResolvedTarget resolveTarget(String name) {
        for (var world : Server.getInstance().getWorlds()) {
            Character online = world.getPlayerStorage().getCharacterByName(name);
            if (online != null) {
                return new ResolvedTarget(online.getId(), online.getName(), online.getAccountID(), online);
            }
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, accountid FROM characters WHERE LOWER(name) = LOWER(?)")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? new ResolvedTarget(result.getInt("id"), result.getString("name"),
                        result.getInt("accountid"), null)
                        : null;
            }
        } catch (SQLException exception) {
            log.error("Failed to resolve character '{}'", name, exception);
            return null;
        }
    }

    private record ResolvedTarget(int id, String name, int accountId, Character onlineCharacter) {
    }
}
