package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.field.AgentKpqTestFixtureService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/** GM-only KPQ observation harness. Production admission does not depend on this class. */
public final class AgentKpqTestService {
    private static final Logger log = LoggerFactory.getLogger(AgentKpqTestService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.SPAWN_STAGGER_MS");
    private static final int ROSTER_SIZE = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.ROSTER_SIZE");
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();

    private AgentKpqTestService() {
    }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        if (params == null || params.length == 0) return help();
        try {
            return switch (params[0].toLowerCase()) {
                case "start" -> start(operator, partySize(params, 1), seed(params, 2, nowMs), 1, nowMs);
                case "checkpoint" -> checkpoint(operator, params, nowMs);
                case "status" -> status(operator);
                case "pause" -> pause(operator, true);
                case "resume", "continue" -> pause(operator, false);
                case "run" -> runAgain(operator, nowMs);
                case "rotate", "switch" -> rotate(operator, count(params, 1), nowMs);
                case "stop" -> stop(operator);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("KPQ test command failed for operator {}", operator.getId(), failure);
            return List.of("KPQ test command failed: " + failure.getMessage());
        }
    }

    private static List<String> start(
            Character operator, int size, long seed, int checkpoint, long nowMs) throws Exception {
        if (operator.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
            return List.of("Stand in Kerning City (103000000) before starting KPQ.");
        }
        Run old = RUNS.get(operator.getId());
        if (old != null) return List.of("A KPQ test session already exists. Use !kpqtest stop first.");
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, seed, operator.getId(), size, nowMs);
        session.setRequestedCheckpointStage(checkpoint);
        List<String> names = shuffledRoster(seed).subList(0, size);
        for (String name : names) ensureBackingCharacter(operator, name);
        Run run = new Run(operator, session, seed, new LinkedHashSet<>(names));
        RUNS.put(operator.getId(), run);
        AgentKpqSessionRegistry.register(session);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int ordinal = i;
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal, false), SPAWN_STAGGER_MS * i);
        }
        return List.of("KPQ test " + session.sessionId() + " recruiting " + size
                + " Agents (seed " + seed + ", checkpoint stage " + checkpoint + ").",
                "Roster order is intentionally shuffled: " + names);
    }

    private static void launch(Run run, String name, int ordinal, boolean replacement) {
        synchronized (run.launchLock) {
            AgentKpqSession session = run.session;
            if (RUNS.get(run.operator.getId()) != run || session.phase() == AgentKpqSession.Phase.FAILED) return;
            Character launched = null;
            try {
            MapleMap map = AgentMapGatewayRuntime.map().resolveMap(run.operator.getWorld(),
                    AgentClientGatewayRuntime.clients().channel(run.operator), AgentKpqDefinition.RECRUIT_MAP);
            Point npc = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .npcPosition(run.operator, AgentKpqDefinition.ENTRY_NPC);
            Point candidate = new Point((npc == null ? -260 : npc.x) + 55 + ordinal * 42,
                    npc == null ? 155 : npc.y);
            Point spawn = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
            AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                    .spawnStationaryAgentForLeaderAt(run.operator, name, map, spawn);
            if (!result.success()) throw new IllegalStateException(result.errorMessage());
            Character agent = result.agent();
            launched = agent;
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
            AgentKpqTestFixtureService.prepare(entry, run.seed + ordinal * 10_007L, System.currentTimeMillis());
            AgentMapGatewayRuntime.map().changeMapNear(agent, map, spawn);
            if (!AgentActivityBootstrap.admission().prepare(
                    AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, agent,
                    "entering KPQ test", System.currentTimeMillis())) {
                throw new IllegalStateException("Agent activity did not release for KPQ: " + name);
            }
            joinSessionParty(run, agent);
            session.addMember(agent.getId(), AgentKpqMemberState.MemberType.AGENT);
            AgentKpqSessionRegistry.indexMember(session, agent.getId());
            if (!replacement && session.memberCount() == session.requestedPartySize()) {
                session.resetForRun(System.currentTimeMillis());
            }
            } catch (Exception failure) {
                if (launched != null) disconnect(launched.getId());
                log.warn("Could not launch KPQ fixture {}", name, failure);
                session.fail("Could not launch " + name + ": " + failure.getMessage(), System.currentTimeMillis());
            }
        }
    }

    private static void joinSessionParty(Run run, Character agent) {
        Character leader = character(run.session.eventLeaderId());
        if (leader == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                throw new IllegalStateException("Could not create the Agent-led KPQ party");
            }
            return;
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            throw new IllegalStateException("Could not join " + agent.getName() + " to the KPQ party");
        }
        AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
    }

    private static List<String> checkpoint(Character operator, String[] params, long nowMs) throws Exception {
        if (params.length < 2) return List.of("Syntax: !kpqtest checkpoint <1-5> [3|4] [seed]");
        int stage = Integer.parseInt(params[1]);
        if (stage < 1 || stage > 5) return List.of("Checkpoint stage must be 1-5.");
        int size = partySize(params, 2);
        long seed = seed(params, 3, nowMs);
        return start(operator, size, seed, stage, nowMs);
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test session is active.");
        AgentKpqSession session = run.session;
        ArrayList<String> lines = new ArrayList<>();
        lines.add(session.sessionId() + " phase=" + session.phase() + " members="
                + session.memberCount() + '/' + session.requestedPartySize() + " seed=" + session.seed()
                + (session.paused() ? " PAUSED" : ""));
        session.members().stream().sorted(Comparator.comparingInt(AgentKpqMemberState::characterId))
                .forEach(member -> {
                    Character agent = character(member.characterId());
                    lines.add((agent == null ? "#" + member.characterId() : agent.getName())
                            + " role=" + member.role() + " map=" + (agent == null ? -1 : agent.getMapId())
                            + " number=" + member.partyNumber() + " coupons=" + member.couponTarget()
                            + " position=" + member.assignedPosition());
                });
        if (!session.failure().isBlank()) lines.add("failure=" + session.failure());
        return lines;
    }

    private static List<String> pause(Character operator, boolean paused) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test session is active.");
        run.session.setPaused(paused);
        return List.of("KPQ test " + (paused ? "paused" : "resumed") + '.');
    }

    private static List<String> runAgain(Character operator, long nowMs) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test session is active.");
        if (run.session.phase() != AgentKpqSession.Phase.WAITING_OUTSIDE_TEST) {
            return List.of("A new run can start only while the Agents are waiting outside.");
        }
        run.session.setRequestedCheckpointStage(1);
        run.session.resetForRun(nowMs);
        return List.of("Starting the next KPQ run with the current party.");
    }

    private static List<String> rotate(Character operator, int count, long nowMs) throws Exception {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test session is active.");
        if (run.session.phase() != AgentKpqSession.Phase.WAITING_OUTSIDE_TEST) {
            return List.of("Members can switch only while the party waits outside.");
        }
        if (count < 1 || count > 2 || count >= run.session.memberCount()) {
            return List.of("Switch count must be 1 or 2 and cannot remove the whole party.");
        }
        List<AgentKpqMemberState> candidates = run.session.members().stream()
                .filter(member -> member.characterId() != run.session.eventLeaderId()).toList();
        ArrayList<AgentKpqMemberState> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new java.util.Random(run.seed + nowMs));
        List<String> removed = new ArrayList<>();
        for (AgentKpqMemberState member : shuffled.subList(0, count)) {
            Character agent = character(member.characterId());
            if (agent != null) {
                removed.add(agent.getName());
                AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
            }
            run.session.removeMember(member.characterId());
            AgentKpqSessionRegistry.unindexMember(run.session, member.characterId());
            disconnect(member.characterId());
        }
        List<String> available = shuffledRoster(run.seed + nowMs).stream()
                .filter(name -> !run.usedNames.contains(name)).limit(count).toList();
        for (int i = 0; i < available.size(); i++) {
            String name = available.get(i);
            ensureBackingCharacter(operator, name);
            run.usedNames.add(name);
            int ordinal = run.usedNames.size();
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal, true), SPAWN_STAGGER_MS * i);
        }
        return List.of("Switching out " + removed + " for " + available + ".",
                "Use !kpqtest run after all replacements appear in !kpqtest status.");
    }

    private static List<String> stop(Character operator) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No KPQ test session is active.");
        run.session.members().forEach(member -> disconnect(member.characterId()));
        AgentKpqSessionRegistry.remove(run.session);
        return List.of("Stopped KPQ test " + run.session.sessionId() + ". Backing characters were retained.");
    }

    private static void disconnect(int characterId) {
        Character agent = character(characterId);
        AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }

    private static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        return entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
    }

    private static void ensureBackingCharacter(Character operator, String name) throws Exception {
        String failure = PROVISIONING.ensureBackingCharacter(operator, name);
        if (failure != null) throw new IllegalStateException(failure);
    }

    private static List<String> shuffledRoster(long seed) {
        ArrayList<String> names = new ArrayList<>();
        for (int i = 1; i <= ROSTER_SIZE; i++) names.add("KPQer%02d".formatted(i));
        Collections.shuffle(names, new java.util.Random(new SplittableRandom(seed).nextLong()));
        return names;
    }

    private static int partySize(String[] params, int index) {
        int size = params.length > index ? Integer.parseInt(params[index]) : 4;
        if (size < 3 || size > 4) throw new IllegalArgumentException("KPQ test party size must be 3 or 4");
        return size;
    }

    private static int count(String[] params, int index) {
        return params.length > index ? Integer.parseInt(params[index]) : 1;
    }

    private static long seed(String[] params, int index, long fallback) {
        return params.length > index ? Long.parseLong(params[index]) : fallback;
    }

    private static List<String> help() {
        return List.of("!kpqtest start [3|4] [seed]", "!kpqtest checkpoint <1-5> [3|4] [seed]",
                "!kpqtest status | pause | resume | run | switch <1|2> | stop");
    }

    private static final class Run {
        private final Character operator;
        private final AgentKpqSession session;
        private final long seed;
        private final Set<String> usedNames;
        private final Object launchLock = new Object();

        private Run(Character operator, AgentKpqSession session, long seed, Set<String> usedNames) {
            this.operator = operator;
            this.session = session;
            this.seed = seed;
            this.usedNames = usedNames;
        }
    }
}
