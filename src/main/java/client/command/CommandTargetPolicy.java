package client.command;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Shared command boundary between human characters and autonomous Agents. */
public final class CommandTargetPolicy {
    public static final int AGENT_CONTROL_GM_LEVEL = 6;

    private CommandTargetPolicy() {
    }

    public static boolean isAgent(Character character) {
        return character != null
                && AgentCharacterGatewayRuntime.characters().isHeadlessControlled(character);
    }

    public static boolean canAffect(Character actor, Character target, boolean ownerMayAffectAgent) {
        if (actor == null || target == null) {
            return false;
        }
        if (isAgent(target)) {
            if (ownerMayAffectAgent && actor.gmLevel() >= AGENT_CONTROL_GM_LEVEL) {
                return true;
            }
            actor.yellowMessage("Only the owner may affect Agents with this command.");
            return false;
        }
        if (target != actor && target.gmLevel() >= actor.gmLevel()) {
            actor.yellowMessage("You cannot affect staff of an equal or higher level.");
            return false;
        }
        return true;
    }

    public static boolean includeInHumanCommand(Character actor, Character target) {
        return target != null && !isAgent(target);
    }

    public static boolean includeInOwnerAwareCommand(Character actor, Character target) {
        return target != null
                && (!isAgent(target) || actor.gmLevel() >= AGENT_CONTROL_GM_LEVEL);
    }

    /** Durable lookup for commands that can target an offline character by name. */
    public static boolean isActiveAgentName(String name) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM characters c JOIN agent_characters a ON a.character_id = c.id "
                             + "WHERE LOWER(c.name) = LOWER(?) AND a.status = 'ACTIVE'")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
