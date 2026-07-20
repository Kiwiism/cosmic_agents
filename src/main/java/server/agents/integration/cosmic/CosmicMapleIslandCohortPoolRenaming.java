package server.agents.integration.cosmic;

import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolRenamer;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cosmic SQL adapter for guarded offline cohort-pool name maintenance. */
public final class CosmicMapleIslandCohortPoolRenaming implements MapleIslandCohortPoolRenamer.Hooks {
    public static final CosmicMapleIslandCohortPoolRenaming INSTANCE =
            new CosmicMapleIslandCohortPoolRenaming();

    private CosmicMapleIslandCohortPoolRenaming() {
    }

    @Override
    public Integer characterIdByName(String characterName) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id FROM characters WHERE LOWER(name) = LOWER(?) LIMIT 1")) {
            statement.setString(1, characterName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("id") : null;
            }
        }
    }

    @Override
    public void renameCharacters(List<MapleIslandCohortPoolRenamer.Rename> renames) throws SQLException {
        if (renames == null || renames.isEmpty()) {
            return;
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                validateRenameSet(connection, renames);
                Map<Integer, String> temporaryNames = temporaryNames(connection, renames);
                updateCharacterNames(connection, renames, temporaryNames, true);
                updateRingNames(connection, renames, temporaryNames, true);
                updateCharacterNames(connection, renames, temporaryNames, false);
                updateRingNames(connection, renames, temporaryNames, false);
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private static void validateRenameSet(Connection connection,
                                          List<MapleIslandCohortPoolRenamer.Rename> renames)
            throws SQLException {
        Map<Integer, MapleIslandCohortPoolRenamer.Rename> byCharacterId = new HashMap<>();
        for (MapleIslandCohortPoolRenamer.Rename rename : renames) {
            if (byCharacterId.put(rename.characterId(), rename) != null) {
                throw new SQLException("Duplicate cohort rename for character " + rename.characterId());
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT c.name, c.accountid, a.banned, a.banreason "
                            + "FROM characters c JOIN accounts a ON a.id = c.accountid "
                            + "WHERE c.id = ? FOR UPDATE")) {
                statement.setInt(1, rename.characterId());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()
                            || result.getInt("accountid") != rename.accountId()
                            || !result.getString("name").equals(rename.oldName())
                            || result.getInt("banned") != 1
                            || !CosmicAgentBackingAccountSecurity.AGENT_ONLY_BAN_REASON
                            .equals(result.getString("banreason"))) {
                        throw new SQLException("Cohort character identity changed before rename: "
                                + rename.characterId());
                    }
                }
            }
        }
        for (MapleIslandCohortPoolRenamer.Rename rename : renames) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT id FROM characters WHERE LOWER(name) = LOWER(?) FOR UPDATE")) {
                statement.setString(1, rename.newName());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        if (!byCharacterId.containsKey(result.getInt("id"))) {
                            throw new SQLException("Character name is no longer available: " + rename.newName());
                        }
                    }
                }
            }
        }
    }

    private static Map<Integer, String> temporaryNames(
            Connection connection,
            List<MapleIslandCohortPoolRenamer.Rename> renames) throws SQLException {
        Map<Integer, String> result = new HashMap<>();
        for (MapleIslandCohortPoolRenamer.Rename rename : renames) {
            String temporaryName = "MITmp" + Integer.toString(rename.characterId(), 36);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM characters WHERE LOWER(name) = LOWER(?) AND id <> ? LIMIT 1")) {
                statement.setString(1, temporaryName);
                statement.setInt(2, rename.characterId());
                try (ResultSet query = statement.executeQuery()) {
                    if (query.next()) {
                        throw new SQLException("Temporary cohort rename is occupied: " + temporaryName);
                    }
                }
            }
            result.put(rename.characterId(), temporaryName);
        }
        return result;
    }

    private static void updateCharacterNames(
            Connection connection,
            List<MapleIslandCohortPoolRenamer.Rename> renames,
            Map<Integer, String> temporaryNames,
            boolean toTemporary) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE characters SET name = ? WHERE id = ? AND name = ?")) {
            for (MapleIslandCohortPoolRenamer.Rename rename : renames) {
                statement.setString(1, toTemporary
                        ? temporaryNames.get(rename.characterId()) : rename.newName());
                statement.setInt(2, rename.characterId());
                statement.setString(3, toTemporary
                        ? rename.oldName() : temporaryNames.get(rename.characterId()));
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Cohort character changed during rename: " + rename.characterId());
                }
            }
        }
    }

    private static void updateRingNames(
            Connection connection,
            List<MapleIslandCohortPoolRenamer.Rename> renames,
            Map<Integer, String> temporaryNames,
            boolean toTemporary) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE rings SET partnername = ? WHERE partnername = ?")) {
            for (MapleIslandCohortPoolRenamer.Rename rename : renames) {
                statement.setString(1, toTemporary
                        ? temporaryNames.get(rename.characterId()) : rename.newName());
                statement.setString(2, toTemporary
                        ? rename.oldName() : temporaryNames.get(rename.characterId()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
