package server.agents.capabilities.partyquest.hpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;
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
import server.agents.field.AgentHpqTestFixtureService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPartySnapshot;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.perception.AgentMapPerception;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/** GM-only HPQ observation harness. Production HPQ does not depend on this class. */
public final class AgentHpqTestService {
    private static final Logger log = LoggerFactory.getLogger(AgentHpqTestService.class);
    private static final PartyQuestGateway HPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final ConcurrentHashMap<Integer, Run> RUNS = new ConcurrentHashMap<>();
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqTestService.SPAWN_STAGGER_MS");
    private static final int ROSTER_SIZE = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqTestService.ROSTER_SIZE");

    private AgentHpqTestService() {
    }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        if (params == null || params.length == 0) return help();
        try {
            return switch (params[0].toLowerCase()) {
                case "start" -> start(operator, partySize(params, 1, 3),
                        seed(params, 2, nowMs), Flow.AGENTS_ONLY, nowMs);
                case "withme", "wait" -> start(operator, partySize(params, 1, 3),
                        seed(params, 2, nowMs), Flow.HUMAN_INVITES_AGENTS, nowMs);
                case "agentleader" -> start(operator, partySize(params, 1, 3),
                        seed(params, 2, nowMs), Flow.AGENT_LEADS_HUMAN, nowMs);
                case "status" -> status(operator);
                case "invite" -> invite(operator);
                case "spectate", "attach" -> spectate(operator);
                case "return", "detach" -> returnFromSpectating(operator);
                case "pause" -> pause(operator, true);
                case "resume", "continue" -> pause(operator, false);
                case "bonus" -> bonus(operator, params);
                case "checkpoint" -> checkpoint(operator, params, nowMs);
                case "complete" -> complete(operator, nowMs);
                case "fail" -> fail(operator, params);
                case "stop" -> stop(operator, "stopped by operator", nowMs);
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("HPQ test command failed for operator {}", operator.getId(), failure);
            return List.of("HPQ test command failed: " + failure.getMessage());
        }
    }

    private static List<String> start(Character operator, int partySize, long seed,
                                      Flow flow, long nowMs) throws Exception {
        if (operator.getMapId() != AgentHpqDefinition.RECRUIT_MAP) {
            return List.of("Stand at the Henesys HPQ entrance (100000200) first.");
        }
        if (flow != Flow.AGENTS_ONLY
                && (operator.getLevel() < 10 || operator.getLevel() > 255)) {
            return List.of("Your observing character must be level 10-255 for HPQ.");
        }
        if (AgentPartyGatewayRuntime.party().snapshot(operator) != null) {
            return List.of("Leave your current party before starting an HPQ observation run.");
        }
        Run old = RUNS.get(operator.getId());
        if (old != null) stop(operator, "replaced by a new HPQ test", nowMs);

        int agentCount = flow == Flow.AGENTS_ONLY ? partySize : partySize - 1;
        List<String> names = shuffledRoster(seed).subList(0, agentCount);
        for (String name : names) ensureBackingCharacter(operator, name);
        AgentPartyQuestEngagement engagement = new AgentPartyQuestEngagement(
                "hpq", AgentPartyQuestEngagement.Mode.TEST_OBSERVATION,
                seed, operator.getId(), partySize, nowMs);
        if (flow != Flow.AGENTS_ONLY) {
            engagement.addMember(operator.getId(),
                    AgentPartyQuestEngagement.MemberType.HUMAN, nowMs);
        }
        Run run = new Run(operator, engagement, seed, flow, new LinkedHashSet<>(names));
        run.eventLeaderId = flow == Flow.HUMAN_INVITES_AGENTS ? operator.getId() : 0;
        RUNS.put(operator.getId(), run);
        try {
            AgentPartyQuestEngagementRegistry.register(engagement);
            openLobby(run, nowMs);
        } catch (RuntimeException failure) {
            RUNS.remove(operator.getId(), run);
            AgentPartyQuestEngagementRegistry.remove(engagement);
            throw failure;
        }
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            int ordinal = index;
            AgentSchedulerRuntime.schedule(
                    () -> launch(run, name, ordinal), SPAWN_STAGGER_MS * index);
        }
        if (flow == Flow.HUMAN_INVITES_AGENTS) {
            return List.of(
                    "Preparing HPQ observers " + names + ". Create a party and invite each Agent.",
                    "Once the party reaches " + partySize
                            + " members, talk to Tory normally and remain with the party to observe.",
                    "Use !hpqtest status, checkpoint <seeds|bunny|ninecakes>, complete, or stop.");
        }
        if (flow == Flow.AGENT_LEADS_HUMAN) {
            return List.of(
                    "Preparing an Agent-led HPQ party with " + agentCount + " Agents.",
                    "Accept the party invitation from the Agent leader. The Agent will enter through Tory.",
                    "Use !hpqtest invite if the invitation expires, then !hpqtest status to inspect the run.");
        }
        return List.of("Preparing an Agent-only HPQ party of " + partySize
                + " (seed " + seed + "). Use !hpqtest status to inspect it.");
    }

    private static void openLobby(Run run, long nowMs) {
        AgentPartyQuestLobbySession lobby = new AgentPartyQuestLobbySession(
                run.engagement.engagementId(), AgentHpqLobbyProfile.profile(), run.seed,
                run.operator.getId(), run.engagement.requestedPartySize(),
                AgentPartyQuestCandidateScope.OWNER_ONLY, nowMs);
        if (run.flow != Flow.AGENTS_ONLY) {
            lobby.addMember(run.operator.getId(), AgentPartyQuestLobbySession.MemberType.HUMAN,
                    run.flow == Flow.HUMAN_INVITES_AGENTS
                            ? AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER
                            : AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY, nowMs);
        }
        AgentPartyQuestLobbyRuntime.register(lobby, nowMs);
        run.engagement.beginLobby(lobby.lobbyId(), nowMs);
        run.lobby = lobby;
        startMonitor(run);
    }

    private static void launch(Run run, String name, int ordinal) {
        synchronized (run.lock) {
            if (RUNS.get(run.operator.getId()) != run) return;
            Character launched = null;
            try {
                MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                        run.operator.getWorld(), AgentClientGatewayRuntime.clients().channel(run.operator),
                        AgentHpqDefinition.RECRUIT_MAP);
                Point npc = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .npcPosition(run.operator, AgentHpqDefinition.ENTRY_NPC);
                Point candidate = new Point((npc == null ? -120 : npc.x) + 45 + ordinal * 38,
                        npc == null ? 0 : npc.y);
                Point spawn = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
                if (spawn == null) {
                    var portal = map == null ? null : map.getRandomPlayerSpawnpoint();
                    spawn = portal == null ? new Point(0, 0) : new Point(portal.getPosition());
                }
                AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                        .spawnStationaryAgentForLeaderAt(run.operator, name, map, spawn);
                if (!result.success()) throw new IllegalStateException(result.errorMessage());
                Character agent = result.agent();
                launched = agent;
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry == null) throw new IllegalStateException("spawned Agent runtime is unavailable");
                AgentHpqTestFixtureService.PreparationResult prepared =
                        AgentHpqTestFixtureService.prepare(
                                entry, run.seed + ordinal * 10_007L, System.currentTimeMillis());
                AgentCombatVariationRuntime.configure(entry, new AgentCombatVariationSettings(
                        run.seed + ordinal * 10_007L, true, 0.35d, 8, true, 0.50d));
                AgentMapGatewayRuntime.map().changeMapNear(agent, map, spawn);
                if (!AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, agent,
                        "entering HPQ observation", System.currentTimeMillis())) {
                    throw new IllegalStateException(name + " could not release its current activity");
                }
                long memberNowMs = System.currentTimeMillis();
                AgentPartyQuestEngagementRegistry.addAndIndexMember(
                        run.engagement, agent.getId(),
                        AgentPartyQuestEngagement.MemberType.AGENT, memberNowMs);
                if (run.flow != Flow.HUMAN_INVITES_AGENTS) joinOwnedParty(run, agent);
                AgentPartyQuestLobbyRegistry.addAndIndexMember(
                        run.lobby, agent.getId(), AgentPartyQuestLobbySession.MemberType.AGENT,
                        run.flow == Flow.HUMAN_INVITES_AGENTS
                                ? AgentPartyQuestLobbySession.MemberRole.LOOKING_FOR_PARTY
                                : agent.getId() == run.eventLeaderId
                                ? AgentPartyQuestLobbySession.MemberRole.RECRUITING_LEADER
                                : AgentPartyQuestLobbySession.MemberRole.JOINED_MEMBER,
                        memberNowMs);
                if (run.lobby.coordinatorAgentId() == 0) {
                    run.lobby.setCoordinatorAgentId(agent.getId());
                }
                log.info("HPQ fixture launched: name={} career={} level={} weapon={}",
                        name, prepared.career(), prepared.level(), prepared.weaponItemId());
                maybeInviteOperator(run);
            } catch (Exception failure) {
                if (launched != null) disconnect(launched.getId());
                failRun(run, "Could not launch " + name + ": " + failure.getMessage());
            }
        }
    }

    private static void joinOwnedParty(Run run, Character agent) {
        Character leader = character(run.eventLeaderId);
        if (leader == null) {
            if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                throw new IllegalStateException("could not create the HPQ fixture party");
            }
            run.eventLeaderId = agent.getId();
            return;
        }
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(leader);
        if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
            throw new IllegalStateException(agent.getName() + " could not join the HPQ fixture party");
        }
        AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
    }

    private static void startMonitor(Run run) {
        if (run.monitoring) return;
        run.monitoring = true;
        AgentSchedulerRuntime.schedule(() -> monitor(run), 500L);
    }

    private static void monitor(Run run) {
        if (RUNS.get(run.operator.getId()) != run) return;
        try {
            maybeInviteOperator(run);
            if (run.session == null) attemptHandoff(run);
            if (run.session != null && run.session.terminal()) {
                String outcome = run.session.phase() == AgentHpqSession.Phase.COMPLETED
                        ? "completed" : "failed: " + run.session.failure();
                finishRun(run, outcome);
                return;
            }
        } catch (RuntimeException failure) {
            failRun(run, "HPQ observation monitor failed: " + failure.getMessage());
            return;
        }
        AgentSchedulerRuntime.schedule(() -> monitor(run), 500L);
    }

    private static void attemptHandoff(Run run) {
        synchronized (run.lock) {
            AgentPartyQuestLobbySession lobby = run.lobby;
            if (lobby == null || !lobby.active() || lobby.paused()) return;
            AgentPartyQuestLobbyReconciler.Snapshot party =
                    AgentPartyQuestLobbyReconciler.reconcile(lobby, System.currentTimeMillis());
            if (party.memberIds().size() != run.engagement.requestedPartySize()) return;
            if (party.memberIds().stream().anyMatch(id -> !run.engagement.memberIds().contains(id))) return;
            Character leader = onlineCharacter(party.leaderId());
            if (leader == null) return;
            List<Character> members = party.memberIds().stream()
                    .map(AgentHpqTestService::onlineCharacter)
                    .filter(java.util.Objects::nonNull).toList();
            if (members.size() != party.memberIds().size()) return;
            long nowMs = System.currentTimeMillis();
            lobby.markReady(nowMs);
            run.engagement.lobbyReady(nowMs);
            AgentHpqAdmissionService.AdmissionResult result =
                    AgentHpqAdmissionService.admitFromLobby(
                            run.engagement, lobby, run.operator, leader, members,
                            run.seed, nowMs, AgentHpqSession.Mode.TEST_OBSERVATION);
            if (!result.success()) return;
            run.eventLeaderId = leader.getId();
            run.session = result.session();
            run.lobby = null;
            run.operator.dropMessage(6, run.flow == Flow.HUMAN_INVITES_AGENTS
                    ? "HPQ party assembled. Talk to Tory to enter and observe normally."
                    : run.flow == Flow.AGENT_LEADS_HUMAN
                    ? "Agent-led mixed HPQ assembled. The Agent leader will enter normally."
                    : "Agent-only HPQ party assembled; use !hpqtest spectate after entry to observe it.");
        }
    }

    private static void maybeInviteOperator(Run run) {
        if (run == null) return;
        synchronized (run.lock) {
            if (run.flow != Flow.AGENT_LEADS_HUMAN || run.inviteSent
                    || AgentPartyGatewayRuntime.party().hasParty(run.operator)
                    || run.engagement.agentIds().size() < run.engagement.requestedPartySize() - 1) return;
            Character leader = character(run.eventLeaderId);
            if (leader != null && AgentPartyGatewayRuntime.party().invitePartyMember(leader, run.operator)) {
                run.inviteSent = true;
                run.operator.dropMessage(6, "Accept " + leader.getName()
                        + "'s party invitation for the Agent-led HPQ observation.");
            }
        }
    }

    private static List<String> invite(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.flow != Flow.AGENT_LEADS_HUMAN) {
            return List.of("No Agent-led mixed HPQ invitation is pending.");
        }
        if (AgentPartyGatewayRuntime.party().hasParty(operator)) {
            return List.of("You are already in the HPQ party.");
        }
        run.inviteSent = false;
        maybeInviteOperator(run);
        return List.of(run.inviteSent
                ? "The Agent leader sent a new party invitation."
                : "The Agent-led party is still being prepared; retry shortly.");
    }

    private static List<String> checkpoint(Character operator, String[] params, long nowMs) {
        if (params.length < 2) {
            return List.of("Syntax: !hpqtest checkpoint <seeds|bunny|ninecakes>");
        }
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active HPQ event session.");
        Character leader = onlineCharacter(run.session.eventLeaderId());
        EventInstanceManager event = leader == null ? null : HPQ.event(leader);
        if (event == null || leader.getMapId() != AgentHpqDefinition.STAGE_MAP) {
            return List.of("Enter the HPQ stage normally before applying a checkpoint.");
        }
        String checkpoint = params[1].toLowerCase();
        return switch (checkpoint) {
            case "seeds" -> List.of("HPQ is already at the live seed collection stage.");
            case "bunny" -> {
                activateBunny(event, leader.getMap(), leader);
                yield List.of("Activated the test-only Moon Bunny checkpoint with ordinary defense behavior.");
            }
            case "ninecakes", "nine" -> {
                activateBunny(event, leader.getMap(), leader);
                setCakeCount(leader, 9);
                event.setProperty("bunnyCake", "9");
                run.session.markProgress(nowMs);
                yield List.of("Moon Bunny checkpoint prepared with nine cakes on the event leader.");
            }
            default -> List.of("Checkpoint must be seeds, bunny, or ninecakes.");
        };
    }

    private static List<String> complete(Character operator, long nowMs) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active HPQ event session.");
        Character leader = onlineCharacter(run.session.eventLeaderId());
        EventInstanceManager event = leader == null ? null : HPQ.event(leader);
        if (event == null || leader.getMapId() != AgentHpqDefinition.STAGE_MAP) {
            return List.of("The HPQ party must be in the main stage.");
        }
        activateBunny(event, leader.getMap(), leader);
        setCakeCount(leader, AgentHpqDefinition.REQUIRED_RICE_CAKES);
        event.setProperty("bunnyCake", Integer.toString(AgentHpqDefinition.REQUIRED_RICE_CAKES));
        run.session.markProgress(nowMs);
        return List.of(character(run.session.eventLeaderId()) == null
                ? "Prepared ten cakes. As the human event leader, talk to Growlie and submit them normally."
                : "Prepared ten cakes. The Agent event leader will submit them through Growlie normally.");
    }

    private static List<String> spectate(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || run.flow != Flow.AGENTS_ONLY || run.session == null) {
            return List.of("Spectating is available after an Agent-only HPQ session enters its event.");
        }
        Character leader = onlineCharacter(run.session.eventLeaderId());
        EventInstanceManager event = leader == null ? null : HPQ.event(leader);
        if (event == null) return List.of("The Agent party has not entered its private HPQ instance yet.");
        MapleMap map = event.getMapInstance(leader.getMapId());
        var portal = map == null ? null : map.getRandomPlayerSpawnpoint();
        if (map == null || portal == null) return List.of("The current HPQ instance map is unavailable.");
        AgentMapGatewayRuntime.map().changeMapNear(operator, map, portal.getPosition());
        run.spectating = true;
        return List.of("Attached to the Agent-only HPQ instance as a non-participant observer.",
                "Do not attack, loot, use NPCs, or change reactors. Use !hpqtest return to leave.");
    }

    private static List<String> returnFromSpectating(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null || !run.spectating) return List.of("You are not attached as an HPQ spectator.");
        returnObserver(run);
        return List.of("Returned to the Henesys HPQ entrance.");
    }

    private static void returnObserver(Run run) {
        if (run == null || !run.spectating) return;
        MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                run.operator.getWorld(), AgentClientGatewayRuntime.clients().channel(run.operator),
                AgentHpqDefinition.RECRUIT_MAP);
        var portal = recruit == null ? null : recruit.getRandomPlayerSpawnpoint();
        if (recruit != null) {
            AgentMapGatewayRuntime.map().changeMapNear(run.operator, recruit,
                    portal == null ? new Point(0, 0) : portal.getPosition());
        }
        run.spectating = false;
    }

    private static List<String> bonus(Character operator, String[] params) {
        if (params.length < 2) return List.of("Syntax: !hpqtest bonus <skip|enter>");
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active HPQ event session.");
        AgentHpqSession.BonusMode mode = switch (params[1].toLowerCase()) {
            case "skip" -> AgentHpqSession.BonusMode.SKIP;
            case "enter" -> AgentHpqSession.BonusMode.ENTER;
            default -> null;
        };
        if (mode == null) return List.of("Bonus mode must be skip or enter.");
        run.session.setBonusMode(mode);
        return List.of("HPQ bonus mode set to " + mode + " for this observation run.");
    }

    private static List<String> fail(Character operator, String[] params) {
        if (params.length < 2) {
            return List.of("Syntax: !hpqtest fail <coordinator|leader|bunny|timeout>");
        }
        Run run = RUNS.get(operator.getId());
        if (run == null || run.session == null) return List.of("No active HPQ event session.");
        Character leader = onlineCharacter(run.session.eventLeaderId());
        return switch (params[1].toLowerCase()) {
            case "coordinator" -> {
                Character coordinator = character(run.session.executionAgentId());
                if (coordinator == null) yield List.of("The execution coordinator is not a live Agent.");
                disconnect(coordinator.getId());
                yield List.of("Disconnected the HPQ coordinator; verify idempotent failure cleanup.");
            }
            case "leader" -> {
                Character agentLeader = character(run.session.eventLeaderId());
                if (agentLeader == null) yield List.of("Refusing to disconnect a human event leader.");
                disconnect(agentLeader.getId());
                yield List.of("Disconnected the Agent event leader; verify event and party cleanup.");
            }
            case "bunny" -> {
                if (leader == null || leader.getMapId() != AgentHpqDefinition.STAGE_MAP) {
                    yield List.of("The party must be in the Moon Bunny stage first.");
                }
                leader.getMap().killMonster(AgentHpqDefinition.MOON_BUNNY);
                yield List.of("Killed the Moon Bunny through the live map; the authored failure path should fire.");
            }
            case "timeout" -> {
                EventInstanceManager event = leader == null ? null : HPQ.event(leader);
                if (event == null) yield List.of("The party has not entered its event instance.");
                event.restartEventTimer(1_000L);
                yield List.of("Reduced the live event timer to one second; verify timeout cleanup.");
            }
            default -> List.of("Failure target must be coordinator, leader, bunny, or timeout.");
        };
    }

    private static void activateBunny(EventInstanceManager event, MapleMap map, Character observer) {
        event.setProperty("stage", "6");
        map.allowSummonState(true);
        if (AgentPrimitiveCapabilityGatewayRuntime.gateway().liveMonsterCount(
                observer, Set.of(AgentHpqDefinition.MOON_BUNNY)) == 0) {
            map.spawnMonsterOnGroundBelow(AgentHpqDefinition.MOON_BUNNY,
                    AgentHpqDefinition.MOON_BUNNY_POSITION.x,
                    AgentHpqDefinition.MOON_BUNNY_POSITION.y);
        }
    }

    private static void setCakeCount(Character leader, int target) {
        int current = leader.getItemQuantity(AgentHpqDefinition.RICE_CAKE, false);
        if (current > target) {
            AgentInventoryGatewayRuntime.inventory().removeById(
                    leader, client.inventory.InventoryType.ETC,
                    AgentHpqDefinition.RICE_CAKE, current - target, false, false);
        } else if (current < target && !AgentInventoryGatewayRuntime.inventory().addItem(
                leader, AgentHpqDefinition.RICE_CAKE, (short) (target - current))) {
            throw new IllegalStateException("event leader has no ETC space for the cake checkpoint");
        }
    }

    private static List<String> status(Character operator) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No HPQ observation run is active.");
        List<String> lines = new ArrayList<>();
        lines.add("HPQ " + run.engagement.engagementId() + ": " + run.engagement.state()
                + ", roster " + run.engagement.memberIds().size() + '/'
                + run.engagement.requestedPartySize());
        if (run.lobby != null) {
            lines.add("Lobby " + run.lobby.state() + (run.lobby.paused() ? " (paused)" : ""));
        }
        if (run.session != null) {
            lines.add("Session " + run.session.sessionId() + ": " + run.session.phase()
                    + (run.session.paused() ? " (paused)" : ""));
            Character leader = onlineCharacter(run.session.eventLeaderId());
            lines.add("Leader " + (leader == null ? run.session.eventLeaderId() : leader.getName())
                    + ", map " + (leader == null ? -1 : leader.getMapId())
                    + ", cakes " + (leader == null ? -1
                    : leader.getItemQuantity(AgentHpqDefinition.RICE_CAKE, false)) + "/10");
            if (leader != null && HPQ.event(leader) != null) {
                EventInstanceManager event = HPQ.event(leader);
                lines.add("Event " + HPQ.eventName(leader) + ", timer "
                        + Math.max(0L, event.getTimeLeft()) / 1_000L + "s, planted seeds "
                        + HPQ.property(leader, "stage") + "/6, cakes produced "
                        + HPQ.property(leader, "bunnyCake"));
                lines.add("Flowers: " + AgentPrimitiveCapabilityGatewayRuntime.gateway().reactors(leader).stream()
                        .filter(reactor -> reactor.getId() >= 9_108_000 && reactor.getId() <= 9_108_005)
                        .sorted(java.util.Comparator.comparingInt(server.maps.Reactor::getId))
                        .map(reactor -> reactor.getName() + "=" + reactor.getState()).toList());
                var bunny = AgentMapPerception.monsters(leader.getMap()).stream()
                        .filter(monster -> monster.getId() == AgentHpqDefinition.MOON_BUNNY)
                        .findFirst().orElse(null);
                lines.add("Moon Bunny: " + (bunny == null ? "absent"
                        : "alive, HP " + bunny.getHp() + '/' + bunny.getMaxHp()));
            }
            lines.add("Members: " + run.session.members().stream().map(member -> {
                Character character = onlineCharacter(member.characterId());
                String name = character == null ? Integer.toString(member.characterId()) : character.getName();
                String seeds = character == null ? "?" : AgentHpqDefinition.seedBeds().stream()
                        .filter(bed -> character.getItemQuantity(bed.seedItemId(), false) > 0)
                        .map(bed -> bed.seedItemId() + "x"
                                + character.getItemQuantity(bed.seedItemId(), false))
                        .collect(java.util.stream.Collectors.joining(","));
                return name + '=' + member.role()
                        + (member.assignedSeedItemId() == 0 ? ""
                        : " target=" + member.assignedSeedItemId())
                        + (seeds.isEmpty() ? "" : " seeds[" + seeds + ']');
            }).toList());
            lines.add("Bonus " + run.session.bonusMode() + ", last progress "
                    + Math.max(0L, System.currentTimeMillis() - run.session.lastProgressAtMs()) / 1_000L
                    + "s ago; leader ETC next free slot "
                    + (leader == null ? -1
                    : leader.getInventory(client.inventory.InventoryType.ETC).getNextFreeSlot()));
        }
        if (!run.engagement.diagnostics().isEmpty()) {
            lines.add("Latest diagnostic: " + run.engagement.diagnostics().getLast());
        }
        return lines;
    }

    private static List<String> pause(Character operator, boolean paused) {
        Run run = RUNS.get(operator.getId());
        if (run == null) return List.of("No HPQ observation run is active.");
        if (run.session != null) run.session.setPaused(paused);
        if (run.lobby != null) run.lobby.setPaused(paused);
        return List.of("HPQ observation " + (paused ? "paused" : "resumed") + '.');
    }

    private static List<String> stop(Character operator, String reason, long nowMs) {
        Run run = RUNS.remove(operator.getId());
        if (run == null) return List.of("No HPQ observation run is active.");
        returnObserver(run);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), nowMs);
        if (run.session != null && AgentHpqSessionRegistry.active(run.session.executionAgentId())) {
            AgentHpqTerminationService.fail(run.session, reason, nowMs);
        } else if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, nowMs);
        }
        run.engagement.agentIds().forEach(AgentHpqTestService::disconnect);
        return List.of("Stopped HPQ observation " + run.engagement.engagementId()
                + ". Backing characters were retained.");
    }

    private static void finishRun(Run run, String outcome) {
        if (!RUNS.remove(run.operator.getId(), run)) return;
        returnObserver(run);
        run.engagement.agentIds().forEach(AgentHpqTestService::disconnect);
        run.operator.dropMessage(6, "HPQ observation " + outcome + ".");
    }

    private static void failRun(Run run, String reason) {
        if (run == null || !RUNS.remove(run.operator.getId(), run)) return;
        long nowMs = System.currentTimeMillis();
        returnObserver(run);
        if (run.lobby != null) AgentPartyQuestLobbyRuntime.unregister(run.lobby.lobbyId(), nowMs);
        if (AgentPartyQuestEngagementRegistry.byId(run.engagement.engagementId()) != null) {
            AgentPartyQuestLifecycleRuntime.closeTest(run.engagement, nowMs);
        }
        run.engagement.agentIds().forEach(AgentHpqTestService::disconnect);
        run.operator.dropMessage(6, "HPQ observation stopped safely: " + reason);
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

    private static void ensureBackingCharacter(Character operator, String name) throws Exception {
        String failure = PROVISIONING.ensureBackingCharacter(operator, name);
        if (failure != null) throw new IllegalStateException(failure);
    }

    private static List<String> shuffledRoster(long seed) {
        ArrayList<String> names = new ArrayList<>();
        for (int index = 1; index <= ROSTER_SIZE; index++) {
            names.add("HPQer%02d".formatted(index));
        }
        Collections.shuffle(names, new java.util.Random(new SplittableRandom(seed).nextLong()));
        return names;
    }

    private static int partySize(String[] params, int index, int fallback) {
        if (params.length <= index) return fallback;
        int size = Integer.parseInt(params[index]);
        if (size < 3 || size > 6) throw new IllegalArgumentException("HPQ party size must be 3-6");
        return size;
    }

    private static long seed(String[] params, int index, long fallback) {
        return params.length <= index ? fallback : Long.parseLong(params[index]);
    }

    private static List<String> help() {
        return List.of(
                "!hpqtest withme [3-6] [seed] (recommended visual observation)",
                "!hpqtest start [3-6] [seed] (Agent-only run)",
                "!hpqtest agentleader [3-6] [seed] | invite",
                "!hpqtest spectate | return | bonus <skip|enter>",
                "!hpqtest checkpoint <seeds|bunny|ninecakes>",
                "!hpqtest fail <coordinator|leader|bunny|timeout>",
                "!hpqtest complete | status | pause | resume | stop");
    }

    private enum Flow { AGENTS_ONLY, HUMAN_INVITES_AGENTS, AGENT_LEADS_HUMAN }

    private static final class Run {
        private final Object lock = new Object();
        private final Character operator;
        private final AgentPartyQuestEngagement engagement;
        private final long seed;
        private final Flow flow;
        @SuppressWarnings("unused")
        private final Set<String> names;
        private volatile AgentPartyQuestLobbySession lobby;
        private volatile AgentHpqSession session;
        private volatile int eventLeaderId;
        private volatile boolean monitoring;
        private volatile boolean inviteSent;
        private volatile boolean spectating;

        private Run(Character operator, AgentPartyQuestEngagement engagement,
                    long seed, Flow flow, Set<String> names) {
            this.operator = operator;
            this.engagement = engagement;
            this.seed = seed;
            this.flow = flow;
            this.names = Set.copyOf(names);
        }
    }
}
