package server.life.autonomy.balrog;

import net.server.services.task.channel.OverallService;
import net.server.services.task.channel.ServerMobAutonomyService;
import net.server.services.type.ChannelServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.life.LifeFactory;
import server.life.Monster;
import server.life.MonsterListener;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;

/** Owns Easy Balrog's seal, claw, body, clear, and cleanup phase graph. */
public final class EasyBalrogEncounterService {
    public static final int BODY_ID = 8_830_007;
    public static final int RELEASED_CLAW_ID = 8_830_008;
    public static final int INITIAL_CLAW_ID = 8_830_009;
    public static final int RELEASED_CLAW_HELPER_ID = 8_830_011;
    public static final int INITIAL_CLAW_HELPER_ID = 8_830_012;
    public static final int RELEASE_SEAL_ID = 8_830_013;
    public static final long RELEASE_DELAY_MS = 60_000L;

    private static final Logger log =
            LoggerFactory.getLogger(EasyBalrogEncounterService.class);
    private static final Map<MapleMap, Encounter> ENCOUNTERS = new ConcurrentHashMap<>();

    private EasyBalrogEncounterService() {
    }

    public static EncounterHandle start(MapleMap map, Point origin) {
        return start(map, origin, LifeFactory::getMonster);
    }

    static EncounterHandle start(MapleMap map, Point origin,
                                 IntFunction<Monster> monsterFactory) {
        if (map == null || origin == null || map.getChannelServer() == null) {
            throw new IllegalArgumentException("Easy Balrog requires a live map and origin");
        }
        stop(map, "replaced");
        Encounter encounter = new Encounter(map, new Point(origin), monsterFactory);
        ENCOUNTERS.put(map, encounter);
        try {
            encounter.start();
            return encounter;
        } catch (RuntimeException failure) {
            ENCOUNTERS.remove(map, encounter);
            encounter.abort("start-failed");
            throw failure;
        }
    }

    public static void stop(MapleMap map) {
        stop(map, "host-stop");
    }

    public static void stop(MapleMap map, String reason) {
        if (map == null) {
            return;
        }
        Encounter encounter = ENCOUNTERS.remove(map);
        if (encounter != null) {
            encounter.abort(reason);
        }
    }

    public static boolean isActive(MapleMap map) {
        Encounter encounter = ENCOUNTERS.get(map);
        return encounter != null && encounter.phase != Phase.CLEARED
                && encounter.phase != Phase.ABORTED;
    }

    /** Returns the one stable, combined gauge used by all three encounter actors. */
    public static Optional<HpBarSnapshot> hpBarSnapshot(Monster monster) {
        if (monster == null || monster.getMap() == null) {
            return Optional.empty();
        }
        Encounter encounter = ENCOUNTERS.get(monster.getMap());
        return encounter == null ? Optional.empty() : encounter.hpBarSnapshot(monster);
    }

    public record HpBarSnapshot(int mobId, int currentHp, int maxHp,
                                byte tagColor, byte tagBackgroundColor,
                                int identityHash) {
    }

    public interface EncounterHandle {
        Phase phase();

        MapleMap map();

        void abort(String reason);
    }

    public enum Phase {
        SEALED,
        TWO_CLAWS,
        ONE_CLAW,
        BODY,
        CLEARED,
        ABORTED
    }

    private enum Role {
        BODY,
        RELEASED_CLAW,
        INITIAL_CLAW,
        RELEASE_SEAL
    }

    private static final class Encounter implements EncounterHandle {
        private final MapleMap map;
        private final Point origin;
        private final IntFunction<Monster> monsterFactory;
        private final long generation = System.nanoTime();
        private final EnumSet<Role> defeated = EnumSet.noneOf(Role.class);
        private final List<Monster> helpers = new ArrayList<>();
        private volatile Phase phase = Phase.SEALED;
        private Monster body;
        private Monster releasedClaw;
        private Monster initialClaw;
        private Monster seal;

        private Encounter(MapleMap map, Point origin,
                          IntFunction<Monster> monsterFactory) {
            this.map = map;
            this.origin = origin;
            this.monsterFactory = monsterFactory;
        }

        private synchronized void start() {
            body = actor(BODY_ID, Role.BODY);
            releasedClaw = actor(RELEASED_CLAW_ID, Role.RELEASED_CLAW);
            initialClaw = actor(INITIAL_CLAW_ID, Role.INITIAL_CLAW);
            seal = actor(RELEASE_SEAL_ID, Role.RELEASE_SEAL);

            map.spawnFakeMonsterOnGroundBelow(body, origin);
            map.spawnMonsterOnGroundBelow(initialClaw, origin);
            map.spawnMonsterOnGroundBelow(seal, origin);
            registerCombatActor(initialClaw);

            OverallService scheduler = (OverallService) map.getChannelServer()
                    .getServiceAccess(ChannelServices.OVERALL);
            scheduler.registerOverallAction(map.getId(), this::releaseSeal,
                    RELEASE_DELAY_MS);
            log.info("Easy Balrog encounter started map={} generation={}",
                    map.getId(), generation);
        }

        private Monster actor(int mobId, Role role) {
            Monster monster = monsterFactory.apply(mobId);
            if (monster == null) {
                throw new IllegalStateException("Missing Easy Balrog mob " + mobId);
            }
            monster.suppressRevives();
            monster.addListener(new EncounterMonsterListener(this, role, monster));
            return monster;
        }

        private void registerCombatActor(Monster monster) {
            if (map.getChannelServer().getServiceAccess(ChannelServices.MOB_AUTONOMY)
                    instanceof ServerMobAutonomyService autonomy) {
                autonomy.registerEncounterActor(monster, this);
            }
        }

        private synchronized void releaseSeal() {
            if (phase == Phase.CLEARED || phase == Phase.ABORTED
                    || defeated.contains(Role.RELEASE_SEAL)) {
                return;
            }
            if (seal != null && seal.isAlive() && seal.getMap() == map
                    && map.getMonsterByOid(seal.getObjectId()) == seal) {
                map.killMonster(seal, null, false, (short) 0);
            } else {
                onDefeated(Role.RELEASE_SEAL, seal, 0);
            }
        }

        private synchronized void onDefeated(Role role, Monster monster,
                                             int animationTimeMs) {
            if (phase == Phase.CLEARED || phase == Phase.ABORTED
                    || !defeated.add(role)) {
                return;
            }
            switch (role) {
                case RELEASE_SEAL -> spawnReleasedClaw();
                case RELEASED_CLAW, INITIAL_CLAW -> {
                    spawnHelper(role, monster, animationTimeMs);
                    boolean both = defeated.contains(Role.RELEASED_CLAW)
                            && defeated.contains(Role.INITIAL_CLAW);
                    if (both) {
                        activateBody();
                    } else {
                        phase = Phase.ONE_CLAW;
                    }
                }
                case BODY -> clear();
            }
        }

        private void spawnReleasedClaw() {
            if (phase == Phase.CLEARED || phase == Phase.ABORTED
                    || releasedClaw == null || releasedClaw.getMap() != null) {
                return;
            }
            map.spawnMonsterOnGroundBelow(releasedClaw, origin);
            registerCombatActor(releasedClaw);
            phase = defeated.contains(Role.INITIAL_CLAW)
                    ? Phase.ONE_CLAW : Phase.TWO_CLAWS;
            log.info("Easy Balrog seal released map={} generation={}",
                    map.getId(), generation);
        }

        private void spawnHelper(Role role, Monster defeatedMonster,
                                 int animationTimeMs) {
            if (defeatedMonster == null || map.getChannelServer() == null) {
                return;
            }
            int helperId = role == Role.RELEASED_CLAW
                    ? RELEASED_CLAW_HELPER_ID : INITIAL_CLAW_HELPER_ID;
            Point position = new Point(defeatedMonster.getPosition());
            OverallService scheduler = (OverallService) map.getChannelServer()
                    .getServiceAccess(ChannelServices.OVERALL);
            scheduler.registerOverallAction(map.getId(), () -> {
                synchronized (this) {
                    if (phase == Phase.CLEARED || phase == Phase.ABORTED) {
                        return;
                    }
                    Monster helper = monsterFactory.apply(helperId);
                    if (helper != null) {
                        helper.suppressRevives();
                        helpers.add(helper);
                        map.spawnMonsterOnGroundBelow(helper, position);
                    }
                }
            }, Math.max(0, animationTimeMs));
        }

        private void activateBody() {
            if (body == null || !body.isAlive() || body.getMap() != map
                    || map.getMonsterByOid(body.getObjectId()) != body) {
                abort("body-missing");
                return;
            }
            registerCombatActor(body);
            map.makeMonsterReal(body);
            phase = Phase.BODY;
            log.info("Easy Balrog body activated map={} generation={}",
                    map.getId(), generation);
        }

        private void clear() {
            phase = Phase.CLEARED;
            ENCOUNTERS.remove(map, this);
            removeHelpers();
            log.info("Easy Balrog encounter cleared map={} generation={}",
                    map.getId(), generation);
        }

        @Override
        public Phase phase() {
            return phase;
        }

        @Override
        public MapleMap map() {
            return map;
        }

        @Override
        public synchronized void abort(String reason) {
            if (phase == Phase.CLEARED || phase == Phase.ABORTED) {
                return;
            }
            phase = Phase.ABORTED;
            ENCOUNTERS.remove(map, this);
            removeActor(body);
            removeActor(releasedClaw);
            removeActor(initialClaw);
            removeActor(seal);
            removeHelpers();
            log.info("Easy Balrog encounter aborted map={} generation={} reason={}",
                    map.getId(), generation, reason);
        }

        private void removeHelpers() {
            for (Monster helper : List.copyOf(helpers)) {
                removeActor(helper);
            }
            helpers.clear();
        }

        private void removeActor(Monster monster) {
            if (monster != null && monster.isAlive() && monster.getMap() == map
                    && map.getMonsterByOid(monster.getObjectId()) == monster) {
                map.killMonster(monster, null, false, (short) 0);
            }
        }

        private synchronized Optional<HpBarSnapshot> hpBarSnapshot(Monster monster) {
            if (phase == Phase.CLEARED || phase == Phase.ABORTED
                    || monster != body && monster != releasedClaw
                    && monster != initialClaw) {
                return Optional.empty();
            }
            int maxHp = sumHp(Monster::getMaxHp);
            int currentHp = sumHp(actor -> Math.max(0, actor.getHp()));
            byte tagColor = body.getStats().getTagColor() > 0
                    ? body.getStats().getTagColor() : EasyBalrogHpBarPolicy.tagColor();
            byte background = body.getStats().getTagBgColor() > 0
                    ? body.getStats().getTagBgColor()
                    : EasyBalrogHpBarPolicy.tagBackgroundColor();
            return Optional.of(new HpBarSnapshot(
                    BODY_ID, currentHp, maxHp, tagColor, background, body.hashCode()));
        }

        private int sumHp(java.util.function.ToIntFunction<Monster> value) {
            long sum = 0L;
            for (Monster actor : List.of(body, releasedClaw, initialClaw)) {
                sum += value.applyAsInt(actor);
            }
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, sum));
        }
    }

    private record EncounterMonsterListener(Encounter encounter, Role role,
                                             Monster monster) implements MonsterListener {
        @Override
        public void monsterKilled(int animationTime) {
            encounter.onDefeated(role, monster, animationTime);
        }

        @Override
        public void monsterDamaged(client.Character from, int trueDmg) {
        }

        @Override
        public void monsterHealed(int trueHeal) {
        }
    }
}
