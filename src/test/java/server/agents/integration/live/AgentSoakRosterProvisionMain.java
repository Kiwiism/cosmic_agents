package server.agents.integration.live;

import client.Client;
import config.YamlConfig;
import net.server.Server;
import server.agents.integration.AgentAccountResolution;
import server.agents.integration.AgentBackingAccountSecurityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentBackingAccountSecurity;
import server.agents.population.AgentPopulationRecord;
import server.agents.population.FileAgentPopulationStore;
import server.agents.registry.AgentResolvedCharacter;
import tools.DatabaseConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/** Opt-in disposable-database provisioner for scheduler live-soak backing characters. */
public final class AgentSoakRosterProvisionMain {
    private static final int WORLD = 0;
    private static final int CHANNEL = 1;

    private AgentSoakRosterProvisionMain() {
    }

    public static void main(String[] args) {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        boolean serverStarted = false;
        int exitCode = 1;
        try {
            Path repoRoot = Path.of("").toAbsolutePath().normalize();
            AgentSoakRosterProvisioning.Options options = AgentSoakRosterProvisioning.parse(args, repoRoot);
            String configuredDatabase = AgentSoakRosterProvisioning.configuredDatabase(
                    Files.readString(repoRoot.resolve(YamlConfig.CONFIG_FILE_NAME)));
            AgentSoakRosterProvisioning.requireDisposableDatabase(
                    configuredDatabase, options.expectedDatabase());

            System.out.printf("[AGENT-SOAK-ROSTER] database=%s target=%d output=%s%n",
                    configuredDatabase, options.target(), options.output());
            Server.getInstance().init();
            serverStarted = true;
            verifyConnectedDatabase(options.expectedDatabase());

            ProvisionResult result = provision(options);
            new FileAgentPopulationStore(options.output()).save(
                    AgentSoakRosterProvisioning.snapshot(result.records()));
            System.out.printf("[AGENT-SOAK-ROSTER] RESULT=PASS target=%d created=%d reused=%d output=%s%n",
                    result.records().size(), result.created(), result.reused(), options.output());
            exitCode = 0;
        } catch (Throwable failure) {
            System.err.println("[AGENT-SOAK-ROSTER] RESULT=FAIL reason=" + failure.getMessage());
            failure.printStackTrace(System.err);
        } finally {
            if (serverStarted) {
                Server.getInstance().shutdown(false).run();
            } else {
                System.exit(exitCode);
            }
        }
    }

    private static ProvisionResult provision(AgentSoakRosterProvisioning.Options options) throws Exception {
        List<AgentPopulationRecord> records = new ArrayList<>(options.target());
        int created = 0;
        int reused = 0;
        for (int sequence = 1; sequence <= options.target(); sequence++) {
            String name = AgentSoakRosterProvisioning.name(options.prefix(), sequence);
            AgentResolvedCharacter existing = AgentPersistenceGatewayRuntime.persistence().findCharacterByName(name);
            if (existing != null) {
                require(CosmicAgentBackingAccountSecurity.isAgentOnlyAccount(existing.accountId()),
                        "Existing character '" + name + "' is not on an Agent-only account");
                records.add(new AgentPopulationRecord(existing.id(), existing.name(), null));
                reused++;
            } else {
                records.add(create(name));
                created++;
            }
            if (sequence == options.target() || sequence % 25 == 0) {
                System.out.printf("[AGENT-SOAK-ROSTER] progress=%d/%d created=%d reused=%d%n",
                        sequence, options.target(), created, reused);
            }
        }
        return new ProvisionResult(records, created, reused);
    }

    private static AgentPopulationRecord create(String name) throws Exception {
        AgentAccountResolution account = AgentPersistenceGatewayRuntime.persistence()
                .resolveOrCreateAgentAccount(name);
        require(account.isSuccess(), "Agent account creation failed for '" + name + "': "
                + account.failureMessage());
        try {
            require(AgentBackingAccountSecurityRuntime.lockInteractiveLogin(account.accountId()),
                    "Agent account lock failed for '" + name + "'");
            Client client = AgentClientGatewayRuntime.clients().createHeadlessClient(WORLD, CHANNEL);
            client.setAccID(account.accountId());
            client.setAccountName(name);
            int characterId = AgentClientGatewayRuntime.clients().createBackingCharacter(client, name);
            require(characterId > 0, "Backing character creation failed for '" + name + "'");
            return new AgentPopulationRecord(characterId, name, null);
        } catch (Throwable failure) {
            deleteAccount(account.accountId());
            throw failure;
        }
    }

    private static void verifyConnectedDatabase(String expectedDatabase) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection()) {
            String connectedDatabase = connection.getCatalog();
            AgentSoakRosterProvisioning.requireDisposableDatabase(connectedDatabase, expectedDatabase);
        }
    }

    private static void deleteAccount(int accountId) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            statement.setInt(1, accountId);
            statement.executeUpdate();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record ProvisionResult(List<AgentPopulationRecord> records, int created, int reused) {
    }
}
