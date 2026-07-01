package server.agents.runtime;

import client.Character;
import server.agents.auth.AgentAuthorizationResult;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.ScheduledFuture;

/**
 * Agent-owned lifecycle map mutation helpers over the temporary BotEntry store.
 * BotManager still supplies runtime side effects such as scheduled-task canceling.
 */
public final class AgentLifecycleService {
    public record AgentSpawnResult(boolean success, Character agent, boolean autoRegistered, String errorMessage) {
        public static AgentSpawnResult ok(Character agent, boolean autoRegistered) {
            return new AgentSpawnResult(true, agent, autoRegistered, null);
        }

        public static AgentSpawnResult fail(String message) {
            return new AgentSpawnResult(false, null, false, message);
        }
    }

    public record SpawnHooks(BiFunction<MapleMap, Point, Point> resolveSpawnPosition,
                             RegisterSpawnedAgent registerSpawnedAgent,
                             OfflineAgentLoader loadOfflineAgent,
                             PlaceSpawnedOnlineAgent placeSpawnedOnlineAgent,
                             Consumer<BotEntry> startFollowLeader,
                             ChangeMapToSpawn changeMapToSpawn) {
    }

    @FunctionalInterface
    public interface RegisterSpawnedAgent {
        BotEntry register(int leaderCharId, Character leader, Character agent);
    }

    @FunctionalInterface
    public interface OfflineAgentLoader {
        Character load(int charId, int world, int channel, MapleMap targetMap, Point desiredPosition) throws SQLException;
    }

    @FunctionalInterface
    public interface PlaceSpawnedOnlineAgent {
        void place(BotEntry entry, Character agent, MapleMap spawnMap, Point spawnPosition);
    }

    @FunctionalInterface
    public interface ChangeMapToSpawn {
        void change(Character agent, MapleMap spawnMap, Point spawnPosition);
    }

    public record RegisterHooks(long tickMs,
                                AgentTickScheduler tickScheduler,
                                AgentTickCallback tickCallback,
                                Consumer<BotEntry> cancelExistingTask,
                                AgentFormationService.FormationState defaultFormation,
                                Consumer<BotEntry> normalizeSpawnedAgent,
                                LongSupplier spawnStatusDelayMs) {
    }

    @FunctionalInterface
    public interface AgentTickScheduler {
        ScheduledFuture<?> schedule(Runnable tick, long periodMs);
    }

    @FunctionalInterface
    public interface AgentTickCallback {
        void tick(BotEntry entry, int leaderCharId, int agentCharId);
    }

    private AgentLifecycleService() {
    }

    public static AgentSpawnResult spawnAgentForLeader(Character leader,
                                                       String agentName,
                                                       AgentOwnershipService ownershipService,
                                                       SpawnHooks hooks) throws SQLException {
        AgentResolvedCharacter resolved = ownershipService.resolveCharacterByName(agentName);
        if (resolved == null) {
            return AgentSpawnResult.fail("No character named '" + agentName + "' exists.");
        }
        if (resolved.isOnline() && !resolved.isOnlineAsBot()) {
            return AgentSpawnResult.fail("'" + agentName + "' is currently being played by a real player.");
        }

        AgentAuthorizationResult auth = ownershipService.ensureCanControl(leader, resolved);
        if (!auth.allowed()) {
            return AgentSpawnResult.fail(auth.failureMessage());
        }

        MapleMap map = leader.getMap();
        Point spawnPosition = hooks.resolveSpawnPosition().apply(map, leader.getPosition());
        if (resolved.isOnline()) {
            Character agent = resolved.onlineCharacter();
            Character activeLeader = AgentRuntimeRegistry.activeLeaderByAgentCharacterId(agent.getId());
            if (activeLeader != null && activeLeader.getId() != leader.getId()) {
                return AgentSpawnResult.fail("Bot '" + agentName + "' is controlled by " + activeLeader.getName() + ".");
            }

            BotEntry entry = activeLeader == null
                    ? hooks.registerSpawnedAgent().register(leader.getId(), leader, agent)
                    : AgentRuntimeRegistry.findByCharacterId(leader.getId(), agent.getId());
            if (agent.getMapId() != map.getId()) {
                hooks.changeMapToSpawn().change(agent, map, spawnPosition);
            }
            hooks.placeSpawnedOnlineAgent().place(entry, agent, map, spawnPosition);
            if (entry != null) {
                hooks.startFollowLeader().accept(entry);
            }
            return AgentSpawnResult.ok(agent, auth.autoRegistered());
        }

        Character agent = hooks.loadOfflineAgent().load(
                resolved.id(),
                leader.getClient().getWorld(),
                leader.getClient().getChannel(),
                map,
                spawnPosition);
        BotEntry entry = hooks.registerSpawnedAgent().register(leader.getId(), leader, agent);
        hooks.startFollowLeader().accept(entry);
        return AgentSpawnResult.ok(agent, auth.autoRegistered());
    }

    public static BotEntry registerAgent(int leaderCharId,
                                         Character leader,
                                         Character agent,
                                         boolean normalizeSpawnState,
                                         RegisterHooks hooks) {
        List<BotEntry> entries = AgentRuntimeRegistry.mutableEntriesForLeader(leaderCharId);
        entries.removeIf(entry -> {
            if (AgentBotRuntimeIdentityRuntime.botIs(entry, agent.getId())) {
                hooks.cancelExistingTask().accept(entry);
                return true;
            }
            return false;
        });

        int agentCharId = agent.getId();
        BotEntry[] ref = new BotEntry[1];
        ScheduledFuture<?> task = hooks.tickScheduler().schedule(
                () -> hooks.tickCallback().tick(ref[0], leaderCharId, agentCharId),
                hooks.tickMs());
        BotEntry entry = new BotEntry(agent, leader, task);
        ref[0] = entry;

        AgentBotMovementStateRuntime.refreshMovementProfile(entry, agent);
        AgentNavigationGraphService.warmGraphAsync(agent.getMap(), AgentBotMovementStateRuntime.movementProfile(entry));
        entries.add(entry);

        AgentFormationService.FormationState formation = AgentFormationService.stateForLeader(
                AgentFormationService.formationsByLeaderId(), leaderCharId, hooks.defaultFormation());
        AgentFormationService.applyOffsets(entries, formation);

        if (normalizeSpawnState) {
            hooks.normalizeSpawnedAgent().accept(entry);
        }
        AgentBotManagerStatusRuntime.scheduleSpawnStatusCheck(entry, agent, hooks.spawnStatusDelayMs().getAsLong());
        return entry;
    }

    public static void removeLeaderEntries(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                           Map<Integer, ?> leaderFormations,
                                           Map<Integer, ?> townClusterAnchors,
                                           int leaderCharId,
                                           Consumer<BotEntry> beforeRemove) {
        List<BotEntry> entries = entriesByLeaderId.remove(leaderCharId);
        if (entries != null) {
            for (BotEntry entry : entries) {
                beforeRemove.accept(entry);
            }
        }
        leaderFormations.remove(leaderCharId);
        townClusterAnchors.remove(leaderCharId);
    }

    public static boolean removeAgentByCharacterId(Map<Integer, List<BotEntry>> entriesByLeaderId,
                                                   Map<Integer, ?> leaderFormations,
                                                   Map<Integer, ?> townClusterAnchors,
                                                   int agentCharId,
                                                   Consumer<BotEntry> beforeRemove) {
        boolean removed = false;
        for (Map.Entry<Integer, List<BotEntry>> leaderEntry : entriesByLeaderId.entrySet()) {
            List<BotEntry> entries = leaderEntry.getValue();
            boolean removedFromLeader = entries.removeIf(entry -> {
                if (AgentBotRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
                    beforeRemove.accept(entry);
                    return true;
                }
                return false;
            });
            if (removedFromLeader) {
                removed = true;
                if (entries.isEmpty() && entriesByLeaderId.remove(leaderEntry.getKey(), entries)) {
                    leaderFormations.remove(leaderEntry.getKey());
                    townClusterAnchors.remove(leaderEntry.getKey());
                }
            }
        }
        return removed;
    }
}
