package server.agents.runtime;

import client.Character;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.auth.AgentAuthorizationResult;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.integration.AgentClientGatewayRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.ScheduledFuture;

/**
 * Agent-owned lifecycle map mutation helpers over the Agent runtime store.
 * Runtime side effects such as scheduled-task canceling are supplied through
 * Agent-owned hook bundles while AgentRuntimeEntry is the backing session object.
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
                             Consumer<AgentRuntimeEntry> startFollowLeader,
                             ChangeMapToSpawn changeMapToSpawn) {
    }

    @FunctionalInterface
    public interface RegisterSpawnedAgent {
        AgentRuntimeEntry register(int leaderCharId, Character leader, Character agent);
    }

    @FunctionalInterface
    public interface OfflineAgentLoader {
        Character load(int charId, int world, int channel, MapleMap targetMap, Point desiredPosition) throws SQLException;
    }

    @FunctionalInterface
    public interface PlaceSpawnedOnlineAgent {
        void place(AgentRuntimeEntry entry, Character agent, MapleMap spawnMap, Point spawnPosition);
    }

    @FunctionalInterface
    public interface ChangeMapToSpawn {
        void change(Character agent, MapleMap spawnMap, Point spawnPosition);
    }

    @FunctionalInterface
    public interface SpawnFailureLogger {
        void log(String agentName, Character leader, SQLException exception);
    }

    public record RegisterHooks(long tickMs,
                                AgentTickScheduler tickScheduler,
                                AgentTickCallback tickCallback,
                                Consumer<AgentRuntimeEntry> cancelExistingTask,
                                AgentFormationService.FormationState defaultFormation,
                                Consumer<AgentRuntimeEntry> normalizeSpawnedAgent,
                                LongSupplier spawnStatusDelayMs) {
    }

    @FunctionalInterface
    public interface AgentTickScheduler {
        ScheduledFuture<?> schedule(Runnable tick, long periodMs);
    }

    @FunctionalInterface
    public interface AgentTickCallback {
        void tick(AgentRuntimeEntry entry, int leaderCharId, int agentCharId);
    }

    public record ReloginHooks(LeaderResolver resolveLeader,
                               BiFunction<MapleMap, Point, Point> resolveSpawnPosition,
                               OfflineAgentLoader loadOfflineAgent,
                               RegisterSpawnedAgent registerSpawnedAgent,
                               SessionDelayedActionScheduler delayedActionScheduler,
                               LongSupplier returnAnnouncementDelayMs,
                               AgentMapSpeaker mapSpeaker) {
    }

    @FunctionalInterface
    public interface LeaderResolver {
        Character resolve(int world, int leaderCharId);
    }

    @FunctionalInterface
    public interface DelayedActionScheduler {
        void schedule(long delayMs, Runnable action);
    }

    @FunctionalInterface
    public interface SessionDelayedActionScheduler {
        void schedule(AgentRuntimeEntry entry, long delayMs, Runnable action);
    }

    @FunctionalInterface
    public interface AgentMapSpeaker {
        void say(Character agent, String text);
    }

    @FunctionalInterface
    public interface ReloginFailureLogger {
        void log(int agentCharId, SQLException exception);
    }

    public record DismissHooks(Consumer<AgentRuntimeEntry> cancelTask,
                               Consumer<AgentRuntimeEntry> stopAgent,
                               DelayedActionScheduler delayedActionScheduler,
                               LongSupplier farewellDelayMs,
                               AgentEntrySpeaker entrySpeaker,
                               FarewellMessageSupplier farewellMessageSupplier) {
    }

    @FunctionalInterface
    public interface AgentEntrySpeaker {
        void say(AgentRuntimeEntry entry, String text);
    }

    @FunctionalInterface
    public interface FarewellMessageSupplier {
        String message();
    }

    private AgentLifecycleService() {
    }

    public static AgentSpawnResult spawnAgentForLeader(Character leader,
                                                       String agentName,
                                                       AgentOwnershipService ownershipService,
                                                       SpawnHooks hooks) throws SQLException {
        return spawnAgentForLeaderAt(
                leader, agentName, leader.getMap(), leader.getPosition(), ownershipService, hooks, false);
    }

    public static AgentSpawnResult spawnAgentForLeaderAt(Character leader,
                                                         String agentName,
                                                         MapleMap spawnMap,
                                                         Point desiredSpawnPosition,
                                                         AgentOwnershipService ownershipService,
                                                         SpawnHooks hooks) throws SQLException {
        return spawnAgentForLeaderAt(
                leader, agentName, spawnMap, desiredSpawnPosition, ownershipService, hooks, true);
    }

    private static AgentSpawnResult spawnAgentForLeaderAt(Character leader,
                                                          String agentName,
                                                          MapleMap spawnMap,
                                                          Point desiredSpawnPosition,
                                                          AgentOwnershipService ownershipService,
                                                          SpawnHooks hooks,
                                                          boolean changeMapBeforeRegistration) throws SQLException {
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

        MapleMap map = spawnMap;
        Point spawnPosition = hooks.resolveSpawnPosition().apply(map, desiredSpawnPosition);
        if (resolved.isOnline()) {
            Character agent = resolved.onlineCharacter();
            Character activeLeader = AgentRuntimeRegistry.activeLeaderByAgentCharacterId(agent.getId());
            if (activeLeader != null && activeLeader.getId() != leader.getId()) {
                return AgentSpawnResult.fail("Bot '" + agentName + "' is controlled by " + activeLeader.getName() + ".");
            }

            if (changeMapBeforeRegistration && agent.getMapId() != map.getId()) {
                hooks.changeMapToSpawn().change(agent, map, spawnPosition);
            }
            AgentRuntimeEntry entry = activeLeader == null
                    ? hooks.registerSpawnedAgent().register(leader.getId(), leader, agent)
                    : AgentRuntimeRegistry.findByCharacterId(leader.getId(), agent.getId());
            if (!changeMapBeforeRegistration && agent.getMapId() != map.getId()) {
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
                AgentClientGatewayRuntime.clients().world(leader),
                AgentClientGatewayRuntime.clients().channel(leader),
                map,
                spawnPosition);
        AgentRuntimeEntry entry = hooks.registerSpawnedAgent().register(leader.getId(), leader, agent);
        hooks.startFollowLeader().accept(entry);
        return AgentSpawnResult.ok(agent, auth.autoRegistered());
    }

    public static AgentSpawnResult spawnAgentForLeaderQuietly(Character leader,
                                                             String agentName,
                                                             AgentOwnershipService ownershipService,
                                                             SpawnHooks hooks,
                                                             SpawnFailureLogger failureLogger) {
        try {
            return spawnAgentForLeader(leader, agentName, ownershipService, hooks);
        } catch (SQLException e) {
            failureLogger.log(agentName, leader, e);
            return AgentSpawnResult.fail("Failed to load bot character '" + agentName + "'.");
        }
    }

    public static AgentSpawnResult spawnAgentForLeaderAtQuietly(Character leader,
                                                               String agentName,
                                                               MapleMap spawnMap,
                                                               Point desiredSpawnPosition,
                                                               AgentOwnershipService ownershipService,
                                                               SpawnHooks hooks,
                                                               SpawnFailureLogger failureLogger) {
        try {
            return spawnAgentForLeaderAt(
                    leader, agentName, spawnMap, desiredSpawnPosition, ownershipService, hooks);
        } catch (SQLException e) {
            failureLogger.log(agentName, leader, e);
            return AgentSpawnResult.fail("Failed to load bot character '" + agentName + "'.");
        }
    }

    public static AgentRuntimeEntry registerAgent(int leaderCharId,
                                                  Character leader,
                                                  Character agent,
                                                  boolean normalizeSpawnState,
                                                  RegisterHooks hooks) {
        List<AgentRuntimeEntry> entries = AgentRuntimeRegistry.mutableEntriesForLeader(leaderCharId);
        List<AgentRuntimeEntry> replacedEntries = new ArrayList<>();
        for (AgentRuntimeEntry existingEntry : entries) {
            if (AgentRuntimeIdentityRuntime.botIs(existingEntry, agent.getId())) {
                replacedEntries.add(existingEntry);
            }
        }
        entries.removeAll(replacedEntries);

        int agentCharId = agent.getId();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentMovementStateRuntime.refreshMovementProfile(entry, agent);
        AgentNavigationGraphService.warmGraphAsync(agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        entries.add(entry);

        AgentFormationService.FormationState formation = AgentFormationService.stateForLeader(
                AgentFormationService.formationsByLeaderId(), leaderCharId, hooks.defaultFormation());
        AgentFormationService.applyOffsets(entries, formation);

        if (normalizeSpawnState) {
            hooks.normalizeSpawnedAgent().accept(entry);
        }

        try {
            ScheduledFuture<?> task = AgentTickSchedulingService.register(
                    entry,
                    () -> hooks.tickCallback().tick(entry, leaderCharId, agentCharId),
                    hooks.tickMs(),
                    hooks.tickScheduler());
            entry.scheduledTaskState().attachScheduledTask(task);
        } catch (RuntimeException | Error failure) {
            entries.remove(entry);
            entries.addAll(replacedEntries);
            AgentFormationService.applyOffsets(entries, formation);
            if (entries.isEmpty()) {
                AgentRuntimeRegistry.entriesByLeaderId().remove(leaderCharId, entries);
            }
            throw failure;
        }

        replacedEntries.forEach(hooks.cancelExistingTask());
        AgentLifecycleStatusCoordinator.scheduleSpawnStatusCheck(
                entry, agent, hooks.spawnStatusDelayMs().getAsLong());
        return entry;
    }

    public static boolean reloginAgent(int agentCharId,
                                       int leaderCharId,
                                       int world,
                                       int channel,
                                       ReloginHooks hooks) throws SQLException {
        Character leader = hooks.resolveLeader().resolve(world, leaderCharId);
        if (leader == null) {
            return false;
        }

        MapleMap map = leader.getMap();
        Point spawnPosition = hooks.resolveSpawnPosition().apply(map, leader.getPosition());
        Character agent = hooks.loadOfflineAgent().load(agentCharId, world, channel, map, spawnPosition);
        AgentRuntimeEntry entry = hooks.registerSpawnedAgent().register(leaderCharId, leader, agent);
        hooks.delayedActionScheduler().schedule(entry, hooks.returnAnnouncementDelayMs().getAsLong(), () -> {
            hooks.mapSpeaker().say(agent, "back!!");
            agent.changeFaceExpression(AgentEmote.HAPPY.getValue());
        });
        return true;
    }

    public static boolean reloginAgentQuietly(int agentCharId,
                                              int leaderCharId,
                                              int world,
                                              int channel,
                                              ReloginHooks hooks,
                                              ReloginFailureLogger failureLogger) {
        try {
            return reloginAgent(agentCharId, leaderCharId, world, channel, hooks);
        } catch (SQLException e) {
            failureLogger.log(agentCharId, e);
            return false;
        }
    }

    public static boolean dismissAgentByName(int leaderCharId, String agentName, DismissHooks hooks) {
        List<AgentRuntimeEntry> entries = AgentRuntimeRegistry.entriesByLeaderId().get(leaderCharId);
        if (entries == null) {
            return false;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(leaderCharId, agentName);
        if (entry == null) {
            return false;
        }

        entries.remove(entry);
        hooks.cancelTask().accept(entry);
        hooks.stopAgent().accept(entry);
        hooks.delayedActionScheduler().schedule(hooks.farewellDelayMs().getAsLong(), () ->
                hooks.entrySpeaker().say(entry, hooks.farewellMessageSupplier().message()));
        return true;
    }

    public static void removeLeaderEntries(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                           Map<Integer, ?> leaderFormations,
                                           Map<Integer, ?> townClusterAnchors,
                                           int leaderCharId,
                                           Consumer<AgentRuntimeEntry> beforeRemove) {
        List<AgentRuntimeEntry> entries = entriesByLeaderId.remove(leaderCharId);
        if (entries != null) {
            for (AgentRuntimeEntry entry : entries) {
                beforeRemove.accept(entry);
            }
        }
        leaderFormations.remove(leaderCharId);
        townClusterAnchors.remove(leaderCharId);
    }

    public static void cancelScheduledTickIfPresent(AgentRuntimeEntry entry) {
        if (entry != null) {
            if (entry.scheduledTaskState().hasScheduledTask()) {
                entry.scheduledTaskState().cancelScheduledTask();
            }
            entry.scheduledTaskScope().cancelAll();
            AgentMailboxRuntime.close(entry);
        }
    }

    public static boolean removeAgentByCharacterId(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                                   Map<Integer, ?> leaderFormations,
                                                   Map<Integer, ?> townClusterAnchors,
                                                   int agentCharId,
                                                   Consumer<AgentRuntimeEntry> beforeRemove) {
        boolean removed = false;
        for (Map.Entry<Integer, List<AgentRuntimeEntry>> leaderEntry : entriesByLeaderId.entrySet()) {
            List<AgentRuntimeEntry> entries = leaderEntry.getValue();
            boolean removedFromLeader = entries.removeIf(entry -> {
                if (AgentRuntimeIdentityRuntime.botIs(entry, agentCharId)) {
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

    public static boolean removeAgentEntry(Map<Integer, List<AgentRuntimeEntry>> entriesByLeaderId,
                                           Map<Integer, ?> leaderFormations,
                                           Map<Integer, ?> townClusterAnchors,
                                           AgentRuntimeEntry expectedEntry,
                                           Consumer<AgentRuntimeEntry> beforeRemove) {
        if (expectedEntry == null) {
            return false;
        }
        for (Map.Entry<Integer, List<AgentRuntimeEntry>> leaderEntry : entriesByLeaderId.entrySet()) {
            List<AgentRuntimeEntry> entries = leaderEntry.getValue();
            if (!entries.remove(expectedEntry)) {
                continue;
            }
            beforeRemove.accept(expectedEntry);
            if (entries.isEmpty() && entriesByLeaderId.remove(leaderEntry.getKey(), entries)) {
                leaderFormations.remove(leaderEntry.getKey());
                townClusterAnchors.remove(leaderEntry.getKey());
            }
            return true;
        }
        return false;
    }
}
