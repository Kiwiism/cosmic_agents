package server.agents.integration.cosmic;

import client.Client;
import client.creator.BotCreator;
import server.agents.integration.AgentAccountResolution;
import server.agents.integration.AgentBackingAccountSecurityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolProvisioner;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortCharacterTemplate;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Cosmic SQL/client adapter for the guarded reusable cohort provisioner. */
public final class CosmicMapleIslandCohortProvisioning implements MapleIslandCohortPoolProvisioner.Hooks {
    private static final int COHORT_ACCOUNT_CHARACTER_SLOTS = config.AgentTuning.intValue("server.agents.integration.cosmic.CosmicMapleIslandCohortProvisioning.COHORT_ACCOUNT_CHARACTER_SLOTS");
    public static final CosmicMapleIslandCohortProvisioning INSTANCE =
            new CosmicMapleIslandCohortProvisioning();

    private CosmicMapleIslandCohortProvisioning() {
    }

    @Override
    public boolean accountNameExists(String accountName) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM accounts WHERE LOWER(name) = LOWER(?) LIMIT 1")) {
            statement.setString(1, accountName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public MapleIslandCohortPoolProvisioner.CreatedAccount createDedicatedAccount(String accountName)
            throws Exception {
        AgentAccountResolution account = AgentPersistenceGatewayRuntime.persistence()
                .resolveOrCreateAgentAccount(accountName);
        if (!account.isSuccess()) {
            throw new SQLException(account.failureMessage());
        }
        if (!account.created()) {
            throw new SQLException("Refusing to reuse an existing account for cohort provisioning: "
                    + accountName);
        }
        if (!AgentBackingAccountSecurityRuntime.lockInteractiveLogin(account.accountId())) {
            throw new SQLException("Failed to lock cohort account '" + accountName + "'");
        }
        MapleIslandCohortPoolProvisioner.AccountCapacity capacity =
                ensureDedicatedAccountCapacity(account.accountId());
        if (capacity.characterSlots() <= 0) {
            throw new SQLException("Cohort account has no character slots: " + accountName);
        }
        return new MapleIslandCohortPoolProvisioner.CreatedAccount(
                account.accountId(), accountName, capacity.characterSlots());
    }

    @Override
    public boolean isDedicatedAgentAccount(int accountId) throws SQLException {
        return CosmicAgentBackingAccountSecurity.isAgentOnlyAccount(accountId);
    }

    @Override
    public MapleIslandCohortPoolProvisioner.AccountCapacity ensureDedicatedAccountCapacity(int accountId)
            throws SQLException {
        if (!isDedicatedAgentAccount(accountId)) {
            throw new SQLException("Refusing to expand non-Agent cohort account " + accountId);
        }
        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE accounts SET characterslots = ? WHERE id = ? AND characterslots < ?")) {
                statement.setInt(1, COHORT_ACCOUNT_CHARACTER_SLOTS);
                statement.setInt(2, accountId);
                statement.setInt(3, COHORT_ACCOUNT_CHARACTER_SLOTS);
                statement.executeUpdate();
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        return accountCapacity(accountId);
    }

    @Override
    public MapleIslandCohortPoolProvisioner.AccountCapacity accountCapacity(int accountId)
            throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT a.characterslots, COUNT(c.id) AS character_count "
                             + "FROM accounts a LEFT JOIN characters c ON c.accountid = a.id "
                             + "WHERE a.id = ? GROUP BY a.id, a.characterslots")) {
            statement.setInt(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("No account exists for cohort pool id " + accountId);
                }
                return new MapleIslandCohortPoolProvisioner.AccountCapacity(
                        result.getInt("characterslots"), result.getInt("character_count"));
            }
        }
    }

    @Override
    public boolean characterNameExists(String characterName) throws SQLException {
        return AgentPersistenceGatewayRuntime.persistence().findCharacterByName(characterName) != null;
    }

    public boolean characterIdentityMatches(int characterId,
                                            String characterName,
                                            int accountId,
                                            String accountName,
                                            int world) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM characters c JOIN accounts a ON a.id = c.accountid "
                             + "WHERE c.id = ? AND LOWER(c.name) = LOWER(?) "
                             + "AND c.accountid = ? AND LOWER(a.name) = LOWER(?) "
                             + "AND c.world = ? LIMIT 1")) {
            statement.setInt(1, characterId);
            statement.setString(2, characterName);
            statement.setInt(3, accountId);
            statement.setString(4, accountName);
            statement.setInt(5, world);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    @Override
    public int createCharacter(int accountId,
                               String accountName,
                               String characterName,
                               int world,
                               int channel,
                               MapleIslandCohortCharacterTemplate characterTemplate) {
        Client client = AgentClientGatewayRuntime.clients().createHeadlessClient(world, channel);
        client.setAccID(accountId);
        client.setAccountName(accountName);
        return BotCreator.createCharacter(
                client,
                characterName,
                characterTemplate.face(),
                characterTemplate.hair(),
                characterTemplate.skin(),
                characterTemplate.gender(),
                characterTemplate.top(),
                characterTemplate.bottom(),
                characterTemplate.shoes(),
                characterTemplate.weapon());
    }
}
