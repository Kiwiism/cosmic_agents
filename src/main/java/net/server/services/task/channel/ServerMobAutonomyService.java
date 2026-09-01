package net.server.services.task.channel;

import client.BotClient;
import client.Character;
import net.packet.Packet;
import net.server.services.BaseService;
import net.server.services.type.ChannelServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;
import scripting.event.EventInstanceManager;
import server.combat.ServerMobDamageService;
import server.life.MobSkill;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.BossActionGeometry;
import server.life.autonomy.BossActorBehavior;
import server.life.autonomy.BossClientSimulationCapability;
import server.life.autonomy.GenericWzMobBehavior;
import server.life.autonomy.ServerMobActionCatalog;
import server.life.autonomy.ServerMobBehaviorRegistry;
import server.life.autonomy.balrog.BalrogSummonedAddBehavior;
import server.life.autonomy.chronos.ChronosFamilyActorBehavior;
import server.expeditions.Expedition;
import server.maps.MapleMap;
import server.movement.AbsoluteLifeMovement;
import server.movement.LifeMovementFragment;
import tools.PacketCreator;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.random.RandomGenerator;

/** Channel-owned combat decisions for mob templates registered for server autonomy. */
public final class ServerMobAutonomyService extends BaseService {
    private static final Logger log = LoggerFactory.getLogger(ServerMobAutonomyService.class);
    private static final int TICK_MS = 50;
    private static final int MIN_ACTION_LOCK_MS = 500;
    private static final int DECISION_PAUSE_MS = 250;
    private static final long CLIENT_CAPABILITY_GRACE_NANOS = 2_000_000_000L;
    private static final long CLIENT_CONTROLLER_LEASE_NANOS = 15_000_000_000L;
    private static final Set<ServerMobAutonomyService> INSTANCES = ConcurrentHashMap.newKeySet();

    private final Map<Monster, ActorRuntime> actors = new ConcurrentHashMap<>();
    private final Map<Object, AuthorityRuntime> authorities = new ConcurrentHashMap<>();
    private final Map<Monster, AuthorityRuntime> authorityByMonster = new ConcurrentHashMap<>();
    private final Set<Monster> agentInteractiveSummons = ConcurrentHashMap.newKeySet();
    private final RandomGenerator random;
    private final boolean schedulingEnabled;
    private final long clientCapabilityGraceNanos;
    private final Object tickLock = new Object();
    private volatile ScheduledFuture<?> task;

    public ServerMobAutonomyService() {
        this(RandomGenerator.getDefault(), true);
    }

    ServerMobAutonomyService(RandomGenerator random, boolean schedulingEnabled) {
        this(random, schedulingEnabled, CLIENT_CAPABILITY_GRACE_NANOS);
    }

    ServerMobAutonomyService(RandomGenerator random, boolean schedulingEnabled,
                             long clientCapabilityGraceNanos) {
        this.random = random;
        this.schedulingEnabled = schedulingEnabled;
        this.clientCapabilityGraceNanos = Math.max(0L, clientCapabilityGraceNanos);
        INSTANCES.add(this);
    }

    public boolean acquire(Monster monster, Character activatingAgent) {
        if (monster == null || activatingAgent == null || !monster.isAlive()) {
            return false;
        }
        MapleMap map = monster.getMap();
        if (map == null || activatingAgent.getMap() != map) {
            return false;
        }
        boolean regularSummon = agentInteractiveSummons.contains(monster);
        BossActorBehavior chronosBehavior = ChronosFamilyActorBehavior
                .behaviorFor(monster.getId()).orElse(null);
        BossActorBehavior balrogAddBehavior = BalrogSummonedAddBehavior
                .behaviorFor(monster.getId()).orElse(null);
        boolean ordinaryServerMob = regularSummon || chronosBehavior != null
                || balrogAddBehavior != null;
        BossActorBehavior behavior = ServerMobBehaviorRegistry.behaviorFor(monster.getId())
                .orElseGet(() -> chronosBehavior != null
                        ? chronosBehavior
                        : balrogAddBehavior != null
                        ? balrogAddBehavior
                        : regularSummon ? new GenericWzMobBehavior(monster.getId()) : null);
        if (behavior == null) {
            return false;
        }
        synchronized (tickLock) {
            AuthorityRuntime authority = authorityByMonster.get(monster);
            if (authority == null) {
                authority = register(monster, behavior, monster, ordinaryServerMob,
                        ordinaryServerMob, ordinaryServerMob ? -1 : eligiblePartyId(activatingAgent),
                        System.nanoTime());
            } else if (ordinaryServerMob && authority.mode != AuthorityMode.SERVER_STICKY) {
                activateServer(authority);
            }
            ActorRuntime actor = actors.get(monster);
            if (actor != null && behavior.usesPrimaryAggroTargetOnly()) {
                actor.primaryAggroTargetId = activatingAgent.getId();
            }
            ensureScheduled();
            return authority.mode == AuthorityMode.SERVER_STICKY;
        }
    }

    /** Registers a supported standalone boss as soon as it becomes visible. */
    public void registerSpawnedBoss(Monster monster) {
        if (monster == null || monster.isFake() || !monster.isAlive()) {
            return;
        }
        BossActorBehavior behavior = ServerMobBehaviorRegistry.behaviorFor(monster.getId())
                .filter(BossActorBehavior::autoStartOnSpawn)
                .orElse(null);
        if (behavior == null || monster.getMap() == null) {
            return;
        }
        synchronized (tickLock) {
            if (!authorityByMonster.containsKey(monster)) {
                register(monster, behavior, monster, false, false, -1, System.nanoTime());
            }
        }
        ensureScheduled();
    }

    /** Adds a boss component to one encounter-wide authority decision. */
    public void registerEncounterActor(Monster monster, Object encounterKey) {
        if (monster == null || encounterKey == null || !monster.isAlive()) {
            return;
        }
        BossActorBehavior behavior = ServerMobBehaviorRegistry.behaviorFor(monster.getId())
                .orElse(null);
        if (behavior == null || monster.getMap() == null) {
            return;
        }
        synchronized (tickLock) {
            AuthorityRuntime existing = authorityByMonster.get(monster);
            if (existing != null && existing.key != encounterKey) {
                release(monster, "join-encounter");
            }
            if (!authorityByMonster.containsKey(monster)) {
                register(monster, behavior, encounterKey, false, false, -1, System.nanoTime());
            }
        }
        ensureScheduled();
    }

    /** Makes a summoned add inherit its parent's encounter authority. */
    public static void inheritAuthorityInstances(Monster parent, Monster child) {
        if (parent == null || child == null) {
            return;
        }
        for (ServerMobAutonomyService service : Set.copyOf(INSTANCES)) {
            service.inheritAuthority(parent, child);
        }
    }

    private void inheritAuthority(Monster parent, Monster child) {
        synchronized (tickLock) {
            AuthorityRuntime authority = authorityByMonster.get(parent);
            if (authority == null || child.getMap() != authority.map) {
                return;
            }
            // Ordinary summons retain normal client control until an Agent hit promotes
            // movement and combat together for the bounded Agent-aggro lease.
            if (!ServerMobBehaviorRegistry.supports(child.getId())) {
                BossActorBehavior parentBehavior = authority.behaviors.get(parent);
                if (parentBehavior != null
                        && !parentBehavior.allowServerTakeoverForOrdinarySummons()) {
                    return;
                }
                agentInteractiveSummons.add(child);
                return;
            }
            if (!authorityByMonster.containsKey(child)) {
                BossActorBehavior behavior = ServerMobBehaviorRegistry.behaviorFor(child.getId())
                        .orElseThrow();
                register(child, behavior, authority.key,
                        authority.mode == AuthorityMode.SERVER_STICKY,
                        false,
                        authority.eligiblePartyId, System.nanoTime());
            }
        }
        ensureScheduled();
    }

    /** Records a controller packet only after MOVE_LIFE ownership validation succeeds. */
    public void recordAcceptedClientMovement(Monster monster, Character controller) {
        if (monster == null || controller == null) {
            return;
        }
        synchronized (tickLock) {
            AuthorityRuntime authority = authorityByMonster.get(monster);
            if (authority == null || authority.mode != AuthorityMode.NATIVE_CLIENT
                    || authority.controllerId != controller.getId()) {
                return;
            }
            long now = System.nanoTime();
            boolean firstConfirmation = !authority.confirmed;
            authority.confirmed = true;
            authority.leaseUntilNanos = now + CLIENT_CONTROLLER_LEASE_NANOS;
            if (firstConfirmation) {
                log.info("Boss native controller confirmed map={} controller={} actors={}",
                        authority.map.getId(), controller.getId(),
                        authority.monsters.size());
            }
        }
    }

    public boolean isActive(Monster monster) {
        return monster != null && actors.containsKey(monster);
    }

    public static boolean requiresServerPhysicsInstance(Monster monster) {
        if (monster == null) return false;
        for (ServerMobAutonomyService service : Set.copyOf(INSTANCES)) {
            AuthorityRuntime authority = service.authorityByMonster.get(monster);
            BossActorBehavior behavior = authority == null
                    ? null : authority.behaviors.get(monster);
            if (authority != null && authority.mode == AuthorityMode.SERVER_STICKY
                    && (authority.ordinaryMob
                    || behavior != null && behavior.usesServerMobPhysics())) {
                return true;
            }
        }
        return false;
    }

    public boolean blocksAgentPhysics(Monster monster) {
        AuthorityRuntime authority = monster == null ? null : authorityByMonster.get(monster);
        BossActorBehavior behavior = authority == null
                ? null : authority.behaviors.get(monster);
        return authority != null && !authority.ordinaryMob
                && (behavior == null || !behavior.usesServerMobPhysics());
    }

    public static boolean blocksAgentPhysicsInstance(Monster monster) {
        if (monster == null) return false;
        for (ServerMobAutonomyService service : Set.copyOf(INSTANCES)) {
            if (service.blocksAgentPhysics(monster)) return true;
        }
        return false;
    }

    public static void releaseOrdinaryAggroInstances(Monster monster, String reason) {
        if (monster == null) return;
        for (ServerMobAutonomyService service : Set.copyOf(INSTANCES)) {
            service.releaseOrdinaryAggro(monster, reason);
        }
    }

    private void releaseOrdinaryAggro(Monster monster, String reason) {
        AuthorityRuntime authority = authorityByMonster.get(monster);
        if (authority == null || !authority.ordinaryMob) return;
        boolean mayReacquire = monster.isAlive() && monster.getMap() == authority.map;
        release(monster, reason, mayReacquire);
    }

    public boolean retainsNativeAuthority(Monster monster) {
        AuthorityRuntime authority = monster == null ? null : authorityByMonster.get(monster);
        return authority != null && authority.mode == AuthorityMode.NATIVE_CLIENT;
    }

    public static boolean isActiveInstance(Monster monster) {
        for (ServerMobAutonomyService service : Set.copyOf(INSTANCES)) {
            if (service.isActive(monster)) {
                return true;
            }
        }
        return false;
    }

    public static void releaseMonsterInstances(Monster monster) {
        for (ServerMobAutonomyService service : Set.copyOf(INSTANCES)) {
            service.release(monster, "monster-removed");
        }
    }

    private synchronized void ensureScheduled() {
        if (schedulingEnabled && task == null
                && (!actors.isEmpty() || !authorities.isEmpty())) {
            task = TimerManager.getInstance().register(this::tickSafely, TICK_MS, TICK_MS);
        }
    }

    private synchronized void stopIfIdle() {
        if (actors.isEmpty() && authorities.isEmpty() && task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void tickSafely() {
        try {
            tickAt(System.nanoTime());
        } catch (RuntimeException | LinkageError failure) {
            log.warn("Server mob autonomy tick failed", failure);
        }
    }

    void tickForTest(long nowNanos) {
        tickAt(nowNanos);
    }

    int activeActorCountForTest() {
        return actors.size();
    }

    int nativeAuthorityCountForTest() {
        return (int) authorities.values().stream()
                .filter(authority -> authority.mode == AuthorityMode.NATIVE_CLIENT)
                .count();
    }

    List<Character> combatTargetsForTest(Monster monster) {
        ActorRuntime actor = actors.get(monster);
        return actor == null ? List.of() : targetsFor(actor);
    }

    private void tickAt(long nowNanos) {
        synchronized (tickLock) {
            for (AuthorityRuntime authority : List.copyOf(authorities.values())) {
                pruneInvalidMembers(authority);
                if (authority.monsters.isEmpty()) {
                    authorities.remove(authority.key, authority);
                    continue;
                }
                if (authority.mode == AuthorityMode.NATIVE_CLIENT
                        && !retainNativeAuthority(authority, nowNanos)) {
                    activateServer(authority);
                }
            }
            for (ActorRuntime actor : List.copyOf(actors.values())) {
                if (!valid(actor)) {
                    release(actor.monster, "invalid-state");
                    continue;
                }
                if (nowNanos < actor.actionUntilNanos || nowNanos < actor.nextDecisionNanos) {
                    continue;
                }
                if (physicsReactionInProgress(actor)) {
                    actor.nextDecisionNanos = nowNanos + DECISION_PAUSE_MS * 1_000_000L;
                    continue;
                }
                List<Character> targets = targetsFor(actor);
                if (targets.isEmpty()) {
                    actor.nextDecisionNanos = nowNanos + 500_000_000L;
                    continue;
                }
                actor.behavior.select(actor.monster, targets, actor.actions, random)
                        .ifPresent(selected -> startAction(actor, selected, nowNanos));
                if (actor.nextDecisionNanos <= nowNanos) {
                    actor.nextDecisionNanos = nowNanos + DECISION_PAUSE_MS * 1_000_000L;
                }
            }
        }
        stopIfIdle();
    }

    private void startAction(ActorRuntime actor, BossActorBehavior.SelectedAction selected,
                             long nowNanos) {
        BossAction action = selected.action();
        Point origin = actor.monster.getPosition();
        if (origin == null) {
            return;
        }
        if (!reserve(actor.monster, action)) {
            return;
        }
        Point targetPosition = selected.primaryTarget() == null
                ? null : selected.primaryTarget().getPosition();
        boolean facingLeft = selected.facingLeftOverride() != null
                ? selected.facingLeftOverride()
                : targetPosition == null
                        ? (actor.monster.getStance() & 1) != 0
                        : targetPosition.x < origin.x;
        List<Integer> selectedRegions = selectRegions(action);
        int pOption = encodeRegionOption(selectedRegions);
        int impactDelayMs = impactDelay(action);
        int actionLockMs = Math.max(MIN_ACTION_LOCK_MS,
                Math.max(action.animationTimeMs(), impactDelayMs));
        actor.actionUntilNanos = nowNanos + actionLockMs * 1_000_000L;
        actor.nextDecisionNanos = actor.actionUntilNanos + DECISION_PAUSE_MS * 1_000_000L;
        pausePhysics(actor, actor.actionUntilNanos);
        broadcastAction(actor, action, facingLeft, pOption, origin);
        if (action instanceof BossAction.Skill skill) {
            log.info("Boss server cast mob={} oid={} map={} skill={} level={} action={} "
                            + "impactMs={} lockMs={}",
                    actor.monster.getId(), actor.monster.getObjectId(), actor.map.getId(),
                    skill.mobSkill().getType(), skill.mobSkill().getId().level(),
                    action.actionNumber(), impactDelayMs, actionLockMs);
        } else {
            log.debug("Boss server action mob={} oid={} map={} action={} regions={} impactMs={}",
                    actor.monster.getId(), actor.monster.getObjectId(), actor.map.getId(),
                    action.actionNumber(), selectedRegions, impactDelayMs);
        }
        int primaryTargetId = selected.primaryTarget() == null
                ? 0 : selected.primaryTarget().getId();
        scheduleImpact(actor, new PreparedAction(
                action, facingLeft, selectedRegions, primaryTargetId), impactDelayMs);
    }

    private static boolean reserve(Monster monster, BossAction action) {
        if (action instanceof BossAction.Skill skill) {
            return monster.canUseSkill(skill.mobSkill(), true);
        }
        BossAction.OrdinaryAttack attack = (BossAction.OrdinaryAttack) action;
        return monster.canUseAttack(attack.attackIndex(), false) > 0;
    }

    private static int impactDelay(BossAction action) {
        if (action instanceof BossAction.Skill skill) {
            return Math.max(0, skill.effectDelayMs());
        }
        return Math.max(0, ((BossAction.OrdinaryAttack) action).impactDelayMs());
    }

    private static void pausePhysics(ActorRuntime actor, long untilNanos) {
        if (actor.map.getChannelServer() == null) {
            return;
        }
        MobPhysicsService physics = (MobPhysicsService) actor.map.getChannelServer()
                .getServiceAccess(ChannelServices.MOB_PHYSICS);
        physics.beginServerCombatAction(actor.monster, untilNanos);
    }

    private static boolean physicsReactionInProgress(ActorRuntime actor) {
        if (actor.map.getChannelServer() == null) {
            return false;
        }
        MobPhysicsService physics = (MobPhysicsService) actor.map.getChannelServer()
                .getServiceAccess(ChannelServices.MOB_PHYSICS);
        return physics.reactionInProgress(actor.monster);
    }

    private List<Integer> selectRegions(BossAction action) {
        if (!(action instanceof BossAction.OrdinaryAttack attack)
                || !attack.hasDistributedRegions()) {
            return List.of();
        }
        List<Integer> regions = new ArrayList<>();
        for (int i = 0; i < attack.areaCount(); i++) {
            regions.add(i);
        }
        for (int i = regions.size() - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            int value = regions.get(i);
            regions.set(i, regions.get(swap));
            regions.set(swap, value);
        }
        return regions.stream().limit(attack.selectedAreaCount()).sorted().toList();
    }

    private static int encodeRegionOption(List<Integer> selectedRegions) {
        int mask = 0;
        for (int region : selectedRegions) {
            if (region >= 0 && region < Short.SIZE) {
                mask |= 1 << region;
            }
        }
        return mask;
    }

    private static void broadcastAction(ActorRuntime actor, BossAction action,
                                        boolean facingLeft, int pOption, Point origin) {
        int facing = facingLeft ? 1 : 0;
        int rawActivity;
        int skillId = 0;
        int skillLevel = 0;
        if (action instanceof BossAction.Skill skill) {
            rawActivity = 42 + (skill.actionNumber() - 1) * 2 + facing;
            skillId = skill.mobSkill().getId().type().getId();
            skillLevel = skill.mobSkill().getId().level();
        } else {
            BossAction.OrdinaryAttack attack = (BossAction.OrdinaryAttack) action;
            rawActivity = 24 + attack.attackIndex() * 2 + facing;
        }

        int stableStance = facingLeft ? 5 : 4;
        AbsoluteLifeMovement movement = new AbsoluteLifeMovement(
                0, new Point(origin.x, origin.y + 2), 0, stableStance);
        movement.setPixelsPerSecond(new Point(0, 0));
        movement.setFh(actor.monster.getFh());
        Packet packet = PacketCreator.moveMonster(
                actor.monster.getObjectId(), rawActivity, skillId, skillLevel,
                pOption, origin, List.<LifeMovementFragment>of(movement));
        // A server-owned action has no controlling client whose movement packet can
        // fill in distant observers. Publish the telegraph map-wide so every real
        // client that currently has this field loaded sees the same boss animation.
        actor.map.broadcastMessage(packet);
        if (action instanceof BossAction.OrdinaryAttack attack && attack.tremble()) {
            actor.map.broadcastMessage(PacketCreator.trembleEffect(1, 0));
        }
    }

    private static void scheduleImpact(ActorRuntime actor, PreparedAction prepared,
                                       int delayMs) {
        if (actor.map.getChannelServer() == null) {
            return;
        }
        OverallService overall = (OverallService) actor.map.getChannelServer()
                .getServiceAccess(ChannelServices.OVERALL);
        overall.registerOverallAction(actor.map.getId(),
                () -> applyImpact(actor, prepared), delayMs);
    }

    private static void applyImpact(ActorRuntime actor, PreparedAction prepared) {
        if (!valid(actor)) {
            return;
        }
        BossAction action = prepared.action;
        if (action instanceof BossAction.Skill skillAction) {
            List<Character> banished = new ArrayList<>();
            MobSkill skill = skillAction.mobSkill();
            skill.applyEffect(null, actor.monster, true, banished,
                    target -> isCombatTarget(actor, target));
            for (Character target : banished) {
                target.changeMapBanish(actor.monster.getBanish());
            }
            return;
        }

        BossAction.OrdinaryAttack attack = (BossAction.OrdinaryAttack) action;
        Point origin = actor.monster.getPosition();
        List<Character> impactTargets = livingTargets(actor);
        if (actor.behavior.usesPrimaryAggroTargetOnly()) {
            impactTargets = impactTargets.stream()
                    .filter(target -> target.getId() == prepared.primaryTargetId)
                    .toList();
        }
        for (Character target : impactTargets) {
            if (BossActionGeometry.contains(
                    attack, origin, target.getPosition(), prepared.facingLeft,
                    prepared.selectedRegions)) {
                ServerMobDamageService.applyOrdinaryAttack(actor.monster, target, attack);
            }
        }
    }

    private static List<Character> livingTargets(ActorRuntime actor) {
        return actor.map.getAllPlayers().stream()
                .filter(Character::isAlive)
                .filter(target -> target.isLoggedinWorld()
                        || target.getClient() instanceof BotClient)
                .filter(target -> !target.isChangingMaps() && !target.isHidden())
                .filter(target -> target.getMap() == actor.map && target.getPosition() != null)
                .filter(target -> isCombatTarget(actor, target))
                .toList();
    }

    private static boolean isCombatTarget(ActorRuntime actor, Character target) {
        EventInstanceManager event = actor.map.getEventInstance();
        if (event != null && target.getEventInstance() != event) {
            return false;
        }
        Expedition expedition = event == null ? null : event.getExpedition();
        return actor.authority.ordinaryMob || expedition == null || expedition.contains(target);
    }

    private static List<Character> targetsFor(ActorRuntime actor) {
        List<Character> targets = livingTargets(actor);
        if (!actor.behavior.usesPrimaryAggroTargetOnly()) {
            return targets;
        }
        return targets.stream()
                .filter(target -> target.getId() == actor.primaryAggroTargetId)
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }

    private static boolean valid(ActorRuntime actor) {
        return actor.active && valid(actor.monster, actor.map);
    }

    private static boolean valid(Monster monster, MapleMap map) {
        return monster.isAlive() && monster.getMap() == map
                && map.getMonsterByOid(monster.getObjectId()) == monster;
    }

    private AuthorityRuntime register(Monster monster, BossActorBehavior behavior,
                                      Object key, boolean forceServer,
                                      boolean ordinaryMob, int eligiblePartyId, long nowNanos) {
        MapleMap map = monster.getMap();
        AuthorityRuntime authority = authorities.get(key);
        if (authority == null) {
            AuthorityMode initialMode = forceServer
                    || capableClientCandidates(map, eligiblePartyId).isEmpty()
                    ? AuthorityMode.SERVER_STICKY : AuthorityMode.NATIVE_CLIENT;
            authority = new AuthorityRuntime(
                    key, map, initialMode, eligiblePartyId, ordinaryMob);
            authorities.put(key, authority);
        }
        if (authority.map != map) {
            throw new IllegalArgumentException("boss authority cannot span map instances");
        }
        authority.monsters.add(monster);
        authority.behaviors.put(monster, behavior);
        authorityByMonster.put(monster, authority);
        if (authority.mode == AuthorityMode.SERVER_STICKY) {
            activateServerActor(monster, map, behavior, authority);
        } else {
            retainNativeAuthority(authority, nowNanos);
        }
        return authority;
    }

    private void activateServer(AuthorityRuntime authority) {
        if (authority.mode == AuthorityMode.SERVER_STICKY) {
            return;
        }
        authority.mode = AuthorityMode.SERVER_STICKY;
        authority.controllerId = 0;
        authority.confirmed = false;
        for (Monster monster : List.copyOf(authority.monsters)) {
            BossActorBehavior behavior = authority.behaviors.get(monster);
            if (behavior != null && valid(monster, authority.map)) {
                activateServerActor(monster, authority.map, behavior, authority);
            }
        }
        log.info("Boss authority entered sticky server mode map={} actors={}",
                authority.map.getId(), authority.monsters.size());
    }

    private void activateServerActor(Monster monster, MapleMap map,
                                     BossActorBehavior behavior,
                                     AuthorityRuntime authority) {
        if (!authority.ordinaryMob && !behavior.usesServerMobPhysics()) {
            MobPhysicsService.releaseMonsterInstances(
                    monster, MobPhysicsService.ReleaseReason.SERVER_COMBAT_OWNERSHIP);
        }
        monster.clearBossControllerPin();
        monster.aggroRemoveController();
        ActorRuntime created = new ActorRuntime(
                monster, map, behavior, ServerMobActionCatalog.forMob(monster.getId()), authority);
        ActorRuntime actor = actors.putIfAbsent(monster, created);
        if (actor == null) {
            log.info("Server mob autonomy acquired mob={} oid={} map={}",
                    monster.getId(), monster.getObjectId(), map.getId());
        }
    }

    private boolean retainNativeAuthority(AuthorityRuntime authority, long nowNanos) {
        List<Character> candidates = capableClientCandidates(
                authority.map, authority.eligiblePartyId).stream()
                .filter(candidate -> !authority.rejectedControllerIds.contains(candidate.getId()))
                .toList();
        Character selected = candidates.stream()
                .filter(candidate -> candidate.getId() == authority.controllerId)
                .findFirst()
                .orElse(null);

        if (selected != null && authority.confirmed
                && nowNanos < authority.leaseUntilNanos) {
            pinNativeController(authority, selected);
            return true;
        }
        if (selected != null && !authority.confirmed
                && nowNanos < authority.graceUntilNanos) {
            pinNativeController(authority, selected);
            return true;
        }
        if (authority.controllerId != 0) {
            authority.rejectedControllerIds.add(authority.controllerId);
            candidates = capableClientCandidates(
                    authority.map, authority.eligiblePartyId).stream()
                    .filter(candidate ->
                            !authority.rejectedControllerIds.contains(candidate.getId()))
                    .toList();
        }
        if (candidates.isEmpty()) {
            return false;
        }
        selected = candidates.getFirst();
        authority.controllerId = selected.getId();
        authority.confirmed = false;
        authority.graceUntilNanos = nowNanos + clientCapabilityGraceNanos;
        authority.leaseUntilNanos = 0L;
        pinNativeController(authority, selected);
        log.info("Boss native controller selected map={} controller={} actors={}",
                authority.map.getId(), selected.getId(), authority.monsters.size());
        return true;
    }

    private static void pinNativeController(AuthorityRuntime authority, Character selected) {
        for (Monster monster : List.copyOf(authority.monsters)) {
            if (!valid(monster, authority.map)) {
                continue;
            }
            monster.pinBossController(selected);
            Character current = monster.getController();
            if (current == null || current.getId() != selected.getId()) {
                monster.aggroSwitchController(selected, true);
            }
        }
    }

    private static List<Character> capableClientCandidates(
            MapleMap map, int eligiblePartyId) {
        EventInstanceManager event = map.getEventInstance();
        List<Character> roster = event == null ? map.getAllPlayers() : event.getPlayers();
        if (roster == null) {
            return List.of();
        }
        Set<Integer> presentIds = map.getAllPlayers().stream()
                .map(Character::getId)
                .collect(java.util.stream.Collectors.toSet());
        Expedition expedition = event == null ? null : event.getExpedition();
        var stream = roster.stream()
                .filter(character -> presentIds.contains(character.getId()))
                .filter(character -> expedition == null || expedition.contains(character))
                .filter(Character::isAlive)
                .filter(Character::isLoggedinWorld)
                .filter(character -> character.getMap() == map && !character.isChangingMaps())
                .filter(character -> !character.isHidden())
                .filter(character -> eligiblePartyId <= 0
                        || character.getPartyId() == eligiblePartyId)
                .filter(character -> character.getClient() != null
                        && character.getClient().getBossSimulationCapability()
                        == BossClientSimulationCapability.NATIVE_MOB_SIMULATION);
        if (event != null) {
            stream = stream.filter(character -> character.getEventInstance() == event);
        }
        return stream.sorted(Comparator.comparingInt(Character::getId)).toList();
    }

    private static int eligiblePartyId(Character activatingAgent) {
        if (activatingAgent == null) return -1;
        int partyId = activatingAgent.getPartyId();
        return partyId > 0 ? partyId : -1;
    }

    private void pruneInvalidMembers(AuthorityRuntime authority) {
        for (Monster monster : List.copyOf(authority.monsters)) {
            if (!valid(monster, authority.map)) {
                release(monster, "invalid-authority-state");
            }
        }
    }

    private void release(Monster monster, String reason) {
        release(monster, reason, false);
    }

    private void release(Monster monster, String reason, boolean retainOrdinaryEligibility) {
        synchronized (tickLock) {
            if (!retainOrdinaryEligibility) agentInteractiveSummons.remove(monster);
            ActorRuntime removed = actors.remove(monster);
            AuthorityRuntime authority = authorityByMonster.remove(monster);
            monster.clearBossControllerPin();
            if (removed != null) {
                removed.active = false;
                log.info("Server mob autonomy released mob={} oid={} map={} reason={}",
                        monster.getId(), monster.getObjectId(), removed.map.getId(), reason);
            }
            if (authority != null) {
                authority.monsters.remove(monster);
                authority.behaviors.remove(monster);
                if (authority.monsters.isEmpty()) {
                    authorities.remove(authority.key, authority);
                }
                log.info("Boss authority released mob={} oid={} map={} mode={} reason={}",
                        monster.getId(), monster.getObjectId(), authority.map.getId(),
                        authority.mode, reason);
            }
        }
        stopIfIdle();
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> current = task;
        task = null;
        if (current != null) {
            current.cancel(false);
        }
        actors.values().forEach(actor -> actor.active = false);
        authorityByMonster.keySet().forEach(Monster::clearBossControllerPin);
        actors.clear();
        authorityByMonster.clear();
        authorities.clear();
        agentInteractiveSummons.clear();
        INSTANCES.remove(this);
    }

    private static final class ActorRuntime {
        private final Monster monster;
        private final MapleMap map;
        private final BossActorBehavior behavior;
        private final ServerMobActionCatalog.MonsterActions actions;
        private final AuthorityRuntime authority;
        private long actionUntilNanos;
        private long nextDecisionNanos;
        private int primaryAggroTargetId;
        private volatile boolean active = true;

        private ActorRuntime(Monster monster, MapleMap map, BossActorBehavior behavior,
                             ServerMobActionCatalog.MonsterActions actions,
                             AuthorityRuntime authority) {
            this.monster = monster;
            this.map = map;
            this.behavior = behavior;
            this.actions = actions;
            this.authority = authority;
        }
    }

    private static final class AuthorityRuntime {
        private final Object key;
        private final MapleMap map;
        private final Set<Monster> monsters = new LinkedHashSet<>();
        private final Map<Monster, BossActorBehavior> behaviors = new ConcurrentHashMap<>();
        private final Set<Integer> rejectedControllerIds = new HashSet<>();
        private final int eligiblePartyId;
        private final boolean ordinaryMob;
        private AuthorityMode mode;
        private int controllerId;
        private long graceUntilNanos;
        private long leaseUntilNanos;
        private boolean confirmed;

        private AuthorityRuntime(Object key, MapleMap map, AuthorityMode mode,
                                 int eligiblePartyId, boolean ordinaryMob) {
            this.key = key;
            this.map = map;
            this.mode = mode;
            this.eligiblePartyId = eligiblePartyId;
            this.ordinaryMob = ordinaryMob;
        }
    }

    private enum AuthorityMode {
        NATIVE_CLIENT,
        SERVER_STICKY
    }

    private record PreparedAction(BossAction action, boolean facingLeft,
                                  List<Integer> selectedRegions, int primaryTargetId) {
    }
}
