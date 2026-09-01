package server.agents.capabilities.expedition;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;
import server.agents.auth.AgentAuthorityService;
import server.agents.capabilities.combat.AgentCombatVariationRuntime;
import server.agents.capabilities.combat.AgentCombatVariationSettings;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.commands.AgentSpawnCommandExecutor;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentExpeditionGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
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
import server.expeditions.Expedition;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;

/** Reusable 1-30 member expedition lobby with physical travel and six-member party partitioning. */
public final class AgentExpeditionLobbyService {
    private static final Logger log = LoggerFactory.getLogger(AgentExpeditionLobbyService.class);
    private static final AgentSpawnCommandExecutor PROVISIONING = new AgentSpawnCommandExecutor();
    private static final long SPAWN_STAGGER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.AgentExpeditionLobbyService.SPAWN_STAGGER_MS");
    private static final long MONITOR_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.AgentExpeditionLobbyService.MONITOR_MS");
    private static final long ASSEMBLY_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.AgentExpeditionLobbyService.ASSEMBLY_TIMEOUT_MS");
    private static final long PHASE_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.AgentExpeditionLobbyService.PHASE_TIMEOUT_MS");
    private static final long TRAVEL_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.AgentExpeditionLobbyService.TRAVEL_TIMEOUT_MS");
    private static final long CLEARED_RETENTION_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.AgentExpeditionLobbyService.CLEARED_RETENTION_MS");
    private static final int RALLY_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.AgentExpeditionLobbyService.RALLY_DISTANCE_PX");

    private final LongFunction<AgentExpeditionScenario> scenarioFactory;
    private final Map<Integer, Run> runs = new ConcurrentHashMap<>();

    public AgentExpeditionLobbyService(LongFunction<AgentExpeditionScenario> scenarioFactory) {
        if (scenarioFactory == null) {
            throw new IllegalArgumentException("an expedition scenario factory is required");
        }
        this.scenarioFactory = scenarioFactory;
    }

    public List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        String action = params == null || params.length == 0 ? "help" : params[0].toLowerCase();
        try {
            return switch (action) {
                case "start" -> start(operator, seed(params, nowMs), nowMs, false);
                case "quick" -> start(operator, seed(params, nowMs), nowMs, true);
                case "status" -> status(operator, nowMs);
                case "watch", "observe" -> watch(operator);
                case "stop" -> stop(operator, "operator stopped the run");
                default -> help();
            };
        } catch (Exception failure) {
            log.warn("Expedition command failed for operator {}", operator.getId(), failure);
            return List.of("Expedition command failed: " + failure.getMessage());
        }
    }

    private List<String> start(
            Character operator, long seed, long nowMs, boolean quick) throws Exception {
        AgentExpeditionScenario scenario = scenarioFactory.apply(seed);
        if (scenario == null) throw new IllegalStateException("scenario factory returned no expedition");
        AgentExpeditionSpec spec = scenario.spec();
        Expedition existing = AgentExpeditionGatewayRuntime.expedition()
                .current(operator, spec.expeditionType());
        if (existing != null) {
            return List.of("A " + spec.displayName() + " expedition already exists on this channel.");
        }
        ArrayList<String> response = new ArrayList<>();
        if (runs.containsKey(operator.getId())) {
            response.addAll(stop(operator, "replaced by a new run"));
        }
        for (String name : spec.memberNames()) {
            String failure = PROVISIONING.ensureBackingCharacter(operator, name);
            if (failure != null) throw new IllegalStateException(failure);
        }
        var clients = AgentClientGatewayRuntime.clients();
        int world = clients.world(operator);
        int channel = clients.channel(operator);
        int stagingMapId = operator.getMapId();
        Point stagingPosition = new Point(operator.getPosition());
        int stagingPortalId = 0;
        if (quick) {
            MapleMap entrance = AgentMapGatewayRuntime.map().resolveMap(
                    world, channel, spec.entranceMapId());
            stagingPortalId = scenario.quickEntryPortalId();
            if (entrance == null || entrance.getPortal(stagingPortalId) == null) {
                throw new IllegalStateException("the expedition entrance staging point is unavailable");
            }
            stagingMapId = spec.entranceMapId();
            stagingPosition = new Point(entrance.getPortal(stagingPortalId).getPosition());
        }
        Run run = new Run(operator, scenario, seed, nowMs, world, channel,
                stagingMapId, stagingPosition, quick, stagingPortalId);
        runs.put(operator.getId(), run);
        for (int ordinal = 0; ordinal < spec.participantCount(); ordinal++) {
            int memberOrdinal = ordinal;
            String name = spec.memberNames().get(ordinal);
            AgentSchedulerRuntime.schedule(
                    () -> launch(run, name, memberOrdinal), SPAWN_STAGGER_MS * ordinal);
        }
        AgentSchedulerRuntime.schedule(() -> monitor(run), MONITOR_MS);
        response.add("Preparing " + spec.participantCount() + " Agents for " + spec.displayName()
                + " (seed " + seed + ") in " + spec.partyCount() + " party/parties.");
        response.addAll(scenario.rosterSummary());
        response.add(quick
                ? "Quick mode: Agents spawn inside entrance map " + spec.entranceMapId()
                + " and rally at the expedition NPC."
                : "Agents spawn beside you, then physically travel to map "
                + spec.entranceMapId() + '.');
        response.add("Use !balrogtest status, then !balrogtest watch once the fight starts.");
        return response;
    }

    private void launch(Run run, String name, int ordinal) {
        synchronized (run.lock) {
            if (!active(run)) return;
            Character launched = null;
            try {
                MapleMap stagingMap = AgentMapGatewayRuntime.map().resolveMap(
                        run.world, run.channel, run.stagingMapId);
                int spawnSpacing = run.quick ? run.scenario.quickEntrySpacingPx() : 34;
                Point candidate = new Point(run.stagingPosition.x
                        + formationOffset(ordinal, run.spec().participantCount(), spawnSpacing),
                        run.stagingPosition.y);
                Point spawn = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .groundPoint(stagingMap, candidate);
                if (spawn == null) {
                    var portal = stagingMap == null ? null : stagingMap.getPortal(run.stagingPortalId);
                    spawn = portal == null ? new Point(run.stagingPosition)
                            : new Point(portal.getPosition());
                }
                AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                        .spawnStationaryAgentForLeaderAt(run.operator, name, stagingMap, spawn);
                if (!result.success()) throw new IllegalStateException(result.errorMessage());
                Character agent = result.agent();
                launched = agent;
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
                if (entry == null) throw new IllegalStateException("spawned Agent runtime is unavailable");
                if (!AgentActivityBootstrap.admission().prepare(
                        AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, agent,
                        "entering " + run.spec().displayName(), System.currentTimeMillis())) {
                    throw new IllegalStateException("Agent activity did not release for " + name);
                }
                long memberSeed = run.seed + ordinal * 10_007L;
                AgentExpeditionPreparedMember prepared = run.scenario.prepareMember(
                        entry, ordinal, memberSeed, System.currentTimeMillis());
                AgentCombatVariationRuntime.configure(entry, new AgentCombatVariationSettings(
                        memberSeed, true, 0.35d, 8, true, 0.50d));
                AgentMapGatewayRuntime.map().changeMapNear(agent, stagingMap, spawn);
                AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(entry, agent);
                run.members.put(agent.getId(), new Member(ordinal, name, prepared));
                log.info("Expedition fixture {} job={} build={} hit={} weapon={} attack={}",
                        name, prepared.job(), prepared.build(),
                        String.format("%.0f%%", prepared.minimumHitChance() * 100.0d),
                        prepared.weaponItemId(), prepared.weaponAttack());
            } catch (Exception failure) {
                if (launched != null) disconnect(launched.getId());
                fail(run, "Could not launch " + name + ": " + failure.getMessage());
            }
        }
    }

    private void monitor(Run run) {
        if (!active(run)) return;
        try {
            long nowMs = System.currentTimeMillis();
            long timeout = switch (run.phase) {
                case ASSEMBLING -> ASSEMBLY_TIMEOUT_MS;
                case NAVIGATING -> TRAVEL_TIMEOUT_MS;
                default -> PHASE_TIMEOUT_MS;
            };
            if (run.phase != Phase.FIGHTING && run.phase != Phase.CLEARED
                    && nowMs - run.phaseStartedAtMs > timeout) {
                throw new IllegalStateException(run.phase + " timed out");
            }
            List<Character> members = members(run);
            if (run.phase != Phase.ASSEMBLING && members.size() != run.spec().participantCount()) {
                throw new IllegalStateException("a required Agent disappeared");
            }
            switch (run.phase) {
                case ASSEMBLING -> {
                    if (members.size() == run.spec().participantCount()) {
                        transition(run, Phase.FORMING_PARTIES, nowMs);
                    }
                }
                case FORMING_PARTIES -> formParties(run, members, nowMs);
                case NAVIGATING -> navigateToEntrance(run, members, nowMs);
                case CREATING_EXPEDITION -> createExpedition(run, nowMs);
                case REGISTERING -> registerMembers(run, members, nowMs);
                case READY_COUNTDOWN -> readyCountdown(run, members, nowMs);
                case STARTING -> startEvent(run, members, nowMs);
                case FIGHTING -> fight(run, members, nowMs);
                case POST_CLEAR -> postClear(run, members, nowMs);
                case CLEARED, FAILED, STOPPED -> { }
            }
            if (active(run) && run.phase != Phase.CLEARED) {
                AgentSchedulerRuntime.schedule(() -> monitor(run), MONITOR_MS);
            }
        } catch (Exception failure) {
            fail(run, failure.getMessage());
        }
    }

    private void formParties(Run run, List<Character> members, long nowMs) {
        List<Character> ordered = ordered(run, members);
        int capacity = run.spec().partyCapacity();
        for (int index = 0; index < ordered.size(); index++) {
            Character agent = ordered.get(index);
            if (index % capacity == 0) {
                if (!AgentPartyGatewayRuntime.party().createAgentParty(agent)) {
                    throw new IllegalStateException("could not create expedition party " + (index / capacity + 1));
                }
                run.partyLeaderIds.add(agent.getId());
                continue;
            }
            Character leader = ordered.get(index - index % capacity);
            AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(leader);
            if (party == null || !AgentPartyGatewayRuntime.party().joinAgentParty(agent, party.id())) {
                throw new IllegalStateException("could not add " + agent.getName() + " to party "
                        + (index / capacity + 1));
            }
            AgentPartyGatewayRuntime.party().publishAgentOnline(agent, party.id());
        }
        run.expeditionLeaderId = ordered.getFirst().getId();
        transition(run, Phase.NAVIGATING, nowMs);
    }

    private void navigateToEntrance(Run run, List<Character> members, long nowMs) {
        if (rallyMembers(run, members, nowMs)) {
            transition(run, Phase.CREATING_EXPEDITION, nowMs);
        }
    }

    private boolean rallyMembers(Run run, List<Character> members, long nowMs) {
        boolean allReady = true;
        for (Character member : ordered(run, members)) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            if (entry == null) throw new IllegalStateException(member.getName() + " runtime disappeared");
            if (member.getMapId() != run.spec().entranceMapId()) {
                AgentRouteOutcome outcome = AgentPrimitiveCapabilityGatewayRuntime.gateway().travelTo(
                        entry, member, run.spec().entranceMapId(), nowMs);
                run.routes.put(member.getId(), outcome);
                if (outcome.status() == AgentRouteStatus.NO_ROUTE) {
                    throw new IllegalStateException(member.getName() + " has no route from "
                            + outcome.sourceMapId() + " to " + outcome.destinationMapId());
                }
                allReady = false;
                continue;
            }
            Point npc = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .npcPosition(member, run.spec().entryNpcId());
            if (npc == null) throw new IllegalStateException("expedition entrance NPC is unavailable");
            Member fixture = run.members.get(member.getId());
            Point candidate = new Point(npc.x + formationOffset(
                    fixture.ordinal(), run.spec().participantCount(),
                    run.scenario.lobbyRallySpacingPx()), npc.y);
            Point rally = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .groundPoint(member.getMap(), candidate);
            if (rally == null) rally = npc;
            if (member.getPosition().distanceSq(rally) > RALLY_DISTANCE_PX * RALLY_DISTANCE_PX) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, rally, true);
                allReady = false;
            } else {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
                run.routes.put(member.getId(), new AgentRouteOutcome(
                        AgentRouteStatus.ARRIVED, member.getMapId(), member.getMapId(),
                        run.spec().entranceMapId(), false));
            }
        }
        return allReady;
    }

    private void createExpedition(Run run, long nowMs) {
        Character leader = character(run.expeditionLeaderId);
        if (leader == null || leader.getMapId() != run.spec().entranceMapId()) {
            throw new IllegalStateException("expedition leader is not at the entrance");
        }
        if (!AgentPartyQuestGatewayRuntime.partyQuest().runNpc(
                leader, run.spec().entryNpcId(), selections(run.spec().createSelections()))) {
            throw new IllegalStateException("entrance NPC did not create " + run.spec().displayName());
        }
        Expedition expedition = AgentExpeditionGatewayRuntime.expedition()
                .current(leader, run.spec().expeditionType());
        if (expedition == null || !expedition.isLeader(leader)) {
            throw new IllegalStateException(run.spec().displayName() + " expedition was not registered");
        }
        run.expedition = expedition;
        transition(run, Phase.REGISTERING, nowMs);
    }

    private void registerMembers(Run run, List<Character> members, long nowMs) {
        Expedition expedition = run.expedition;
        if (expedition == null || !expedition.isRegistering()) {
            throw new IllegalStateException("expedition registration closed unexpectedly");
        }
        for (Character member : ordered(run, members)) {
            if (expedition.contains(member)) continue;
            if (!AgentPartyQuestGatewayRuntime.partyQuest().runNpc(
                    member, run.spec().entryNpcId(), selections(run.spec().joinSelections()))) {
                throw new IllegalStateException(member.getName() + " could not register");
            }
            log.info("Expedition registration scenario={} member={} registered={}/{}",
                    run.spec().scenarioId(), member.getName(), expedition.getMembers().size(),
                    run.spec().participantCount());
            return;
        }
        if (expedition.getMembers().size() != run.spec().participantCount()) {
            throw new IllegalStateException("expedition roster is not the requested size");
        }
        run.readyAtMs = 0L;
        transition(run, Phase.READY_COUNTDOWN, nowMs);
    }

    private void readyCountdown(Run run, List<Character> members, long nowMs) {
        if (!rallyMembers(run, members, nowMs)) {
            run.readyAtMs = 0L;
            return;
        }
        if (run.readyAtMs == 0L) {
            run.readyAtMs = nowMs + run.spec().readyCountdownMs();
            Character leader = character(run.expeditionLeaderId);
            if (leader != null) {
                AgentPacketGatewayRuntime.packets().broadcastChatText(
                        leader, run.spec().displayName() + " party ready. Entering in "
                                + Math.max(0L, run.spec().readyCountdownMs()) / 1_000L + " seconds.",
                        false, 0);
            }
            log.info("Expedition ready scenario={} registered={} parties={} startsInMs={}",
                    run.spec().scenarioId(), run.expedition == null ? 0
                            : run.expedition.getMembers().size(), run.spec().partyCount(),
                    run.spec().readyCountdownMs());
            if (run.spec().readyCountdownMs() > 0L) return;
        }
        if (nowMs >= run.readyAtMs) transition(run, Phase.STARTING, nowMs);
    }

    private void startEvent(Run run, List<Character> members, long nowMs) {
        Character leader = character(run.expeditionLeaderId);
        if (leader == null) throw new IllegalStateException("expedition leader disappeared");
        if (!run.startRequested) {
            run.startRequested = true;
            if (!AgentPartyQuestGatewayRuntime.partyQuest().runNpc(
                    leader, run.spec().entryNpcId(), selections(run.spec().startSelections()))) {
                throw new IllegalStateException("entrance NPC did not start " + run.spec().displayName());
            }
        }
        EventInstanceManager event = AgentPartyQuestGatewayRuntime.partyQuest().event(leader);
        if (event == null || members.stream().anyMatch(member -> member.getMapId() != run.spec().battleMapId()
                || !AgentPartyQuestGatewayRuntime.partyQuest().sameEvent(leader, member))) {
            return;
        }
        run.event = event;
        run.scenario.tickCombat(members, event, nowMs);
        transition(run, Phase.FIGHTING, nowMs);
        run.operator.dropMessage(6, run.spec().displayName()
                + " entered combat. Use !balrogtest watch to observe.");
    }

    private void fight(Run run, List<Character> members, long nowMs) {
        EventInstanceManager event = run.event;
        if (event == null || event.isEventDisposed()) {
            throw new IllegalStateException("event instance ended before victory");
        }
        for (Character member : members) {
            if (!member.isAlive()) throw new IllegalStateException(member.getName() + " died");
        }
        run.scenario.tickCombat(members, event, nowMs);
        if (event.isEventCleared()) {
            members.stream().map(member -> AgentRuntimeRegistry.findByAgentCharacterId(member.getId()))
                    .filter(java.util.Objects::nonNull)
                    .forEach(AgentPrimitiveCapabilityGatewayRuntime.gateway()::stop);
            run.scenario.beginPostClear(members, event, nowMs);
            transition(run, Phase.POST_CLEAR, nowMs);
        }
    }

    private void postClear(Run run, List<Character> members, long nowMs) {
        EventInstanceManager event = run.event;
        if (event == null) {
            throw new IllegalStateException("event instance disappeared during post-clear rewards");
        }
        boolean complete = event.isEventDisposed()
                ? members.stream().allMatch(member -> member.getMapId() == run.spec().returnMapId())
                : run.scenario.tickPostClear(members, event, nowMs);
        if (!complete) {
            if (event.isEventDisposed()) {
                throw new IllegalStateException("event instance ended before post-clear rewards completed");
            }
            return;
        }
        transition(run, Phase.CLEARED, nowMs);
        boolean returned = returnToLobby(run);
        log.info("Agent expedition cleared scenario={} members={} returnedToLobby={} total={} {}",
                run.spec().scenarioId(), run.spec().participantCount(), returned,
                formattedDuration(nowMs - run.startedAtMs), timingSummary(run, nowMs));
        run.operator.dropMessage(6, run.spec().displayName() + " cleared by "
                + run.spec().participantCount() + " Agents in " + seconds(nowMs - run.startedAtMs)
                + "; returned-to-lobby=" + returned + ". " + timingSummary(run, nowMs));
        run.operator.dropMessage(6,
                "Use !balrogtest status to inspect the returned Agents, then stop when done.");
        AgentSchedulerRuntime.schedule(() -> {
            if (runs.remove(run.operator.getId(), run)) {
                release(run, Phase.STOPPED);
            }
        }, CLEARED_RETENTION_MS);
    }

    private boolean returnToLobby(Run run) {
        EventInstanceManager event = run.event;
        if (event == null) return false;
        if (event.isEventDisposed()) {
            run.event = null;
            run.expedition = null;
            return members(run).stream().allMatch(
                    member -> member.getMapId() == run.spec().returnMapId());
        }
        for (Character participant : new ArrayList<>(event.getPlayers())) {
            try {
                event.exitPlayer(participant);
            } catch (RuntimeException failure) {
                log.warn("Could not return expedition participant {} to the lobby",
                        participant.getId(), failure);
            }
        }
        event.dispose();
        run.event = null;
        run.expedition = null;
        return members(run).stream().allMatch(
                member -> member.getMapId() == run.spec().returnMapId());
    }

    private List<String> watch(Character operator) {
        Run run = runs.get(operator.getId());
        if (run == null || run.event == null
                || (run.phase != Phase.FIGHTING && run.phase != Phase.POST_CLEAR)) {
            return List.of("The expedition fight is not ready to observe yet.");
        }
        if (AgentPartyQuestGatewayRuntime.partyQuest().event(operator) == run.event) {
            return List.of("You are already observing this expedition.");
        }
        run.event.registerPlayer(operator);
        return List.of("Entered the event as a GM observer; the expedition roster is unchanged.");
    }

    private List<String> status(Character operator, long nowMs) {
        Run run = runs.get(operator.getId());
        if (run == null) return List.of("No Agent expedition is active.");
        ArrayList<String> lines = new ArrayList<>();
        lines.add(run.spec().displayName() + " phase=" + run.phase + " elapsed="
                + seconds(nowMs - run.startedAtMs) + " members=" + run.members.size() + '/'
                + run.spec().participantCount() + " parties=" + run.spec().partyCount()
                + (run.phase == Phase.READY_COUNTDOWN && run.readyAtMs > nowMs
                ? " starts-in=" + seconds(run.readyAtMs - nowMs) : "") + '.');
        for (Character member : ordered(run, members(run))) {
            Member fixture = run.members.get(member.getId());
            AgentRouteOutcome route = run.routes.get(member.getId());
            String routeText = route == null ? "" : " route=" + route.status()
                    + (route.nextMapId() > 0 && route.nextMapId() != route.sourceMapId()
                    ? "->" + route.nextMapId() : "");
            lines.add(member.getName() + " " + fixture.prepared().job() + " lv" + member.getLevel()
                    + " " + fixture.prepared().build() + " weapon=" + fixture.prepared().weaponItemId()
                    + " HP " + member.getHp() + '/' + member.getMaxHp() + " map " + member.getMapId()
                    + " pos=(" + member.getPosition().x + ',' + member.getPosition().y + ')'
                    + " hit " + Math.round(fixture.prepared().minimumHitChance() * 100.0d) + '%'
                    + routeText);
        }
        Character leader = character(run.expeditionLeaderId);
        if (leader != null) lines.addAll(run.scenario.battleStatus(leader));
        return lines;
    }

    private List<String> stop(Character operator, String reason) {
        Run run = runs.remove(operator.getId());
        if (run == null) return List.of("No Agent expedition is active.");
        log.info("Stopping Agent expedition {}: {}", run.spec().scenarioId(), reason);
        release(run, Phase.STOPPED);
        return List.of("Stopped " + run.spec().displayName() + "; backing characters were retained.");
    }

    private void fail(Run run, String reason) {
        if (run == null || !runs.remove(run.operator.getId(), run)) return;
        log.warn("Agent expedition failed: scenario={} phase={} reason={}",
                run.spec().scenarioId(), run.phase, reason);
        release(run, Phase.FAILED);
        run.operator.dropMessage(6, run.spec().displayName() + " failed: " + reason);
    }

    private void release(Run run, Phase terminal) {
        run.phase = terminal;
        EventInstanceManager event = run.event;
        Expedition expedition = run.expedition;
        run.event = null;
        run.expedition = null;
        if (event != null && !event.isEventDisposed()) {
            for (Character participant : new ArrayList<>(event.getPlayers())) {
                try {
                    event.exitPlayer(participant);
                } catch (RuntimeException failure) {
                    log.warn("Could not exit expedition participant {}", participant.getId(), failure);
                }
            }
            event.dispose();
        } else if (event == null && expedition != null) {
            expedition.dispose(false);
            AgentExpeditionGatewayRuntime.expedition().remove(run.operator, expedition);
        }
        members(run).stream()
                .sorted(Comparator.comparingInt(member -> run.partyLeaderIds.contains(member.getId()) ? 1 : 0))
                .filter(AgentPartyGatewayRuntime.party()::hasParty)
                .forEach(member -> AgentPartyGatewayRuntime.party().leaveCurrentParty(member));
        new ArrayList<>(run.members.keySet()).forEach(AgentExpeditionLobbyService::disconnect);
    }

    private List<Character> members(Run run) {
        return run.members.keySet().stream().map(AgentExpeditionLobbyService::character)
                .filter(java.util.Objects::nonNull).toList();
    }

    private static List<Character> ordered(Run run, List<Character> members) {
        return members.stream().sorted(Comparator.comparingInt(
                member -> run.members.get(member.getId()).ordinal())).toList();
    }

    private static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        return entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
    }

    private static void disconnect(int characterId) {
        Character agent = character(characterId);
        AgentRuntimeCleanupService.removeAgentByCharacterId(characterId);
        if (agent != null) AgentCharacterGatewayRuntime.characters().disconnect(agent, false, false);
    }

    private boolean active(Run run) {
        return run != null && runs.get(run.operator.getId()) == run;
    }

    private static void transition(Run run, Phase phase, long nowMs) {
        run.phaseDurationsMs.merge(run.phase,
                Math.max(0L, nowMs - run.phaseStartedAtMs), Long::sum);
        run.phase = phase;
        run.phaseStartedAtMs = nowMs;
        log.info("Agent expedition phase={} scenario={} members={}",
                phase, run.spec().scenarioId(), run.members.size());
    }

    static int partyIndex(int ordinal, int partyCapacity) {
        if (ordinal < 0 || partyCapacity < 1 || partyCapacity > 6) {
            throw new IllegalArgumentException("valid member ordinal and party capacity are required");
        }
        return ordinal / partyCapacity;
    }

    static int formationOffset(int ordinal, int memberCount, int spacingPx) {
        if (ordinal < 0 || ordinal >= memberCount || memberCount < 1 || memberCount > 30
                || spacingPx < 1) {
            throw new IllegalArgumentException("a valid formation slot is required");
        }
        return ordinal * spacingPx - (memberCount - 1) * spacingPx / 2;
    }

    private static int[] selections(List<Integer> selections) {
        return selections.stream().mapToInt(Integer::intValue).toArray();
    }

    private static long seed(String[] params, long fallback) {
        return params != null && params.length > 1 ? Long.parseLong(params[1]) : fallback;
    }

    private static String seconds(long durationMs) {
        return Math.max(0L, durationMs) / 1_000L + "s";
    }

    private static String timingSummary(Run run, long nowMs) {
        return "timings prep=" + duration(run, Phase.ASSEMBLING)
                + " parties=" + duration(run, Phase.FORMING_PARTIES)
                + " rally=" + duration(run, Phase.NAVIGATING)
                + " create=" + duration(run, Phase.CREATING_EXPEDITION)
                + " register=" + duration(run, Phase.REGISTERING)
                + " ready=" + duration(run, Phase.READY_COUNTDOWN)
                + " start=" + duration(run, Phase.STARTING)
                + " fight=" + duration(run, Phase.FIGHTING)
                + " rewards=" + duration(run, Phase.POST_CLEAR)
                + " total=" + formattedDuration(nowMs - run.startedAtMs) + '.';
    }

    private static String duration(Run run, Phase phase) {
        return formattedDuration(run.phaseDurationsMs.getOrDefault(phase, 0L));
    }

    private static String formattedDuration(long durationMs) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0L, durationMs) / 1_000.0d);
    }

    private static List<String> help() {
        return List.of("!balrogtest start [seed]", "!balrogtest quick [seed]",
                "!balrogtest status",
                "!balrogtest watch", "!balrogtest stop");
    }

    enum Phase {
        ASSEMBLING,
        FORMING_PARTIES,
        NAVIGATING,
        CREATING_EXPEDITION,
        REGISTERING,
        READY_COUNTDOWN,
        STARTING,
        FIGHTING,
        POST_CLEAR,
        CLEARED,
        FAILED,
        STOPPED
    }

    private record Member(int ordinal, String name, AgentExpeditionPreparedMember prepared) {
    }

    private static final class Run {
        private final Character operator;
        private final AgentExpeditionScenario scenario;
        private final long seed;
        private final long startedAtMs;
        private final int world;
        private final int channel;
        private final int stagingMapId;
        private final Point stagingPosition;
        private final boolean quick;
        private final int stagingPortalId;
        private final Object lock = new Object();
        private final Map<Integer, Member> members = new ConcurrentHashMap<>();
        private final Map<Integer, AgentRouteOutcome> routes = new ConcurrentHashMap<>();
        private final Map<Phase, Long> phaseDurationsMs = new ConcurrentHashMap<>();
        private final Set<Integer> partyLeaderIds = ConcurrentHashMap.newKeySet();
        private volatile Phase phase = Phase.ASSEMBLING;
        private volatile long phaseStartedAtMs;
        private volatile int expeditionLeaderId;
        private volatile boolean startRequested;
        private volatile long readyAtMs;
        private volatile Expedition expedition;
        private volatile EventInstanceManager event;

        private Run(Character operator, AgentExpeditionScenario scenario, long seed, long nowMs,
                    int world, int channel, int stagingMapId, Point stagingPosition,
                    boolean quick, int stagingPortalId) {
            this.operator = operator;
            this.scenario = scenario;
            this.seed = seed;
            this.startedAtMs = nowMs;
            this.phaseStartedAtMs = nowMs;
            this.world = world;
            this.channel = channel;
            this.stagingMapId = stagingMapId;
            this.stagingPosition = stagingPosition;
            this.quick = quick;
            this.stagingPortalId = stagingPortalId;
        }

        private AgentExpeditionSpec spec() {
            return scenario.spec();
        }
    }
}
