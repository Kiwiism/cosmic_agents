package server.agents.capabilities.partyquest.lpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.auth.AgentAuthorityService;
import server.agents.capabilities.combat.AgentCombatVariationRuntime;
import server.agents.capabilities.combat.AgentCombatVariationSettings;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestCandidateScope;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyReconciler;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRegistry;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbySession;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.field.AgentLpqTestFixtureService;
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
import scripting.event.EventInstanceManager;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** GM-only full LPQ and mixed-party observation harness. */
public final class AgentLpqTestService {
    private static final List<Integer> RECRUITMENT_SLOT_OFFSETS =
            List.of(-145, -95, -45, 5, 55);
    private static final List<String> RESERVED_AGENT_NAMES = java.util.stream.IntStream
            .rangeClosed(1, 6)
            .mapToObj(index -> "LPQer%02d".formatted(index))
            .toList();
    private static final Logger log = LoggerFactory.getLogger(AgentLpqTestService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqTestService.SPAWN_STAGGER_MS");

    private AgentLpqTestService() { }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        if (params == null || params.length == 0) return help();
        try {
            return switch (params[0].toLowerCase()) {
                case "start" -> start(operator, seed(params, 1, nowMs), Flow.AGENTS_ONLY,
                        StartStage.FULL_RUN, AgentLpqSession.HumanRolePreference.DEFAULT, nowMs);
                case "boss" -> start(operator, seed(params, 1, nowMs), Flow.AGENTS_ONLY,
                        StartStage.BOSS, AgentLpqSession.HumanRolePreference.DEFAULT, nowMs);
                case "withme", "humanleader" -> start(operator, mixedSeed(params, 1, nowMs),
                        Flow.HUMAN_LEADER, StartStage.FULL_RUN,
                        humanRolePreference(operator, params, 1), nowMs);
                case "agentleader" -> start(operator, mixedSeed(params, 1, nowMs),
                        Flow.AGENT_LEADER, StartStage.FULL_RUN,
                        humanRolePreference(operator, params, 1), nowMs);
                case "invite" -> invite(operator);
                case "spectate", "attach" -> spectate(operator);
                case "follow" -> follow(operator, params);
                case "stay", "unfollow" -> stay(operator);
                case "return", "detach" -> returnFromSpectating(operator);
                case "status" -> status(operator);
                case "pause" -> pause(operator, true);
                case "resume" -> pause(operator, false);
                case "bonus" -> bonus(operator, params);
                case "stage8chat", "boxchat" -> stage8Chat(operator, params);
                case "run" -> runAgain(operator, nowMs);
                case "stop" -> stop(operator, "stopped by operator", nowMs);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("LPQ test command failed for operator {}", operator.getId(), failure);
            return List.of("LPQ test command failed: " + failure.getMessage());
        }
    }

    private static synchronized List<String> start(
            Character operator, long seed, Flow flow, StartStage startStage,
            AgentLpqSession.HumanRolePreference humanRolePreference, long nowMs) throws Exception {
        if (operator.getMapId() != AgentLpqDefinition.RECRUIT_MAP) {
            return List.of("Stand at the Ludibrium PQ entrance (221024500) first.");
        }
        if (flow != Flow.AGENTS_ONLY && (operator.getLevel() < 35 || operator.getLevel() > 50)) {
            return List.of("Your participating character must be level 35-50.");
        }
        if (AgentPartyGatewayRuntime.party().snapshot(operator) != null) {
            return List.of("Leave your current party before starting this LPQ test.");
        }
        resetReservedTestAgents(operator, nowMs);
        int agentCount = flow == Flow.AGENTS_ONLY ? 6 : 5;
        List<String> names = RESERVED_AGENT_NAMES.subList(0, agentCount);
        for (String name : names) {
            ensureBackingCharacter(operator, name);
        }
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "lpq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                seed, operator.getId(), 6, nowMs);
        if (flow != Flow.AGENTS_ONLY) engagement.addMember(
                operator.getId(), AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
        Run run = new Run(operator, engagement, seed, flow, startStage,
                humanRolePreference, new LinkedHashSet<>(names));
        run.eventLeaderId = flow == Flow.HUMAN_LEADER ? operator.getId() : 0;
        RUNS.put(operator.getId(), run);
        AgentPartyQuestEngagementRegistry.register(engagement);
        openLobby(run, nowMs);
        for (int index = 0; index < names.size(); index++) {
            int ordinal = index;
            AgentSchedulerRuntime.schedule(() -> launch(run, names.get(ordinal), ordinal),
                    SPAWN_STAGGER_MS * index);
        }
        return switch (flow) {
            case AGENTS_ONLY -> List.of(startStage == StartStage.BOSS
                    ? "Six LPQ Agents are preparing for a direct boss-stage test. They will enter the real event, then move together to Stage 9; use !lpqtest spectate after entry."
                    : "Six LPQ Agents are preparing off-screen, then will form up at the Red Sign and announce the five-second start. Use !lpqtest spectate after entry.");
            case HUMAN_LEADER -> List.of("Five LPQ Agents are preparing off-screen. Create a party, invite all five, gather at the Red Sign, then talk to it after the five-second announcement. Human role: "
                    + humanRolePreference + ".");
            case AGENT_LEADER -> List.of("Five LPQ Agents are forming a party and recruiting one. Chat 'looking for LPQ' or 'invite me LPQ', accept the invitation, and gather for the five-second start. Human role: "
                    + humanRolePreference + ". !lpqtest invite remains a manual resend.");
        };
    }

    /**
     * The observation harness deliberately reuses LPQer01..06, so concurrent runs
     * cannot safely coexist. Close any prior harness session and reclaim only those
     * reserved test runtimes before the next operator starts.
     */
    private static void resetReservedTestAgents(Character operator, long nowMs) {
        for (Run active : List.copyOf(RUNS.values())) {
            String reason = active.operator.getId() == operator.getId()
                    ? "replaced by a new LPQ test"
                    : "replaced by LPQ test operator " + operator.getName();
            stop(active.operator, reason, nowMs);
        }
        for (String name : RESERVED_AGENT_NAMES) {
            AgentRuntimeEntry stale = AgentRuntimeRegistry.findByAgentName(name);
            if (stale != null) {
                disconnect(AgentRuntimeIdentityRuntime.botId(stale));
            }
        }
    }

    private static void openLobby(Run run, long nowMs) {
        AgentPartyQuestLobbySession lobby = new AgentPartyQuestLobbySession(
                run.engagement.engagementId(), AgentLpqLobbyProfile.profile(), run.seed,
                run.operator.getId(), 6, AgentPartyQuestCandidateScope.OWNER_ONLY, nowMs);
        if (run.flow != Flow.AGENTS_ONLY) lobby.addMember(run.operator.getId(),
                AgentPartyQuestLobbySession.MemberType.HUMAN,
                run.flow == Flow.HUMAN_LEADER ? AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER
                        : AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY, nowMs);
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
                MapleMap stagingMap = AgentMapGatewayRuntime.map().resolveMap(
                        run.operator.getWorld(), channel, AgentLpqDefinition.STAGING_MAP);
                if (stagingMap == null) {
                    throw new IllegalStateException("LPQ off-screen staging map is unavailable");
                }
                var stagingPortal = stagingMap == null ? null : stagingMap.getRandomPlayerSpawnpoint();
                Point candidate = stagingPortal == null
                        ? new Point(0, 0) : stagingPortal.getPosition();
                Point spawn = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(stagingMap, candidate);
                if (spawn == null) spawn = new Point(0, 0);
                AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                        .spawnStationaryAgentForLeaderAt(run.operator, name, stagingMap, spawn);
                if (!result.success()) throw new IllegalStateException(result.errorMessage());
                launched = result.agent();
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(launched.getId());
                if (entry == null) throw new IllegalStateException("spawned LPQ Agent runtime is unavailable");
                String buildId = AgentLpqTestFixtureService.buildIdForTestParty(
                        ordinal, run.flow != Flow.AGENTS_ONLY, run.humanRolePreference);
                AgentLpqTestFixtureService.PreparationResult prepared = AgentLpqTestFixtureService.prepare(
                        entry, buildId, run.seed + ordinal * 10_007L, System.currentTimeMillis());
                AgentCombatVariationRuntime.configure(entry, new AgentCombatVariationSettings(
                        run.seed + ordinal * 10_007L, true, 0.35d, 8, true, 0.50d));
                MapleMap lobbyMap = AgentMapGatewayRuntime.map().resolveMap(
                        run.operator.getWorld(), channel, AgentLpqDefinition.RECRUIT_MAP);
                if (lobbyMap == null) {
                    throw new IllegalStateException("LPQ recruitment map is unavailable");
                }
                var lobbyPortal = lobbyMap == null ? null : lobbyMap.getRandomPlayerSpawnpoint();
                Point lobbySpawn = lobbyPortal == null ? new Point(0, 0) : lobbyPortal.getPosition();
                var lobbyNpc = lobbyMap.getNPCById(AgentLpqDefinition.ENTRY_NPC);
                Point lobbyAnchor = lobbyNpc == null ? lobbySpawn : lobbyNpc.getPosition();
                Point spacedLobbySpawn = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(
                        lobbyMap, new Point(lobbyAnchor.x + recruitmentSlotOffset(ordinal),
                                lobbyAnchor.y));
                AgentMapGatewayRuntime.map().changeMapNear(
                        launched, lobbyMap, spacedLobbySpawn == null ? lobbySpawn : spacedLobbySpawn);
                if (!AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, launched,
                        "entering LPQ observation", System.currentTimeMillis())) {
                    throw new IllegalStateException(name + " could not release its activity");
                }
                long now = System.currentTimeMillis();
                AgentPartyQuestEngagementRegistry.addAndIndexMember(run.engagement, launched.getId(),
                        AgentPartyQuestEngagement.MemberType.AGENT, now);
                if (run.flow != Flow.HUMAN_LEADER) joinOwnedParty(run, launched);
                AgentPartyQuestLobbyRegistry.addAndIndexMember(run.lobby, launched.getId(),
                        AgentPartyQuestLobbySession.MemberType.AGENT,
                        run.flow == Flow.HUMAN_LEADER ? AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY
                                : launched.getId() == run.eventLeaderId
                                ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                                : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, now);
                if (run.lobby.coordinatorAgentId() == 0) run.lobby.setCoordinatorAgentId(launched.getId());
                log.info("LPQ fixture launched: name={} career={} level={}", name, prepared.career(), prepared.level());
            } catch (Exception failure) {
                if (launched != null) disconnect(launched.getId());
                failRun(run, "Could not launch " + name + ": " + failure.getMessage());
            }
        }
    }

    private static void joinOwnedParty(Run run, Character agent) {
        Character leader = online(run.eventLeaderId);
        if (leader == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) throw new IllegalStateException("could not create LPQ party");
            run.eventLeaderId = agent.getId();
            return;
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            throw new IllegalStateException(agent.getName() + " could not join LPQ party");
        }
        AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
    }

    private static void monitor(Run run) {
        if (RUNS.get(run.operator.getId()) != run) return;
        try {
            if (run.session == null) attemptHandoff(run);
            if (run.session != null && run.startStage == StartStage.BOSS
                    && !run.startStageApplied) attemptBossStageShortcut(run);
            if (run.spectating && run.autoFollow) updateSpectator(run);
            if (run.session != null && run.session.terminal()) {
                if (run.session.phase() == AgentLpqSession.Phase.COMPLETED) holdCompletedRun(run);
                else finishRun(run, "failed: " + run.session.failure());
                return;
            }
        } catch (RuntimeException failure) {
            failRun(run, "LPQ monitor failed: " + failure.getMessage());
            return;
        }
        AgentSchedulerRuntime.schedule(() -> monitor(run), 500L);
    }

    /**
     * Moves a fully registered test party into the real Stage 9 instance without
     * synthesizing the Black Ratz trigger, Alishar, or the boss key. This keeps the
     * shortcut useful as an end-to-end test of the authored trigger and server boss
     * runtime rather than reducing it to a pre-spawned combat fixture.
     */
    private static void attemptBossStageShortcut(Run run) {
        synchronized (run.lock) {
            if (run.startStageApplied || run.session == null) return;
            Character leader = online(run.session.eventLeaderId());
            EventInstanceManager event = leader == null ? null : leader.getEventInstance();
            if (event == null || event.isEventDisposed()) return;
            List<Character> members = run.session.members().stream()
                    .map(member -> online(member.characterId()))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!directBossEntryReady(run.session.requestedPartySize(), event, members)) return;
            MapleMap bossMap = event.getMapInstance(AgentLpqDefinition.stage(9).mapId());
            if (bossMap == null) throw new IllegalStateException("LPQ boss map is unavailable");
            var portal = bossMap.getPortal(0);
            Point spawn = portal == null ? new Point(0, 0) : portal.getPosition();
            synchronized (run.session) {
                // This property makes the direct state consistent with the authored
                // Stage 8 portal, while deliberately leaving Stage 9 uncleared.
                event.setProperty("8stageclear", "true");
                for (Character member : members) {
                    AgentMapGatewayRuntime.map().changeMapNear(member, bossMap, spawn);
                }
                run.session.bindEventInstance(event);
                run.session.transition(AgentLpqSession.Phase.STAGE_9, System.currentTimeMillis());
            }
            run.startStageApplied = true;
            run.operator.dropMessage(6,
                    "Direct LPQ boss test is ready: all six Agents entered Stage 9. The Black Ratz trigger remains live and Alishar was not pre-spawned.");
            log.info("LPQ direct boss-stage test activated: session={} leader={} members={}",
                    run.session.sessionId(), run.session.eventLeaderId(),
                    members.stream().map(Character::getId).toList());
        }
    }

    static boolean directBossEntryReady(
            int requestedPartySize, EventInstanceManager event, List<Character> members) {
        return requestedPartySize > 0 && event != null && !event.isEventDisposed()
                && members != null && members.size() == requestedPartySize
                && members.stream().allMatch(member -> member != null
                && member.getEventInstance() == event
                && member.getMap() != null
                && AgentLpqDefinition.isEventMap(member.getMapId()));
    }

    static int recruitmentSlotOffset(int ordinal) {
        return RECRUITMENT_SLOT_OFFSETS.get(
                Math.floorMod(ordinal, RECRUITMENT_SLOT_OFFSETS.size()));
    }

    private static void attemptHandoff(Run run) {
        synchronized (run.lock) {
            if (run.lobby == null || !run.lobby.active() || run.lobby.paused()) return;
            AgentPartyQuestLobbyReconciler.Snapshot party = AgentPartyQuestLobbyReconciler.reconcile(
                    run.lobby, System.currentTimeMillis());
            if (party.memberIds().size() != 6
                    || party.memberIds().stream().anyMatch(id -> !run.engagement.memberIds().contains(id))) return;
            Character leader = online(party.leaderId());
            List<Character> members = party.memberIds().stream().map(AgentLpqTestService::online)
                    .filter(java.util.Objects::nonNull).toList();
            if (leader == null || members.size() != 6) return;
            long now = System.currentTimeMillis();
            run.lobby.markReady(now);
            run.engagement.lobbyReady(now);
            AgentLpqAdmissionService.AdmissionResult result = AgentLpqAdmissionService.admitFromLobby(
                    run.engagement, run.lobby, run.operator, leader, members,
                    run.seed, now, AgentLpqSession.Mode.TEST_OBSERVATION,
                    run.flow == Flow.AGENTS_ONLY ? 0 : run.operator.getId(),
                    run.humanRolePreference);
            if (!result.success()) return;
            run.eventLeaderId = leader.getId();
            run.session = result.session();
            run.session.setStage8AssignmentChatEnabled(run.stage8AssignmentChatEnabled);
            run.lobby = null;
            run.operator.dropMessage(6, run.flow == Flow.AGENTS_ONLY
                    ? "Six-Agent LPQ assembled. Use !lpqtest spectate after they enter."
                    : run.flow == Flow.HUMAN_LEADER
                    ? "Mixed LPQ assembled. Gather at the Red Sign; after the five-second announcement, talk to it to enter."
                    : "Agent-led mixed LPQ assembled. Gather at the Red Sign for the five-second start.");
        }
    }

    private static void maybeInviteOperator(Run run) {
        synchronized (run.lock) {
            if (run.flow != Flow.AGENT_LEADER || run.inviteSent
                    || AgentPartyGatewayRuntime.party().hasParty(run.operator)
                    || run.engagement.agentIds().size() < 5) return;
            Character leader = online(run.eventLeaderId);
            if (leader != null && AgentPartyGatewayRuntime.party().invitePartyMember(leader, run.operator)) {
                run.inviteSent = true;
                run.operator.dropMessage(6, "Accept " + leader.getName() + "'s LPQ invitation.");
            }
        }
    }

    private static List<String> invite(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.flow != Flow.AGENT_LEADER) return List.of("No Agent-led LPQ invitation is pending.");
        run.inviteSent = false;
        maybeInviteOperator(run);
        return List.of(run.inviteSent ? "LPQ invitation sent." : "LPQ party is still assembling.");
    }

    private static List<String> spectate(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.flow != Flow.AGENTS_ONLY || run.session == null) {
            return List.of("Spectating requires an active Agent-only LPQ session.");
        }
        Character leader = online(run.session.eventLeaderId());
        if (leader == null || leader.getEventInstance() == null) return List.of("The LPQ party has not entered yet.");
        run.spectating = true;
        run.followId = leader.getId();
        updateSpectator(run);
        run.autoFollow = false;
        return List.of("Attached as a non-party LPQ spectator. Manual warps will not auto-follow.",
                "Use !lpqtest follow <leader|name> to follow stage changes, !lpqtest stay to stop following, or return.",
                "Do not attack, loot, use portals/NPCs, or hit reactors.");
    }

    private static List<String> follow(Character operator, String[] params) {
        Run run = RUNS.get(operator.getId());
        if (run == null || !run.spectating || params.length < 2) {
            return List.of("Syntax while spectating: !lpqtest follow <leader|AgentName>");
        }
        if (params[1].equalsIgnoreCase("leader")) run.followId = run.session.eventLeaderId();
        else {
            Character target = run.session.members().stream().map(member -> online(member.characterId()))
                    .filter(java.util.Objects::nonNull).filter(member -> member.getName().equalsIgnoreCase(params[1]))
                    .findFirst().orElse(null);
            if (target == null) return List.of("That LPQ participant is unavailable.");
            run.followId = target.getId();
        }
        run.autoFollow = true;
        updateSpectator(run);
        Character target = online(run.followId);
        return List.of("Following " + (target == null ? run.followId : target.getName()) + '.');
    }

    public static List<String> warpObserver(Character operator, String[] params) {
        Run run = operator == null ? null : RUNS.get(operator.getId());
        if (run == null || run.session == null || !run.spectating) {
            return List.of("Use !lpqtest spectate before !warplpq.");
        }
        if (params == null || params.length == 0) {
            return List.of("Syntax: !warplpq <1-6|leader|scout [1-2]|teleport|darksight|magic|physical|top|bottom|platform|boss|room 501-506>");
        }
        List<AgentLpqMemberState> candidates = warpCandidates(run.session, params);
        if (candidates.isEmpty()) return List.of("No active LPQ member matches that slot or role.");
        int requestedIndex = roleOrdinal(params);
        if (requestedIndex < 0 || requestedIndex >= candidates.size()) {
            return List.of("That role has " + candidates.size() + " active member(s).");
        }
        Character target = online(candidates.get(requestedIndex).characterId());
        if (target == null || target.getMap() == null
                || target.getEventInstance() != run.session.eventInstance()) {
            return List.of("That LPQ participant is currently unavailable.");
        }
        run.followId = target.getId();
        run.autoFollow = false;
        AgentMapGatewayRuntime.map().changeMapNear(
                operator, target.getMap(), target.getPosition());
        return List.of("Warped to LPQ slot " + memberSlot(run.session, target.getId())
                + ": " + target.getName() + " (" + candidates.get(requestedIndex).role() + ").");
    }

    static List<AgentLpqMemberState> warpCandidates(
            AgentLpqSession session, String[] params) {
        if (session == null || params == null || params.length == 0) return List.of();
        List<AgentLpqMemberState> ordered = orderedMembers(session);
        String selector = params[0].toLowerCase(java.util.Locale.ROOT)
                .replace("_", "").replace("-", "");
        try {
            int slot = Integer.parseInt(selector);
            return slot >= 1 && slot <= ordered.size()
                    ? List.of(ordered.get(slot - 1)) : List.of();
        } catch (NumberFormatException ignored) {
            // Named selectors continue below.
        }
        if ("leader".equals(selector)) return ordered.isEmpty()
                ? List.of() : List.of(ordered.getFirst());
        if ("scout".equals(selector) || "scouts".equals(selector)) {
            Set<Integer> scoutIds = Set.copyOf(AgentLpqCoordinator.stageTwoScoutIds(
                    session.members(), session.eventLeaderId(), 2));
            return ordered.stream().filter(member -> scoutIds.contains(member.characterId())).toList();
        }
        int roomMapId = roomSelector(selector, params);
        if (roomMapId != 0) {
            return ordered.stream().filter(member -> {
                Character character = online(member.characterId());
                return member.assignedMapId() == roomMapId
                        || character != null && character.getMapId() == roomMapId;
            }).toList();
        }
        return ordered.stream().filter(member -> roleMatches(selector, member.role())).toList();
    }

    private static List<AgentLpqMemberState> orderedMembers(AgentLpqSession session) {
        return session.members().stream().sorted(java.util.Comparator
                .comparing((AgentLpqMemberState member) ->
                        member.characterId() != session.eventLeaderId())
                .thenComparingInt(AgentLpqMemberState::characterId)).toList();
    }

    private static int memberSlot(AgentLpqSession session, int characterId) {
        List<AgentLpqMemberState> ordered = orderedMembers(session);
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).characterId() == characterId) return index + 1;
        }
        return 0;
    }

    private static int roleOrdinal(String[] params) {
        if (params.length < 2 || "room".equalsIgnoreCase(params[0])) return 0;
        try {
            return Math.max(0, Integer.parseInt(params[1]) - 1);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int roomSelector(String selector, String[] params) {
        String value = selector.startsWith("room") ? selector.substring(4) : "";
        if ("room".equals(selector) && params.length >= 2) value = params[1];
        try {
            int room = Integer.parseInt(value);
            if (room >= 501 && room <= 506) return 922_010_000 + room;
        } catch (NumberFormatException ignored) {
            // Not a room selector.
        }
        return 0;
    }

    private static boolean roleMatches(String selector, AgentLpqMemberState.Role role) {
        return switch (selector) {
            case "teleport", "teleportrunner", "teleportroom" -> role == AgentLpqMemberState.Role.TELEPORT_RUNNER;
            case "darksight", "darksightrunner", "darksightroom" -> role == AgentLpqMemberState.Role.DARK_SIGHT_RUNNER;
            case "magic", "magicattacker" -> role == AgentLpqMemberState.Role.MAGIC_ATTACKER;
            case "physical", "physicalattacker" -> role == AgentLpqMemberState.Role.PHYSICAL_ATTACKER;
            case "top", "trigger", "rangedtrigger" -> role == AgentLpqMemberState.Role.RANGED_TRIGGER;
            case "bottom", "rombard", "bossattacker" -> role == AgentLpqMemberState.Role.BOSS_ATTACKER;
            case "platform", "platformholder" -> role == AgentLpqMemberState.Role.PLATFORM_HOLDER;
            case "mover", "platformmover" -> role == AgentLpqMemberState.Role.PLATFORM_MOVER;
            case "boss" -> role == AgentLpqMemberState.Role.BOSS_ATTACKER;
            default -> false;
        };
    }

    private static void updateSpectator(Run run) {
        if (!run.spectating || run.session == null) return;
        Character target = online(run.followId);
        if (target == null || target.getEventInstance() != run.session.eventInstance()) {
            target = online(run.session.eventLeaderId());
            if (target == null) return;
            run.followId = target.getId();
        }
        if (target.getMap() != null && run.operator.getMap() != target.getMap()) {
            AgentMapGatewayRuntime.map().changeMapNear(run.operator, target.getMap(), target.getPosition());
        }
    }

    private static List<String> returnFromSpectating(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || !run.spectating) return List.of("You are not spectating LPQ.");
        returnObserver(run);
        return List.of("Returned to the LPQ recruitment map.");
    }

    private static List<String> stay(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || !run.spectating) return List.of("You are not spectating LPQ.");
        run.autoFollow = false;
        return List.of("Automatic LPQ following is off. Use !warplpq freely or !lpqtest follow <leader|name> to resume it.");
    }

    private static void returnObserver(Run run) {
        if (!run.spectating) return;
        if (run.operator.getClient() == null
                || run.operator.getClient().getPlayer() != run.operator) {
            run.spectating = false;
            run.autoFollow = false;
            run.followId = 0;
            return;
        }
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(run.operator.getWorld(),
                AgentClientGatewayRuntime.clients().channel(run.operator), AgentLpqDefinition.RECRUIT_MAP);
        var portal = map == null ? null : map.getRandomPlayerSpawnpoint();
        if (map != null) AgentMapGatewayRuntime.map().changeMapNear(run.operator, map,
                portal == null ? new Point(0, 0) : portal.getPosition());
        run.spectating = false;
        run.autoFollow = false;
        run.followId = 0;
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No LPQ test is active.");
        List<String> lines = new ArrayList<>();
        lines.add("LPQ " + run.engagement.state() + ", roster " + run.engagement.memberIds().size() + "/6");
        if (run.engagement.state() == AgentPartyQuestEngagement.State.POST_RUN_HOLD) {
            lines.add("Agents are waiting in the LPQ entrance lobby. Use !lpqtest run for the next run.");
        }
        if (run.lobby != null) lines.add("Lobby " + run.lobby.state());
        if (run.session != null) {
            Character leader = online(run.session.eventLeaderId());
            lines.add("Session " + run.session.phase() + (run.session.paused() ? " (paused)" : "")
                    + ", leader " + (leader == null ? run.session.eventLeaderId() : leader.getName())
                    + ", map " + (leader == null ? -1 : leader.getMapId()));
            lines.add("Leader passes/key " + (leader == null ? "?" : leader.getItemQuantity(AgentLpqDefinition.PASS, false)
                    + "/" + leader.getItemQuantity(AgentLpqDefinition.BOSS_KEY, false))
                    + ", Stage 8 attempt " + (run.session.stage8Attempt() + 1)
                    + ", IGN->box chat " + (run.session.stage8AssignmentChatEnabled() ? "on" : "off")
                    + (run.session.hasHumanMember() ? ", human role "
                    + run.session.humanRolePreference() : ""));
            lines.add("Members: " + run.session.members().stream().map(member -> {
                Character character = online(member.characterId());
                return (character == null ? member.characterId() : character.getName()) + "=" + member.role()
                        + " map:" + (character == null ? -1 : character.getMapId())
                        + (member.assignedMapId() == 0 ? "" : " target:" + member.assignedMapId())
                        + (member.assignedPlatform() == 0 ? "" : " box:" + member.assignedPlatform());
            }).toList());
            lines.add("Rooms active " + run.session.rooms().assignments()
                    + ", completed " + run.session.rooms().completedRooms()
                    + ", maze " + run.session.maze().observations()
                    + ", last progress " + (System.currentTimeMillis() - run.session.lastProgressAtMs()) / 1000L + "s ago");
        }
        if (run.spectating) {
            Character target = online(run.followId);
            lines.add("Spectating " + (target == null ? run.followId : target.getName()));
        }
        return lines;
    }

    private static List<String> pause(Character operator, boolean value) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No LPQ test is active.");
        if (run.session != null) run.session.setPaused(value);
        if (run.lobby != null) run.lobby.setPaused(value);
        return List.of("LPQ " + (value ? "paused" : "resumed") + '.');
    }

    private static List<String> bonus(Character operator, String[] params) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null || params.length < 2) return List.of("Syntax: !lpqtest bonus <skip|enter>");
        run.session.setBonusMode(params[1].equalsIgnoreCase("skip")
                ? AgentLpqSession.BonusMode.SKIP : AgentLpqSession.BonusMode.ENTER);
        return List.of("LPQ bonus policy is " + run.session.bonusMode() + '.');
    }

    private static List<String> stage8Chat(Character operator, String[] params) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No LPQ test is active.");
        if (params.length < 2 || !(params[1].equalsIgnoreCase("on")
                || params[1].equalsIgnoreCase("off"))) {
            return List.of("Syntax: !lpqtest stage8chat <on|off>");
        }
        boolean enabled = params[1].equalsIgnoreCase("on");
        run.stage8AssignmentChatEnabled = enabled;
        if (run.session != null) run.session.setStage8AssignmentChatEnabled(enabled);
        return List.of("Stage 8 IGN->box chat is " + (enabled ? "ON" : "OFF") + '.');
    }

    private static List<String> stop(Character operator, String reason, long nowMs) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No LPQ test is active.");
        returnObserver(run);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), nowMs);
        if (run.session != null && AgentLpqSessionRegistry.active(run.session.executionAgentId())) {
            AgentLpqTerminationService.fail(run.session, reason, nowMs);
        } else if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, nowMs);
        }
        run.engagement.agentIds().forEach(AgentLpqTestService::disconnect);
        return List.of("Stopped LPQ test; backing characters were retained.");
    }

    private static List<String> runAgain(Character operator, long nowMs) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No LPQ test is active.");
        if (run.engagement.state() != AgentPartyQuestEngagement.State.POST_RUN_HOLD) {
            return List.of("A new LPQ can start only while the Agents are waiting in the entrance lobby.");
        }
        if (!heldPartyMatches(run)) {
            return List.of("The held LPQ party changed. Use !lpqtest start to rebuild the six-Agent test party.");
        }
        run.session = null;
        openLobby(run, nowMs);
        for (int agentId : run.engagement.agentIds()) {
            AgentPartyQuestLobbyRegistry.addAndIndexMember(run.lobby, agentId,
                    AgentPartyQuestLobbySession.MemberType.AGENT,
                    agentId == run.eventLeaderId
                            ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                            : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER, nowMs);
        }
        run.lobby.setCoordinatorAgentId(run.eventLeaderId);
        attemptHandoff(run);
        return List.of("The held LPQ party started its next run.");
    }

    private static boolean heldPartyMatches(Run run) {
        Character leader = online(run.eventLeaderId);
        AgentPartySnapshot party = leader == null ? null : AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null) return false;
        Set<Integer> expected = new java.util.HashSet<>(run.engagement.memberIds());
        Set<Integer> actual = party.members().stream().filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        boolean leaderStillLeads = party.members().stream().anyMatch(member -> member != null
                && member.id() == run.eventLeaderId && member.leader());
        return leaderStillLeads && actual.equals(expected);
    }

    private static void holdCompletedRun(Run run) {
        if (RUNS.get(run.operator.getId()) != run) return;
        returnObserver(run);
        run.engagement.agentIds().stream().map(AgentRuntimeRegistry::findByAgentCharacterId)
                .filter(java.util.Objects::nonNull)
                .forEach(entry -> AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry));
        run.operator.dropMessage(6,
                "LPQ observation completed. All Agents are waiting at the entrance; use !lpqtest run or !lpqtest stop.");
    }

    private static void finishRun(Run run, String outcome) {
        if (!RUNS.remove(run.operator.getId(), run)) return;
        returnObserver(run);
        run.engagement.agentIds().forEach(AgentLpqTestService::disconnect);
        run.operator.dropMessage(6, "LPQ observation " + outcome + '.');
    }

    private static void failRun(Run run, String reason) {
        if (run == null || !RUNS.remove(run.operator.getId(), run)) return;
        long now = System.currentTimeMillis();
        returnObserver(run);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), now);
        if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, now);
        }
        run.engagement.agentIds().forEach(AgentLpqTestService::disconnect);
        run.operator.dropMessage(6, "LPQ stopped safely: " + reason);
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
    private static void ensureBackingCharacter(Character operator, String name) throws Exception {
        String failure = PROVISIONING.ensureBackingCharacter(operator, name);
        if (failure != null) throw new IllegalStateException(failure);
    }
    private static long seed(String[] params, int index, long fallback) {
        return params.length <= index ? fallback : Long.parseLong(params[index]);
    }
    private static long mixedSeed(String[] params, int index, long fallback) {
        if (params.length <= index) return fallback;
        String value = params[index];
        if (value.equalsIgnoreCase("default") || value.equalsIgnoreCase("auto")) {
            return seed(params, index + 1, fallback);
        }
        return parseHumanRole(value) == AgentLpqSession.HumanRolePreference.DEFAULT
                ? Long.parseLong(value) : seed(params, index + 1, fallback);
    }
    private static AgentLpqSession.HumanRolePreference humanRolePreference(
            Character operator, String[] params, int index) {
        if (params.length <= index) return AgentLpqSession.HumanRolePreference.DEFAULT;
        AgentLpqSession.HumanRolePreference requested = parseHumanRole(params[index]);
        return switch (requested) {
            case TELEPORT -> AgentLpqRosterRequirementPolicy.teleportMagic(operator)
                    ? requested : AgentLpqSession.HumanRolePreference.DEFAULT;
            case DARK_SIGHT -> AgentLpqRosterRequirementPolicy.darkSight(operator)
                    ? requested : AgentLpqSession.HumanRolePreference.DEFAULT;
            case RANGED -> AgentLpqRosterRequirementPolicy.rangedAttack(operator)
                    ? requested : AgentLpqSession.HumanRolePreference.DEFAULT;
            default -> AgentLpqSession.HumanRolePreference.DEFAULT;
        };
    }
    private static AgentLpqSession.HumanRolePreference parseHumanRole(String value) {
        if (value == null) return AgentLpqSession.HumanRolePreference.DEFAULT;
        return switch (value.toLowerCase()) {
            case "teleport", "tp" -> AgentLpqSession.HumanRolePreference.TELEPORT;
            case "darksight", "dark-sight", "ds" -> AgentLpqSession.HumanRolePreference.DARK_SIGHT;
            case "ranged", "range" -> AgentLpqSession.HumanRolePreference.RANGED;
            default -> AgentLpqSession.HumanRolePreference.DEFAULT;
        };
    }
    private static List<String> help() {
        return List.of("!lpqtest start [seed] (six Agents; spectator remains outside party)",
                "!lpqtest boss [seed] (six Agents enter the real event directly at Stage 9)",
                "!lpqtest humanleader [teleport|darksight|ranged] [seed] (you lead five Agents)",
                "!lpqtest agentleader [teleport|darksight|ranged] [seed] (a five-Agent party recruits you)",
                "!lpqtest invite (manual Agent-leader invitation resend)",
                "!lpqtest spectate | follow <leader|AgentName> | stay | return",
                "!warplpq <1-6|leader|scout [1-2]|teleport|darksight|magic|physical|top|bottom|platform|boss|room 501-506>",
                "!lpqtest stage8chat <on|off> (IGN->box chat; default off)",
                "!lpqtest status | pause | resume | bonus <skip|enter> | run | stop");
    }

    private enum Flow { AGENTS_ONLY, HUMAN_LEADER, AGENT_LEADER }
    private enum StartStage { FULL_RUN, BOSS }
    private static final class Run {
        final Object lock = new Object();
        final Character operator;
        final AgentPartyQuestEngagement engagement;
        final long seed;
        final Flow flow;
        final StartStage startStage;
        final AgentLpqSession.HumanRolePreference humanRolePreference;
        @SuppressWarnings("unused") final Set<String> names;
        volatile AgentPartyQuestLobbySession lobby;
        volatile AgentLpqSession session;
        volatile int eventLeaderId;
        volatile boolean inviteSent;
        volatile boolean spectating;
        volatile boolean autoFollow;
        volatile boolean stage8AssignmentChatEnabled;
        volatile boolean startStageApplied;
        volatile int followId;
        Run(Character operator, AgentPartyQuestEngagement engagement, long seed, Flow flow,
            StartStage startStage, AgentLpqSession.HumanRolePreference humanRolePreference,
            Set<String> names) {
            this.operator = operator; this.engagement = engagement; this.seed = seed;
            this.flow = flow; this.startStage = startStage;
            this.humanRolePreference = humanRolePreference;
            this.names = Set.copyOf(names);
        }
    }
}
