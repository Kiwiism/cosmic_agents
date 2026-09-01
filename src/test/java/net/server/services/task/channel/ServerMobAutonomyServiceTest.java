package net.server.services.task.channel;

import client.BotClient;
import client.Character;
import client.Client;
import net.packet.Packet;
import net.server.channel.Channel;
import net.server.services.type.ChannelServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.partyquest.lpq.AgentLpqMemberState;
import server.agents.capabilities.partyquest.lpq.AgentLpqSession;
import server.agents.capabilities.partyquest.lpq.AgentLpqSessionRegistry;
import server.expeditions.Expedition;
import server.life.Monster;
import server.life.autonomy.BossClientSimulationCapability;
import server.life.autonomy.alishar.AlisharActorBehavior;
import server.life.autonomy.balrog.EasyBalrogInitialClawBehavior;
import server.life.autonomy.balrog.EasyBalrogReleasedClawBehavior;
import server.life.autonomy.papapixie.PapaPixieActorBehavior;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerMobAutonomyServiceTest {
    private final RandomGenerator random = mock(RandomGenerator.class);
    private final ServerMobAutonomyService service =
            new ServerMobAutonomyService(random, false);

    @AfterEach
    void tearDown() {
        service.dispose();
    }

    @Test
    void acquiresRegisteredTemplateIdempotentlyAndCleansInvalidActor() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = mock(Character.class);
        when(map.getId()).thenReturn(922010900);
        when(monster.getId()).thenReturn(AlisharActorBehavior.MOB_ID);
        when(monster.getObjectId()).thenReturn(77);
        when(monster.getMap()).thenReturn(map);
        when(monster.isAlive()).thenReturn(true);
        when(agent.getMap()).thenReturn(map);
        when(map.getMonsterByOid(77)).thenReturn(monster);

        assertTrue(service.acquire(monster, agent));
        assertTrue(service.acquire(monster, agent));
        assertEquals(1, service.activeActorCountForTest());

        when(monster.isAlive()).thenReturn(false);
        service.tickForTest(System.nanoTime());
        assertEquals(0, service.activeActorCountForTest());
    }

    @Test
    void stickyServerAlisharKeepsRoamingPhysicsAvailable() {
        Fixture fixture = fixture(0);

        assertTrue(service.acquire(fixture.monster, fixture.agent));

        assertFalse(service.blocksAgentPhysics(fixture.monster));
        assertTrue(ServerMobAutonomyService.requiresServerPhysicsInstance(fixture.monster));
    }

    @Test
    void schedulesWzAttackImpactAndBroadcastsTelegraph() {
        Fixture fixture = fixture(0);
        when(fixture.monster.canUseSkill(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(false);

        assertTrue(service.acquire(fixture.monster, fixture.agent));
        service.tickForTest(System.nanoTime());

        verify(fixture.monster).canUseAttack(0, false);
        verify(fixture.physics).beginServerCombatAction(
                org.mockito.ArgumentMatchers.eq(fixture.monster),
                org.mockito.ArgumentMatchers.anyLong());
        ArgumentCaptor<Packet> telegraph = ArgumentCaptor.forClass(Packet.class);
        verify(fixture.map).broadcastMessage(telegraph.capture());
        assertEquals(25, Byte.toUnsignedInt(telegraph.getValue().getBytes()[8]),
                "left-facing attack1 must use the client attack animation activity");
        ArgumentCaptor<Runnable> impact = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.overall).registerOverallAction(
                org.mockito.ArgumentMatchers.eq(922010900), impact.capture(),
                org.mockito.ArgumentMatchers.eq(1_155L));
    }

    @Test
    void ordinarySummonStaysOnNormalClientControlUntilAnAgentHitsIt() {
        Fixture fixture = fixture(0);
        Monster summon = mock(Monster.class);
        when(summon.getId()).thenReturn(9_300_016);
        when(summon.getObjectId()).thenReturn(78);
        when(summon.getMap()).thenReturn(fixture.map);
        when(summon.isAlive()).thenReturn(true);
        when(fixture.map.getMonsterByOid(78)).thenReturn(summon);

        assertTrue(service.acquire(fixture.monster, fixture.agent));
        ServerMobAutonomyService.inheritAuthorityInstances(fixture.monster, summon);

        assertFalse(service.isActive(summon));
        verify(summon, never()).clearBossControllerPin();

        assertTrue(service.acquire(summon, fixture.agent));
        assertTrue(service.isActive(summon));
        verify(summon).clearBossControllerPin();
    }

    @Test
    void standaloneChronosWaitsForItsAgentAggroTargetToEnterMagicAttackRange() {
        MapleMap map = mock(MapleMap.class);
        Monster chronos = mock(Monster.class);
        Character agent = mock(Character.class);
        Character closerNonTarget = mock(Character.class);
        Channel channel = mock(Channel.class);
        MobPhysicsService physics = mock(MobPhysicsService.class);
        OverallService overall = mock(OverallService.class);
        when(map.getId()).thenReturn(922010900);
        when(map.getChannelServer()).thenReturn(channel);
        when(channel.getServiceAccess(ChannelServices.MOB_PHYSICS)).thenReturn(physics);
        when(channel.getServiceAccess(ChannelServices.OVERALL)).thenReturn(overall);
        when(chronos.getId()).thenReturn(9_300_016);
        when(chronos.getObjectId()).thenReturn(78);
        when(chronos.getMap()).thenReturn(map);
        when(chronos.isAlive()).thenReturn(true);
        when(chronos.getPosition()).thenReturn(new Point(0, 0));
        when(chronos.getMp()).thenReturn(100);
        when(chronos.canUseAttack(0, false)).thenReturn(1);
        when(agent.getId()).thenReturn(101);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(new Point(250, 0));
        when(agent.isAlive()).thenReturn(true);
        when(agent.isLoggedinWorld()).thenReturn(true);
        when(closerNonTarget.getId()).thenReturn(102);
        when(closerNonTarget.getMap()).thenReturn(map);
        when(closerNonTarget.getPosition()).thenReturn(new Point(20, 0));
        when(closerNonTarget.isAlive()).thenReturn(true);
        when(closerNonTarget.isLoggedinWorld()).thenReturn(true);
        when(map.getAllPlayers()).thenReturn(List.of(closerNonTarget, agent));
        when(map.getMonsterByOid(78)).thenReturn(chronos);

        long startedAt = System.nanoTime();
        assertTrue(service.acquire(chronos, agent));
        service.tickForTest(startedAt);

        verify(chronos, never()).canUseAttack(0, false);

        when(agent.getPosition()).thenReturn(new Point(150, 0));
        service.tickForTest(startedAt + 300_000_000L);

        verify(chronos).canUseAttack(0, false);
        verify(overall).registerOverallAction(
                org.mockito.ArgumentMatchers.eq(922010900),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(800L));
    }

    @Test
    void easyBalrogSummonUsesNativeClientUntilAgentHitThenReturnsAfterAggroLease() {
        MapleMap map = mock(MapleMap.class);
        Monster claw = encounterMonster(
                EasyBalrogReleasedClawBehavior.MOB_ID, 501, map);
        Monster crimson = encounterMonster(6_400_009, 502, map);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        when(map.getMonsterByOid(501)).thenReturn(claw);
        when(map.getMonsterByOid(502)).thenReturn(crimson);

        service.registerEncounterActor(claw, new Object());
        ServerMobAutonomyService.inheritAuthorityInstances(claw, crimson);

        assertFalse(service.isActive(crimson));
        assertTrue(service.acquire(crimson, agent));
        assertTrue(service.isActive(crimson));
        assertTrue(ServerMobAutonomyService.requiresServerPhysicsInstance(crimson));

        ServerMobAutonomyService.releaseOrdinaryAggroInstances(crimson, "test-timeout");

        assertFalse(service.isActive(crimson));
        assertFalse(ServerMobAutonomyService.requiresServerPhysicsInstance(crimson));
    }

    @Test
    void schedulesAlisharSealUsingItsExplicitSkillOneAnimation() {
        Fixture fixture = fixture(0);

        assertTrue(service.acquire(fixture.monster, fixture.agent));
        service.tickForTest(System.nanoTime());

        verify(fixture.monster).canUseSkill(
                org.mockito.ArgumentMatchers.argThat(skill -> skill.getType()
                        == server.life.MobSkillType.SEAL),
                org.mockito.ArgumentMatchers.eq(true));
        verify(fixture.overall).registerOverallAction(
                org.mockito.ArgumentMatchers.eq(922010900),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(800L));
        ArgumentCaptor<Packet> telegraph = ArgumentCaptor.forClass(Packet.class);
        verify(fixture.map).broadcastMessage(telegraph.capture());
        byte[] bytes = telegraph.getValue().getBytes();
        assertEquals(43, Byte.toUnsignedInt(bytes[8]),
                "left-facing skill1 must use the client skill animation activity");
        assertEquals(server.life.MobSkillType.SEAL.getId(), Byte.toUnsignedInt(bytes[9]));
        assertEquals(1, Byte.toUnsignedInt(bytes[10]));
    }

    @Test
    void summonedBalrogsBroadcastTheirOwnWzAttackAnimationsAfterTakeover() {
        for (int mobId : List.of(6_400_008, 6_400_009)) {
            Fixture fixture = fixture(mobId, 0);

            assertTrue(service.acquire(fixture.monster, fixture.agent));
            service.tickForTest(System.nanoTime());

            verify(fixture.monster).canUseAttack(0, false);
            verify(fixture.physics).beginServerCombatAction(
                    org.mockito.ArgumentMatchers.eq(fixture.monster),
                    org.mockito.ArgumentMatchers.anyLong());
            verify(fixture.map).broadcastMessage(org.mockito.ArgumentMatchers.any(Packet.class));
        }
    }

    @Test
    void easyBalrogSummonCastPausesPhysicsForItsCompleteWzAnimation() {
        Fixture fixture = fixture(EasyBalrogReleasedClawBehavior.MOB_ID, 0);
        long actionStarted = System.nanoTime();

        assertTrue(service.acquire(fixture.monster, fixture.agent));
        service.tickForTest(actionStarted);

        ArgumentCaptor<Long> castEnd = ArgumentCaptor.forClass(Long.class);
        verify(fixture.physics).beginServerCombatAction(
                org.mockito.ArgumentMatchers.eq(fixture.monster), castEnd.capture());
        assertEquals(actionStarted + 5_550_000_000L, castEnd.getValue(),
                "Balrog skill1 physics must stay silent for all 37 authored frames");
        verify(fixture.overall).registerOverallAction(
                org.mockito.ArgumentMatchers.eq(922010900),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(5_550L));
    }

    @Test
    void papaPixieServerCastPausesPhysicsForItsCompleteWzAnimation() {
        Fixture fixture = fixture(PapaPixieActorBehavior.MOB_ID, 0);
        long actionStarted = System.nanoTime();

        assertTrue(service.acquire(fixture.monster, fixture.agent));
        service.tickForTest(actionStarted);

        ArgumentCaptor<Long> castEnd = ArgumentCaptor.forClass(Long.class);
        verify(fixture.physics).beginServerCombatAction(
                org.mockito.ArgumentMatchers.eq(fixture.monster), castEnd.capture());
        assertEquals(actionStarted + 2_730_000_000L, castEnd.getValue());
        verify(fixture.overall).registerOverallAction(
                org.mockito.ArgumentMatchers.eq(922010900),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(1_950L));
    }

    @Test
    void papaPixieShortSkillPausesPhysicsForItsCompleteWzAnimation() {
        Fixture fixture = fixture(PapaPixieActorBehavior.MOB_ID, 4);
        when(fixture.monster.getHp()).thenReturn(1);
        long actionStarted = System.nanoTime();

        assertTrue(service.acquire(fixture.monster, fixture.agent));
        service.tickForTest(actionStarted);

        ArgumentCaptor<Long> castEnd = ArgumentCaptor.forClass(Long.class);
        verify(fixture.physics).beginServerCombatAction(
                org.mockito.ArgumentMatchers.eq(fixture.monster), castEnd.capture());
        assertEquals(actionStarted + 910_000_000L, castEnd.getValue());
        verify(fixture.overall).registerOverallAction(
                org.mockito.ArgumentMatchers.eq(922010900),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(910L));
    }

    @Test
    void capableAttackingPartyMemberKeepsPapaPixieNativeAuthority() {
        NativeFixture fixture = nativeFixture(
                91_011, 91_012, PapaPixieActorBehavior.MOB_ID);
        ServerMobAutonomyService guardedService =
                new ServerMobAutonomyService(random, false, 1_000_000L);
        try {
            assertFalse(guardedService.acquire(fixture.monster, fixture.agent));
            guardedService.recordAcceptedClientMovement(fixture.monster, fixture.human);
            guardedService.tickForTest(System.nanoTime() + 2_000_000L);

            assertFalse(guardedService.isActive(fixture.monster));
            assertEquals(1, guardedService.nativeAuthorityCountForTest());
        } finally {
            guardedService.dispose();
            AgentLpqSessionRegistry.remove(fixture.session);
        }
    }

    @Test
    void capableObserverCannotBecomePapaPixieSimulationAuthority() {
        NativeFixture fixture = nativeFixture(
                91_021, 91_022, PapaPixieActorBehavior.MOB_ID);
        Character observer = nativeHuman(91_099, fixture.map, fixture.event);
        when(observer.getPartyId()).thenReturn(778);
        when(fixture.map.getAllPlayers()).thenReturn(List.of(observer));
        when(fixture.event.getPlayers()).thenReturn(List.of(observer));
        ServerMobAutonomyService guardedService =
                new ServerMobAutonomyService(random, false, 1_000_000L);
        try {
            assertTrue(guardedService.acquire(fixture.monster, fixture.agent));
            guardedService.tickForTest(System.nanoTime() + 2_000_000L);

            assertTrue(guardedService.isActive(fixture.monster));
            assertEquals(0, guardedService.nativeAuthorityCountForTest());
        } finally {
            guardedService.dispose();
            AgentLpqSessionRegistry.remove(fixture.session);
        }
    }

    @Test
    void capableHumanLpqParticipantKeepsStickyNativeAuthority() {
        NativeFixture fixture = nativeFixture(91_101, 91_102);
        ServerMobAutonomyService guardedService =
                new ServerMobAutonomyService(random, false, 1_000_000L);
        try {
            assertFalse(guardedService.acquire(fixture.monster, fixture.agent));
            guardedService.recordAcceptedClientMovement(fixture.monster, fixture.human);
            guardedService.tickForTest(System.nanoTime() + 2_000_000L);

            assertFalse(guardedService.isActive(fixture.monster));
            assertEquals(1, guardedService.nativeAuthorityCountForTest());
        } finally {
            guardedService.dispose();
            AgentLpqSessionRegistry.remove(fixture.session);
        }
    }

    @Test
    void lpqClientThatDoesNotSimulateFallsBackToServerAfterGrace() {
        NativeFixture fixture = nativeFixture(91_201, 91_202);
        ServerMobAutonomyService guardedService =
                new ServerMobAutonomyService(random, false, 1_000_000L);
        try {
            assertFalse(guardedService.acquire(fixture.monster, fixture.agent));
            guardedService.tickForTest(System.nanoTime() + 2_000_000L);

            assertTrue(guardedService.isActive(fixture.monster));
            assertEquals(0, guardedService.nativeAuthorityCountForTest());
            verify(fixture.monster).aggroRemoveController();
        } finally {
            guardedService.dispose();
            AgentLpqSessionRegistry.remove(fixture.session);
        }
    }

    @Test
    void observerDoesNotCountAsLpqSimulationAuthority() {
        NativeFixture fixture = nativeFixture(91_301, 91_302);
        Character observer = mock(Character.class);
        when(observer.getId()).thenReturn(91_399);
        when(observer.getClient()).thenReturn(mock(Client.class));
        when(observer.isLoggedinWorld()).thenReturn(true);
        when(observer.getMap()).thenReturn(fixture.map);
        when(observer.getEventInstance()).thenReturn(fixture.event);
        when(observer.getPartyId()).thenReturn(-1);
        when(fixture.map.getAllPlayers()).thenReturn(List.of(observer));
        when(fixture.event.getPlayers()).thenReturn(List.of(observer));
        ServerMobAutonomyService guardedService =
                new ServerMobAutonomyService(random, false, 1_000_000L);
        try {
            assertTrue(guardedService.acquire(fixture.monster, fixture.agent));
            guardedService.tickForTest(System.nanoTime() + 2_000_000L);

            assertTrue(guardedService.isActive(fixture.monster));
        } finally {
            guardedService.dispose();
            AgentLpqSessionRegistry.remove(fixture.session);
        }
    }

    @Test
    void disconnectedNativeControllerHandsOffBeforeServerFallback() {
        NativeFixture fixture = nativeFixture(91_401, 91_402);
        Character replacement = mock(Character.class);
        int replacementId = fixture.human.getId() + 50_000;
        when(replacement.getId()).thenReturn(replacementId);
        when(replacement.getClient()).thenReturn(mock(Client.class));
        when(replacement.getClient().getBossSimulationCapability())
                .thenReturn(BossClientSimulationCapability.NATIVE_MOB_SIMULATION);
        when(replacement.getMap()).thenReturn(fixture.map);
        when(replacement.getEventInstance()).thenReturn(fixture.event);
        when(replacement.getPartyId()).thenReturn(777);
        when(replacement.isAlive()).thenReturn(true);
        when(replacement.isLoggedinWorld()).thenReturn(true);
        ServerMobAutonomyService guardedService =
                new ServerMobAutonomyService(random, false, 1_000_000L);
        try {
            assertFalse(guardedService.acquire(fixture.monster, fixture.agent));
            guardedService.recordAcceptedClientMovement(fixture.monster, fixture.human);
            guardedService.tickForTest(System.nanoTime());

            when(fixture.map.getAllPlayers()).thenReturn(List.of(replacement));
            when(fixture.event.getPlayers()).thenReturn(List.of(replacement));
            guardedService.tickForTest(System.nanoTime());
            guardedService.recordAcceptedClientMovement(fixture.monster, replacement);
            guardedService.tickForTest(System.nanoTime() + 2_000_000L);

            verify(fixture.monster, atLeastOnce()).aggroSwitchController(replacement, true);
            assertFalse(guardedService.isActive(fixture.monster));
            assertEquals(1, guardedService.nativeAuthorityCountForTest());
        } finally {
            guardedService.dispose();
            AgentLpqSessionRegistry.remove(fixture.session);
        }
    }

    @Test
    void encounterAuthorityHandsOffEveryActorThenStaysServerOwned() {
        MapleMap map = mock(MapleMap.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Character first = nativeHuman(92_001, map, event);
        Character replacement = nativeHuman(92_002, map, event);
        Monster initial = encounterMonster(
                EasyBalrogInitialClawBehavior.MOB_ID, 501, map);
        Monster released = encounterMonster(
                EasyBalrogReleasedClawBehavior.MOB_ID, 502, map);
        when(map.getEventInstance()).thenReturn(event);
        when(map.getAllPlayers()).thenReturn(List.of(first, replacement));
        when(event.getPlayers()).thenReturn(List.of(first, replacement));
        when(map.getMonsterByOid(501)).thenReturn(initial);
        when(map.getMonsterByOid(502)).thenReturn(released);
        Object encounterKey = new Object();

        service.registerEncounterActor(initial, encounterKey);
        service.registerEncounterActor(released, encounterKey);
        service.recordAcceptedClientMovement(initial, first);
        service.tickForTest(System.nanoTime());

        verify(initial, atLeastOnce()).pinBossController(first);
        verify(released, atLeastOnce()).pinBossController(first);

        when(map.getAllPlayers()).thenReturn(List.of(replacement));
        when(event.getPlayers()).thenReturn(List.of(replacement));
        service.tickForTest(System.nanoTime());
        service.recordAcceptedClientMovement(released, replacement);
        service.tickForTest(System.nanoTime());

        verify(initial, atLeastOnce()).aggroSwitchController(replacement, true);
        verify(released, atLeastOnce()).aggroSwitchController(replacement, true);

        when(map.getAllPlayers()).thenReturn(List.of());
        when(event.getPlayers()).thenReturn(List.of());
        service.tickForTest(System.nanoTime());
        assertEquals(2, service.activeActorCountForTest());
        assertEquals(0, service.nativeAuthorityCountForTest());

        when(map.getAllPlayers()).thenReturn(List.of(first));
        when(event.getPlayers()).thenReturn(List.of(first));
        service.tickForTest(System.nanoTime());
        assertEquals(2, service.activeActorCountForTest(),
                "sticky server authority must not return to a client");
        assertEquals(0, service.nativeAuthorityCountForTest());
    }

    @Test
    void renderOnlyParticipantStartsBalrogInServerMode() {
        MapleMap map = mock(MapleMap.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Character wasm = nativeHuman(92_101, map, event);
        when(wasm.getClient().getBossSimulationCapability())
                .thenReturn(BossClientSimulationCapability.RENDER_ONLY);
        Monster claw = encounterMonster(
                EasyBalrogInitialClawBehavior.MOB_ID, 601, map);
        when(map.getEventInstance()).thenReturn(event);
        when(map.getAllPlayers()).thenReturn(List.of(wasm));
        when(event.getPlayers()).thenReturn(List.of(wasm));
        when(map.getMonsterByOid(601)).thenReturn(claw);

        service.registerEncounterActor(claw, new Object());

        assertTrue(service.isActive(claw));
        assertEquals(0, service.nativeAuthorityCountForTest());

        service.tickForTest(System.nanoTime() + 30_000_000_000L);
        assertTrue(service.isActive(claw),
                "a warped observer cannot reclaim sticky server boss authority");
    }

    @Test
    void onlyRegisteredExpeditionParticipantCanControlEasyBalrog() {
        MapleMap map = mock(MapleMap.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Expedition expedition = mock(Expedition.class);
        Character participant = nativeHuman(92_301, map, event);
        Character observer = nativeHuman(92_302, map, event);
        Monster claw = encounterMonster(
                EasyBalrogInitialClawBehavior.MOB_ID, 801, map);
        when(map.getEventInstance()).thenReturn(event);
        when(map.getAllPlayers()).thenReturn(List.of(observer, participant));
        when(event.getPlayers()).thenReturn(List.of(observer, participant));
        when(event.getExpedition()).thenReturn(expedition);
        when(expedition.contains(participant)).thenReturn(true);
        when(expedition.contains(observer)).thenReturn(false);
        when(map.getMonsterByOid(801)).thenReturn(claw);

        service.registerEncounterActor(claw, new Object());

        assertFalse(service.isActive(claw));
        assertEquals(1, service.nativeAuthorityCountForTest());
        verify(claw).pinBossController(participant);
        verify(claw, never()).pinBossController(observer);
    }

    @Test
    void stickyBalrogTargetsRegisteredBotAgentAndExcludesWarpedObserver() {
        MapleMap map = mock(MapleMap.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Expedition expedition = mock(Expedition.class);
        Character agent = mock(Character.class);
        Character observer = nativeHuman(92_402, map, event);
        Monster claw = encounterMonster(
                EasyBalrogInitialClawBehavior.MOB_ID, 802, map);
        when(agent.getId()).thenReturn(92_401);
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(agent.isAlive()).thenReturn(true);
        when(agent.isLoggedinWorld()).thenReturn(false);
        when(agent.getMap()).thenReturn(map);
        when(agent.getEventInstance()).thenReturn(event);
        when(agent.getPosition()).thenReturn(new Point(412, 258));
        when(map.getEventInstance()).thenReturn(event);
        when(map.getAllPlayers()).thenReturn(List.of(observer, agent));
        when(event.getPlayers()).thenReturn(List.of(observer, agent));
        when(event.getExpedition()).thenReturn(expedition);
        when(expedition.contains(agent)).thenReturn(true);
        when(expedition.contains(observer)).thenReturn(false);
        when(map.getMonsterByOid(802)).thenReturn(claw);

        service.registerEncounterActor(claw, new Object());

        assertTrue(service.isActive(claw));
        assertTrue(service.blocksAgentPhysics(claw));
        assertEquals(List.of(agent), service.combatTargetsForTest(claw));
    }

    @Test
    void expeditionObserverCannotBecomeNativeBossController() {
        MapleMap map = mock(MapleMap.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Expedition expedition = mock(Expedition.class);
        Character observer = nativeHuman(92_201, map, event);
        Monster claw = encounterMonster(
                EasyBalrogInitialClawBehavior.MOB_ID, 701, map);
        when(map.getEventInstance()).thenReturn(event);
        when(map.getAllPlayers()).thenReturn(List.of(observer));
        when(event.getPlayers()).thenReturn(List.of(observer));
        when(event.getExpedition()).thenReturn(expedition);
        when(expedition.contains(observer)).thenReturn(false);
        when(map.getMonsterByOid(701)).thenReturn(claw);

        service.registerEncounterActor(claw, new Object());

        assertTrue(service.isActive(claw));
        assertEquals(0, service.nativeAuthorityCountForTest());

        service.tickForTest(System.nanoTime() + 30_000_000_000L);
        assertTrue(service.isActive(claw));
    }

    private Fixture fixture(int selectedIndex) {
        return fixture(AlisharActorBehavior.MOB_ID, selectedIndex);
    }

    private Fixture fixture(int mobId, int selectedIndex) {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = mock(Character.class);
        Character target = mock(Character.class);
        Channel channel = mock(Channel.class);
        MobPhysicsService physics = mock(MobPhysicsService.class);
        OverallService overall = mock(OverallService.class);
        when(map.getId()).thenReturn(922010900);
        when(map.getChannelServer()).thenReturn(channel);
        when(channel.getServiceAccess(ChannelServices.MOB_PHYSICS)).thenReturn(physics);
        when(channel.getServiceAccess(ChannelServices.OVERALL)).thenReturn(overall);
        when(monster.getId()).thenReturn(mobId);
        when(monster.getObjectId()).thenReturn(77);
        when(monster.getMap()).thenReturn(map);
        when(monster.isAlive()).thenReturn(true);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getHp()).thenReturn(125_000);
        when(monster.getMaxHp()).thenReturn(125_000);
        when(monster.getMp()).thenReturn(2_500);
        when(monster.canUseAttack(0, false)).thenReturn(1);
        when(monster.canUseSkill(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
        when(agent.getMap()).thenReturn(map);
        when(target.getMap()).thenReturn(map);
        when(target.getPosition()).thenReturn(new Point(-100, 0));
        when(target.isAlive()).thenReturn(true);
        when(target.isLoggedinWorld()).thenReturn(true);
        when(map.getAllPlayers()).thenReturn(List.of(target));
        when(map.getMonsterByOid(77)).thenReturn(monster);
        when(random.nextInt(org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(selectedIndex);
        return new Fixture(map, monster, agent, physics, overall);
    }

    private NativeFixture nativeFixture(int agentId, int humanId) {
        return nativeFixture(agentId, humanId, AlisharActorBehavior.MOB_ID);
    }

    private NativeFixture nativeFixture(int agentId, int humanId, int mobId) {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = mock(Character.class);
        Character human = mock(Character.class);
        EventInstanceManager event = mock(EventInstanceManager.class);
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.PRODUCTION, 1L, humanId, 5, 1_000L);
        session.addMember(agentId, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(humanId, AgentLpqMemberState.MemberType.HUMAN);
        session.addMember(humanId + 50_000, AgentLpqMemberState.MemberType.HUMAN);
        session.addMember(agentId + 10_000, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(agentId + 20_000, AgentLpqMemberState.MemberType.AGENT);
        session.bindEventInstance(event);
        session.transition(AgentLpqSession.Phase.STAGE_9, 2_000L);
        AgentLpqSessionRegistry.registerComplete(session);

        when(map.getId()).thenReturn(922010900);
        when(monster.getId()).thenReturn(mobId);
        when(monster.getObjectId()).thenReturn(77);
        when(monster.getMap()).thenReturn(map);
        when(monster.isAlive()).thenReturn(true);
        when(monster.getController()).thenReturn(human);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getMap()).thenReturn(map);
        when(agent.getEventInstance()).thenReturn(event);
        when(agent.getPartyId()).thenReturn(777);
        when(human.getId()).thenReturn(humanId);
        when(human.getClient()).thenReturn(mock(Client.class));
        when(human.getClient().getBossSimulationCapability())
                .thenReturn(BossClientSimulationCapability.NATIVE_MOB_SIMULATION);
        when(human.getMap()).thenReturn(map);
        when(human.getEventInstance()).thenReturn(event);
        when(human.getPartyId()).thenReturn(777);
        when(human.isAlive()).thenReturn(true);
        when(human.isLoggedinWorld()).thenReturn(true);
        when(map.getAllPlayers()).thenReturn(List.of(human));
        when(map.getEventInstance()).thenReturn(event);
        when(event.getPlayers()).thenReturn(List.of(human));
        when(map.getMonsterByOid(77)).thenReturn(monster);
        return new NativeFixture(map, monster, agent, human, event, session);
    }

    private static Character nativeHuman(int id, MapleMap map,
                                         EventInstanceManager event) {
        Character human = mock(Character.class);
        Client client = mock(Client.class);
        when(human.getId()).thenReturn(id);
        when(human.getClient()).thenReturn(client);
        when(client.getBossSimulationCapability())
                .thenReturn(BossClientSimulationCapability.NATIVE_MOB_SIMULATION);
        when(human.getMap()).thenReturn(map);
        when(human.getEventInstance()).thenReturn(event);
        when(human.isAlive()).thenReturn(true);
        when(human.isLoggedinWorld()).thenReturn(true);
        return human;
    }

    private static Monster encounterMonster(int mobId, int objectId, MapleMap map) {
        Monster monster = mock(Monster.class);
        when(monster.getId()).thenReturn(mobId);
        when(monster.getObjectId()).thenReturn(objectId);
        when(monster.getMap()).thenReturn(map);
        when(monster.isAlive()).thenReturn(true);
        return monster;
    }

    private record Fixture(MapleMap map, Monster monster, Character agent,
                           MobPhysicsService physics, OverallService overall) {
    }

    private record NativeFixture(MapleMap map, Monster monster, Character agent,
                                 Character human, EventInstanceManager event,
                                 AgentLpqSession session) {
    }
}
