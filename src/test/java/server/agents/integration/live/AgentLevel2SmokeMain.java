package server.agents.integration.live;

import client.Character;
import client.CharacterDeletionService;
import client.Client;
import client.inventory.InventoryType;
import client.inventory.Item;
import net.server.Server;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentAccountResolution;
import server.agents.integration.AgentBackingAccountSecurityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentOfflineLoader;
import server.agents.integration.cosmic.CosmicAgentPopulationBackend;
import server.agents.population.AgentPopulationRecord;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.life.Monster;
import server.maps.MapleMap;
import tools.DatabaseConnection;

import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Opt-in Level 2 smoke runner for the real server and headless Agent runtime.
 * It creates and removes its own temporary backing account and character.
 */
public final class AgentLevel2SmokeMain {
    private static final int WORLD = 0;
    private static final int CHANNEL = 1;
    private static final int TEST_MAP_ID = 50_000;
    private static final int TEST_LOOT_ITEM_ID = 4_000_000;

    private AgentLevel2SmokeMain() {
    }

    public static void main(String[] args) {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        boolean passed = false;
        TestIdentity identity = null;
        Character agent = null;
        try {
            Server.getInstance().init();
            identity = createTemporaryAgent();
            agent = runSmoke(identity);
            passed = true;
        } catch (Throwable failure) {
            System.err.println("[AGENT-LEVEL2] failure=" + failure.getMessage());
            failure.printStackTrace(System.err);
        } finally {
            passed &= cleanup(identity, agent);
        }
        System.out.println("[AGENT-LEVEL2] RESULT=" + (passed ? "PASS" : "FAIL"));
        System.exit(passed ? 0 : 1);
    }

    private static TestIdentity createTemporaryAgent() throws Exception {
        String name = "Lv2" + Long.toHexString(System.nanoTime()).substring(0, 8);
        AgentAccountResolution account = AgentPersistenceGatewayRuntime.persistence()
                .resolveOrCreateAgentAccount(name);
        require(account.isSuccess(), "temporary Agent account creation failed: " + account.failureMessage());
        try {
            require(AgentBackingAccountSecurityRuntime.lockInteractiveLogin(account.accountId()),
                    "temporary Agent account could not be locked");

            Client creationClient = AgentClientGatewayRuntime.clients().createHeadlessClient(WORLD, CHANNEL);
            creationClient.setAccID(account.accountId());
            creationClient.setAccountName(name);
            int characterId = AgentClientGatewayRuntime.clients().createBackingCharacter(creationClient, name);
            require(characterId > 0, "temporary Agent character creation failed");
            System.out.printf("[AGENT-LEVEL2] created account=%d character=%d name=%s%n",
                    account.accountId(), characterId, name);
            return new TestIdentity(account.accountId(), characterId, name);
        } catch (Throwable failure) {
            deleteTemporaryAccount(account.accountId());
            throw failure;
        }
    }

    private static Character runSmoke(TestIdentity identity) throws Exception {
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(WORLD, CHANNEL, TEST_MAP_ID);
        require(map != null, "test map " + TEST_MAP_ID + " is unavailable");
        require(await(() -> !map.getAllMonsters().isEmpty(), Duration.ofSeconds(10)),
                "test map has no spawned monsters");

        Point spawn = new Point(map.getAllMonsters().getFirst().getPosition());
        Character agent = CosmicAgentOfflineLoader.loadOfflineAgent(
                identity.characterId(), WORLD, CHANNEL, map, spawn);
        AgentRuntimeEntry entry = AgentInteractionRuntime.registerSelfDirectedAgent(agent);
        require(AgentRuntimeRegistry.hasActiveAgentCharacterId(identity.characterId()),
                "Agent runtime registration was not published");
        System.out.printf("[AGENT-LEVEL2] spawned map=%d position=%s mobs=%d%n",
                agent.getMapId(), agent.getPosition(), map.getAllMonsters().size());

        verifyMovement(entry, agent, map);
        verifyCombat(entry, agent, map);
        verifyLoot(entry, agent, map);
        verifyCleanupAndPersistence(identity, agent);
        return agent;
    }

    private static void verifyMovement(AgentRuntimeEntry entry, Character agent, MapleMap map) throws Exception {
        AgentMovementCommandRuntime.stop(entry);
        Point initial = new Point(agent.getPosition());
        Point target = map.getAllMonsters().stream()
                .map(Monster::getPosition)
                .max(Comparator.comparingDouble(initial::distanceSq))
                .map(Point::new)
                .orElseThrow();
        require(initial.distanceSq(target) >= 400,
                "test map did not provide a distinct movement target");
        AgentMovementCommandRuntime.moveTo(entry, target, false);
        require(await(() -> agent.getPosition().distanceSq(initial) >= 400, Duration.ofSeconds(45)),
                "Agent did not move under the live tick runtime");
        System.out.printf("[AGENT-LEVEL2] movement initial=%s current=%s target=%s%n",
                initial, agent.getPosition(), target);
    }

    private static void verifyCombat(AgentRuntimeEntry entry, Character agent, MapleMap map) throws Exception {
        int initialLevel = agent.getLevel();
        int initialExp = agent.getExp();
        Set<Integer> initialMobIds = new HashSet<>();
        map.getAllMonsters().forEach(mob -> initialMobIds.add(mob.getObjectId()));
        AgentMovementCommandRuntime.grind(entry);
        require(await(() -> agent.getLevel() > initialLevel
                        || agent.getExp() != initialExp
                        || map.getAllMonsters().stream().noneMatch(mob -> initialMobIds.contains(mob.getObjectId())),
                Duration.ofSeconds(90)), "Agent did not kill a monster under the live combat runtime");
        System.out.printf("[AGENT-LEVEL2] combat level=%d->%d exp=%d->%d%n",
                initialLevel, agent.getLevel(), initialExp, agent.getExp());
    }

    private static void verifyLoot(AgentRuntimeEntry entry, Character agent, MapleMap map) throws Exception {
        int initialCount = agent.getInventory(InventoryType.ETC).countById(TEST_LOOT_ITEM_ID);
        Item item = new Item(TEST_LOOT_ITEM_ID, (short) 0, (short) 1);
        map.spawnItemDrop(agent, agent, item, new Point(agent.getPosition()), true, false);
        AgentMovementCommandRuntime.grind(entry);
        require(await(() -> agent.getInventory(InventoryType.ETC).countById(TEST_LOOT_ITEM_ID) > initialCount,
                Duration.ofSeconds(30)), "Agent did not pick up a nearby eligible item");
        System.out.printf("[AGENT-LEVEL2] loot item=%d count=%d->%d%n", TEST_LOOT_ITEM_ID,
                initialCount, agent.getInventory(InventoryType.ETC).countById(TEST_LOOT_ITEM_ID));
    }

    private static void verifyCleanupAndPersistence(TestIdentity identity, Character agent) throws Exception {
        int expectedLevel = agent.getLevel();
        int expectedExp = agent.getExp();
        int expectedItemCount = agent.getInventory(InventoryType.ETC).countById(TEST_LOOT_ITEM_ID);
        int expectedMapId = agent.getMapId();

        CosmicAgentPopulationBackend backend = new CosmicAgentPopulationBackend();
        require(backend.stop(identity.characterId()), "live Agent session did not stop");
        require(await(() -> !AgentRuntimeRegistry.hasActiveAgentCharacterId(identity.characterId()),
                Duration.ofSeconds(10)), "Agent runtime entry remained after disconnect");

        Client verificationClient = AgentClientGatewayRuntime.clients().createHeadlessClient(WORLD, CHANNEL);
        verificationClient.setAccID(identity.accountId());
        Character reloaded = AgentClientGatewayRuntime.clients()
                .loadBackingCharacter(identity.characterId(), verificationClient);
        require(reloaded.getLevel() == expectedLevel && reloaded.getExp() == expectedExp,
                "Agent level/experience did not persist across reload");
        require(reloaded.getMapId() == expectedMapId,
                "Agent map did not persist across reload");
        require(reloaded.getInventory(InventoryType.ETC).countById(TEST_LOOT_ITEM_ID) == expectedItemCount,
                "Agent loot did not persist across reload");
        System.out.printf("[AGENT-LEVEL2] persistence map=%d level=%d exp=%d itemCount=%d%n",
                expectedMapId, expectedLevel, expectedExp, expectedItemCount);
    }

    private static boolean await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return condition.getAsBoolean();
    }

    private static boolean cleanup(TestIdentity identity, Character agent) {
        if (identity == null) {
            return true;
        }
        boolean clean = true;
        try {
            if (AgentRuntimeRegistry.hasActiveAgentCharacterId(identity.characterId())) {
                new CosmicAgentPopulationBackend().stop(identity.characterId());
            } else if (agent != null && agent.getClient() != null && agent.getClient().getPlayer() != null) {
                agent.getClient().forceDisconnect();
            }
        } catch (Throwable failure) {
            System.err.println("[AGENT-LEVEL2] cleanup disconnect failed: " + failure.getMessage());
            clean = false;
        }

        try {
            CharacterDeletionService.Result result = CharacterDeletionService.deleteCharacter(
                    identity.characterId(), identity.accountId());
            require(result.isSuccess(), "temporary character cleanup failed: " + result);
            deleteTemporaryAccount(identity.accountId());
            System.out.printf("[AGENT-LEVEL2] removed account=%d character=%d%n",
                    identity.accountId(), identity.characterId());
        } catch (Throwable failure) {
            System.err.println("[AGENT-LEVEL2] cleanup database rows failed: " + failure.getMessage());
            clean = false;
        }
        return clean;
    }

    private static void deleteTemporaryAccount(int accountId) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            statement.setInt(1, accountId);
            require(statement.executeUpdate() == 1, "temporary account cleanup failed");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record TestIdentity(int accountId, int characterId, String name) {
    }
}
