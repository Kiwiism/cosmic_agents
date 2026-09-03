package server.agents.capabilities.partyquest.lmpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestTestQueueRuntime;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.field.AgentLmpqTestFixtureService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** GM-only variable-size LMPQ observation harness with persistent route evidence. */
public final class AgentLmpqTestService {
    private static final Logger log = LoggerFactory.getLogger(AgentLmpqTestService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lmpq.AgentLmpqTestService.SPAWN_STAGGER_MS");

    private AgentLmpqTestService() { }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        if (params == null || params.length == 0) return help();
        try {
            return switch (params[0].toLowerCase()) {
                case "start" -> start(operator, startOptions(params, nowMs), nowMs);
                case "status", "routes" -> status(operator);
                case "spectate" -> spectate(operator, params);
                case "pause" -> pause(operator, true);
                case "resume" -> pause(operator, false);
                case "stop" -> stop(operator, "stopped by operator", nowMs);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("LMPQ test command failed for operator {}", operator.getId(), failure);
            return List.of("LMPQ test command failed: " + failure.getMessage());
        }
    }

    static StartOptions startOptions(String[] params, long nowMs) {
        int partySize = params.length > 1 ? Integer.parseInt(params[1]) : 3;
        int rendezvousRoom = params.length > 2 ? Integer.parseInt(params[2])
                : AgentLmpqDefinition.RENDEZVOUS_ROOM;
        long seed = params.length > 3 ? Long.parseLong(params[3]) : nowMs;
        if (partySize < AgentLmpqDefinition.MIN_PARTY_SIZE
                || partySize > AgentLmpqDefinition.MAX_PARTY_SIZE) {
            throw new IllegalArgumentException("party size must be 3-6");
        }
        if (rendezvousRoom != AgentLmpqDefinition.RENDEZVOUS_ROOM
                && rendezvousRoom != AgentLmpqDefinition.CLEAR_ROOM) {
            throw new IllegalArgumentException("rendezvous room must be 9 or 16");
        }
        return new StartOptions(partySize, rendezvousRoom, seed);
    }

    private static List<String> start(Character operator, StartOptions options, long nowMs) throws Exception {
        if (operator.getMapId() != AgentLmpqDefinition.RECRUIT_MAP) {
            return List.of("Stand at the LMPQ entrance (220000000) first.");
        }
        if (AgentPartyGatewayRuntime.party().snapshot(operator) != null) {
            return List.of("Leave your current party before starting LMPQ test.");
        }
        if (RUNS.containsKey(operator.getId())) stop(operator, "replaced by a new LMPQ test", nowMs);
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "lmpq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                options.seed(), operator.getId(), options.partySize(), nowMs);
        Run run = new Run(operator, engagement, options);
        RUNS.put(operator.getId(), run);
        AgentPartyQuestEngagementRegistry.register(engagement);
        engagement.beginLobby("lmpq-test-" + operator.getId(), nowMs);
        for (int index = 0; index < options.partySize(); index++) {
            int ordinal = index;
            String name = "LMPQ%dR%02d".formatted(options.partySize(), index + 1);
            String failure = PROVISIONING.ensureBackingCharacter(operator, name);
            if (failure != null) throw new IllegalStateException(failure);
            run.names.put(name, 0);
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal), SPAWN_STAGGER_MS * index);
        }
        return List.of(options.partySize() + " LMPQ Agents are being prepared at level 60.",
                "Rendezvous is Room " + options.rendezvousRoom()
                        + ". Use !lmpqtest status or !lmpqtest spectate.");
    }

    private static void launch(Run run, String name, int ordinal) {
        synchronized (run.lock) {
            if (RUNS.get(run.operator.getId()) != run) return;
            Character launched = null;
            try {
                int channel = AgentClientGatewayRuntime.clients().channel(run.operator);
                MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                        run.operator.getWorld(), channel, AgentLmpqDefinition.RECRUIT_MAP);
                if (recruit == null) throw new IllegalStateException("LMPQ recruitment map unavailable");
                Point spawn = recruit.getRandomPlayerSpawnpoint() == null ? new Point(0, 0)
                        : recruit.getRandomPlayerSpawnpoint().getPosition();
                Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(recruit, spawn);
                AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                        .spawnStationaryAgentForLeaderAt(run.operator, name, recruit,
                                grounded == null ? spawn : grounded);
                if (!result.success()) throw new IllegalStateException(result.errorMessage());
                launched = result.agent();
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(launched.getId());
                if (entry == null) throw new IllegalStateException("spawned LMPQ runtime unavailable");
                AgentLmpqTestFixtureService.PreparationResult prepared = AgentLmpqTestFixtureService.prepare(
                        entry, ordinal, run.options.seed() + ordinal * 10_007L, System.currentTimeMillis());
                Point lobbySpawn = recruit.getRandomPlayerSpawnpoint() == null ? new Point(0, 0)
                        : recruit.getRandomPlayerSpawnpoint().getPosition();
                Point lobbyGround = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .groundPoint(recruit, lobbySpawn);
                AgentMapGatewayRuntime.map().changeMapNear(
                        launched, recruit, lobbyGround == null ? lobbySpawn : lobbyGround);
                if (!AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, launched,
                        "entering LMPQ observation", System.currentTimeMillis())) {
                    throw new IllegalStateException(name + " could not release its previous activity");
                }
                long now = System.currentTimeMillis();
                run.names.put(name, launched.getId());
                log.info("LMPQ fixture launched: name={} job={} level={} weapon={} watk={}",
                        name, prepared.job(), prepared.level(), prepared.weaponItemId(), prepared.weaponAttack());
                var queued = AgentPartyQuestTestQueueRuntime.enqueue(
                        AgentLmpqLobbyProfile.profile(), entry, launched,
                        run.options.partySize(), now,
                        (candidate, admittedAtMs) -> admitQueued(run, candidate, admittedAtMs));
                if (queued.status() != server.agents.runtime.activity.session.AgentActivityAdmissionResult.Status.ACCEPTED) {
                    throw new IllegalStateException(queued.reason());
                }
            } catch (Exception failure) {
                if (launched != null) disconnect(launched.getId());
                fail(run, "Could not launch " + name + ": " + failure.getMessage());
            }
        }
    }

    private static void admitQueued(Run run, Character agent, long nowMs) {
        synchronized (run.lock) {
            if (RUNS.get(run.operator.getId()) != run) {
                throw new IllegalStateException("LMPQ test is no longer active");
            }
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    run.engagement, agent.getId(), AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            joinParty(run, agent);
            run.agents.add(agent.getId());
            if (run.agents.size() == run.options.partySize()) activate(run, nowMs);
        }
    }

    private static void joinParty(Run run, Character agent) {
        Character leader = online(run.leaderId);
        if (leader == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                throw new IllegalStateException("could not create LMPQ party");
            }
            run.leaderId = agent.getId();
            return;
        }
        var party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            throw new IllegalStateException(agent.getName() + " could not join LMPQ party");
        }
        AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
    }

    private static void activate(Run run, long nowMs) {
        AgentLmpqSession session = new AgentLmpqSession(
                AgentLmpqSession.Mode.TEST_OBSERVATION, run.options.seed(), run.operator.getId(),
                run.options.partySize(), nowMs);
        run.agents.forEach(id -> session.addMember(id, AgentLmpqMemberState.MemberType.AGENT));
        session.setLeadership(run.leaderId, run.leaderId);
        session.setRendezvousRoom(run.options.rendezvousRoom());
        run.engagement.reserveEntry(nowMs);
        AgentLmpqSessionRegistry.registerComplete(session);
        run.engagement.activateSession(session.sessionId(), nowMs);
        run.session = session;
        run.operator.dropMessage(6, run.options.partySize()
                + "-Agent LMPQ activated. Movement is restricted to authored portals and navigation.");
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No LMPQ test is active.");
        if (run.session == null) {
            return List.of("LMPQ Agents prepared: " + run.agents.size() + '/' + run.options.partySize());
        }
        List<String> lines = new ArrayList<>();
        String header = "LMPQ phase=" + run.session.phase() + ", rendezvous="
                + run.session.rendezvousRoom() + ", rooms=" + run.session.rooms().snapshot();
        lines.add(header);
        log.info("LMPQ status: session={} {}", run.session.sessionId(), header);
        for (Map.Entry<String, Integer> named : run.names.entrySet()) {
            AgentLmpqMemberState member = run.session.member(named.getValue());
            if (member == null) continue;
            String line = named.getKey() + ": initial=" + member.initialRoom()
                    + ", route=" + member.route() + ", assigned=" + member.assignments()
                    + ", target=" + member.targetRoom();
            lines.add(line);
            log.info("LMPQ route: session={} {}", run.session.sessionId(), line);
        }
        return lines;
    }

    private static List<String> spectate(Character operator, String[] params) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active LMPQ session.");
        String requested = params.length > 1 ? params[1] : "leader";
        int targetId = "leader".equalsIgnoreCase(requested)
                ? run.leaderId : run.names.getOrDefault(requested, 0);
        Character target = online(targetId);
        if (target == null || !AgentLmpqDefinition.isEventMap(target.getMapId())) {
            return List.of("The requested LMPQ Agent is not inside the maze yet.");
        }
        AgentMapGatewayRuntime.map().changeMapNear(operator, target.getMap(), target.getPosition());
        return List.of("Observing " + target.getName() + " in Maze Room "
                + AgentLmpqDefinition.roomForMap(target.getMapId()) + '.');
    }

    private static List<String> pause(Character operator, boolean paused) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active LMPQ session.");
        run.session.setPaused(paused);
        return List.of(paused ? "LMPQ paused." : "LMPQ resumed.");
    }

    private static List<String> stop(Character operator, String reason, long nowMs) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No LMPQ test is active.");
        if (run.session != null) {
            statusLinesToLog(run);
            AgentLmpqTerminationService.release(run.session, reason, nowMs, true);
        }
        if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, nowMs);
        }
        run.agents.forEach(AgentLmpqTestService::disconnect);
        return List.of("LMPQ test stopped safely; final routes were written to the server log.");
    }

    private static void statusLinesToLog(Run run) {
        if (run.session == null) return;
        for (Map.Entry<String, Integer> named : run.names.entrySet()) {
            AgentLmpqMemberState member = run.session.member(named.getValue());
            if (member != null) log.info("LMPQ final route: session={} name={} initial={} route={} assigned={}",
                    run.session.sessionId(), named.getKey(), member.initialRoom(),
                    member.route(), member.assignments());
        }
    }

    private static void fail(Run run, String reason) {
        if (run == null || !RUNS.remove(run.operator.getId(), run)) return;
        long now = System.currentTimeMillis();
        statusLinesToLog(run);
        if (run.session != null) AgentLmpqTerminationService.release(run.session, reason, now, true);
        if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, now);
        }
        run.agents.forEach(AgentLmpqTestService::disconnect);
        run.operator.dropMessage(6, "LMPQ stopped safely: " + reason);
    }

    private static void disconnect(int id) {
        Character agent = online(id);
        AgentRuntimeCleanupService.removeAgentByCharacterId(id);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }

    private static Character online(int id) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
        Character agent = entry == null ? null : server.agents.integration.AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }

    private static List<String> help() {
        return List.of("!lmpqtest start <3-6> [9|16] [seed]",
                "!lmpqtest status | routes | spectate [leader|name] | pause | resume | stop");
    }

    record StartOptions(int partySize, int rendezvousRoom, long seed) { }

    private static final class Run {
        final Object lock = new Object();
        final Character operator;
        final AgentPartyQuestEngagement engagement;
        final StartOptions options;
        final Set<Integer> agents = new LinkedHashSet<>();
        final Map<String, Integer> names = new LinkedHashMap<>();
        volatile int leaderId;
        volatile AgentLmpqSession session;

        Run(Character operator, AgentPartyQuestEngagement engagement, StartOptions options) {
            this.operator = operator;
            this.engagement = engagement;
            this.options = options;
        }
    }
}
