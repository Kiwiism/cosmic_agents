package server.partner;

import client.Character;
import config.AdventurerPartnerConfig;
import config.PartnerMedalEffectConfig;
import config.PartnerMedalEffectLevelConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
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
        config.MEDAL_EFFECTS.add(flatEffect(1142082, "EXP_BONUS", 10.0));
        config.MEDAL_EFFECTS.add(flatEffect(1142083, "REGULAR_MOB_BONUS_DAMAGE", 20.0));
        when(human.haveItemEquipped(1142005)).thenReturn(true);
        when(human.haveItemEquipped(1142082)).thenReturn(true);
        when(human.haveItemEquipped(1142083)).thenReturn(true);

        assertEquals(1.2, service.dropRateMultiplier(human));
        assertEquals(1.1, service.expMultiplier(human));
        assertEquals(200, service.regularMobBonusDamage(human, 1_000));
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
