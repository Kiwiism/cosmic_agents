package server.agents.plans.mapleisland.cohort;

import client.Character;
import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.capabilities.quest.AmherstTestResetMode;
import server.agents.capabilities.quest.AmherstTestResetRequest;
import server.agents.capabilities.quest.AmherstTestResetResult;
import server.agents.capabilities.quest.AmherstTestResetService;
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentBackingAccountSecurity;
import server.agents.integration.cosmic.CosmicAgentOfflineLoader;
import server.agents.integration.cosmic.CosmicCharacterGateway;
import server.agents.integration.cosmic.CosmicMapleIslandCohortProvisioning;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.mapleisland.AgentMapleIslandPlanRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.async.AgentAsyncExecutorRegistry;
import server.agents.runtime.async.AgentAsyncWorkKind;
import server.maps.MapleMap;

import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** Process-level composition root for the persistent pool and one cohort per world/channel. */
public final class MapleIslandCohortRuntime {
    private static final Logger log = LoggerFactory.getLogger(MapleIslandCohortRuntime.class);
    private static final String POOL_FILE_ENV = "COSMIC_MAPLE_ISLAND_COHORT_POOL_FILE";
    private static MapleIslandCohortRuntime instance;

    private final MapleIslandCohortPoolService pool;
    private final MapleIslandCohortRunService runs;

    private MapleIslandCohortRuntime() throws IOException {
        MapleIslandCohortPoolRegistry registry = new MapleIslandCohortPoolRegistry(
                new FileMapleIslandCohortPoolStore(poolPath()));
        MapleIslandCohortPoolProvisioner provisioner = new MapleIslandCohortPoolProvisioner(
                registry, CosmicMapleIslandCohortProvisioning.INSTANCE);
        pool = new MapleIslandCohortPoolService(registry, provisioner,
                MapleIslandCohortRuntime::isCharacterLive);
        runs = new MapleIslandCohortRunService(liveHooks());
        int recovered = pool.recoverStaleLeases(Set.of());
        if (recovered > 0) {
            log.info("Recovered {} stale Maple Island cohort pool lease(s)", recovered);
        }
    }

    public static synchronized MapleIslandCohortRuntime instance() {
        if (instance == null) {
            try {
                instance = new MapleIslandCohortRuntime();
            } catch (IOException failure) {
                throw new IllegalStateException("Maple Island cohort pool is unavailable", failure);
            }
        }
        return instance;
    }

    public MapleIslandCohortRunService.Status start(MapleIslandCohortRunService.StartRequest request)
            throws IOException {
        if (!YamlConfig.config.server.AGENT_MAPLE_ISLAND_COHORT_ENABLED) {
            throw new IllegalStateException("Maple Island cohort provisioning and runs are disabled");
        }
        if (!YamlConfig.config.server.AGENT_MAPLE_ISLAND_SHOWCASE_ENABLED) {
            throw new IllegalStateException("Maple Island showcase runs are disabled");
        }
        pool.recoverStaleLeases(runs.activeSessionIds());
        return runs.start(request);
    }

    public MapleIslandCohortRunService.Status status(int world, int channel) {
        return runs.status(world, channel);
    }

    public MapleIslandCohortRunService.Status cancel(int world, int channel) {
        return runs.cancel(world, channel);
    }

    public MapleIslandCohortRunService.Status stop(int world, int channel) {
        return runs.stop(world, channel);
    }

    public MapleIslandCohortPoolRegistry.Stats poolStats() {
        return pool.stats();
    }

    public MapleIslandCohortPoolSnapshot poolSnapshot() {
        return pool.snapshot();
    }

    private MapleIslandCohortRunService.Hooks liveHooks() {
        return new MapleIslandCohortRunService.Hooks() {
            @Override
            public List<MapleIslandCohortPoolSnapshot.Agent> acquire(
                    int count,
                    String sessionId,
                    int ownerCharacterId,
                    int world,
                    int channel,
                    Set<Integer> excludedCharacterIds) throws Exception {
                return pool.acquire(count, sessionId, ownerCharacterId, world, channel, excludedCharacterIds);
            }

            @Override
            public void startAgent(MapleIslandCohortPoolSnapshot.Agent pooled,
                                   MapleIslandCohortRunService.AgentContext context) throws Exception {
                startPooledAgent(pooled, context);
            }

            @Override
            public void markBroken(MapleIslandCohortPoolSnapshot.Agent pooled,
                                   String sessionId,
                                   String error) throws Exception {
                pool.markBroken(pooled.characterId(), sessionId, error);
            }

            @Override
            public MapleIslandCohortRunService.AgentState agentState(int characterId) {
                return liveAgentState(characterId);
            }

            @Override
            public void stopAgent(int characterId) {
                stopPooledAgent(characterId);
            }

            @Override
            public void releaseSession(String sessionId) throws Exception {
                pool.releaseSession(sessionId);
            }

            @Override
            public java.util.concurrent.ScheduledFuture<?> schedule(Runnable action, long delayMs) {
                return AgentSchedulerRuntime.schedule(action, delayMs);
            }

            @Override
            public void dispatch(Runnable action) {
                AgentAsyncExecutorRegistry.runtime().execute(AgentAsyncWorkKind.MAPLE_ISLAND_COHORT, action);
            }
        };
    }

    private void startPooledAgent(MapleIslandCohortPoolSnapshot.Agent pooled,
                                  MapleIslandCohortRunService.AgentContext context) throws Exception {
        verifyLeasedDedicatedPoolAgent(pooled, context.sessionId(), context.world());
        if (isCharacterLive(pooled.characterId())) {
            throw new IllegalStateException("Pooled Agent is already online: " + pooled.name());
        }

        MapleMap startMap = AgentMapGatewayRuntime.map().resolveMap(
                context.world(), context.channel(), AmherstQuestCatalog.START_MAP_ID);
        Point startPosition = startMap.getPortal(0) == null
                ? new Point(startMap.getRandomPlayerSpawnpoint().getPosition())
                : new Point(startMap.getPortal(0).getPosition());
        Character agent = null;
        try {
            agent = CosmicAgentOfflineLoader.loadOfflineAgent(
                    pooled.characterId(), context.world(), context.channel(), startMap, startPosition);
            AgentRuntimeEntry entry = AgentInteractionRuntime.registerSelfDirectedAgent(agent);
            AgentMovementCommandRuntime.stop(entry);
            AgentPartyLifecycleService.leaveAgentParty(agent);

            AmherstTestResetResult reset = AmherstTestResetService
                    .showcaseHarness(true, pooled.name())
                    .reset(new AmherstTestResetRequest(
                            pooled.characterId(), pooled.name(), AmherstTestResetMode.CLEAN_LV1_START, 0));
            if (!reset.allowed()) {
                throw new IllegalStateException("Clean level-1 reset blocked: " + reset.message());
            }
            if (!AgentMapleIslandPlanRuntime.clearSession(entry)) {
                throw new IllegalStateException("The previous pooled Agent capability is still closing");
            }
            AmherstPlanCard card = AgentMapleIslandPlanRuntime.fullCard();
            AgentMapleIslandPlanRuntime.defaultStore().delete(card.planId(), agent.getId());
            MapleIslandCohortEntrySetup.apply(entry, context);
            AgentMapleIslandPlanRuntime.startFullAuto(entry, agent, System.currentTimeMillis(), event ->
                    log.debug("Maple Island cohort session={} agent={} {}",
                            context.sessionId(), pooled.name(), event));
            pool.markActive(pooled.characterId(), context.sessionId(), System.currentTimeMillis());
        } catch (Exception | Error failure) {
            if (agent != null) {
                AgentRuntimeCleanupService.removeAgentByCharacterId(agent.getId());
                if (agent.getClient() != null) {
                    agent.getClient().forceDisconnect();
                }
            }
            throw failure;
        }
    }

    private void verifyLeasedDedicatedPoolAgent(MapleIslandCohortPoolSnapshot.Agent pooled,
                                                String sessionId,
                                                int requestedWorld) throws Exception {
        MapleIslandCohortPoolSnapshot.Agent durable = pool.snapshot().agents().stream()
                .filter(agent -> agent.characterId() == pooled.characterId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Character is not in the persisted cohort pool"));
        if (durable.leaseState() != MapleIslandCohortPoolSnapshot.LeaseState.LEASED
                || !durable.leaseSessionId().equals(sessionId)
                || !durable.name().equalsIgnoreCase(pooled.name())
                || durable.accountId() != pooled.accountId()
                || !durable.accountName().equalsIgnoreCase(pooled.accountName())) {
            throw new IllegalStateException("Character does not hold this cohort session lease");
        }
        if (durable.world() != requestedWorld
                || !CosmicMapleIslandCohortProvisioning.INSTANCE.characterIdentityMatches(
                durable.characterId(), durable.name(), durable.accountId(), durable.accountName(), durable.world())) {
            throw new IllegalStateException(
                    "Persisted pool identity does not match the requested world or database character/account mapping");
        }
        if (!CosmicAgentBackingAccountSecurity.isAgentOnlyAccount(durable.accountId())) {
            throw new IllegalStateException("Refusing to reset a character outside a dedicated Agent-only account");
        }
    }

    private static MapleIslandCohortRunService.AgentState liveAgentState(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        if (entry == null) {
            return MapleIslandCohortRunService.AgentState.MISSING;
        }
        var state = entry.amherstPlanExecutionState();
        if (state.completed()) {
            return MapleIslandCohortRunService.AgentState.COMPLETED;
        }
        if (state.active()) {
            return MapleIslandCohortRunService.AgentState.RUNNING;
        }
        return state.progress() == null && state.lastError().isBlank()
                ? MapleIslandCohortRunService.AgentState.RUNNING
                : MapleIslandCohortRunService.AgentState.FAILED;
    }

    private static void stopPooledAgent(int characterId) {
        Character agent = CosmicCharacterGateway.INSTANCE.findOnlineCharacterById(characterId);
        AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
        if (agent != null && agent.getClient() != null) {
            agent.getClient().forceDisconnect();
        }
    }

    private static boolean isCharacterLive(int characterId) {
        return AgentRuntimeRegistry.hasActiveAgentCharacterId(characterId)
                || CosmicCharacterGateway.INSTANCE.findOnlineCharacterById(characterId) != null;
    }

    static Path poolPath() {
        String configured = System.getenv(POOL_FILE_ENV);
        return configured == null || configured.isBlank()
                ? Path.of(".runtime", "agents", "maple-island-cohort-pool.json")
                : Path.of(configured);
    }
}
