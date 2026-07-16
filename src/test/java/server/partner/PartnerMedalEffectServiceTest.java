package server.partner;

import client.Character;
import client.Client;
import client.Skill;
import client.SkillFactory;
import config.AdventurerPartnerConfig;
import config.PartnerMedalEffectConfig;
import config.PartnerMedalEffectLevelConfig;
import constants.skills.Bishop;
import net.packet.Packet;
import net.opcodes.SendOpcode;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackInfo;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.StatEffect;
import server.TimerManager;
import server.life.Monster;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.awt.Point;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerMedalEffectServiceTest {
    private AdventurerPartnerConfig config;
    private PartnerRuntimeRegistry runtimes;
    private PartnerMedalEffectService service;
    private Character human;
    private Character partner;

    @BeforeEach
    void setUp() {
        config = new AdventurerPartnerConfig();
        config.ENABLED = true;
        config.MEDAL_DAMAGE_DELAY_MS = 0L;
        runtimes = new PartnerRuntimeRegistry();
        service = new PartnerMedalEffectService(config, runtimes);
        human = mock(Character.class);
        partner = mock(Character.class);
        when(human.getId()).thenReturn(10);
        when(human.getProfileOwnerCharacterId()).thenReturn(10);
        when(human.getLevel()).thenReturn(80);
        when(partner.getId()).thenReturn(20);
        when(partner.getProfileOwnerCharacterId()).thenReturn(20);
        when(partner.getLevel()).thenReturn(95);
        register(PartnerMode.SOLO_TAG);
    }

    @Test
    void lastMatchingPartnerLevelSelectsConfiguredBondCap() {
        PartnerMedalEffectConfig bond = effect(1142073, "SELF_BUFF_BOND");
        bond.LEVELS.add(levelWithPartnerMinimum(70, 10));
        bond.LEVELS.add(levelWithPartnerMinimum(95, 20));
        bond.LEVELS.add(levelWithPartnerMinimum(120, 30));
        config.MEDAL_EFFECTS.add(bond);
        when(human.haveItemEquipped(1142073)).thenReturn(true);

        assertEquals(20, service.selfBuffSkillCap(human, partner, PartnerMode.SOLO_TAG));
    }

    @Test
    void modeFlagPreventsEffectOutsideConfiguredMode() {
        PartnerMedalEffectConfig drop = effect(1142005, "DROP_RATE_BONUS");
        drop.SOLO_TAG_ENABLED = false;
        PartnerMedalEffectLevelConfig level = new PartnerMedalEffectLevelConfig();
        level.PERCENT = 20.0;
        drop.LEVELS.add(level);
        config.MEDAL_EFFECTS.add(drop);
        when(human.haveItemEquipped(1142005)).thenReturn(true);

        assertEquals(1.0, service.dropRateMultiplier(human));
    }

    @Test
    void fameEffectsUseConfiguredStepAndCap() {
        PartnerMedalEffectConfig meso = effect(1142006, "MESO_FAME_BONUS");
        PartnerMedalEffectLevelConfig mesoLevel = new PartnerMedalEffectLevelConfig();
        mesoLevel.FAME_PER_PERCENT = 10;
        mesoLevel.MAX_PERCENT = 50.0;
        meso.LEVELS.add(mesoLevel);
        PartnerMedalEffectConfig etc = effect(1142006, "ETC_FAME_EXTRA_ROLL");
        PartnerMedalEffectLevelConfig etcLevel = new PartnerMedalEffectLevelConfig();
        etcLevel.FAME_PER_PERCENT = 10;
        etcLevel.MAX_PERCENT = 50.0;
        etc.LEVELS.add(etcLevel);
        config.MEDAL_EFFECTS.add(meso);
        config.MEDAL_EFFECTS.add(etc);
        when(human.haveItemEquipped(1142006)).thenReturn(true);

        when(human.getFame()).thenReturn(650);
        assertEquals(1.5, service.mesoMultiplier(human));
        when(human.getFame()).thenReturn(-650);
        assertEquals(0.5, service.extraEtcDropChance(human));
    }

    @Test
    void flatRewardAndDamagePercentagesResolveFromEquippedMedal() {
        config.MEDAL_EFFECTS.add(flatEffect(1142005, "DROP_RATE_BONUS", 20.0));
        config.MEDAL_EFFECTS.add(flatEffect(1142000, "EXP_BONUS", 10.0));
        config.MEDAL_EFFECTS.add(flatEffect(1142083, "REGULAR_MOB_BONUS_DAMAGE", 20.0));
        when(human.haveItemEquipped(1142005)).thenReturn(true);
        when(human.haveItemEquipped(1142000)).thenReturn(true);
        when(human.haveItemEquipped(1142083)).thenReturn(true);

        assertEquals(1.2, service.dropRateMultiplier(human));
        assertEquals(1.1, service.expMultiplier(human));
        assertEquals(200, service.regularMobBonusDamage(human, 1_000));
    }

    @Test
    void regularMobBonusIsPreparedWithoutChangingTheCanonicalAttackPacket() {
        config.MEDAL_EFFECTS.add(flatEffect(1142083, "REGULAR_MOB_BONUS_DAMAGE", 20.0));
        when(human.haveItemEquipped(1142083)).thenReturn(true);
        MapleMap map = mock(MapleMap.class);
        Monster regular = mock(Monster.class);
        Monster boss = mock(Monster.class);
        when(human.getMap()).thenReturn(map);
        when(map.getMonsterByOid(101)).thenReturn(regular);
        when(map.getMonsterByOid(202)).thenReturn(boss);
        when(boss.isBoss()).thenReturn(true);

        AttackInfo attack = new AttackInfo();
        attack.numAttacked = 2;
        attack.numDamage = 2;
        attack.numAttackedAndDamage = 0x22;
        attack.targets = new LinkedHashMap<>();
        attack.targets.put(101, new AttackTarget((short) 0, List.of(100, 200)));
        attack.targets.put(202, new AttackTarget((short) 0, List.of(100, 100)));

        assertTrue(service.prepareRegularMobBonusDamage(human, attack));
        assertEquals(2, attack.numDamage);
        assertEquals(0x22, attack.numAttackedAndDamage);
        assertEquals(List.of(100, 200), attack.targets.get(101).damageLines());
        assertEquals(Set.of(), attack.targets.get(101).critLineIndices());
        assertEquals(List.of(100, 100), attack.targets.get(202).damageLines());
        assertEquals(Set.of(), attack.targets.get(202).critLineIndices());
        assertEquals(Map.of(101, 60, 202, 0), attack.partnerBonusDamageByTarget);
    }

    @Test
    void regularMobBonusDoesNotConsumeThePacketDamageLineNibble() {
        config.MEDAL_EFFECTS.add(flatEffect(1142083, "REGULAR_MOB_BONUS_DAMAGE", 20.0));
        when(human.haveItemEquipped(1142083)).thenReturn(true);
        MapleMap map = mock(MapleMap.class);
        Monster regular = mock(Monster.class);
        when(human.getMap()).thenReturn(map);
        when(map.getMonsterByOid(101)).thenReturn(regular);
        AttackInfo attack = new AttackInfo();
        attack.numAttacked = 1;
        attack.numDamage = 15;
        attack.numAttackedAndDamage = 0x1F;
        attack.targets = new LinkedHashMap<>();
        attack.targets.put(101, new AttackTarget((short) 0, List.of(100)));

        assertTrue(service.prepareRegularMobBonusDamage(human, attack));
        assertEquals(15, attack.numDamage);
        assertEquals(0x1F, attack.numAttackedAndDamage);
    }

    @Test
    void bonusDamageBroadcastsOneRegularServerDamageLineWithoutAnAttackReplay() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Point monsterPosition = new Point(12, 34);
        when(human.getMap()).thenReturn(map);
        when(map.getMonsterByOid(101)).thenReturn(monster);
        when(monster.getObjectId()).thenReturn(101);
        when(monster.isAlive()).thenReturn(true);
        when(monster.getPosition()).thenReturn(monsterPosition);

        service.applyRegularMobBonusDamage(human, Map.of(101, 60));

        ArgumentCaptor<Packet> damagePacket = ArgumentCaptor.forClass(Packet.class);
        verify(map).broadcastMessage(damagePacket.capture(), eq(monsterPosition));
        assertEquals(SendOpcode.DAMAGE_MONSTER.getValue(), opcode(damagePacket.getValue()));
        verify(map).damageMonster(human, monster, 60);
    }

    @Test
    void genesisSwitchEffectUsesV83BodyActionAndFacingPacketFields() {
        byte[] packet = PartnerMedalEffectService.genesisAttackPacket(
                1_000_000_002, human, 1, 1, Map.of()).getBytes();

        assertEquals(1_000_000_002, ByteBuffer.wrap(packet, 2, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertEquals(0, Byte.toUnsignedInt(packet[13]));
        assertEquals(69, Byte.toUnsignedInt(packet[14]));
        assertEquals(0, Byte.toUnsignedInt(packet[15]));
        assertEquals(4, Byte.toUnsignedInt(packet[16]));
    }

    @Test
    void genesisVisualProxyOverlapsTheCasterAtAPacketSafePosition() {
        assertEquals(new Point(50, 123),
                PartnerMedalEffectService.genesisVisualProxyPosition(new Point(50, 123)));
        assertEquals(new Point(-50, Short.MAX_VALUE),
                PartnerMedalEffectService.genesisVisualProxyPosition(
                        new Point(-50, Integer.MAX_VALUE)));
    }

    @Test
    void switchingIntoGenesisMedalBroadcastsAndAppliesGenesisDamage() {
        PartnerMedalEffectConfig genesis = effect(1142137, "SWITCH_SKILL");
        PartnerMedalEffectLevelConfig level = new PartnerMedalEffectLevelConfig();
        level.SKILL_ID = Bishop.GENESIS;
        level.SKILL_LEVEL = 1;
        genesis.LEVELS.add(level);
        config.MEDAL_EFFECTS.add(genesis);
        config.GENESIS_VISUAL_PROXY.DARKSIGHT_ENABLED = true;
        when(human.haveItemEquipped(1142137)).thenReturn(true);

        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        when(human.getMap()).thenReturn(map);
        when(human.getTotalMagic()).thenReturn(100);
        when(human.getTotalInt()).thenReturn(100);
        when(map.getMonsters()).thenReturn(List.of(monster));
        when(map.getMonsterByOid(101)).thenReturn(monster);
        when(monster.getObjectId()).thenReturn(101);
        when(monster.getHp()).thenReturn(10_000);
        when(monster.isAlive()).thenReturn(true);
        Client client = mock(Client.class);
        when(human.getClient()).thenReturn(client);

        Skill skill = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(skill.getMaxLevel()).thenReturn(30);
        when(skill.getEffect(1)).thenReturn(effect);
        when(effect.getMobCount()).thenReturn(15);
        when(effect.getAttackCount()).thenReturn(1);
        when(effect.getMatk()).thenReturn((short) 430);

        Packet proxySpawn = mock(Packet.class);
        TimerManager timer = mock(TimerManager.class);
        AtomicInteger proxyId = new AtomicInteger();
        AtomicReference<PacketCreator.VisualProxyAppearance> proxyAppearance = new AtomicReference<>();
        AtomicReference<Runnable> proxyExit = new AtomicReference<>();
        AtomicReference<Runnable> proxyCleanup = new AtomicReference<>();
        List<Long> scheduledDelays = new ArrayList<>();
        when(timer.schedule(any(Runnable.class), anyLong())).thenAnswer(invocation -> {
            long delay = invocation.getArgument(1);
            scheduledDelays.add(delay);
            if (delay == 700L || delay == 2_500L) {
                invocation.getArgument(0, Runnable.class).run();
            } else if (delay == 4_300L) {
                proxyExit.set(invocation.getArgument(0, Runnable.class));
            } else if (delay == 5_000L) {
                proxyCleanup.set(invocation.getArgument(0, Runnable.class));
            }
            return null;
        });
        try (MockedStatic<SkillFactory> skills = mockStatic(SkillFactory.class);
             MockedStatic<PacketCreator> packets = mockStatic(PacketCreator.class, CALLS_REAL_METHODS);
             MockedStatic<TimerManager> timers = mockStatic(TimerManager.class)) {
            skills.when(() -> SkillFactory.getSkill(Bishop.GENESIS)).thenReturn(skill);
            timers.when(TimerManager::getInstance).thenReturn(timer);
            packets.when(() -> PacketCreator.spawnPlayerVisualProxy(
                            eq(client), eq(human), anyInt(), eq(new Point(0, 0)),
                            eq(232), any(PacketCreator.VisualProxyAppearance.class)))
                    .thenAnswer(invocation -> {
                        proxyId.set(invocation.getArgument(2));
                        proxyAppearance.set(invocation.getArgument(5));
                        return proxySpawn;
                    });

            service.applySwitchInEffects(runtimes.findByAnyActorId(10).orElseThrow());
            packets.verify(
                    () -> PacketCreator.showOwnBuffEffect(Bishop.GENESIS, 1), never());
        }

        verify(map).broadcastMessage(eq(human), any(Packet.class), eq(false), eq(true));
        verify(map).damageMonster(eq(human), eq(monster), intThat(damage -> damage > 0));
        verify(client).sendPacket(proxySpawn);
        verify(client).sendPacket(argThat(packet ->
                packet != null && packet.getBytes() != null
                        && opcode(packet) == SendOpcode.GIVE_FOREIGN_BUFF.getValue()
                        && characterId(packet) == proxyId.get()));
        verify(client).sendPacket(argThat(packet ->
                packet != null && packet.getBytes() != null
                        && opcode(packet) == SendOpcode.MAGIC_ATTACK.getValue()
                        && characterId(packet) == proxyId.get()));
        assertEquals("Seraph", proxyAppearance.get().name());
        assertEquals(1, proxyAppearance.get().gender());
        assertEquals(4, proxyAppearance.get().stance());
        assertEquals(1002333, proxyAppearance.get().visibleEquips().get(1));
        assertEquals(List.of(700L, 4_300L, 5_000L, 2_500L), scheduledDelays);

        proxyExit.get().run();
        proxyCleanup.get().run();
        verify(client).sendPacket(argThat(packet ->
                packet != null && packet.getBytes() != null
                        && opcode(packet) == SendOpcode.REMOVE_PLAYER_FROM_MAP.getValue()
                        && characterId(packet) == proxyId.get()));
    }

    private static int opcode(Packet packet) {
        byte[] bytes = packet.getBytes();
        return Short.toUnsignedInt(ByteBuffer.wrap(bytes, 0, Short.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort());
    }

    private static int characterId(Packet packet) {
        return ByteBuffer.wrap(packet.getBytes(), Short.BYTES, Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
    }

    private void register(PartnerMode mode) {
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10,
                mode == PartnerMode.SOLO_TAG ? ProfileLeaseRegistry.DETACHED_ACTOR : 20,
                10, 20, mode);
        runtime.activate();
        PartnerLink link = new PartnerLink(
                5L, 1, 0, 10, 20, mode, true,
                Instant.now(), Instant.now());
        runtimes.register(new ActivePartnerSession(link, runtime, human, partner, null));
    }

    private static PartnerMedalEffectConfig effect(int itemId, String type) {
        PartnerMedalEffectConfig effect = new PartnerMedalEffectConfig();
        effect.ITEM_ID = itemId;
        effect.EFFECT = type;
        return effect;
    }

    private static PartnerMedalEffectConfig flatEffect(int itemId, String type, double percent) {
        PartnerMedalEffectConfig effect = effect(itemId, type);
        PartnerMedalEffectLevelConfig level = new PartnerMedalEffectLevelConfig();
        level.PERCENT = percent;
        effect.LEVELS.add(level);
        return effect;
    }

    private static PartnerMedalEffectLevelConfig levelWithPartnerMinimum(int minimum, int cap) {
        PartnerMedalEffectLevelConfig level = new PartnerMedalEffectLevelConfig();
        level.CONDITIONS.MIN_PARTNER_LEVEL = minimum;
        level.MAX_SKILL_LEVEL = cap;
        return level;
    }
}
