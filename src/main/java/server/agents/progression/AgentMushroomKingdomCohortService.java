package server.agents.progression;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** GM observation harness for one Agent on each Explorer second-job branch. */
public final class AgentMushroomKingdomCohortService {
    private static final Logger log = LoggerFactory.getLogger(AgentMushroomKingdomCohortService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final long SPAWN_STAGGER_MS = 450L;
    private static final List<CohortMember> ROSTER = List.of(
            new CohortMember("SporeFighter", "fighter"),
            new CohortMember("PepePage", "page"),
            new CohortMember("ThornSpear", "spearman"),
            new CohortMember("EmberSpore", "fp-wizard"),
            new CohortMember("FrostSpore", "il-wizard"),
            new CohortMember("HealShroom", "cleric"),
            new CohortMember("ViolettaBow", "hunter"),
            new CohortMember("PepeBolt", "crossbowman"),
            new CohortMember("ShadowSpore", "assassin"),
            new CohortMember("RoyalBandit", "bandit"),
            new CohortMember("MushBrawler", "brawler"),
            new CohortMember("SporeGunner", "gunslinger"));
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();

    private AgentMushroomKingdomCohortService() { }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        String action = params == null || params.length == 0 ? "help" : params[0].toLowerCase();
        try {
            return switch (action) {
                case "start" -> start(operator, seed(params, nowMs), nowMs);
                case "status" -> status(operator);
                case "stop" -> stop(operator);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("Mushroom Kingdom cohort command failed", failure);
            return List.of("Mushroom Kingdom cohort failed: " + failure.getMessage());
        }
    }

    private static List<String> start(Character operator, long seed, long nowMs) throws Exception {
        ArrayList<String> response = new ArrayList<>();
        if (RUNS.containsKey(operator.getId())) response.addAll(stop(operator));
        for (CohortMember member : ROSTER) {
            String failure = PROVISIONING.ensureBackingCharacter(operator, member.name());
            if (failure != null) return List.of(failure);
        }
        Run run = new Run(operator, seed);
        RUNS.put(operator.getId(), run);
        for (int ordinal = 0; ordinal < ROSTER.size(); ordinal++) {
            CohortMember member = ROSTER.get(ordinal);
            int index = ordinal;
            AgentSchedulerRuntime.schedule(() -> launch(run, member, index), SPAWN_STAGGER_MS * ordinal);
        }
        response.add("Launching 12 level-30 Mushroom Kingdom Agents (seed " + seed + ").");
        response.add("Six male and six female Agents cover: "
                + ROSTER.stream().map(CohortMember::branchId).toList());
        response.add("Use !mushroomtest status or !mushroomtest stop.");
        return response;
    }

    private static void launch(Run run, CohortMember member, int ordinal) {
        if (RUNS.get(run.operator.getId()) != run) return;
        Character launched = null;
        try {
            MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                    run.operator.getWorld(), AgentClientGatewayRuntime.clients().channel(run.operator),
                    AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID);
            Point point = spawnPoint(map, ordinal);
            AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                    .spawnStationaryAgentForLeaderAt(run.operator, member.name(), map, point);
            if (!result.success()) throw new IllegalStateException(result.errorMessage());
            launched = result.agent();
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(launched.getId());
            if (entry == null) throw new IllegalStateException("spawned Agent runtime is unavailable");
            AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(member.branchId());
            AgentMushroomKingdomFixtureService.Prepared prepared =
                    AgentMushroomKingdomFixtureService.prepare(
                            entry, branch, ordinal, mix(run.seed, ordinal), System.currentTimeMillis());
            AgentMapGatewayRuntime.map().changeMapNear(launched, map, point);
            AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(entry, launched);
            if (!AgentUniversalPlanRuntime.start(entry, launched, "mushroom-kingdom-questline",
                    AgentPlanStartRequest.EMPTY, System.currentTimeMillis())) {
                throw new IllegalStateException("Mushroom Kingdom plan admission was rejected");
            }
            synchronized (run) {
                run.agentIds.put(member.name(), launched.getId());
                run.prepared.put(member.name(), prepared);
            }
        } catch (Exception failure) {
            if (launched != null) disconnect(launched.getId());
            synchronized (run) { run.failures.put(member.name(), failure.getMessage()); }
            log.warn("Could not launch Mushroom Kingdom fixture {}", member.name(), failure);
        }
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No Mushroom Kingdom cohort is active.");
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Mushroom Kingdom cohort: " + run.agentIds.size() + "/12 launched, seed " + run.seed + '.');
        synchronized (run) {
            for (CohortMember member : ROSTER) {
                Integer id = run.agentIds.get(member.name());
                if (id == null) {
                    lines.add(member.name() + " [" + member.branchId() + "]: "
                            + run.failures.getOrDefault(member.name(), "launch pending"));
                    continue;
                }
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
                Character agent = entry == null ? null : entry.bot();
                AgentMushroomKingdomState state = entry == null ? null : entry.capabilityStates()
                        .find(AgentMushroomKingdomState.STATE_KEY).orElse(null);
                AgentMushroomKingdomFixtureService.Prepared fixture = run.prepared.get(member.name());
                lines.add(member.name() + " [" + member.branchId() + ", "
                        + (fixture == null ? "preparing" : fixture.apProfileId()) + "]: "
                        + (agent == null ? "offline" : "map " + agent.getMapId()) + ", "
                        + (state == null ? "plan starting" : state.phase() + " q" + state.currentQuestId()
                        + " - " + state.reason()));
            }
        }
        return lines;
    }

    private static List<String> stop(Character operator) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No Mushroom Kingdom cohort is active.");
        List<Integer> ids;
        synchronized (run) { ids = List.copyOf(run.agentIds.values()); }
        ids.forEach(AgentMushroomKingdomCohortService::disconnect);
        return List.of("Stopped the Mushroom Kingdom cohort; backing characters were retained.");
    }

    private static void disconnect(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : entry.bot();
        AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }

    private static Point spawnPoint(MapleMap map, int ordinal) {
        Point base = map.getPortal(0) == null ? new Point(0, 0) : map.getPortal(0).getPosition();
        Point candidate = new Point(base.x + ((ordinal % 6) - 2) * 42, base.y);
        Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
        return grounded == null ? new Point(base) : grounded;
    }

    private static long seed(String[] params, long fallback) {
        if (params != null && params.length > 1) {
            try { return Long.parseLong(params[1]); }
            catch (NumberFormatException ignored) { }
        }
        return fallback;
    }

    private static long mix(long seed, long value) {
        return seed ^ (value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2));
    }

    private static List<String> help() {
        return List.of("!mushroomtest start [seed] - launch one level-30 Agent per Explorer second job",
                "!mushroomtest status - show each branch, map, quest, and runtime reason",
                "!mushroomtest stop - disconnect the cohort and retain backing characters");
    }

    /** Stable, read-only fixture contract for tooling and regression tests. */
    public static List<CohortMember> roster() { return ROSTER; }

    public record CohortMember(String name, String branchId) { }

    private static final class Run {
        private final Character operator;
        private final long seed;
        private final Map<String, Integer> agentIds = new LinkedHashMap<>();
        private final Map<String, AgentMushroomKingdomFixtureService.Prepared> prepared = new LinkedHashMap<>();
        private final Map<String, String> failures = new LinkedHashMap<>();

        private Run(Character operator, long seed) {
            this.operator = operator;
            this.seed = seed;
        }
    }
}
