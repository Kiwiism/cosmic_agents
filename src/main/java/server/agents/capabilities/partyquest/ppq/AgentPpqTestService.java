package server.agents.capabilities.partyquest.ppq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestTestQueueRuntime;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.field.AgentPpqTestFixtureService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** GM-only six-Agent PPQ observation harness. */
public final class AgentPpqTestService {
    private static final Logger log = LoggerFactory.getLogger(AgentPpqTestService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();
    private static final long SPAWN_STAGGER_MS = 500L;
    private AgentPpqTestService() { }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) return List.of("You are not configured as an Agent operator.");
        if (params == null || params.length == 0) return help();
        try {
            return switch (params[0].toLowerCase()) {
                case "start" -> start(operator, Flow.AGENTS_ONLY, parseSkip(params), parseSeed(params, nowMs), nowMs);
                case "humanleader", "withme" -> start(operator, Flow.HUMAN_LEADER,
                        parseSkip(params), parseSeed(params, nowMs), nowMs);
                case "humanmember", "agentleader" -> start(operator, Flow.HUMAN_MEMBER,
                        parseSkip(params), parseSeed(params, nowMs), nowMs);
                case "status" -> status(operator);
                case "pause" -> pause(operator, true);
                case "resume" -> pause(operator, false);
                case "stop" -> stop(operator, "stopped by operator", nowMs);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("PPQ test command failed for operator {}", operator.getId(), failure);
            return List.of("PPQ test command failed: " + failure.getMessage());
        }
    }

    private static List<String> start(Character operator, Flow flow, boolean skipChests,
                                      long seed, long nowMs) throws Exception {
        if (operator.getMapId() != AgentPpqDefinition.RECRUIT_MAP) return List.of("Stand at the Pirate PQ entrance (251010404) first.");
        if (flow != Flow.AGENTS_ONLY && (operator.getLevel() < 55 || operator.getLevel() > 100)) {
            return List.of("Your participating character must be level 55-100.");
        }
        if (AgentPartyGatewayRuntime.party().snapshot(operator) != null) return List.of("Leave your current party before starting PPQ test.");
        if (RUNS.containsKey(operator.getId())) stop(operator, "replaced by a new PPQ test", nowMs);
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "ppq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                seed, operator.getId(), AgentPpqDefinition.PARTY_SIZE, nowMs);
        if (flow != Flow.AGENTS_ONLY) engagement.addMember(
                operator.getId(), AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
        Run run = new Run(operator, engagement, seed, skipChests, flow);
        if (flow == Flow.HUMAN_LEADER) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(operator)) {
                throw new IllegalStateException("could not create the human-led PPQ party");
            }
            run.leaderId = operator.getId();
        }
        RUNS.put(operator.getId(), run);
        AgentPartyQuestEngagementRegistry.register(engagement);
        openLobby(run, nowMs);
        int agentCount = flow == Flow.AGENTS_ONLY ? AgentPpqDefinition.PARTY_SIZE : 5;
        for (int index = 0; index < agentCount; index++) {
            int ordinal = index;
            String name = "PPQer%02d".formatted(index + 1);
            String failure = PROVISIONING.ensureBackingCharacter(operator, name);
            if (failure != null) throw new IllegalStateException(failure);
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal), SPAWN_STAGGER_MS * index);
        }
        return List.of((agentCount == 6 ? "Six" : "Five")
                        + " level-67 PPQ Agents are being prepared with complete AP/SP builds and distinct class hats.",
                flow == Flow.HUMAN_LEADER ? "You are the event leader; the Agents will join your party."
                        : flow == Flow.HUMAN_MEMBER ? "The first Agent is event leader; you will join as the human member."
                        : "The run is fully Agent-controlled.",
                skipChests ? "Chest rooms will be skipped." : "Both chest rooms are enabled; two test chest keys will be provisioned.",
                "Use !ppqtest status.");
    }

    private static void launch(Run run, String name, int ordinal) {
        synchronized (run.lock) {
            if (RUNS.get(run.operator.getId()) != run) return;
            Character launched = null;
            try {
                int channel = AgentClientGatewayRuntime.clients().channel(run.operator);
                MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                        run.operator.getWorld(), channel, AgentPpqDefinition.RECRUIT_MAP);
                if (recruit == null) throw new IllegalStateException("PPQ recruitment map unavailable");
                Point spawn = recruit.getRandomPlayerSpawnpoint() == null ? new Point(0, 0)
                        : recruit.getRandomPlayerSpawnpoint().getPosition();
                Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(recruit, spawn);
                AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                        .spawnStationaryAgentForLeaderAt(run.operator, name, recruit, grounded == null ? spawn : grounded);
                if (!result.success()) throw new IllegalStateException(result.errorMessage());
                launched = result.agent();
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(launched.getId());
                if (entry == null) throw new IllegalStateException("spawned PPQ runtime unavailable");
                AgentPpqTestFixtureService.PreparationResult prepared = prepareFixture(
                        entry, name, ordinal, run.seed + ordinal * 10_007L);
                if (launched.getMapId() != AgentPpqDefinition.RECRUIT_MAP) {
                    Point returnPoint = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                            .groundPoint(recruit, spawn);
                    AgentMapGatewayRuntime.map().changeMapNear(
                            launched, recruit, returnPoint == null ? spawn : returnPoint);
                }
                if (launched.getMapId() != AgentPpqDefinition.RECRUIT_MAP) {
                    throw new IllegalStateException(name + " could not return to the PPQ recruit map");
                }
                if (!AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, launched,
                        "entering PPQ observation", System.currentTimeMillis())) {
                    throw new IllegalStateException(name + " could not release its previous activity");
                }
                long now = System.currentTimeMillis();
                log.info("PPQ fixture launched: name={} job={} level={} weapon={} attack={}",
                        name, prepared.job(), prepared.level(), prepared.weaponItemId(), prepared.weaponAttack());
                int expectedAgents = run.flow == Flow.AGENTS_ONLY ? AgentPpqDefinition.PARTY_SIZE : 5;
                int queueSize = run.flow == Flow.HUMAN_LEADER
                        ? AgentPpqDefinition.PARTY_SIZE : expectedAgents;
                var queued = AgentPartyQuestTestQueueRuntime.enqueue(
                        AgentPpqLobbyProfile.profile(), entry, launched, queueSize, now,
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
                throw new IllegalStateException("PPQ test is no longer active");
            }
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    run.engagement, agent.getId(), AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            var current = AgentPartyGatewayRuntime.party().snapshot(agent);
            if (current == null) joinParty(run, agent);
            AgentPartyQuestLobbyRegistry.addAndIndexMember(
                    run.lobby, agent.getId(), AgentPartyQuestLobbySession.MemberType.AGENT,
                    agent.getId() == run.leaderId
                            ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                            : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
            run.agents.add(agent.getId());
            int expectedAgents = run.flow == Flow.AGENTS_ONLY
                    ? AgentPpqDefinition.PARTY_SIZE : 5;
            if (run.agents.size() == expectedAgents) activate(run, nowMs);
        }
    }

    private static void joinParty(Run run, Character agent) {
        Character leader = online(run.leaderId);
        if (leader == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) throw new IllegalStateException("could not create PPQ party");
            run.leaderId = agent.getId(); return;
        }
        var party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            throw new IllegalStateException(agent.getName() + " could not join PPQ party");
        }
        AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
    }

    private static AgentPpqTestFixtureService.PreparationResult prepareFixture(
            AgentRuntimeEntry entry, String name, int ordinal, long seed) throws Exception {
        Exception firstFailure;
        try {
            return AgentPpqTestFixtureService.prepare(
                    entry, ordinal, seed, System.currentTimeMillis());
        } catch (Exception failure) {
            firstFailure = failure;
            log.warn("PPQ fixture {} needed a second preparation pass: {}", name, failure.getMessage());
        }
        try {
            return AgentPpqTestFixtureService.prepare(
                    entry, ordinal, seed, System.currentTimeMillis());
        } catch (Exception secondFailure) {
            secondFailure.addSuppressed(firstFailure);
            throw secondFailure;
        }
    }

    private static void openLobby(Run run, long nowMs) {
        AgentPartyQuestLobbySession lobby = new AgentPartyQuestLobbySession(
                run.engagement.engagementId(), AgentPpqLobbyProfile.profile(), run.seed,
                run.operator.getId(), AgentPpqDefinition.PARTY_SIZE,
                AgentPartyQuestCandidateScope.OWNER_ONLY, nowMs);
        if (run.flow != Flow.AGENTS_ONLY) lobby.addMember(run.operator.getId(),
                AgentPartyQuestLobbySession.MemberType.HUMAN,
                run.flow == Flow.HUMAN_LEADER
                        ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                        : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
        AgentPartyQuestLobbyRuntime.register(lobby, nowMs);
        run.engagement.beginLobby(lobby.lobbyId(), nowMs);
        run.lobby = lobby;
    }

    private static void activate(Run run, long nowMs) {
        if (run.flow == Flow.HUMAN_MEMBER) {
            Character leader = online(run.leaderId);
            var party = leader == null ? null : AgentPartyGatewayRuntime.party().snapshot(leader);
            if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(run.operator, party.id())) {
                throw new IllegalStateException("human member could not join the Agent-led PPQ party");
            }
        }
        AgentPpqSession session = new AgentPpqSession(
                switch (run.flow) {
                    case AGENTS_ONLY -> AgentPpqSession.Mode.TEST_OBSERVATION;
                    case HUMAN_LEADER -> AgentPpqSession.Mode.HUMAN_LEADER;
                    case HUMAN_MEMBER -> AgentPpqSession.Mode.HUMAN_MEMBER;
                }, run.seed, run.operator.getId(), run.skipChests, nowMs);
        if (run.flow != Flow.AGENTS_ONLY) {
            session.addMember(run.operator.getId(), AgentPpqMemberState.MemberType.HUMAN);
        }
        run.agents.forEach(id -> session.addMember(id, AgentPpqMemberState.MemberType.AGENT));
        int executorId = run.agents.iterator().next();
        session.setLeadership(run.leaderId, executorId);
        if (!run.skipChests) {
            Character leader = online(run.leaderId);
            if (leader == null || !AgentInventoryGatewayRuntime.inventory().addItem(
                    leader, AgentPpqDefinition.CHEST_KEY, (short) 2)) {
                throw new IllegalStateException("could not provision PPQ chest keys");
            }
        }
        run.lobby.markReady(nowMs);
        run.engagement.lobbyReady(nowMs);
        AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), nowMs);
        run.lobby = null;
        run.engagement.reserveEntry(nowMs);
        AgentPpqSessionRegistry.registerComplete(session);
        run.engagement.activateSession(session.sessionId(), nowMs);
        run.session = session;
        run.operator.dropMessage(6, "PPQ party activated; chest rooms "
                + (run.skipChests ? "will be skipped." : "are enabled."));
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No PPQ test is active.");
        if (run.session == null) return List.of("PPQ Agents prepared: " + run.agents.size()
                + "/" + (run.flow == Flow.AGENTS_ONLY ? 6 : 5));
        Character leader = online(run.session.eventLeaderId());
        if (leader != null && leader.getMap() != null) {
            long floorMedals = leader.getMap().getDroppedItems().stream()
                    .filter(drop -> !drop.isPickedUp()
                            && AgentPpqDefinition.MEDALS.contains(drop.getItemId()))
                    .count();
            int liveMobs = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .liveMonsterCount(leader, AgentPpqDefinition.COMBAT_MOBS);
            log.info("PPQ status phase={} map={} pos={} stage2={} medals={}/{}/{} floorMedals={} liveMobs={}",
                    run.session.phase(), leader.getMapId(), leader.getPosition(),
                    AgentPartyQuestGatewayRuntime.partyQuest().property(leader, "stage2"),
                    leader.getItemQuantity(AgentPpqDefinition.ROOKIE_MEDAL, false),
                    leader.getItemQuantity(AgentPpqDefinition.RISING_MEDAL, false),
                    leader.getItemQuantity(AgentPpqDefinition.VETERAN_MEDAL, false),
                    floorMedals, liveMobs);
        }
        return List.of("PPQ phase: " + run.session.phase(),
                "leader: " + run.session.eventLeaderId(),
                "chest rooms: " + (run.skipChests ? "skipped" : "enabled"));
    }
    private static List<String> pause(Character operator, boolean paused) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active PPQ session.");
        run.session.setPaused(paused); return List.of(paused ? "PPQ paused." : "PPQ resumed.");
    }
    private static List<String> stop(Character operator, String reason, long nowMs) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No PPQ test is active.");
        if (run.session != null) AgentPpqTerminationService.release(run.session, reason, nowMs, true);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), nowMs);
        if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, nowMs);
        }
        run.agents.forEach(AgentPpqTestService::disconnect);
        if (AgentPartyGatewayRuntime.party().hasParty(run.operator)) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(run.operator);
        }
        return List.of("PPQ test stopped safely.");
    }
    private static void fail(Run run, String reason) {
        if (run == null || !RUNS.remove(run.operator.getId(), run)) return;
        log.warn("PPQ test run failed: {}", reason);
        long now = System.currentTimeMillis();
        if (run.session != null) AgentPpqTerminationService.release(run.session, reason, now, true);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), now);
        if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, now);
        }
        run.agents.forEach(AgentPpqTestService::disconnect);
        if (AgentPartyGatewayRuntime.party().hasParty(run.operator)) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(run.operator);
        }
        run.operator.dropMessage(6, "PPQ stopped safely: " + reason);
    }
    private static boolean parseSkip(String[] params) {
        for (int i = 1; i < params.length; i++) if ("skipchests".equalsIgnoreCase(params[i])
                || "skip".equalsIgnoreCase(params[i])) return true;
        return false;
    }
    private static long parseSeed(String[] params, long fallback) {
        for (int i = 1; i < params.length; i++) {
            if ("skipchests".equalsIgnoreCase(params[i]) || "skip".equalsIgnoreCase(params[i])) continue;
            return Long.parseLong(params[i]);
        }
        return fallback;
    }
    private static void disconnect(int id) {
        Character agent = online(id); AgentRuntimeCleanupService.removeAgentByCharacterId(id);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }
    private static Character online(int id) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
        Character agent = entry == null ? null : server.agents.integration.AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }
    private static List<String> help() {
        return List.of("!ppqtest start [skipchests] [seed]",
                "!ppqtest humanleader [skipchests] [seed]",
                "!ppqtest humanmember [skipchests] [seed]",
                "!ppqtest status | pause | resume | stop");
    }
    private enum Flow { AGENTS_ONLY, HUMAN_LEADER, HUMAN_MEMBER }
    private static final class Run {
        final Object lock = new Object(); final Character operator;
        final AgentPartyQuestEngagement engagement; final long seed; final boolean skipChests; final Flow flow;
        final Set<Integer> agents = new LinkedHashSet<>();
        volatile int leaderId; volatile AgentPpqSession session;
        volatile AgentPartyQuestLobbySession lobby;
        Run(Character operator, AgentPartyQuestEngagement engagement, long seed, boolean skipChests, Flow flow) {
            this.operator = operator; this.engagement = engagement; this.seed = seed;
            this.skipChests = skipChests; this.flow = flow;
        }
    }
}
