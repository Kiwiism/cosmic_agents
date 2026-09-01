package server.life.autonomy.balrog;

import net.server.channel.Channel;
import net.server.coordinator.world.MonsterAggroCoordinator;
import net.server.services.task.channel.OverallService;
import net.server.services.task.channel.ServerMobAutonomyService;
import net.server.services.type.ChannelServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EasyBalrogEncounterServiceTest {
    private MapleMap map;

    @AfterEach
    void tearDown() {
        EasyBalrogEncounterService.stop(map, "test-cleanup");
    }

    @Test
    void sealAndTwoDistinctClawDeathsActivateBodyAndClear() {
        map = mock(MapleMap.class);
        Channel channel = mock(Channel.class);
        OverallService scheduler = mock(OverallService.class);
        ServerMobAutonomyService autonomy = mock(ServerMobAutonomyService.class);
        AtomicInteger nextOid = new AtomicInteger(1_000);
        Map<Integer, Monster> live = new HashMap<>();
        List<Monster> spawned = new ArrayList<>();
        AtomicReference<Runnable> release = new AtomicReference<>();

        when(map.getId()).thenReturn(105100400);
        when(map.getAggroCoordinator()).thenReturn(mock(MonsterAggroCoordinator.class));
        when(map.getChannelServer()).thenReturn(channel);
        when(channel.getServiceAccess(ChannelServices.OVERALL)).thenReturn(scheduler);
        when(channel.getServiceAccess(ChannelServices.MOB_AUTONOMY)).thenReturn(autonomy);
        when(map.getMonsterByOid(anyInt())).thenAnswer(
                invocation -> live.get(invocation.<Integer>getArgument(0)));
        doAnswer(invocation -> {
            Monster monster = invocation.getArgument(0);
            Point position = invocation.getArgument(1);
            place(monster, position, map, nextOid, live, spawned);
            monster.setFake(true);
            return null;
        }).when(map).spawnFakeMonsterOnGroundBelow(any(Monster.class), any(Point.class));
        doAnswer(invocation -> {
            Monster monster = invocation.getArgument(0);
            Point position = invocation.getArgument(1);
            place(monster, position, map, nextOid, live, spawned);
            return null;
        }).when(map).spawnMonsterOnGroundBelow(any(Monster.class), any(Point.class));
        doAnswer(invocation -> {
            long delay = invocation.getArgument(2);
            if (delay == EasyBalrogEncounterService.RELEASE_DELAY_MS) {
                release.set(invocation.getArgument(1));
            }
            return null;
        }).when(scheduler).registerOverallAction(eq(105100400), any(Runnable.class), anyLong());
        doAnswer(invocation -> {
            Monster monster = invocation.getArgument(0);
            live.remove(monster.getObjectId());
            monster.dispatchMonsterKilled(false);
            return null;
        }).when(map).killMonster(any(Monster.class),
                org.mockito.ArgumentMatchers.isNull(), eq(false), eq((short) 0));
        doAnswer(invocation -> {
            ((Monster) invocation.getArgument(0)).setFake(false);
            return null;
        }).when(map).makeMonsterReal(any(Monster.class));

        EasyBalrogEncounterService.EncounterHandle encounter =
                EasyBalrogEncounterService.start(
                        map, new Point(412, 258), EasyBalrogEncounterServiceTest::monster);

        assertEquals(EasyBalrogEncounterService.Phase.SEALED, encounter.phase());
        assertNotNull(release.get());
        Monster body = mob(spawned, EasyBalrogEncounterService.BODY_ID);
        Monster initial = mob(spawned, EasyBalrogEncounterService.INITIAL_CLAW_ID);
        assertTrue(body.isFake());
        EasyBalrogEncounterService.HpBarSnapshot initialGauge =
                EasyBalrogEncounterService.hpBarSnapshot(initial).orElseThrow();
        assertEquals(EasyBalrogEncounterService.BODY_ID, initialGauge.mobId());
        assertEquals(300, initialGauge.currentHp());
        assertEquals(300, initialGauge.maxHp());
        assertEquals(body.hashCode(), initialGauge.identityHash());
        assertTrue(body.hasBossHPBar());
        assertEquals(body.bossHpBarHash(), initial.bossHpBarHash());

        initial.applyAndGetHpDamage(25, false);
        assertEquals(275, EasyBalrogEncounterService.hpBarSnapshot(initial)
                .orElseThrow().currentHp());

        release.get().run();
        assertEquals(EasyBalrogEncounterService.Phase.TWO_CLAWS, encounter.phase());
        Monster released = mob(spawned, EasyBalrogEncounterService.RELEASED_CLAW_ID);
        assertEquals(body.bossHpBarHash(), released.bossHpBarHash());

        initial.applyAndGetHpDamage(75, false);
        initial.dispatchMonsterKilled(true);
        assertEquals(EasyBalrogEncounterService.Phase.ONE_CLAW, encounter.phase());
        assertEquals(200, EasyBalrogEncounterService.hpBarSnapshot(released)
                .orElseThrow().currentHp());
        released.applyAndGetHpDamage(100, false);
        released.dispatchMonsterKilled(true);
        assertEquals(EasyBalrogEncounterService.Phase.BODY, encounter.phase());
        assertFalse(body.isFake());
        assertEquals(100, EasyBalrogEncounterService.hpBarSnapshot(body)
                .orElseThrow().currentHp());

        body.dispatchMonsterKilled(true);
        assertEquals(EasyBalrogEncounterService.Phase.CLEARED, encounter.phase());
        assertFalse(EasyBalrogEncounterService.isActive(map));
        assertTrue(EasyBalrogEncounterService.hpBarSnapshot(body).isEmpty());
        verify(autonomy, times(3)).registerEncounterActor(any(Monster.class), eq(encounter));
    }

    private static void place(Monster monster, Point position, MapleMap map,
                              AtomicInteger nextOid, Map<Integer, Monster> live,
                              List<Monster> spawned) {
        monster.setMap(map);
        monster.setPosition(position);
        monster.setObjectId(nextOid.getAndIncrement());
        live.put(monster.getObjectId(), monster);
        spawned.add(monster);
    }

    private static Monster mob(List<Monster> monsters, int id) {
        return monsters.stream().filter(monster -> monster.getId() == id)
                .findFirst().orElseThrow();
    }

    private static Monster monster(int id) {
        MonsterStats stats = new MonsterStats();
        stats.hp = 100;
        stats.mp = 100;
        stats.name = Integer.toString(id);
        stats.boss = true;
        EasyBalrogHpBarPolicy.applyMissingStyle(id, stats);
        return new Monster(id, stats);
    }
}
