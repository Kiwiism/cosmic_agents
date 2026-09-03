package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.field.AgentKpqTestFixtureService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.capabilities.combat.AgentCombatVariationRuntime;
import server.agents.capabilities.combat.AgentCombatVariationSettings;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyProfile;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyReconciler;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestTestQueueRuntime;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.integration.PartyQuestGateway;
import scripting.event.EventInstanceManager;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/** GM-only KPQ observation harness. Production admission does not depend on this class. */
public final class AgentKpqTestService {
    private static final PartyQuestGateway KPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final Logger log = LoggerFactory.getLogger(AgentKpqTestService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.SPAWN_STAGGER_MS");
    private static final int ROSTER_SIZE = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.ROSTER_SIZE");
    private static final long ASSEMBLY_STALL_WARN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.ASSEMBLY_STALL_WARN_MS");
    private static final long MISSING_MEMBER_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.MISSING_MEMBER_TIMEOUT_MS");
    private static final long PARTY_REPAIR_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.PARTY_REPAIR_RETRY_MS");
    private static final long ACCURACY_PILL_POLL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqTestService.ACCURACY_PILL_POLL_MS");
    private static final List<String> MELEE_CAREERS = List.of(
            "warrior", "thief-dagger", "pirate-knuckle");
    private static final List<String> RANGED_CAREERS = List.of(
            "bowman", "magician", "thief-claw", "pirate-gun");
    private static final List<String> FIXED_FOUR_AGENT_CAREERS = List.of(
            "thief-claw", "pirate-knuckle", "warrior", "magician");
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
                case "4" -> start(operator, new StartOptions(4, seed(params, 1, nowMs), false),
                        1, nowMs, FIXED_FOUR_AGENT_CAREERS);
                case "start" -> start(operator, startOptions(params, 1, nowMs), 1, nowMs);
                case "withme" -> startMixed(operator, MixedFlow.HUMAN_INVITES_AGENTS,
                        seed(params, 1, nowMs), nowMs);
                case "invite" -> startMixed(operator, MixedFlow.AGENT_INVITES_HUMAN,
                        seed(params, 1, nowMs), nowMs);
                case "wait" -> startMixed(operator, MixedFlow.HUMAN_INVITES_AGENTS,
                        seed(params, 1, nowMs), nowMs);
                case "party", "adopt" -> adoptCurrentParty(operator, seed(params, 1, nowMs), nowMs);
                case "checkpoint" -> checkpoint(operator, params, nowMs);
                case "complete" -> complete(operator, integerAt(params, 1), nowMs);
                case "status" -> status(operator);
                case "spectate" -> spectate(operator);
                case "return" -> returnFromSpectating(operator);
                case "pause" -> pause(operator, true);
                case "resume", "continue" -> pause(operator, false);
                case "coordination", "membercoord" -> memberCoordination(operator, params);
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
            Character operator, StartOptions options, int checkpoint, long nowMs) throws Exception {
        return start(operator, options, checkpoint, nowMs, List.of());
    }

    private static List<String> start(
            Character operator,
            StartOptions options,
            int checkpoint,
            long nowMs,
            List<String> fixedCareers) throws Exception {
        int size = options.partySize();
        long seed = options.seed();
        AgentKpqWatchdogRuntime.ensureStarted();
        if (operator.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
            return List.of("Stand in Kerning City (103000000) before starting KPQ.");
        }
        Run old = RUNS.get(operator.getId());
        ArrayList<String> response = new ArrayList<>();
        if (old != null) {
            response.addAll(stop(operator));
        }
        List<String> names = shuffledRoster(seed).subList(0, size);
        Map<String, String> requestedCareers = !fixedCareers.isEmpty()
                ? fixedCareerAssignments(names, fixedCareers)
                : options.balanced() ? balancedCareerAssignments(names, seed) : Map.of();
        for (String name : names) ensureBackingCharacter(operator, name);
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "kpq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                seed, operator.getId(), size, nowMs);
        Run run = new Run(operator, engagement, seed, new LinkedHashSet<>(names), true,
                MixedFlow.AGENTS_ONLY, AgentKpqSession.PartyOwnership.KPQ_OWNED, checkpoint,
                requestedCareers);
        RUNS.put(operator.getId(), run);
        try {
            AgentPartyQuestEngagementRegistry.register(engagement);
            openLobby(run, nowMs);
        } catch (RuntimeException failure) {
            RUNS.remove(operator.getId(), run);
            AgentPartyQuestEngagementRegistry.remove(engagement);
            throw failure;
        }
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int ordinal = i;
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal, false), SPAWN_STAGGER_MS * i);
        }
        response.add("KPQ test " + engagement.engagementId()
                + " entered the KPQ lobby recruiting " + size
                + " Agents (seed " + seed + ", checkpoint stage " + checkpoint + ").");
        response.add("Roster order is intentionally shuffled: " + names);
        if (!fixedCareers.isEmpty()) {
            response.add("Fixed combat roster: " + requestedCareers);
        } else if (options.balanced()) {
            response.add("Balanced combat roster (2 melee, 2 ranged): " + requestedCareers);
        }
        return response;
    }

    private static List<String> startMixed(
            Character operator, MixedFlow flow, long seed, long nowMs) throws Exception {
        AgentKpqWatchdogRuntime.ensureStarted();
        if (operator.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
            return List.of("Stand in Kerning City (103000000) before starting a mixed KPQ test.");
        }
        AgentPartyQuestLobbyProfile lobbyProfile = AgentKpqLobbyProfile.profile();
        if (operator.getLevel() < lobbyProfile.minimumLevel()
                || operator.getLevel() > lobbyProfile.maximumLevel()) {
            return List.of("Your character must be level 21-30 to join this KPQ test.");
        }
        ArrayList<String> response = new ArrayList<>();
        Run old = RUNS.get(operator.getId());
        if (old != null) response.addAll(stop(operator));

        AgentPartySnapshot existingParty = AgentPartyGatewayRuntime.party().snapshot(operator);
        if (existingParty != null) {
            return List.of("Leave your current party before using this command. "
                    + "Use !kpqtest party to adopt a party you assembled manually.");
        }

        List<String> names = shuffledRoster(seed).subList(0, 3);
        for (String name : names) ensureBackingCharacter(operator, name);
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "kpq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                seed, operator.getId(), 4, nowMs);
        AgentKpqSession.PartyOwnership ownership = flow == MixedFlow.HUMAN_INVITES_AGENTS
                ? AgentKpqSession.PartyOwnership.EXTERNAL
                : AgentKpqSession.PartyOwnership.KPQ_OWNED;
        Run run = new Run(operator, engagement, seed, new LinkedHashSet<>(names), true,
                flow, ownership, 1, Map.of());
        if (flow != MixedFlow.AGENT_INVITES_HUMAN) {
            engagement.addMember(operator.getId(), AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
            run.eventLeaderId = operator.getId();
        }
        RUNS.put(operator.getId(), run);
        try {
            AgentPartyQuestEngagementRegistry.register(engagement);
            openLobby(run, nowMs);
        } catch (RuntimeException failure) {
            RUNS.remove(operator.getId(), run);
            throw failure;
        }
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int ordinal = i;
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal, false), SPAWN_STAGGER_MS * i);
        }
        response.add(switch (flow) {
            case AGENT_INVITES_HUMAN -> "Creating an Agent-led KPQ party. Say 'I'm joining' "
                    + "or 'looking for kpq' nearby, then accept "
                    + names.getFirst() + "'s invitation.";
            case HUMAN_INVITES_AGENTS -> "Spawned unpartied KPQ seekers " + names
                    + ". They will advertise for a party; create one and invite each Agent yourself.";
            default -> throw new IllegalStateException("Unsupported mixed KPQ flow");
        });
        if (flow == MixedFlow.HUMAN_INVITES_AGENTS) {
            response.add("Only your level 21-30 Kerning party leader can recruit these waiting Agents.");
        }
        return response;
    }

    private static List<String> adoptCurrentParty(Character operator, long seed, long nowMs) {
        if (operator.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
            return List.of("Stand in Kerning City (103000000) before adopting a mixed KPQ party.");
        }
        if (RUNS.containsKey(operator.getId())) {
            return List.of("Stop the current KPQ test before adopting another party; "
                    + "this prevents the old engagement cleanup from disbanding the party being adopted.");
        }
        AgentPartySnapshot snapshot = AgentPartyGatewayRuntime.party().snapshot(operator);
        if (snapshot == null) return List.of("Create the desired party before using !kpqtest party.");
        List<Character> online = AgentPartyGatewayRuntime.party().onlineMembers(operator).stream()
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (online.size() != snapshot.members().size()) {
            return List.of("Every party member must be online on this channel before the mixed KPQ test starts.");
        }
        if (online.size() < AgentKpqRecruitmentPolicy.MIN_PARTY_SIZE
                || online.size() > AgentKpqRecruitmentPolicy.MAX_PARTY_SIZE) {
            return List.of("The mixed KPQ test requires a total party size of 3 or 4.");
        }
        for (Character member : online) {
            if (member.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
                return List.of(member.getName() + " must be in Kerning City before the test starts.");
            }
            if (member.getLevel() < 21 || member.getLevel() > 30) {
                return List.of(member.getName() + " must be level 21-30 for KPQ.");
            }
        }
        int leaderId = snapshot.members().stream()
                .filter(java.util.Objects::nonNull)
                .filter(server.agents.integration.AgentPartyMemberSnapshot::leader)
                .mapToInt(server.agents.integration.AgentPartyMemberSnapshot::id)
                .findFirst().orElse(0);
        Character leader = online.stream().filter(member -> member.getId() == leaderId).findFirst().orElse(null);
        if (leader == null) return List.of("The current party leader must be online on this channel.");

        ArrayList<String> response = new ArrayList<>();
        AgentKpqAdmissionService.AdmissionResult result = AgentKpqAdmissionService.admit(
                operator, leader, online, seed, nowMs, AgentKpqSession.Mode.TEST_OBSERVATION);
        if (!result.success()) {
            response.add("Could not adopt the mixed party: " + result.message());
            return response;
        }
        Set<String> agentNames = online.stream()
                .filter(member -> AgentRuntimeRegistry.findByAgentCharacterId(member.getId()) != null)
                .map(Character::getName)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Run run = new Run(operator, result.engagement(), seed, agentNames, false,
                MixedFlow.ADOPTED, AgentKpqSession.PartyOwnership.EXTERNAL, 1, Map.of());
        run.session = result.session();
        run.eventLeaderId = leader.getId();
        RUNS.put(operator.getId(), run);
        long humans = online.size() - agentNames.size();
        response.add("Adopted current party for mixed KPQ observation: " + humans + " human(s), "
                + agentNames.size() + " Agent(s), leader " + leader.getName() + ", seed " + seed + '.');
        return response;
    }

    private static void launch(Run run, String name, int ordinal, boolean replacement) {
        synchronized (run.launchLock) {
            if (RUNS.get(run.operator.getId()) != run
                    || run.engagement.state() == AgentPartyQuestEngagement.State.FAILED
                    || run.engagement.state() == AgentPartyQuestEngagement.State.CLOSED) return;
            Character launched = null;
            try {
                MapleMap map = AgentMapGatewayRuntime.map().resolveMap(run.operator.getWorld(),
                        AgentClientGatewayRuntime.clients().channel(run.operator), AgentKpqDefinition.RECRUIT_MAP);
                Point npc = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .npcPosition(run.operator, AgentKpqDefinition.ENTRY_NPC);
                Point candidate = new Point((npc == null ? -260 : npc.x) + 55 + ordinal * 42,
                        npc == null ? 155 : npc.y);
                Point spawn = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
                if (spawn == null) {
                    var spawnpoint = map == null ? null : map.getRandomPlayerSpawnpoint();
                    Point fallback = spawnpoint == null ? null : spawnpoint.getPosition();
                    spawn = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, fallback);
                    if (spawn == null) {
                        spawn = new Point(0, 0);
                    }
                }
                AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                        .spawnStationaryAgentForLeaderAt(run.operator, name, map, spawn);
                if (!result.success()) throw new IllegalStateException(result.errorMessage());
                Character agent = result.agent();
                launched = agent;
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry == null) {
                    throw new IllegalStateException("spawned Agent runtime is unavailable");
                }
                String requestedCareer = run.requestedCareerByName.get(name);
                var prepared = AgentKpqTestFixtureService.prepare(
                        entry, requestedCareer, run.seed + ordinal * 10_007L, System.currentTimeMillis());
                run.assignedCareerByCharacterId.put(agent.getId(), prepared.career());
                AgentCombatVariationRuntime.configure(entry, new AgentCombatVariationSettings(
                        run.seed + ordinal * 10_007L, true, 0.35d, 8, true, 0.50d));
                if (prepared.minimumHitChance() < 0.60d) {
                    log.warn("KPQ fixture {} reached only {}% hit rate at level {}",
                            name, String.format("%.0f", prepared.minimumHitChance() * 100), prepared.level());
                }
                log.info("KPQ fixture {} career={} completeBuild={} remainingAp={} remainingSp={} weapon={} attack={}",
                        name, prepared.career(), prepared.completeBuild(), prepared.remainingAp(),
                        Arrays.toString(prepared.remainingSps()), prepared.weaponItemId(), prepared.weaponAttack());
                AgentMapGatewayRuntime.map().changeMapNear(agent, map, spawn);
                if (!AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, agent,
                        "entering KPQ test", System.currentTimeMillis())) {
                    throw new IllegalStateException("Agent activity did not release for KPQ: " + name);
                }
                long memberNowMs = System.currentTimeMillis();
                int cohortSize = replacement ? 1
                        : run.flow == MixedFlow.HUMAN_INVITES_AGENTS
                        ? run.engagement.requestedPartySize() : run.usedNames.size();
                var queued = AgentPartyQuestTestQueueRuntime.enqueue(
                        AgentKpqLobbyProfile.profile(), entry, agent, cohortSize, memberNowMs,
                        (candidateAgent, admittedAtMs) ->
                                admitQueued(run, candidateAgent, replacement, admittedAtMs));
                if (queued.status() != server.agents.runtime.activity.session.AgentActivityAdmissionResult.Status.ACCEPTED) {
                    throw new IllegalStateException(queued.reason());
                }
            } catch (Exception failure) {
                if (launched != null) disconnect(launched.getId());
                log.warn("Could not launch KPQ fixture {}", name, failure);
                failRun(run, "Could not launch " + name + ": " + failure.getMessage(),
                        System.currentTimeMillis());
            }
        }
    }

    private static void admitQueued(
            Run run, Character agent, boolean replacement, long nowMs) {
        synchronized (run.launchLock) {
            if (RUNS.get(run.operator.getId()) != run) {
                throw new IllegalStateException("KPQ test is no longer active");
            }
            AgentPartyQuestEngagementRegistry.addAndIndexMember(
                    run.engagement, agent.getId(), AgentPartyQuestEngagement.MemberType.AGENT, nowMs);
            if (AgentPartyGatewayRuntime.party().snapshot(agent) == null) joinSessionParty(run, agent);
            AgentPartyQuestLobbySession lobby = run.lobby;
            if (lobby != null && lobby.active()) {
                AgentPartyQuestLobbyRegistry.addAndIndexMember(
                        lobby, agent.getId(), AgentPartyQuestLobbySession.MemberType.AGENT,
                        agent.getId() == run.eventLeaderId
                                ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                                : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
                if (lobby.coordinatorAgentId() == 0) lobby.setCoordinatorAgentId(agent.getId());
            }
            if (agent.getId() == run.eventLeaderId) {
                AgentKpqDialogue.sayMapNow(agent, "Recruiting for KPQ.");
            } else {
                AgentKpqDialogue.sayMapNow(agent, "Joining KPQ.");
            }
            if (!replacement) rosterLaunchProgress(run);
        }
    }

    private static void joinSessionParty(Run run, Character agent) {
        Character leader = onlineCharacter(run.eventLeaderId);
        if (leader == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                throw new IllegalStateException("Could not create the Agent-led KPQ party");
            }
            run.eventLeaderId = agent.getId();
            return;
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            throw new IllegalStateException("Could not join " + agent.getName() + " to the KPQ party");
        }
        AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
    }

    private static void openLobby(Run run, long nowMs) {
        AgentPartyQuestLobbySession old = run.lobby;
        if (old != null && old.active()) AgentPartyQuestLobbyRuntime.unregister(old.lobbyId(), nowMs);
        AgentPartyQuestLobbySession lobby = new AgentPartyQuestLobbySession(
                run.engagement.engagementId(), AgentKpqLobbyProfile.profile(),
                run.seed ^ run.runOrdinal, run.operator.getId(),
                run.engagement.requestedPartySize(), AgentPartyQuestCandidateScope.OWNER_ONLY, nowMs);
        for (var member : run.engagement.members().entrySet()) {
            boolean agent = member.getValue() == AgentPartyQuestEngagement.MemberType.AGENT;
            AgentPartyQuestLobbySession.MemberRole role;
            if (!agent) role = AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER;
            else if (run.flow == MixedFlow.HUMAN_INVITES_AGENTS) {
                role = AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY;
            } else if (member.getKey() == run.eventLeaderId) {
                role = AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER;
            } else {
                role = AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER;
            }
            lobby.addMember(member.getKey(), agent
                            ? AgentPartyQuestLobbySession.MemberType.AGENT
                            : AgentPartyQuestLobbySession.MemberType.HUMAN,
                    role, nowMs);
        }
        run.engagement.agentIds().stream().findFirst().ifPresent(lobby::setCoordinatorAgentId);
        AgentPartyQuestLobbyRuntime.register(lobby, nowMs);
        run.engagement.beginLobby(lobby.lobbyId(), nowMs);
        run.lobby = lobby;
        run.session = null;
        startAssemblyMonitor(run);
    }

    private static void failRun(Run run, String reason, long nowMs) {
        if (run == null || !RUNS.remove(run.operator.getId(), run)) return;
        log.error("KPQ test engagement failed: engagement={} state={} reason={}",
                run.engagement.engagementId(), run.engagement.state(), reason);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), nowMs);
        AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, nowMs);
        if (run.ownsAgents) {
            run.engagement.agentIds().forEach(AgentKpqTestService::disconnect);
        }
        run.operator.dropMessage(6, "KPQ test stopped safely: " + reason);
    }

    private static void rosterLaunchProgress(Run run) {
        switch (run.flow) {
            case AGENTS_ONLY -> attemptLobbyHandoff(run);
            case AGENT_INVITES_HUMAN -> {
                if (run.engagement.agentIds().size() == 3) {
                    startAssemblyMonitor(run);
                }
            }
            case HUMAN_INVITES_AGENTS -> {
                if (run.engagement.agentIds().size() == 3) {
                    startAssemblyMonitor(run);
                }
            }
            case ADOPTED -> { }
        }
    }

    private static void startAssemblyMonitor(Run run) {
        if (run.assemblyMonitorStarted) return;
        run.assemblyMonitorStarted = true;
        AgentSchedulerRuntime.schedule(() -> monitorAssembly(run), 500L);
    }

    private static void monitorAssembly(Run run) {
        AgentPartyQuestLobbySession lobby = run.lobby;
        if (RUNS.get(run.operator.getId()) != run || lobby == null
                || !lobby.active()
                || run.engagement.state() != AgentPartyQuestEngagement.State.LOBBY_FORMING) {
            return;
        }
        if (lobby.paused()) {
            AgentSchedulerRuntime.schedule(() -> monitorAssembly(run), 500L);
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (!liveLobbyAgents(run, nowMs)) return;
        repairOwnedLobbyParty(run, nowMs);
        if (attemptLobbyHandoff(run)) {
            return;
        }
        // A successful handoff clears run.lobby while this monitor is still scheduled.
        if (run.lobby != lobby || !lobby.active()
                || run.engagement.state() != AgentPartyQuestEngagement.State.LOBBY_FORMING) {
            return;
        }
        if (nowMs - lobby.lastProgressAtMs() >= Math.max(10_000L, ASSEMBLY_STALL_WARN_MS)
                && nowMs - run.lastLobbyWarningAtMs >= Math.max(10_000L, ASSEMBLY_STALL_WARN_MS)) {
            run.lastLobbyWarningAtMs = nowMs;
            String diagnostic = "Lobby assembly is waiting for a valid "
                    + run.engagement.memberIds().size() + '/' + run.engagement.requestedPartySize()
                    + " party roster; Agents remain owned by the lobby system";
            run.engagement.addDiagnostic(diagnostic, nowMs);
            run.operator.dropMessage(6, diagnostic + '.');
        }
        AgentSchedulerRuntime.schedule(() -> monitorAssembly(run), 500L);
    }

    private static boolean liveLobbyAgents(Run run, long nowMs) {
        boolean missing = run.engagement.agentIds().stream()
                .anyMatch(id -> character(id) == null);
        if (!missing) {
            run.missingAgentSinceMs = 0L;
            return true;
        }
        if (run.missingAgentSinceMs == 0L) run.missingAgentSinceMs = nowMs;
        if (nowMs - run.missingAgentSinceMs >= Math.max(5_000L, MISSING_MEMBER_TIMEOUT_MS)) {
            failRun(run, "A required Agent disappeared while the KPQ lobby was forming", nowMs);
            return false;
        }
        return true;
    }

    private static void repairOwnedLobbyParty(Run run, long nowMs) {
        if (run.partyOwnership != AgentKpqSession.PartyOwnership.KPQ_OWNED) return;
        if (nowMs < run.nextPartyRepairAtMs) return;
        run.nextPartyRepairAtMs = nowMs + Math.max(1_000L, PARTY_REPAIR_RETRY_MS);
        Character leader = onlineCharacter(run.eventLeaderId);
        if (leader == null) return;
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(leader)) return;
            party = AgentPartyGatewayRuntime.party().snapshot(leader);
        }
        if (party == null) return;
        Set<Integer> actual = party.members().stream().filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        for (int agentId : run.engagement.agentIds()) {
            if (actual.contains(agentId)) continue;
            Character agent = character(agentId);
            if (agent != null && !AgentPartyGatewayRuntime.party().hasParty(agent)
                    && AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
                AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
            }
        }
    }

    private static boolean attemptLobbyHandoff(Run run) {
        synchronized (run.launchLock) {
            if (RUNS.get(run.operator.getId()) != run || run.lobby == null
                    || !run.lobby.active() || run.lobby.paused()) return false;
            long nowMs = System.currentTimeMillis();
            AgentPartyQuestLobbyReconciler.Snapshot snapshot =
                    AgentPartyQuestLobbyReconciler.reconcile(run.lobby, nowMs);
            if (snapshot.memberIds().size() != run.engagement.requestedPartySize()) return false;
            for (int id : snapshot.memberIds()) {
                if (!run.engagement.memberIds().contains(id)) {
                    Character human = onlineCharacter(id);
                    if (human == null || AgentRuntimeRegistry.findByAgentCharacterId(id) != null) return false;
                    AgentPartyQuestEngagementRegistry.addAndIndexMember(
                            run.engagement, id,
                            AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
                }
            }
            if (!new java.util.HashSet<>(run.engagement.memberIds()).equals(snapshot.memberIds())) return false;
            Character leader = onlineCharacter(snapshot.leaderId());
            if (leader == null) return false;
            run.eventLeaderId = leader.getId();
            run.lobby.markReady(nowMs);
            run.engagement.lobbyReady(nowMs);
            List<Character> party = snapshot.memberIds().stream()
                    .map(AgentKpqTestService::onlineCharacter)
                    .filter(java.util.Objects::nonNull).toList();
            AgentKpqAdmissionService.AdmissionResult result = AgentKpqAdmissionService.admitFromLobby(
                    run.engagement, run.lobby, run.operator, leader, party,
                    run.seed ^ run.runOrdinal, nowMs, AgentKpqSession.Mode.TEST_OBSERVATION,
                    run.partyOwnership);
            if (!result.success()) {
                run.operator.dropMessage(6, "KPQ lobby handoff deferred: " + result.message());
                return false;
            }
            run.session = result.session();
            run.session.setMemberCoordinationChatEnabled(run.memberCoordinationChatEnabled);
            run.session.setRequestedCheckpointStage(run.requestedCheckpointStage);
            run.lobby = null;
            run.assemblyMonitorStarted = false;
            run.runOrdinal++;
            startFixtureMonitor(run);
            run.operator.dropMessage(6, "KPQ party assembled. Gather at Lakelis; "
                    + (run.eventLeaderId == run.operator.getId()
                    ? "you will be prompted to start the PQ."
                    : "the Agent leader will start the PQ."));
            return true;
        }
    }

    private static void startFixtureMonitor(Run run) {
        if (run.fixtureMonitorStarted) return;
        run.fixtureMonitorStarted = true;
        AgentSchedulerRuntime.schedule(() -> monitorFixture(run), 1_000L);
    }

    private static void monitorFixture(Run run) {
        if (RUNS.get(run.operator.getId()) != run
                || run.engagement.state() != AgentPartyQuestEngagement.State.ACTIVE_EVENT) {
            run.fixtureMonitorStarted = false;
            return;
        }
        for (int agentId : run.engagement.agentIds()) {
            if ("magician".equals(run.assignedCareerByCharacterId.get(agentId))) continue;
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agentId);
            if (entry != null && !AgentKpqTestFixtureService.ensureAccuracyPillActive(entry)) {
                log.warn("KPQ fixture could not refresh accuracy pill for Agent {}", agentId);
            }
        }
        AgentSchedulerRuntime.schedule(
                () -> monitorFixture(run), Math.max(5_000L, ACCURACY_PILL_POLL_MS));
    }

    private static List<String> checkpoint(Character operator, String[] params, long nowMs) throws Exception {
        if (params.length < 2) {
            return List.of("Syntax: !kpqtest checkpoint <1-5> [3|4] [balanced] [seed]");
        }
        Integer stageValue = integerAt(params, 1);
        if (stageValue == null) {
            return List.of("Syntax: !kpqtest checkpoint <1-5> [3|4] [balanced] [seed]");
        }
        int stage = stageValue;
        if (stage < 1 || stage > 5) return List.of("Checkpoint stage must be 1-5.");
        return start(operator, startOptions(params, 2, nowMs), stage, nowMs);
    }

    private static List<String> complete(Character operator, Integer requestedStage, long nowMs) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test engagement is active.");
        AgentKpqSession session = run.session;
        if (session == null || AgentKpqSessionRegistry.forOperator(operator.getId()) != session) {
            return List.of("The KPQ party is currently in " + run.engagement.state()
                    + "; complete is available only during an active event stage.");
        }
        Character leader = onlineCharacter(session.eventLeaderId());
        if (leader == null) return List.of("KPQ event leader is no longer online.");
        EventInstanceManager event = KPQ.event(leader);
        if (event == null) return List.of("No active KPQ event found. Start or enter KPQ first.");
        int activeStage = phaseStage(session.phase());
        int stage = requestedStage == null ? activeStage : requestedStage;
        if (stage < 1 || stage > 5) {
            return List.of("Can only complete stages 1-5.");
        }
        if (stage != activeStage) {
            return List.of("The party is currently on Stage " + activeStage
                    + "; complete can only advance the stage being observed.");
        }
        return switch (stage) {
            case 1 -> completeStageOne(session, leader, nowMs);
            case 2, 3, 4 -> completePuzzleStage(session, leader, event, stage, nowMs);
            case 5 -> completeStageFive(session, leader, event, nowMs);
            default -> List.of("Stage " + stage + " is not supported by this command.");
        };
    }

    private static List<String> completeStageOne(AgentKpqSession session,
                                                Character leader,
                                                long nowMs) {
        if (AgentKpqCoordinator.recoverMissingStageOnePassesForTestCommand(
                session, leader, nowMs)) {
            return List.of("Every member had already delivered a pass, but the leader's passes were missing. "
                    + "Applied the diagnostic Stage 1 recovery and opened the ordinary next-stage portal.");
        }
        int needed = session.memberCount() - 1;
        if (leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false) >= needed) {
            boolean cleared = AgentKpqCoordinator.submitReadyStageOne(
                    session, leader, nowMs, "test-complete-command");
            if (!cleared) {
                cleared = AgentKpqCoordinator.forceReadyStageOne(
                        session, leader, nowMs, "test-complete-command");
            }
            return List.of(cleared
                    ? "Stage 1 is clear; each party member will use the ordinary portal to enter Stage 2."
                    : "Stage 1 could not be cleared because the event instance or required passes are unavailable.");
        }
        int totalAdded = 0;
        for (AgentKpqMemberState member : session.members()) {
            if (member.memberType() != AgentKpqMemberState.MemberType.AGENT
                    || member.characterId() == session.eventLeaderId()) {
                continue;
            }
            Character memberCharacter = character(member.characterId());
            if (memberCharacter == null) continue;
            if (member.passCreated()) continue;
            int target = member.couponTarget();
            if (target <= 0) {
                int grid = AgentPartyQuestGatewayRuntime.partyQuest().playerGrid(memberCharacter);
                if (grid < 0) {
                    KPQ.runNpc(memberCharacter, AgentKpqDefinition.CLOTO_NPC);
                    grid = AgentPartyQuestGatewayRuntime.partyQuest().playerGrid(memberCharacter);
                }
                target = AgentKpqDefinition.couponTarget(grid);
            }
            if (target <= 0) {
                return List.of("Could not obtain " + memberCharacter.getName()
                        + "'s Stage 1 coupon question; no completion state was changed.");
            }
            member.markQuestionRequested();
            member.setCouponTarget(target);
            member.setRole(AgentKpqMemberState.Role.COUPON_COLLECTOR);
            int have = memberCharacter.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false);
            int missing = Math.max(0, target - have);
            if (missing > 0) {
                if (!AgentInventoryGatewayRuntime.inventory().addItem(
                        memberCharacter, AgentKpqDefinition.COUPON_ITEM, (short) missing)) {
                    return List.of("Could not supply " + memberCharacter.getName()
                            + " with " + missing + " coupons. Free an ETC slot and run complete again; "
                            + "no stage-clear flag was changed.");
                }
                totalAdded += missing;
            }
        }
        session.markProgress(nowMs);
        AgentKpqDialogue.sayMapNow(narrator(session),
                "Checkpoint supplied the missing coupons. Submit them to Cloto and bring the passes to "
                        + leader.getName() + '.');
        return List.of("Supplied " + totalAdded
                + " missing Stage 1 coupons; the party will now use the ordinary submit, pass-delivery, and portal flow.");
    }

    private static List<String> completePuzzleStage(AgentKpqSession session,
                                                    Character leader,
                                                    EventInstanceManager event,
                                                    int stage,
                                                    long nowMs) {
        AgentKpqDefinition.CombinationStage definition = AgentKpqDefinition.combinationStage(stage);
        String answer = event.getProperty(definition.answerProperty());
        if (answer == null) {
            if (session.member(session.eventLeaderId()).memberType()
                    == AgentKpqMemberState.MemberType.HUMAN) {
                return List.of("The human leader must talk to Cloto once to initialize Stage "
                        + stage + ", then run complete again.");
            }
            KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
            answer = event.getProperty(definition.answerProperty());
        }
        final int answerIndex;
        try {
            answerIndex = Integer.parseInt(answer);
        } catch (NumberFormatException invalidAnswer) {
            return List.of("Could not read the live Stage " + stage
                    + " answer; no completion state was changed.");
        }
        List<Integer> combination = AgentKpqDefinition.answerCombination(stage, answerIndex);
        List<List<Integer>> order = AgentKpqCombinationOrder.forPositionCount(definition.positions().size());
        int attempt = order.indexOf(combination);
        if (attempt < 0) {
            return List.of("The live Stage " + stage + " answer is not in the central formation order.");
        }
        session.setAttemptIndex(attempt);
        session.setCombination(List.of());
        session.markProgress(nowMs);
        return List.of("Stage " + stage + " will form the live answer " + combination
                + ", let Cloto validate it, then walk through the ordinary portal.");
    }

    private static List<String> completeStageFive(AgentKpqSession session,
                                                  Character leader,
                                                  EventInstanceManager event,
                                                  long nowMs) {
        MapleMap stageMap = event.getMapInstance(AgentKpqDefinition.STAGE_5_MAP);
        int have = leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
        int missing = Math.max(0, 10 - have);
        if (missing > 0 && !AgentInventoryGatewayRuntime.inventory().addItem(
                leader, AgentKpqDefinition.PASS_ITEM, (short) missing)) {
            return List.of("The leader had no room for the simulated Stage 5 passes; no monsters were removed.");
        }
        if (stageMap != null) stageMap.killAllMonsters();
        session.markProgress(nowMs);
        AgentKpqDialogue.sayMapNow(narrator(session),
                "King Slime is down. Returning to Cloto with the passes.");
        return List.of("Removed the remaining Stage 5 monsters and supplied " + missing
                + " missing passes; the party will navigate back to Cloto and finish normally.");
    }

    private static int phaseStage(AgentKpqSession.Phase phase) {
        return switch (phase) {
            case STAGE_1 -> 1;
            case STAGE_2 -> 2;
            case STAGE_3 -> 3;
            case STAGE_4 -> 4;
            case STAGE_5 -> 5;
            default -> -1;
        };
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test engagement is active.");
        AgentKpqSession session = run.session;
        ArrayList<String> lines = new ArrayList<>();
        lines.add(run.engagement.engagementId() + " state=" + run.engagement.state()
                + " child=" + (session == null ? "none" : session.sessionId() + ':' + session.phase())
                + " lobby=" + (run.lobby == null ? "none" : run.lobby.lobbyId() + ':' + run.lobby.state())
                + " members=" + run.engagement.memberIds().size() + '/'
                + run.engagement.requestedPartySize() + " seed=" + run.seed + " flow=" + run.flow
                + " memberCoordination=" + (run.memberCoordinationChatEnabled ? "on" : "off")
                + ((session != null && session.paused()) || (run.lobby != null && run.lobby.paused())
                ? " PAUSED" : ""));
        if (session != null && AgentKpqSessionRegistry.forOperator(operator.getId()) == session) {
            session.members().stream().sorted(Comparator.comparingInt(AgentKpqMemberState::characterId))
                    .forEach(member -> {
                        Character agent = onlineCharacter(member.characterId());
                        int couponsHave = agent == null ? -1
                                : agent.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false);
                        String couponStatus = member.passCreated() ? "done"
                                : member.couponTarget() > 0
                                ? (couponsHave >= 0 ? couponsHave + "/" + member.couponTarget()
                                : String.valueOf(member.couponTarget()))
                                : String.valueOf(member.couponTarget());
                        String passStatus = member.passDelivered() ? "delivered"
                                : member.passCreated() ? "created" : "pending";
                        lines.add((agent == null ? "#" + member.characterId() : agent.getName())
                                + " role=" + member.role()
                                + " number=" + member.partyNumber() + " coupons=" + couponStatus
                                + " pass=" + passStatus);
                    });
        } else {
            run.engagement.members().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(member -> {
                        Character character = onlineCharacter(member.getKey());
                        lines.add((character == null ? "#" + member.getKey() : character.getName())
                                + " type=" + member.getValue()
                                + " system=" + run.engagement.state());
                    });
        }
        if (session != null && !session.failure().isBlank()) lines.add("failure=" + session.failure());
        run.engagement.diagnostics().stream().skip(Math.max(0, run.engagement.diagnostics().size() - 3L))
                .forEach(diagnostic -> lines.add("diagnostic=" + diagnostic));
        return lines;
    }

    private static List<String> spectate(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.flow != MixedFlow.AGENTS_ONLY || run.session == null) {
            return List.of("Spectating is available after an Agent-only KPQ session enters its event.");
        }
        Character leader = onlineCharacter(run.session.eventLeaderId());
        EventInstanceManager event = leader == null ? null : KPQ.event(leader);
        if (event == null) return List.of("The Agent party has not entered its private KPQ instance yet.");
        MapleMap map = event.getMapInstance(leader.getMapId());
        var portal = map == null ? null : map.getRandomPlayerSpawnpoint();
        if (map == null || portal == null) return List.of("The current KPQ instance map is unavailable.");
        AgentMapGatewayRuntime.map().changeMapNear(operator, map, portal.getPosition());
        return List.of("Attached to the Agent-only KPQ instance as a non-participant observer.",
                "Do not attack, loot, use NPCs, portals, or formation positions. Use !kpqtest return to leave.");
    }

    private static List<String> returnFromSpectating(Character operator) {
        MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                operator.getWorld(), AgentClientGatewayRuntime.clients().channel(operator),
                AgentKpqDefinition.RECRUIT_MAP);
        var portal = recruit == null ? null : recruit.getRandomPlayerSpawnpoint();
        if (recruit == null) return List.of("The Kerning City return map is unavailable.");
        AgentMapGatewayRuntime.map().changeMapNear(operator, recruit,
                portal == null ? new Point(0, 0) : portal.getPosition());
        return List.of("Returned to Kerning City from KPQ spectating.");
    }

    private static List<String> pause(Character operator, boolean paused) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test engagement is active.");
        if (run.session != null && AgentKpqSessionRegistry.forOperator(operator.getId()) == run.session) {
            run.session.setPaused(paused);
        } else if (run.lobby != null && run.lobby.active()) {
            run.lobby.setPaused(paused);
        } else {
            return List.of("The KPQ test is held outside; there is no active lobby or event to "
                    + (paused ? "pause." : "resume."));
        }
        return List.of("KPQ test " + (paused ? "paused" : "resumed") + '.');
    }

    private static List<String> memberCoordination(Character operator, String[] params) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test engagement is active.");
        if (params == null || params.length < 2) {
            return List.of("Member coordination chat is "
                    + (run.memberCoordinationChatEnabled ? "on" : "off")
                    + ". Use !kpqtest coordination <on|off>.");
        }
        boolean enabled;
        switch (params[1].toLowerCase()) {
            case "on", "true", "1" -> enabled = true;
            case "off", "false", "0" -> enabled = false;
            default -> {
                return List.of("Use !kpqtest coordination <on|off>.");
            }
        }
        run.memberCoordinationChatEnabled = enabled;
        if (run.session != null) run.session.setMemberCoordinationChatEnabled(enabled);
        return List.of("Agent member coordination chat " + (enabled ? "enabled" : "disabled")
                + ". Execution coordination is unchanged.");
    }

    private static List<String> runAgain(Character operator, long nowMs) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test engagement is active.");
        if (run.engagement.state() != AgentPartyQuestEngagement.State.POST_RUN_HOLD) {
            return List.of("A new run can start only while the Agents are waiting outside.");
        }
        if (!ensureHeldParty(run)) {
            return List.of("The held external party no longer matches the KPQ roster. "
                    + "Reassemble it, then use !kpqtest party to adopt it as a new test.");
        }
        run.requestedCheckpointStage = 1;
        openLobby(run, nowMs);
        attemptLobbyHandoff(run);
        return List.of("The current party entered a new KPQ lobby for the next run.");
    }

    private static boolean ensureHeldParty(Run run) {
        Character leader = onlineCharacter(run.eventLeaderId);
        Set<Integer> expected = new java.util.HashSet<>(run.engagement.memberIds());
        AgentPartySnapshot snapshot = leader == null ? null : AgentPartyGatewayRuntime.party().snapshot(leader);
        if (snapshot != null) {
            Set<Integer> actual = snapshot.members().stream().filter(java.util.Objects::nonNull)
                    .map(server.agents.integration.AgentPartyMemberSnapshot::id)
                    .collect(java.util.stream.Collectors.toSet());
            boolean correctLeader = snapshot.members().stream().anyMatch(member -> member != null
                    && member.id() == run.eventLeaderId && member.leader());
            if (correctLeader && actual.equals(expected)) return true;
        }
        if (run.partyOwnership == AgentKpqSession.PartyOwnership.EXTERNAL || leader == null) return false;
        run.engagement.memberIds().stream().map(AgentKpqTestService::onlineCharacter)
                .filter(java.util.Objects::nonNull)
                .filter(AgentPartyGatewayRuntime.party()::hasParty)
                .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty);
        if (!AgentPartyGatewayRuntime.party().createAgentParty(leader)) return false;
        AgentPartySnapshot rebuilt = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (rebuilt == null) return false;
        for (int memberId : run.engagement.memberIds()) {
            if (memberId == leader.getId()) continue;
            Character member = onlineCharacter(memberId);
            if (member == null || !AgentPartyGatewayRuntime.party().joinAgentParty(member, rebuilt.id())) {
                return false;
            }
            if (AgentRuntimeRegistry.findByAgentCharacterId(memberId) != null) {
                AgentPartyGatewayRuntime.party().publishAgentOnline(member, rebuilt.id());
            }
        }
        return true;
    }

    private static List<String> rotate(Character operator, int count, long nowMs) throws Exception {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No KPQ test engagement is active.");
        if (run.engagement.state() != AgentPartyQuestEngagement.State.POST_RUN_HOLD) {
            return List.of("Members can switch only while the party waits outside.");
        }
        if (!run.ownsAgents || run.flow != MixedFlow.AGENTS_ONLY) {
            return List.of("Automatic switching is disabled for an adopted mixed party; change the party manually, then start a new party test.");
        }
        if (count < 1 || count > 2 || count >= run.engagement.memberIds().size()) {
            return List.of("Switch count must be 1 or 2 and cannot remove the whole party.");
        }
        List<Integer> candidates = run.engagement.agentIds().stream()
                .filter(id -> id != run.eventLeaderId).toList();
        ArrayList<Integer> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new java.util.Random(run.seed + nowMs));
        List<String> removed = new ArrayList<>();
        List<String> vacatedCareers = new ArrayList<>();
        for (int memberId : shuffled.subList(0, count)) {
            String career = run.assignedCareerByCharacterId.get(memberId);
            if (run.balancedRoster && career != null) vacatedCareers.add(career);
            Character agent = character(memberId);
            if (agent != null) {
                removed.add(agent.getName());
                run.requestedCareerByName.remove(agent.getName());
                AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
            }
            run.assignedCareerByCharacterId.remove(memberId);
            AgentPartyQuestEngagementRegistry.removeAndUnindexMember(
                    run.engagement, memberId, nowMs);
            disconnect(memberId);
        }
        List<String> available = shuffledRoster(run.seed + nowMs).stream()
                .filter(name -> !run.usedNames.contains(name)).limit(count).toList();
        for (int i = 0; i < available.size(); i++) {
            String name = available.get(i);
            ensureBackingCharacter(operator, name);
            run.usedNames.add(name);
            if (i < vacatedCareers.size()) {
                run.requestedCareerByName.put(name, vacatedCareers.get(i));
            }
            int ordinal = run.usedNames.size();
            AgentSchedulerRuntime.schedule(() -> launch(run, name, ordinal, true),
                    AgentPartyQuestTestQueueRuntime.replacementDelayMs() + SPAWN_STAGGER_MS * i);
        }
        return List.of("Switching out " + removed + " for " + available + ".",
                "Replacements enter the lobby after 30 seconds; use !kpqtest run after they appear in status.");
    }

    private static List<String> stop(Character operator) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No KPQ test engagement is active.");
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId());
        if (run.ownsAgents && run.partyOwnership == AgentKpqSession.PartyOwnership.EXTERNAL) {
            run.engagement.agentIds().stream()
                    .map(AgentKpqTestService::character)
                    .filter(java.util.Objects::nonNull)
                    .filter(AgentPartyGatewayRuntime.party()::hasParty)
                    .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty);
        }
        Character leader = onlineCharacter(run.eventLeaderId);
        EventInstanceManager event = leader == null ? null : KPQ.event(leader);
        if (run.session != null && AgentKpqSessionRegistry.forOperator(operator.getId()) == run.session) {
            AgentKpqTerminationService.stopTest(run.session, event, System.currentTimeMillis());
        }
        if (run.session != null
                && run.partyOwnership == AgentKpqSession.PartyOwnership.KPQ_OWNED) {
            AgentKpqTerminationService.cleanupOwnedParty(run.session);
        }
        run.engagement.agentIds().stream()
                .map(AgentRuntimeRegistry::findByAgentCharacterId)
                .filter(java.util.Objects::nonNull)
                .forEach(entry -> AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry));
        String engagementId = run.engagement.engagementId();
        AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, System.currentTimeMillis());
        if (run.ownsAgents) {
            run.engagement.agentIds().forEach(AgentKpqTestService::disconnect);
        }
        return List.of("Stopped KPQ test " + engagementId
                + ". Backing characters were retained and no PQ lobby remains active.");
    }

    static void closeEventInstance(EventInstanceManager event) {
        AgentKpqTerminationService.closeEvent(event);
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

    private static Character onlineCharacter(int characterId) {
        Character agent = character(characterId);
        return agent != null ? agent
                : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }

    private static Character narrator(AgentKpqSession session) {
        return character(session.formationCallerId());
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

    static StartOptions startOptions(String[] params, int firstOptionIndex, long fallbackSeed) {
        int size = 4;
        int index = firstOptionIndex;
        if (params != null && params.length > index
                && !"balanced".equalsIgnoreCase(params[index])) {
            size = Integer.parseInt(params[index++]);
            if (size < 3 || size > 4) {
                throw new IllegalArgumentException("KPQ test party size must be 3 or 4");
            }
        }
        boolean balanced = false;
        Long requestedSeed = null;
        while (params != null && params.length > index) {
            String option = params[index++];
            if ("balanced".equalsIgnoreCase(option)) {
                if (balanced) throw new IllegalArgumentException("balanced may be specified only once");
                balanced = true;
            } else if (requestedSeed == null) {
                requestedSeed = Long.parseLong(option);
            } else {
                throw new IllegalArgumentException("unexpected KPQ test option: " + option);
            }
        }
        return new StartOptions(size, requestedSeed == null ? fallbackSeed : requestedSeed, balanced);
    }

    static Map<String, String> balancedCareerAssignments(List<String> names, long seed) {
        if (names == null || names.size() < 3 || names.size() > 4) {
            throw new IllegalArgumentException("balanced KPQ rosters require 3 or 4 Agents");
        }
        int meleeCount = names.size() / 2;
        int rangedCount = names.size() - meleeCount;
        ArrayList<String> melee = new ArrayList<>(MELEE_CAREERS);
        ArrayList<String> ranged = new ArrayList<>(RANGED_CAREERS);
        Collections.shuffle(melee, new java.util.Random(seed ^ 0x4D454C45454CL));
        Collections.shuffle(ranged, new java.util.Random(seed ^ 0x52414E474544L));
        ArrayList<String> careers = new ArrayList<>(names.size());
        careers.addAll(melee.subList(0, meleeCount));
        careers.addAll(ranged.subList(0, rangedCount));
        Collections.shuffle(careers, new java.util.Random(seed ^ 0x42414C414E4345L));
        LinkedHashMap<String, String> assignments = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) assignments.put(names.get(i), careers.get(i));
        return Collections.unmodifiableMap(assignments);
    }

    static Map<String, String> fixedCareerAssignments(List<String> names, List<String> careers) {
        if (names == null || careers == null || names.size() != careers.size() || names.isEmpty()) {
            throw new IllegalArgumentException("fixed KPQ roster names and careers must have the same size");
        }
        LinkedHashMap<String, String> assignments = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) assignments.put(names.get(i), careers.get(i));
        return Collections.unmodifiableMap(assignments);
    }

    private static Integer integerAt(String[] params, int index) {
        return params != null && params.length > index ? Integer.valueOf(params[index]) : null;
    }

    private static int count(String[] params, int index) {
        return params.length > index ? Integer.parseInt(params[index]) : 1;
    }

    private static long seed(String[] params, int index, long fallback) {
        return params != null && params.length > index ? Long.parseLong(params[index]) : fallback;
    }

    private static List<String> help() {
        return List.of("!kpqtest 4 [seed] (claw thief, knuckle pirate, warrior, magician)",
                "!kpqtest start [3|4] [balanced] [seed]",
                "!kpqtest withme [seed] (spawn 3 unpartied Agents; invite them yourself)",
                "!kpqtest invite [seed] (request Agent invite through nearby KPQ chat)",
                "!kpqtest wait [seed] (alias for withme)",
                "!kpqtest party [seed] (adopt current mixed party)",
                "!kpqtest checkpoint <1-5> [3|4] [balanced] [seed]",
                "!kpqtest complete [1-5]",
                "!kpqtest coordination <on|off>",
                "!kpqtest status | pause | resume | run | switch <1|2> | stop");
    }

    private static final class Run {
        private final Character operator;
        private final AgentPartyQuestEngagement engagement;
        private final long seed;
        private final Set<String> usedNames;
        private final Map<String, String> requestedCareerByName;
        private final Map<Integer, String> assignedCareerByCharacterId = new ConcurrentHashMap<>();
        private final boolean balancedRoster;
        private final boolean ownsAgents;
        private final MixedFlow flow;
        private final AgentKpqSession.PartyOwnership partyOwnership;
        private final Object launchLock = new Object();
        private volatile AgentPartyQuestLobbySession lobby;
        private volatile AgentKpqSession session;
        private volatile boolean assemblyMonitorStarted;
        private volatile boolean fixtureMonitorStarted;
        private volatile int eventLeaderId;
        private volatile int requestedCheckpointStage;
        private volatile int runOrdinal;
        private volatile long lastLobbyWarningAtMs;
        private volatile long missingAgentSinceMs;
        private volatile long nextPartyRepairAtMs;
        private volatile boolean memberCoordinationChatEnabled =
                AgentKpqSession.defaultMemberCoordinationChatEnabled();

        private Run(Character operator,
                    AgentPartyQuestEngagement engagement,
                    long seed,
                    Set<String> usedNames,
                    boolean ownsAgents,
                    MixedFlow flow,
                    AgentKpqSession.PartyOwnership partyOwnership,
                    int requestedCheckpointStage,
                    Map<String, String> requestedCareers) {
            this.operator = operator;
            this.engagement = engagement;
            this.seed = seed;
            this.usedNames = usedNames;
            this.requestedCareerByName = new ConcurrentHashMap<>(requestedCareers);
            this.balancedRoster = !requestedCareers.isEmpty();
            this.ownsAgents = ownsAgents;
            this.flow = flow;
            this.partyOwnership = partyOwnership;
            this.requestedCheckpointStage = requestedCheckpointStage;
        }
    }

    record StartOptions(int partySize, long seed, boolean balanced) {
    }

    private enum MixedFlow {
        AGENTS_ONLY,
        AGENT_INVITES_HUMAN,
        HUMAN_INVITES_AGENTS,
        ADOPTED
    }
}
