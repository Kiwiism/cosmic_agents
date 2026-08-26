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
import server.life.NPC;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** GM observation harness for real Explorer second-job advancements. */
public final class AgentSecondJobCohortService {
    private static final Logger log = LoggerFactory.getLogger(AgentSecondJobCohortService.class);
    private static final int HENESYS_MAP_ID = 100_000_000;
    // Fixture preparation persists a full character snapshot. Keep those transactions serial in
    // practice; overlapping five saves can deadlock MySQL's quest-progress replacement work.
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentSecondJobCohortService.SPAWN_STAGGER_MS");
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final List<CohortMember> ALL_BRANCH_ROSTER = List.of(
            new CohortMember("JobFighter", "fighter"),
            new CohortMember("JobPage", "page"),
            new CohortMember("JobSpearman", "spearman"),
            new CohortMember("JobFPWizard", "fp-wizard"),
            new CohortMember("JobILWizard", "il-wizard"),
            new CohortMember("JobCleric", "cleric"),
            new CohortMember("JobHunter", "hunter"),
            new CohortMember("JobCrossbow", "crossbowman"),
            new CohortMember("JobAssassin", "assassin"),
            new CohortMember("JobBandit", "bandit"),
            new CohortMember("JobBrawler", "brawler"),
            new CohortMember("JobGunner", "gunslinger"));
    private static final List<CohortMember> DEFAULT_ROSTER = members(
            "fighter", "cleric", "hunter", "assassin", "gunslinger");
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();

    private AgentSecondJobCohortService() { }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        String action = params == null || params.length == 0 ? "help" : params[0].toLowerCase();
        try {
            return switch (action) {
                case "start" -> {
                    StartSelection selection = parseStart(params, nowMs);
                    yield start(operator, selection.seed(), selection.roster(), false, nowMs);
                }
                case "startfor" -> {
                    StartSelection selection = parseNamedStart(params, nowMs);
                    yield start(operator, selection.seed(), selection.roster(), true, nowMs);
                }
                case "status" -> status(operator);
                case "stage" -> stage(operator, params);
                case "stop" -> stop(operator);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("Second-job cohort command failed", failure);
            return List.of("Second-job cohort failed: " + failure.getMessage());
        }
    }

    private static List<String> start(Character operator, long seed, List<CohortMember> roster,
                                      boolean preserveAppearance, long nowMs) throws Exception {
        if (RUNS.containsKey(operator.getId())) stop(operator);
        if (roster.isEmpty()) return List.of("No matching second-job branches were requested.");
        for (CohortMember member : roster) {
            String failure = PROVISIONING.ensureBackingCharacter(operator, member.name());
            if (failure != null) return List.of(failure);
        }
        Run run = new Run(operator, seed, roster, preserveAppearance);
        RUNS.put(operator.getId(), run);
        for (int ordinal = 0; ordinal < roster.size(); ordinal++) {
            CohortMember member = roster.get(ordinal);
            int index = ordinal;
            AgentSchedulerRuntime.schedule(() -> launch(run, member, index), SPAWN_STAGGER_MS * ordinal);
        }
        return List.of("Launching " + roster.size() + " level-30 first-job Agent(s) in Henesys (seed "
                        + seed + ").",
                "Pass condition: every requested Agent reaches its selected second-job ID.",
                "Use !secondjobtest status or !secondjobtest stop.");
    }

    private static void launch(Run run, CohortMember member, int ordinal) {
        if (RUNS.get(run.operator.getId()) != run) return;
        Character launched = null;
        try {
            MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                    run.operator.getWorld(), AgentClientGatewayRuntime.clients().channel(run.operator),
                    HENESYS_MAP_ID);
            Point point = spawnPoint(map, ordinal);
            AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                    .spawnStationaryAgentForLeaderAt(run.operator, member.name(), map, point);
            if (!result.success()) throw new IllegalStateException(result.errorMessage());
            launched = result.agent();
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(launched.getId());
            if (entry == null) throw new IllegalStateException("spawned Agent runtime is unavailable");
            AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(member.branchId());
            AgentMushroomKingdomFixtureService.Prepared prepared =
                    run.preserveAppearance
                            ? AgentMushroomKingdomFixtureService
                            .prepareExistingCharacterForSecondJobAdvancement(
                                    entry, branch, ordinal, mix(run.seed, ordinal),
                                    System.currentTimeMillis())
                            : AgentMushroomKingdomFixtureService.prepareForSecondJobAdvancement(
                                    entry, branch, ordinal, mix(run.seed, ordinal),
                                    System.currentTimeMillis());
            AgentMapGatewayRuntime.map().changeMapNear(launched, map, point);
            AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(entry, launched);
            AgentPlanStartRequest request = new AgentPlanStartRequest(Map.of(
                    "branch", branch.id(), "family", branch.family().name()), null);
            if (!AgentUniversalPlanRuntime.start(entry, launched, "victoria-second-job",
                    request, System.currentTimeMillis())) {
                throw new IllegalStateException("second-job plan admission was rejected");
            }
            synchronized (run) {
                run.agentIds.put(member.name(), launched.getId());
                run.prepared.put(member.name(), prepared);
            }
        } catch (Exception failure) {
            if (launched != null) disconnect(launched.getId());
            synchronized (run) { run.failures.put(member.name(), message(failure)); }
            log.warn("Could not launch second-job fixture {}", member.name(), failure);
        }
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No second-job cohort is active.");
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Second-job cohort: " + run.agentIds.size() + '/' + run.roster.size()
                + " launched, seed " + run.seed + '.');
        synchronized (run) {
            for (CohortMember member : run.roster) {
                AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(member.branchId());
                Integer id = run.agentIds.get(member.name());
                if (id == null) {
                    lines.add(member.name() + " [" + member.branchId() + "]: "
                            + run.failures.getOrDefault(member.name(), "launch pending"));
                    continue;
                }
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
                Character agent = entry == null ? null : entry.bot();
                AgentSecondJobAdvancementState state = entry == null ? null : entry.capabilityStates()
                        .find(AgentSecondJobAdvancementState.STATE_KEY).orElse(null);
                if (agent == null) {
                    lines.add(member.name() + " [" + member.branchId() + "]: offline");
                    continue;
                }
                boolean passed = agent.getJob().getId() == branch.targetJobId();
                int items = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .itemCount(agent, branch.collectionItemId());
                int liveTargets = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .liveMonsterCount(agent, branch.trialMobIds());
                lines.add(member.name() + " [" + branch.firstJobId() + "->" + branch.targetJobId()
                        + "]: " + (passed ? "PASS" : state == null ? "STARTING" : state.phase())
                        + ", map " + agent.getMapId() + ", trial items " + items + '/'
                        + branch.requiredCount() + ", live targets " + liveTargets
                        + (state == null ? "" : " - " + state.reason()));
            }
        }
        return lines;
    }

    private static List<String> stop(Character operator) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No second-job cohort is active.");
        List<Integer> ids;
        synchronized (run) { ids = List.copyOf(run.agentIds.values()); }
        ids.forEach(AgentSecondJobCohortService::disconnect);
        return List.of("Stopped the second-job cohort; backing characters were retained.");
    }

    private static List<String> stage(Character operator, String[] params) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No second-job cohort is active.");
        if (params == null || params.length < 2) {
            return List.of("Usage: !secondjobtest stage <Agent name|branch>");
        }
        String selector = params[1].toLowerCase();
        CohortMember member = run.roster.stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(selector)
                        || candidate.branchId().equalsIgnoreCase(selector))
                .findFirst().orElse(null);
        if (member == null) return List.of("No requested second-job fixture matches " + params[1] + '.');
        Integer id;
        synchronized (run) { id = run.agentIds.get(member.name()); }
        AgentRuntimeEntry entry = id == null ? null : AgentRuntimeRegistry.findByAgentCharacterId(id);
        Character agent = entry == null ? null : entry.bot();
        AgentSecondJobAdvancementState state = entry == null ? null : entry.capabilityStates()
                .find(AgentSecondJobAdvancementState.STATE_KEY).orElse(null);
        if (agent == null || state == null) return List.of(member.name() + " is not ready to stage.");
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(member.branchId());
        int mapId;
        int npcId;
        switch (state.phase()) {
            case LEADER, RETURN_TO_LEADER, VERIFY -> {
                mapId = branch.leaderMapId();
                npcId = branch.leaderNpcId();
            }
            case INSTRUCTOR -> {
                mapId = branch.instructorMapId();
                npcId = branch.instructorNpcId();
            }
            case TRIAL, EXAMINER -> {
                return List.of(member.name() + " is already in the live trial phase.");
            }
            case READY, COMPLETE, BLOCKED -> {
                return List.of(member.name() + " cannot be staged from " + state.phase() + '.');
            }
            default -> throw new IllegalStateException("Unhandled second-job phase " + state.phase());
        }
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                operator.getWorld(), AgentClientGatewayRuntime.clients().channel(operator), mapId);
        NPC npc = map.getNPCById(npcId);
        Point destination = npc == null ? spawnPoint(map, 2) : new Point(npc.getPosition());
        Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, destination);
        AgentMapGatewayRuntime.map().changeMapNear(agent, map,
                grounded == null ? destination : grounded);
        AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(entry, agent);
        state.capabilityProgress();
        return List.of("Staged " + member.name() + " near NPC " + npcId + " on map " + mapId
                + " for phase " + state.phase() + '.');
    }

    private static void disconnect(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : entry.bot();
        AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }

    private static Point spawnPoint(MapleMap map, int ordinal) {
        Point base = map.getPortal(0) == null ? new Point(0, 0) : map.getPortal(0).getPosition();
        Point candidate = new Point(base.x + (ordinal - 2) * 45, base.y);
        Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
        return grounded == null ? new Point(base) : grounded;
    }

    static StartSelection parseStart(String[] params, long fallbackSeed) {
        long seed = fallbackSeed;
        int selectorStart = 1;
        if (params != null && params.length > 1) {
            try {
                seed = Long.parseLong(params[1]);
                selectorStart = 2;
            } catch (NumberFormatException ignored) { }
        }
        if (params == null || params.length <= selectorStart) {
            return new StartSelection(seed, DEFAULT_ROSTER);
        }
        List<String> selectors = java.util.Arrays.stream(params, selectorStart, params.length)
                .map(String::toLowerCase)
                .toList();
        if (selectors.contains("all")) return new StartSelection(seed, ALL_BRANCH_ROSTER);
        List<CohortMember> roster = ALL_BRANCH_ROSTER.stream()
                .filter(member -> selectors.contains(member.branchId())
                        || selectors.contains(member.name().toLowerCase()))
                .toList();
        return new StartSelection(seed, roster);
    }

    static StartSelection parseNamedStart(String[] params, long fallbackSeed) {
        if (params == null || params.length < 3 || params[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Usage: !secondjobtest startfor <Agent name> <branch> [seed]");
        }
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(params[2]);
        long seed = fallbackSeed;
        if (params.length >= 4) seed = Long.parseLong(params[3]);
        return new StartSelection(seed, List.of(new CohortMember(params[1], branch.id())));
    }

    private static List<CohortMember> members(String... branchIds) {
        List<String> selected = List.of(branchIds);
        return ALL_BRANCH_ROSTER.stream()
                .filter(member -> selected.contains(member.branchId()))
                .toList();
    }

    private static long mix(long seed, long value) {
        return seed ^ (value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2));
    }

    private static String message(Exception failure) {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value;
    }

    private static List<String> help() {
        return List.of("!secondjobtest start [seed] [branches...] - launch one representative per family or selected branches",
                "!secondjobtest start [seed] all - launch all 12 Explorer branches",
                "!secondjobtest startfor <Agent name> <branch> [seed] - reset an existing Agent without changing its appearance",
                "!secondjobtest status - show job, map, phase, reason, and trial-item progress",
                "!secondjobtest stage <Agent name|branch> - fast-stage the current non-trial NPC phase",
                "!secondjobtest stop - disconnect the cohort and retain backing characters");
    }

    public static List<CohortMember> roster() { return DEFAULT_ROSTER; }
    public static List<CohortMember> allBranchRoster() { return ALL_BRANCH_ROSTER; }

    public record CohortMember(String name, String branchId) { }
    record StartSelection(long seed, List<CohortMember> roster) {
        StartSelection { roster = List.copyOf(roster); }
    }

    private static final class Run {
        private final Character operator;
        private final long seed;
        private final List<CohortMember> roster;
        private final boolean preserveAppearance;
        private final Map<String, Integer> agentIds = new LinkedHashMap<>();
        private final Map<String, AgentMushroomKingdomFixtureService.Prepared> prepared = new LinkedHashMap<>();
        private final Map<String, String> failures = new LinkedHashMap<>();

        private Run(Character operator, long seed, List<CohortMember> roster,
                    boolean preserveAppearance) {
            this.operator = operator;
            this.seed = seed;
            this.roster = List.copyOf(roster);
            this.preserveAppearance = preserveAppearance;
        }
    }
}
