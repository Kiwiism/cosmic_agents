package server.agents.capabilities.partyquest.epq;

import client.Character;
import client.Job;
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
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.field.AgentEpqTestFixtureService;
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
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.runtime.activity.AgentActivityOwnershipState;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** GM-only full-Agent and one-human EPQ observation harness. */
public final class AgentEpqTestService {
    private static final Logger log = LoggerFactory.getLogger(AgentEpqTestService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();
    private static final List<String> NAMES = List.of(
            "EPQer01", "EPQer02", "EPQer03", "EPQer04", "EPQer05");
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqTestService.SPAWN_STAGGER_MS");
    private static final long PREPARATION_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqTestService.PREPARATION_TIMEOUT_MS");

    private AgentEpqTestService() { }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        if (params == null || params.length == 0) return help();
        try {
            return switch (params[0].toLowerCase()) {
                case "start" -> start(operator, seed(params, 1, nowMs), Flow.AGENTS_ONLY, nowMs);
                case "withme", "humanleader" -> start(
                        operator, seed(params, 1, nowMs), Flow.HUMAN_LEADER, nowMs);
                case "agentleader" -> start(
                        operator, seed(params, 1, nowMs), Flow.AGENT_LEADER, nowMs);
                case "invite" -> invite(operator);
                case "spectate", "attach" -> spectate(operator);
                case "return", "detach" -> returnFromSpectating(operator);
                case "status" -> status(operator);
                case "pause" -> pause(operator, true);
                case "resume" -> pause(operator, false);
                case "stop" -> stop(operator, "stopped by operator", nowMs);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("EPQ test command failed for operator {}", operator.getId(), failure);
            return List.of("EPQ test command failed: " + failure.getMessage());
        }
    }

    private static synchronized List<String> start(
            Character operator, long seed, Flow flow, long nowMs) throws Exception {
        if (operator.getMapId() != AgentEpqDefinition.RECRUIT_MAP) {
            return List.of("Stand at the Ellin Forest PQ entrance (300030100) first.");
        }
        if (AgentPartyGatewayRuntime.party().snapshot(operator) != null) {
            return List.of("Leave your current party before starting this EPQ test.");
        }
        if (flow != Flow.AGENTS_ONLY) {
            if (operator.getLevel() < AgentEpqDefinition.MIN_LEVEL
                    || operator.getLevel() > AgentEpqDefinition.MAX_LEVEL) {
                return List.of("Your participating character must be level 44-55.");
            }
            if (AgentEpqRosterRequirementPolicy.branch(operator) == null) {
                return List.of("Your participating character must be an Explorer class.");
            }
        }
        resetReservedAgents(nowMs);
        int agentCount = flow == Flow.AGENTS_ONLY ? 5 : 4;
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "epq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                seed, operator.getId(), AgentEpqRosterRequirementPolicy.PARTY_SIZE, nowMs);
        if (flow != Flow.AGENTS_ONLY) engagement.addMember(
                operator.getId(), AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
        Run run = new Run(operator, engagement, seed, flow,
                AgentEpqTestFixtureService.agentBranchesFor(
                        flow == Flow.AGENTS_ONLY ? null : operator));
        if (flow == Flow.HUMAN_LEADER) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(operator)) {
                throw new IllegalStateException("could not create the human-led EPQ party");
            }
            run.eventLeaderId = operator.getId();
        }
        RUNS.put(operator.getId(), run);
        AgentPartyQuestEngagementRegistry.register(engagement);
        openLobby(run, nowMs);
        for (int index = 0; index < agentCount; index++) {
            String name = NAMES.get(index);
            String failure = PROVISIONING.ensureBackingCharacter(operator, name);
            if (failure != null) throw new IllegalStateException(failure);
            int ordinal = index;
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal),
                    SPAWN_STAGGER_MS * index);
        }
        return switch (flow) {
            case AGENTS_ONLY -> List.of(
                    "Five EPQ Agents are preparing: Warrior, Magician, Bowman, Thief, and Pirate.",
                    "Use !epqtest status while they assemble and enter.");
            case HUMAN_LEADER -> List.of(
                    "Your EPQ party was created; four missing-class Agents are joining it.",
                    "You are the leader, so talk to Ellin after the party assembles.");
            case AGENT_LEADER -> List.of(
                    "Four missing-class Agents are assembling an EPQ party.",
                    "Accept their invitation when it arrives; use !epqtest invite to resend it.");
        };
    }

    private static void openLobby(Run run, long nowMs) {
        AgentPartyQuestLobbySession lobby = new AgentPartyQuestLobbySession(
                run.engagement.engagementId(), AgentEpqLobbyProfile.profile(), run.seed,
                run.operator.getId(), AgentEpqRosterRequirementPolicy.PARTY_SIZE,
                AgentPartyQuestCandidateScope.OWNER_ONLY, nowMs);
        if (run.flow != Flow.AGENTS_ONLY) lobby.addMember(run.operator.getId(),
                AgentPartyQuestLobbySession.MemberType.HUMAN,
                AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
        AgentPartyQuestLobbyRuntime.register(lobby, nowMs);
        run.engagement.beginLobby(lobby.lobbyId(), nowMs);
        run.lobby = lobby;
        monitor(run);
    }

    private static void launch(Run run, String name, int ordinal) {
        synchronized (run.lock) {
            if (RUNS.get(run.operator.getId()) != run) return;
            Character launched = null;
            try {
                int channel = AgentClientGatewayRuntime.clients().channel(run.operator);
                MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                        run.operator.getWorld(), channel, AgentEpqDefinition.RECRUIT_MAP);
                if (recruit == null) throw new IllegalStateException("EPQ recruitment map unavailable");
                Point candidate = recruit.getRandomPlayerSpawnpoint() == null ? new Point(0, 0)
                        : recruit.getRandomPlayerSpawnpoint().getPosition();
                Point spawn = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(recruit, candidate);
                AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                        .spawnStationaryAgentForLeaderAt(run.operator, name, recruit,
                                spawn == null ? candidate : spawn);
                if (!result.success()) throw new IllegalStateException(result.errorMessage());
                launched = result.agent();
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(launched.getId());
                if (entry == null) throw new IllegalStateException("spawned EPQ runtime unavailable");
                Job branch = run.agentBranches.get(ordinal);
                var prepared = AgentEpqTestFixtureService.prepare(
                        entry, branch, run.seed + ordinal * 10_007L, System.currentTimeMillis());
                AgentMapGatewayRuntime.map().changeMapNear(
                        launched, recruit, spawn == null ? candidate : spawn);
                if (!AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, launched,
                        "entering EPQ observation", System.currentTimeMillis())) {
                    throw new IllegalStateException(name + " could not release its previous activity");
                }
                long now = System.currentTimeMillis();
                AgentPartyQuestEngagementRegistry.addAndIndexMember(run.engagement, launched.getId(),
                        AgentPartyQuestEngagement.MemberType.AGENT, now);
                if (run.flow != Flow.HUMAN_LEADER) joinOwnedParty(run, launched);
                else joinExistingParty(run, launched);
                AgentPartyQuestLobbyRegistry.addAndIndexMember(run.lobby, launched.getId(),
                        AgentPartyQuestLobbySession.MemberType.AGENT,
                        launched.getId() == run.eventLeaderId
                                ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                                : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, now);
                if (run.lobby.coordinatorAgentId() == 0) {
                    run.lobby.setCoordinatorAgentId(launched.getId());
                }
                run.agents.add(launched.getId());
                log.info("EPQ fixture launched name={} branch={} build={} hit={}",
                        name, branch, prepared.buildId(), prepared.minimumHitChance());
            } catch (Exception failure) {
                if (launched != null) disconnect(launched.getId());
                fail(run, "Could not launch " + name + ": " + failure.getMessage());
            }
        }
    }

    private static void joinOwnedParty(Run run, Character agent) {
        Character leader = online(run.eventLeaderId);
        if (leader == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                throw new IllegalStateException("could not create EPQ party");
            }
            run.eventLeaderId = agent.getId();
            return;
        }
        joinParty(leader, agent);
    }

    private static void joinExistingParty(Run run, Character agent) {
        Character leader = online(run.eventLeaderId);
        if (leader == null) throw new IllegalStateException("human EPQ leader unavailable");
        joinParty(leader, agent);
    }

    private static void joinParty(Character leader, Character agent) {
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            throw new IllegalStateException(agent.getName() + " could not join EPQ party");
        }
        AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
    }

    private static void monitor(Run run) {
        if (RUNS.get(run.operator.getId()) != run) return;
        try {
            if (run.flow == Flow.AGENT_LEADER) maybeInvite(run);
            if (run.session == null) attemptHandoff(run);
            if (run.session == null
                    && System.currentTimeMillis() - run.startedAtMs >= PREPARATION_TIMEOUT_MS) {
                fail(run, "EPQ party preparation timed out after "
                        + (PREPARATION_TIMEOUT_MS / 1_000L) + " seconds");
                return;
            }
            if (run.spectating) updateSpectator(run);
            if (run.session != null && run.session.terminal()) {
                if (run.session.phase() == AgentEpqSession.Phase.FAILED) {
                    fail(run, run.session.failure());
                }
                return;
            }
        } catch (RuntimeException failure) {
            fail(run, "EPQ monitor failed: " + failure.getMessage());
            return;
        }
        AgentSchedulerRuntime.schedule(() -> monitor(run), 500L);
    }

    private static void maybeInvite(Run run) {
        if (run.inviteSent || run.agents.size() < 4
                || AgentPartyGatewayRuntime.party().hasParty(run.operator)) return;
        Character leader = online(run.eventLeaderId);
        if (leader != null && AgentPartyGatewayRuntime.party().invitePartyMember(leader, run.operator)) {
            run.inviteSent = true;
            run.operator.dropMessage(6, "Accept " + leader.getName() + "'s EPQ invitation.");
        }
    }

    private static void attemptHandoff(Run run) {
        Character leader = online(run.eventLeaderId);
        AgentPartySnapshot party = leader == null ? null : AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || party.members().size() != AgentEpqRosterRequirementPolicy.PARTY_SIZE) return;
        List<Character> members = party.members().stream()
                .filter(java.util.Objects::nonNull)
                .map(member -> online(member.id()))
                .filter(java.util.Objects::nonNull).toList();
        if (members.size() != AgentEpqRosterRequirementPolicy.PARTY_SIZE
                || !AgentEpqRosterRequirementPolicy.evaluate(members).complete()) return;
        long now = System.currentTimeMillis();
        Set<Integer> ids = members.stream().map(Character::getId)
                .collect(java.util.stream.Collectors.toSet());
        run.lobby.reconcileParty(party.id(), run.eventLeaderId, ids, now);
        run.lobby.markReady(now);
        run.engagement.lobbyReady(now);
        AgentEpqAdmissionService.AdmissionResult result = AgentEpqAdmissionService.admitFromLobby(
                run.engagement, run.lobby, run.operator, leader, members,
                run.seed, now, run.flow == Flow.AGENTS_ONLY
                ? AgentEpqSession.Mode.TEST_OBSERVATION : AgentEpqSession.Mode.HUMAN_ASSISTED);
        if (!result.success()) throw new IllegalStateException(result.message());
        run.session = result.session();
        run.lobby = null;
        run.operator.dropMessage(6, run.flow == Flow.AGENTS_ONLY
                ? "Five-Agent EPQ activated. The Agent leader will enter through Ellin."
                : run.flow == Flow.HUMAN_LEADER
                ? "Mixed EPQ activated. Talk to Ellin to lead the party inside."
                : "Agent-led mixed EPQ activated. Follow the Agent leader inside.");
    }

    private static List<String> invite(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.flow != Flow.AGENT_LEADER) {
            return List.of("No Agent-led EPQ invitation is pending.");
        }
        run.inviteSent = false;
        maybeInvite(run);
        return List.of(run.inviteSent ? "EPQ invitation sent." : "EPQ party is still assembling.");
    }

    private static List<String> spectate(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.flow != Flow.AGENTS_ONLY || run.session == null) {
            return List.of("Spectating requires an active five-Agent EPQ session.");
        }
        Character leader = online(run.session.eventLeaderId());
        if (leader == null || leader.getEventInstance() == null
                || !AgentEpqDefinition.isEventMap(leader.getMapId())) {
            return List.of("The EPQ party has not entered its event instance yet.");
        }
        run.spectating = true;
        run.followId = leader.getId();
        updateSpectator(run);
        return List.of("Attached as an EPQ spectator; stage changes will auto-follow.",
                "Do not attack, loot, use portals/NPCs, or hit reactors. Use !epqtest return to leave.");
    }

    private static List<String> returnFromSpectating(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || !run.spectating) return List.of("You are not spectating EPQ.");
        returnObserver(run);
        return List.of("Returned to the EPQ recruitment map.");
    }

    private static void updateSpectator(Run run) {
        Character target = online(run.followId);
        if (target == null || run.session == null
                || target.getEventInstance() != run.session.eventInstance()) {
            target = run.session == null ? null : online(run.session.eventLeaderId());
            if (target == null) return;
            run.followId = target.getId();
        }
        if (target.getMap() != null && run.operator.getMap() != target.getMap()) {
            AgentMapGatewayRuntime.map().changeMapNear(
                    run.operator, target.getMap(), target.getPosition());
        }
    }

    private static void returnObserver(Run run) {
        if (!run.spectating) return;
        MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                run.operator.getWorld(), AgentClientGatewayRuntime.clients().channel(run.operator),
                AgentEpqDefinition.RECRUIT_MAP);
        if (recruit != null) {
            Point spawn = recruit.getRandomPlayerSpawnpoint() == null ? new Point(0, 0)
                    : recruit.getRandomPlayerSpawnpoint().getPosition();
            AgentMapGatewayRuntime.map().changeMapNear(run.operator, recruit, spawn);
        }
        run.spectating = false;
        run.followId = 0;
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No EPQ test is active.");
        if (run.session == null) return List.of("EPQ Agents prepared: " + run.agents.size()
                + "/" + run.agentBranches.size(), "flow: " + run.flow);
        Character leader = online(run.session.eventLeaderId());
        AgentRuntimeEntry entry = leader == null ? null
                : AgentRuntimeRegistry.findByAgentCharacterId(leader.getId());
        String leaderState = leader == null ? "leader: offline"
                : "leader: " + leader.getName() + " map=" + leader.getMapId()
                + " pos=" + leader.getPosition()
                + (entry == null ? " runtime=missing"
                : " target=" + AgentMoveTargetStateRuntime.moveTarget(entry)
                + " simulation=" + entry.simulationState().mode()
                + "/" + entry.simulationState().abstractExecutionScope()
                + " ownership=" + entry.capabilityStates()
                .require(AgentActivityOwnershipState.STATE_KEY).snapshot().status());
        String stageState = stageState(run, leader);
        List<String> lines = new ArrayList<>();
        lines.add("EPQ phase: " + run.session.phase());
        lines.add("members: " + run.session.memberCount());
        lines.add("last progress: " + run.session.lastProgressAtMs());
        lines.add(leaderState);
        lines.add(stageState);
        if (leader != null && (leader.getMapId() == AgentEpqDefinition.STAGE_TWO_MAP
                || leader.getMapId() == AgentEpqDefinition.STAGE_FOUR_MAP)) {
            for (AgentEpqMemberState member : run.session.members()) {
                Character character = online(member.characterId());
                if (character == null) continue;
                AgentRuntimeEntry memberEntry = AgentRuntimeRegistry.findByAgentCharacterId(character.getId());
                lines.add("stage member: " + character.getName()
                        + " pos=" + character.getPosition()
                        + " target=" + (memberEntry == null ? "runtime-missing"
                        : AgentMoveTargetStateRuntime.moveTarget(memberEntry))
                        + " poison=" + character.countItem(AgentEpqDefinition.POISON)
                        + " purified=" + character.countItem(AgentEpqDefinition.PURIFIED_POISON)
                        + " capture=" + character.countItem(AgentEpqDefinition.PURIFICATION_MARBLE)
                        + " marbles=" + character.countItem(AgentEpqDefinition.MONSTER_MARBLE));
            }
        }
        lines.add(run.session.failure().isBlank() ? "failure: none" : "failure: " + run.session.failure());
        return List.copyOf(lines);
    }

    private static String stageState(Run run, Character leader) {
        if (leader == null || leader.getMap() == null) {
            return "stage state: n/a";
        }
        if (leader.getMapId() == AgentEpqDefinition.STAGE_FOUR_MAP) {
            var flowers = leader.getMap().getAllMonsters().stream()
                    .filter(monster -> monster.isAlive()
                            && monster.getId() == AgentEpqDefinition.POISON_FLOWER)
                    .toList();
            long minimumHp = flowers.stream().mapToLong(server.life.Monster::getHp).min().orElse(0L);
            long maximumHp = flowers.stream().mapToLong(server.life.Monster::getHp).max().orElse(0L);
            int captures = 0;
            int marbles = 0;
            for (AgentEpqMemberState member : run.session.members()) {
                Character character = online(member.characterId());
                if (character == null) continue;
                captures += character.countItem(AgentEpqDefinition.PURIFICATION_MARBLE);
                marbles += character.countItem(AgentEpqDefinition.MONSTER_MARBLE);
            }
            return "stage 4: flowers=" + flowers.size() + " hp=" + minimumHp + ".." + maximumHp
                    + " capture=" + captures + " marbles=" + marbles;
        }
        if (leader.getMapId() != AgentEpqDefinition.STAGE_TWO_MAP) return "stage state: n/a";
        int poison = 0;
        int purified = 0;
        for (AgentEpqMemberState member : run.session.members()) {
            Character character = online(member.characterId());
            if (character == null) continue;
            poison += character.countItem(AgentEpqDefinition.POISON);
            purified += character.countItem(AgentEpqDefinition.PURIFIED_POISON);
        }
        int poisonDrops = 0;
        int purifiedDrops = 0;
        for (var drop : leader.getMap().getDroppedItems()) {
            if (drop.getItemId() == AgentEpqDefinition.POISON) poisonDrops++;
            if (drop.getItemId() == AgentEpqDefinition.PURIFIED_POISON) purifiedDrops++;
        }
        var pond = leader.getMap().getReactorById(AgentEpqDefinition.POND_REACTOR);
        var spine = leader.getMap().getReactorById(AgentEpqDefinition.SPINE_REACTOR);
        return "stage 2: mobs=" + leader.getMap().countMonsters()
                + " poison=" + poison + "/drops=" + poisonDrops
                + " purified=" + purified + "/drops=" + purifiedDrops
                + " pond=" + (pond == null ? "missing" : pond.getState())
                + " spine=" + (spine == null ? "missing" : spine.getState());
    }

    private static List<String> pause(Character operator, boolean paused) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active EPQ session.");
        run.session.setPaused(paused);
        return List.of(paused ? "EPQ paused." : "EPQ resumed.");
    }

    private static synchronized List<String> stop(Character operator, String reason, long nowMs) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No EPQ test is active.");
        close(run, reason, nowMs);
        return List.of("EPQ test stopped safely.");
    }

    private static void fail(Run run, String reason) {
        if (run == null || !RUNS.remove(run.operator.getId(), run)) return;
        log.warn("EPQ observation stopped operator={} reason={}",
                run.operator.getName(), reason);
        close(run, reason, System.currentTimeMillis());
        run.operator.dropMessage(6, "EPQ stopped safely: " + reason);
    }

    private static void close(Run run, String reason, long nowMs) {
        returnObserver(run);
        if (run.session != null) AgentEpqTerminationService.release(run.session, reason, nowMs, true);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), nowMs);
        if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, nowMs);
        }
        run.agents.forEach(AgentEpqTestService::disconnect);
    }

    private static void resetReservedAgents(long nowMs) {
        for (Run active : List.copyOf(RUNS.values())) {
            RUNS.remove(active.operator.getId(), active);
            close(active, "replaced by another EPQ test", nowMs);
        }
        for (String name : NAMES) {
            AgentRuntimeEntry stale = AgentRuntimeRegistry.findByAgentName(name);
            if (stale != null) disconnect(AgentRuntimeIdentityRuntime.botId(stale));
        }
    }

    private static void disconnect(int id) {
        Character agent = online(id);
        AgentRuntimeCleanupService.removeAgentByCharacterId(id);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }

    private static Character online(int id) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }

    private static long seed(String[] params, int index, long fallback) {
        return params.length > index ? Long.parseLong(params[index]) : fallback;
    }

    private static List<String> help() {
        return List.of(
                "!epqtest start [seed] (five Agents, one per Explorer class)",
                "!epqtest humanleader [seed] (you lead four missing-class Agents)",
                "!epqtest agentleader [seed] (an Agent leads; accept the invitation)",
                "!epqtest spectate | return (five-Agent run only)",
                "!epqtest invite | status | pause | resume | stop");
    }

    private enum Flow { AGENTS_ONLY, HUMAN_LEADER, AGENT_LEADER }

    private static final class Run {
        final Object lock = new Object();
        final Character operator;
        final AgentPartyQuestEngagement engagement;
        final long seed;
        final long startedAtMs;
        final Flow flow;
        final List<Job> agentBranches;
        final Set<Integer> agents = new LinkedHashSet<>();
        volatile int eventLeaderId;
        volatile boolean inviteSent;
        volatile boolean spectating;
        volatile int followId;
        volatile AgentPartyQuestLobbySession lobby;
        volatile AgentEpqSession session;

        Run(Character operator, AgentPartyQuestEngagement engagement, long seed,
            Flow flow, List<Job> agentBranches) {
            this.operator = operator;
            this.engagement = engagement;
            this.seed = seed;
            this.startedAtMs = engagement.startedAtMs();
            this.flow = flow;
            this.agentBranches = List.copyOf(agentBranches);
        }
    }
}
