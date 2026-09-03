package server.agents.capabilities.expedition.balrog;

import client.BuffStat;
import client.Character;
import client.status.MonsterStatus;
import net.server.services.task.channel.ServerMobAutonomyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.combat.AgentCombatBuffRuntime;
import server.agents.capabilities.combat.AgentCombatPolicyDiagnostics;
import server.agents.capabilities.expedition.AgentExpeditionPreparedMember;
import server.agents.capabilities.expedition.AgentExpeditionScenario;
import server.agents.capabilities.expedition.AgentExpeditionSpec;
import server.agents.capabilities.looting.AgentLootEligibility;
import server.agents.field.AgentBalrogTestFixtureService;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.expeditions.ExpeditionType;
import server.life.Monster;
import server.maps.MapItem;
import server.maps.Reactor;

import java.awt.Point;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Easy Balrog's build pool, claw/body phase policy, and battle status. */
public final class AgentEasyBalrogScenario implements AgentExpeditionScenario {
    private static final Logger log = LoggerFactory.getLogger(AgentEasyBalrogScenario.class);
    private static final int POWER_ELIXIR_ITEM_ID = 2_000_005;
    private static final int BATTLE_EXIT_NPC = 1_061_018;
    private static final int REWARD_REACTOR = 1_052_002;
    private static final int REWARD_EXIT_PORTAL = 2;
    private static final long POST_CLEAR_TIMEOUT_MS = 180_000L;
    private static final long REWARD_FIDGET_INITIAL_STEP_MS = 275L;
    private static final long REWARD_FIDGET_MIN_PERIOD_MS = 1_400L;
    private static final long REWARD_FIDGET_PERIOD_VARIANCE_MS = 1_600L;
    private static final int INTERACTION_RANGE_PX = 120;
    private static final int LOOT_PICKUP_DISTANCE_PX =
            AgentEasyBalrogRewardGracePolicy.AGENT_PICKUP_DISTANCE_PX;
    private static final int POST_CLEAR_RALLY_SPACING_PX = 18;
    private static final int POST_CLEAR_RALLY_DISTANCE_PX = 24;
    private static final int RECOVERY_THRESHOLD_PERCENT = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogScenario.RECOVERY_THRESHOLD_PERCENT");
    private static final long VITALS_LOG_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogScenario.VITALS_LOG_INTERVAL_MS");
    private static final List<String> MEMBER_NAMES = List.of(
            "Balrog01", "Balrog02", "Balrog03", "Balrog04", "Balrog05", "Balrog06",
            "Balrog07", "Balrog08", "Balrog09", "Balrog10", "Balrog11", "Balrog12");

    private final List<AgentBalrogTestFixtureService.Build> roster;
    private final AgentExpeditionSpec spec;
    private final Map<Integer, Integer> memberOrdinals = new ConcurrentHashMap<>();
    private CombatPhase combatPhase;
    private PostClearPhase postClearPhase;
    private long nextVitalsLogAtMs;
    private long humanLootGraceEndsAtMs;
    private boolean rewardRoomWarpRequested;
    private boolean rewardAssignmentsInitialized;
    private final Map<Integer, Integer> rewardAssignments = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> rewardCollectedCounts = new ConcurrentHashMap<>();
    private final Map<Integer, Long> nextRewardFidgetAtByMember = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> rewardFidgetStepByMember = new ConcurrentHashMap<>();

    public AgentEasyBalrogScenario(long seed) {
        roster = AgentBalrogTestFixtureService.selectRoster(seed);
        spec = new AgentExpeditionSpec(
                "easy-balrog-level-60",
                "Easy Balrog",
                ExpeditionType.BALROG_EASY,
                AgentBalrogDefinition.RECRUIT_MAP,
                AgentBalrogDefinition.BATTLE_MAP,
                AgentBalrogDefinition.RECRUIT_MAP,
                AgentBalrogDefinition.ENTRY_NPC,
                AgentBalrogDefinition.PARTY_CAPACITY,
                5_000L,
                MEMBER_NAMES,
                List.of(1, 1),
                List.of(1),
                List.of(1, 2, 0));
    }

    @Override
    public AgentExpeditionSpec spec() {
        return spec;
    }

    @Override
    public AgentExpeditionPreparedMember prepareMember(
            AgentRuntimeEntry entry, int ordinal, long memberSeed, long nowMs) throws Exception {
        AgentBalrogTestFixtureService.Build build = roster.get(ordinal);
        int clothingRank = AgentBalrogTestFixtureService.clothingRank(roster, ordinal);
        AgentBalrogTestFixtureService.PreparationResult prepared =
                AgentBalrogTestFixtureService.prepare(
                        entry, build, clothingRank, memberSeed, nowMs);
        Character member = AgentRuntimeIdentityRuntime.bot(entry);
        if (member != null) {
            memberOrdinals.put(member.getId(), ordinal);
        }
        return new AgentExpeditionPreparedMember(
                prepared.job().name(),
                prepared.buildId(),
                prepared.minimumHitChance(),
                prepared.weaponItemId(),
                prepared.weaponAttack());
    }

    @Override
    public int quickEntryPortalId() {
        return 1;
    }

    @Override
    public int quickEntrySpacingPx() {
        return 9;
    }

    @Override
    public int lobbyRallySpacingPx() {
        return 48;
    }

    @Override
    public void tickCombat(List<Character> members, EventInstanceManager event, long nowMs) {
        if (members.isEmpty() || members.getFirst().getMap() == null) return;
        List<Monster> monsters = server.agents.perception.AgentMapPerception
                .monsters(members.getFirst().getMap());
        boolean liveClaw = monsters.stream().anyMatch(mob -> mob.isAlive()
                && AgentBalrogDefinition.CLAW_MOBS.contains(mob.getId()));
        boolean realBody = monsters.stream().anyMatch(mob -> mob.isAlive()
                && mob.getId() == AgentBalrogDefinition.BODY_MOB && !mob.isFake());
        boolean liveAdds = monsters.stream().anyMatch(mob -> mob.isAlive()
                && AgentBalrogDefinition.SUMMONED_ADDS.contains(mob.getId()));
        boolean seal = monsters.stream().anyMatch(mob -> mob.isAlive()
                && mob.getId() == AgentBalrogDefinition.RELEASE_SEAL_MOB);
        boolean reflecting = monsters.stream().anyMatch(mob -> mob.isAlive()
                && (mob.getId() == AgentBalrogDefinition.BODY_MOB
                || AgentBalrogDefinition.CLAW_MOBS.contains(mob.getId()))
                && (mob.isBuffed(MonsterStatus.WEAPON_REFLECT)
                || mob.isBuffed(MonsterStatus.MAGIC_REFLECT)));
        CombatPhase nextPhase = liveClaw
                ? (seal ? CombatPhase.SEALED_CLAW : CombatPhase.CLAW)
                : (realBody ? CombatPhase.BODY : CombatPhase.TRANSITION);
        if (nextPhase != combatPhase) {
            combatPhase = nextPhase;
            log.info("Easy Balrog combat phase={} members={} mobs={}",
                    combatPhase, members.size(), battleStatus(members.getFirst()));
        }
        for (Character member : members) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            if (entry == null) continue;
            maintainBattleResources(entry, member);
            int ordinal = memberOrdinals.getOrDefault(member.getId(), 0);
            boolean ranged = AgentEasyBalrogCombatPolicy
                    .isRanged(roster.get(ordinal).weaponClass());
            Set<Integer> bossTargets = liveClaw
                    ? AgentBalrogDefinition.CLAW_MOBS
                    : realBody ? Set.of(AgentBalrogDefinition.BODY_MOB) : Set.of();
            if (reflecting && !liveAdds) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
                continue;
            }
            if (liveClaw) {
                Set<Integer> ordinaryTargets = new HashSet<>();
                if (!reflecting) ordinaryTargets.addAll(AgentBalrogDefinition.CLAW_MOBS);
                if (liveAdds) ordinaryTargets.addAll(AgentBalrogDefinition.SUMMONED_ADDS);
                if (ordinaryTargets.isEmpty()) {
                    AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
                } else {
                    AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(
                            entry, Set.copyOf(ordinaryTargets));
                }
                continue;
            }
            if (!realBody) {
                if (liveAdds) {
                    AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(
                            entry, AgentBalrogDefinition.SUMMONED_ADDS);
                } else {
                    AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
                }
                continue;
            }
            if (liveAdds) {
                // Adds use ordinary mobile combat. Once they are gone the next tick restores the
                // authored upper-left head station before the body becomes eligible again.
                AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(
                        entry, AgentBalrogDefinition.SUMMONED_ADDS);
                continue;
            }
            if (bossTargets.isEmpty()) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
                continue;
            }
            Point authoredAnchor = AgentEasyBalrogCombatPolicy.headAnchor(ordinal, ranged);
            Point anchor = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .groundPoint(member.getMap(), authoredAnchor);
            if (!AgentEasyBalrogCombatPolicy.atAnchor(member.getPosition(), anchor)) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, anchor, true);
                continue;
            }
            AgentPrimitiveCapabilityGatewayRuntime.gateway().grindFromAnchor(
                    entry, anchor, bossTargets, Set.of());
        }
        if (nowMs >= nextVitalsLogAtMs) {
            nextVitalsLogAtMs = nowMs + VITALS_LOG_INTERVAL_MS;
            log.info("Easy Balrog party vitals phase={} {}", combatPhase,
                    members.stream().map(AgentEasyBalrogScenario::vitals).toList());
            log.info("Easy Balrog combat decisions phase={} {}", combatPhase,
                    members.stream().map(member -> combatDecision(member, nowMs)).toList());
        }
    }

    @Override
    public void beginPostClear(
            List<Character> members, EventInstanceManager event, long nowMs) {
        clearRewardClaims(event);
        postClearPhase = PostClearPhase.ENTERING_REWARD_ROOM;
        humanLootGraceEndsAtMs = 0L;
        nextRewardFidgetAtByMember.clear();
        rewardFidgetStepByMember.clear();
        rewardRoomWarpRequested = false;
        rewardAssignmentsInitialized = false;
        rewardAssignments.clear();
        rewardCollectedCounts.clear();
        log.info("Easy Balrog post-clear phase={} members={}", postClearPhase, members.size());
    }

    @Override
    public boolean preserveNonAgentParticipantsAfterClear() {
        return true;
    }

    @Override
    public long postClearTimeoutMs() {
        return POST_CLEAR_TIMEOUT_MS;
    }

    @Override
    public boolean retainReturnedMembersUntilNextRun() {
        return true;
    }

    @Override
    public boolean tickPostClear(
            List<Character> members, EventInstanceManager event, long nowMs) {
        if (members.stream().allMatch(
                member -> member.getMapId() == AgentBalrogDefinition.RECRUIT_MAP)) {
            clearRewardClaims(event);
            return true;
        }
        if (members.stream().anyMatch(
                member -> member.getMapId() == AgentBalrogDefinition.BATTLE_MAP)) {
            stageAtBattleExitNpc(members);
            return false;
        }
        if (members.stream().anyMatch(member -> member.getMapId()
                != AgentBalrogDefinition.CLEAR_MAP
                && member.getMapId() != AgentBalrogDefinition.RECRUIT_MAP)) {
            return false;
        }

        Character breaker = members.stream()
                .filter(member -> member.getMapId() == AgentBalrogDefinition.CLEAR_MAP)
                .min(java.util.Comparator.comparingInt(member ->
                        memberOrdinals.getOrDefault(member.getId(), Integer.MAX_VALUE)))
                .orElse(null);
        if (breaker == null) return false;
        Reactor reactor = AgentPrimitiveCapabilityGatewayRuntime.gateway().reactors(breaker).stream()
                .filter(candidate -> candidate != null && candidate.getId() == REWARD_REACTOR)
                .filter(Reactor::isAlive).filter(Reactor::isActive)
                .findFirst().orElse(null);
        long openedAtMs = AgentEasyBalrogRewardGracePolicy.rewardOpenedAt(event);
        if (openedAtMs == 0L && reactor != null) {
            postClearPhase = PostClearPhase.BREAKING_REWARD_REACTOR;
            spreadRewardParty(members, breaker);
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(breaker.getId());
            Point target = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .groundPoint(breaker.getMap(), reactor.getPosition());
            if (entry == null || target == null) return false;
            if (!near(breaker.getPosition(), target, INTERACTION_RANGE_PX)) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, target, true);
            } else if (AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .hitReactor(breaker, reactor.getObjectId())) {
                openedAtMs = AgentEasyBalrogRewardGracePolicy.rewardOpenedAt(event);
                if (openedAtMs > 0L) {
                    beginHumanLootGrace(openedAtMs, breaker.getName());
                }
            }
            return false;
        }
        if (openedAtMs == 0L) {
            return false;
        }
        if (humanLootGraceEndsAtMs == 0L) {
            beginHumanLootGrace(openedAtMs, "another participant");
        }
        if (nowMs < humanLootGraceEndsAtMs) {
            fidgetRewardParty(members, nowMs);
            return false;
        }
        if (!rewardAssignmentsInitialized) {
            initializeRewardAssignments(members, breaker);
            rewardAssignmentsInitialized = true;
            postClearPhase = PostClearPhase.COLLECTING_REWARDS;
            log.info("Easy Balrog Agent reward collection started assignments={}",
                    rewardAssignments.size());
        }
        collectAssignedRewardsAndExit(members);
        return members.stream().allMatch(
                member -> member.getMapId() == AgentBalrogDefinition.RECRUIT_MAP);
    }

    private void stageAtBattleExitNpc(List<Character> members) {
        List<Character> battleMembers = members.stream()
                .filter(member -> member.getMapId() == AgentBalrogDefinition.BATTLE_MAP)
                .sorted(java.util.Comparator.comparingInt(member ->
                        memberOrdinals.getOrDefault(member.getId(), Integer.MAX_VALUE)))
                .toList();
        if (battleMembers.isEmpty()) return;
        Point npc = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .npcPosition(battleMembers.getFirst(), BATTLE_EXIT_NPC);
        if (npc == null) return;
        for (Character member : battleMembers) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            int ordinal = memberOrdinals.getOrDefault(member.getId(), 0);
            Point authored = new Point(npc.x + expeditionFormationOffset(
                    ordinal, members.size(), POST_CLEAR_RALLY_SPACING_PX), npc.y);
            Point rally = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .groundPoint(member.getMap(), authored);
            if (entry == null || rally == null) {
                continue;
            }
            if (!near(member.getPosition(), rally, POST_CLEAR_RALLY_DISTANCE_PX)) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, rally, true);
            } else {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
            }
        }
        if (rewardRoomWarpRequested) return;
        Character transitioner = battleMembers.stream()
                .filter(member -> near(member.getPosition(), npc, INTERACTION_RANGE_PX))
                .min(Comparator.comparingDouble(member -> member.getPosition().distanceSq(npc)))
                .orElse(null);
        if (transitioner == null) return;
        rewardRoomWarpRequested = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .runNpcScript(transitioner, BATTLE_EXIT_NPC);
        if (rewardRoomWarpRequested) {
            log.info("Easy Balrog battle exit NPC used by={} members={}",
                    transitioner.getName(), battleMembers.size());
        }
    }

    private void beginHumanLootGrace(long openedAtMs, String opener) {
        humanLootGraceEndsAtMs = openedAtMs
                + AgentEasyBalrogRewardGracePolicy.HUMAN_LOOT_GRACE_MS;
        postClearPhase = PostClearPhase.HUMAN_LOOT_GRACE;
        log.info("Easy Balrog reward reactor opened by={} humanLootGraceMs={}",
                opener, AgentEasyBalrogRewardGracePolicy.HUMAN_LOOT_GRACE_MS);
    }

    @Override
    public void endRun(EventInstanceManager event) {
        clearRewardClaims(event);
        rewardAssignments.clear();
        rewardCollectedCounts.clear();
        nextRewardFidgetAtByMember.clear();
        rewardFidgetStepByMember.clear();
    }

    private static void clearRewardClaims(EventInstanceManager event) {
        if (event != null) {
            AgentEasyBalrogRewardClaimRegistry.clear(
                    event.getMapInstance(AgentBalrogDefinition.CLEAR_MAP));
        }
    }

    static int expeditionFormationOffset(int ordinal, int memberCount, int spacingPx) {
        if (ordinal < 0 || ordinal >= memberCount || memberCount < 1 || spacingPx < 1) {
            throw new IllegalArgumentException("a valid expedition formation slot is required");
        }
        return ordinal * spacingPx - (memberCount - 1) * spacingPx / 2;
    }

    private void spreadRewardParty(List<Character> members, Character breaker) {
        for (Character member : members) {
            if (member == breaker || member.getMapId() != AgentBalrogDefinition.CLEAR_MAP) continue;
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            int ordinal = memberOrdinals.getOrDefault(member.getId(), 0);
            Point spread = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(
                    member.getMap(), new Point(150 + ordinal * 58, 210));
            if (entry != null && spread != null
                    && !AgentEasyBalrogCombatPolicy.atAnchor(member.getPosition(), spread)) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, spread, true);
            }
        }
    }

    private void fidgetRewardParty(List<Character> members, long nowMs) {
        for (Character member : members) {
            if (member.getMapId() != AgentBalrogDefinition.CLEAR_MAP) continue;
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            int ordinal = memberOrdinals.getOrDefault(member.getId(), 0);
            long nextAtMs = nextRewardFidgetAtByMember.computeIfAbsent(
                    member.getId(), ignored -> nowMs + ordinal * REWARD_FIDGET_INITIAL_STEP_MS);
            if (entry == null || nowMs < nextAtMs) continue;
            int step = rewardFidgetStepByMember.merge(member.getId(), 1, Integer::sum);
            int offset = Math.floorMod(step + ordinal, 3) * 12 - 12;
            Point target = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(
                    member.getMap(), new Point(120 + ordinal * 62 + offset, 210));
            if (target != null) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, target, true);
            }
            nextRewardFidgetAtByMember.put(member.getId(),
                    nowMs + rewardFidgetPeriodMs(ordinal, step));
        }
    }

    static long rewardFidgetPeriodMs(int ordinal, int step) {
        return REWARD_FIDGET_MIN_PERIOD_MS + Math.floorMod(
                ordinal * 397L + step * 613L, REWARD_FIDGET_PERIOD_VARIANCE_MS);
    }

    private void initializeRewardAssignments(List<Character> members, Character source) {
        List<AgentEasyBalrogRewardPolicy.Member> rewardMembers = members.stream()
                .filter(member -> member.getMapId() == AgentBalrogDefinition.CLEAR_MAP)
                .map(member -> {
                    int ordinal = memberOrdinals.getOrDefault(member.getId(), 0);
                    return new AgentEasyBalrogRewardPolicy.Member(
                            member.getId(), ordinal, roster.get(ordinal).weaponClass());
                }).toList();
        List<AgentEasyBalrogRewardPolicy.Drop> drops = server.agents.perception.AgentMapPerception
                .items(source.getMap()).stream()
                .filter(drop -> !drop.isPickedUp())
                .filter(drop -> members.stream().anyMatch(member -> {
                    AgentRuntimeEntry entry = AgentRuntimeRegistry
                            .findByAgentCharacterId(member.getId());
                    return member.getMap() == source.getMap()
                            && AgentLootEligibility.canBotReceiveAssignedLoot(entry, member, drop);
                }))
                .map(drop -> new AgentEasyBalrogRewardPolicy.Drop(
                        drop.getObjectId(), drop.getItemId()))
                .toList();
        rewardAssignments.putAll(AgentEasyBalrogRewardPolicy.assign(rewardMembers, drops));
    }

    private void collectAssignedRewardsAndExit(List<Character> members) {
        var rewardMap = members.stream()
                .filter(member -> member.getMapId() == AgentBalrogDefinition.CLEAR_MAP)
                .findFirst().map(Character::getMap).orElse(null);
        Map<Integer, MapItem> liveDrops = server.agents.perception.AgentMapPerception
                .items(rewardMap).stream()
                .filter(drop -> !drop.isPickedUp())
                .collect(java.util.stream.Collectors.toMap(MapItem::getObjectId, drop -> drop));
        rewardAssignments.keySet().removeIf(objectId -> !liveDrops.containsKey(objectId));
        int collectionWave = rewardAssignments.values().stream()
                .mapToInt(agentId -> rewardCollectedCounts.getOrDefault(agentId, 0))
                .min().orElse(0);
        Map<Integer, MapItem> activeTargets = new java.util.LinkedHashMap<>();
        Map<Integer, Integer> activeClaims = new java.util.LinkedHashMap<>();
        for (Character member : members) {
            if (member.getMapId() != AgentBalrogDefinition.CLEAR_MAP
                    || rewardCollectedCounts.getOrDefault(member.getId(), 0) > collectionWave) {
                continue;
            }
            MapItem target = assignedTarget(member, liveDrops);
            if (target != null) {
                activeTargets.put(member.getId(), target);
                activeClaims.put(target.getObjectId(), member.getId());
            }
        }
        AgentEasyBalrogRewardClaimRegistry.replace(rewardMap, activeClaims);
        for (Character member : members) {
            if (member.getMapId() != AgentBalrogDefinition.CLEAR_MAP) continue;
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            if (entry == null) continue;
            MapItem target = activeTargets.get(member.getId());
            if (target != null) {
                Point ground = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .groundPoint(member.getMap(), target.getPosition());
                if (ground == null) continue;
                if (!near(member.getPosition(), ground, LOOT_PICKUP_DISTANCE_PX)) {
                    AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, ground, true);
                } else if (AgentPrimitiveCapabilityGatewayRuntime.gateway().lootItem(
                        member, target.getObjectId(), LOOT_PICKUP_DISTANCE_PX)) {
                    rewardAssignments.remove(target.getObjectId());
                    AgentEasyBalrogRewardClaimRegistry.collected(
                            member.getMap(), target.getObjectId());
                    rewardCollectedCounts.merge(member.getId(), 1, Integer::sum);
                    log.info("Easy Balrog reward collected agent={} item={} oid={}",
                            member.getName(), target.getItemId(), target.getObjectId());
                }
                continue;
            }
            if (rewardAssignments.containsValue(member.getId())) continue;
            postClearPhase = PostClearPhase.EXITING_TO_LOBBY;
            Point portal = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                    .portalPosition(member, REWARD_EXIT_PORTAL);
            if (portal == null) continue;
            if (!near(member.getPosition(), portal, INTERACTION_RANGE_PX)) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().navigate(entry, portal, true);
            } else {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
                AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .enterPortal(member, REWARD_EXIT_PORTAL);
            }
        }
    }

    private MapItem assignedTarget(Character member, Map<Integer, MapItem> liveDrops) {
        return rewardAssignments.entrySet().stream()
                .filter(assignment -> assignment.getValue() == member.getId())
                .map(assignment -> liveDrops.get(assignment.getKey()))
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingDouble(drop ->
                        member.getPosition().distanceSq(drop.getPosition())))
                .orElse(null);
    }

    private static boolean near(Point from, Point to, int rangePx) {
        return from != null && to != null
                && from.distanceSq(to) <= (long) rangePx * rangePx;
    }

    private static void maintainBattleResources(AgentRuntimeEntry entry, Character member) {
        AgentCombatBuffRuntime.tryCastCriticalSurvivalBuff(entry, member);
        if (needsExpeditionRecovery(member.getHp(), member.getCurrentMaxHp())
                || needsExpeditionRecovery(member.getMp(), member.getCurrentMaxMp())) {
            AgentPrimitiveCapabilityGatewayRuntime.gateway().useItem(member, POWER_ELIXIR_ITEM_ID);
        }
    }

    static boolean needsExpeditionRecovery(int current, int maximum) {
        return maximum > 0 && (long) current * 100L <= (long) maximum * RECOVERY_THRESHOLD_PERCENT;
    }

    private static String vitals(Character member) {
        return member.getName() + "=" + member.getHp() + '/' + member.getCurrentMaxHp()
                + "hp," + member.getMp() + '/' + member.getCurrentMaxMp() + "mp"
                + (member.getBuffedValue(BuffStat.MAGIC_GUARD) == null ? "" : ",MG")
                + "@(" + member.getPosition().x + ',' + member.getPosition().y + ')';
    }

    private static String combatDecision(Character member, long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
        var decision = AgentCombatPolicyDiagnostics.snapshot(entry, nowMs).combatDecision();
        return member.getName() + '=' + (decision == null
                ? "none"
                : decision.mode() + "/" + decision.outcome()
                + " candidates=" + decision.objectiveCandidates() + '/' + decision.scoredCandidates()
                + " target=" + decision.selectedMobId() + ':' + decision.selectedObjectId());
    }

    @Override
    public List<String> battleStatus(Character leader) {
        if (leader == null || leader.getMap() == null) return List.of();
        if (postClearPhase != null && (leader.getMapId() == AgentBalrogDefinition.CLEAR_MAP
                || leader.getMapId() == AgentBalrogDefinition.RECRUIT_MAP)) {
            return List.of("Easy Balrog rewards: phase=" + postClearPhase + '.');
        }
        List<String> mobs = server.agents.perception.AgentMapPerception.monsters(leader.getMap()).stream()
                .filter(Monster::isAlive)
                .filter(mob -> AgentBalrogDefinition.COMBAT_MOBS.contains(mob.getId())
                        || mob.getId() == AgentBalrogDefinition.RELEASE_SEAL_MOB)
                .map(mob -> mob.getId() + (mob.isFake() ? "(fake)" : "")
                        + (ServerMobAutonomyService.isActiveInstance(mob)
                        ? "(server)" : "(client)")
                        + ((mob.isBuffed(MonsterStatus.WEAPON_REFLECT)
                        || mob.isBuffed(MonsterStatus.MAGIC_REFLECT)) ? "(reflect)" : "")
                        + "=" + mob.getHp() + '/' + mob.getMaxHp()
                        + "@" + (mob.getPosition() == null
                        ? "(?,?)" : '(' + Integer.toString(mob.getPosition().x)
                        + ',' + mob.getPosition().y + ')'))
                .toList();
        return mobs.isEmpty() ? List.of() : List.of("Easy Balrog mobs: " + mobs);
    }

    public List<AgentBalrogTestFixtureService.Build> roster() {
        return roster;
    }

    enum CombatPhase {
        SEALED_CLAW,
        CLAW,
        TRANSITION,
        BODY
    }

    enum PostClearPhase {
        ENTERING_REWARD_ROOM,
        BREAKING_REWARD_REACTOR,
        HUMAN_LOOT_GRACE,
        COLLECTING_REWARDS,
        EXITING_TO_LOBBY
    }

    @Override
    public List<String> rosterSummary() {
        return List.of(
                "Selected level-60 weapon builds: "
                        + roster.stream().map(AgentBalrogTestFixtureService.Build::buildId).toList(),
                "Per Agent: 2,000 Power Elixirs, 500 All Cures, 100 Sniper Pills, "
                        + "and 30,000 weapon projectiles when required.");
    }
}
