package server.partner;

import client.BuffStat;
import client.Character;
import client.Skill;
import config.AdventurerPartnerConfig;
import net.server.PlayerBuffValueHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.StatEffect;
import tools.Pair;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoloTagBuffSharingServiceTest {
    @Test
    void soloPreparationPregrantsLearnedSelfBuffEvenWhenItIsNotActive() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        Skill shadowPartner = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(shadowPartner.getId()).thenReturn(4111002);
        when(shadowPartner.getMaxLevel()).thenReturn(30);
        when(shadowPartner.getEffect(30)).thenReturn(effect);
        when(shadowPartner.getAction()).thenReturn(true);
        when(effect.isSkill()).thenReturn(true);
        when(effect.isOverTime()).thenReturn(true);
        when(effect.getDuration()).thenReturn(180_000);
        when(effect.getStatups()).thenReturn(List.of(
                new Pair<>(BuffStat.SHADOWPARTNER, 50)));
        when(effect.isPartyBuff()).thenReturn(false);
        when(human.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));
        when(partner.getSkills()).thenReturn(Map.of());
        when(human.getAllBuffs()).thenReturn(List.of());
        when(partner.getAllBuffs()).thenReturn(List.of());
        List<SoloTagBuffSharingService.SkillGrant> grants = new ArrayList<>();

        sharing.prepareSessionSkills(PartnerMode.SOLO_TAG, human, partner, grants::add);

        assertEquals(1, grants.size());
        assertEquals(partner, grants.getFirst().recipient());
        assertEquals(4111002, grants.getFirst().skill().getId());
        assertEquals(30, grants.getFirst().level());
    }

    @Test
    void soloPreparationPregrantsActiveBuffSkillsWithoutCheckingBondItem() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        Skill magicGuard = new Skill(2001002);
        Skill shadowPartner = new Skill(4111002);
        PlayerBuffValueHolder magicGuardBuff =
                buff(BuffStat.MAGIC_GUARD, 40, magicGuard.getId());
        PlayerBuffValueHolder shadowPartnerBuff =
                buff(BuffStat.SHADOWPARTNER, 50, shadowPartner.getId());
        when(human.getAllBuffs()).thenReturn(List.of(magicGuardBuff));
        when(human.getSkills()).thenReturn(Map.of(
                magicGuard, new Character.SkillEntry((byte) 20, 0, -1L)));
        when(partner.getAllBuffs()).thenReturn(List.of(shadowPartnerBuff));
        when(partner.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));
        List<SoloTagBuffSharingService.SkillGrant> grants = new ArrayList<>();

        sharing.prepareSessionSkills(PartnerMode.SOLO_TAG, human, partner, grants::add);

        assertEquals(2, grants.size());
        assertTrue(grants.stream().anyMatch(grant -> grant.recipient() == human
                && grant.skill().getId() == 4111002 && grant.level() == 30));
        assertTrue(grants.stream().anyMatch(grant -> grant.recipient() == partner
                && grant.skill().getId() == 2001002 && grant.level() == 20));
        verify(human, never()).haveItemEquipped(anyInt());
        verify(partner, never()).haveItemEquipped(anyInt());
    }

    @Test
    void reportsDonorSkillAtExactLevelBeforeApplyingBorrowedBuff() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        PlayerBuffValueHolder buff = buff(BuffStat.SHADOWPARTNER, 50, shadowPartner.getId());
        when(human.getAllBuffs()).thenReturn(List.of(buff));
        when(human.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));
        when(partner.getAllBuffs()).thenReturn(List.of());
        when(partner.getSkills()).thenReturn(Map.of());
        when(partner.haveItemEquipped(config.SOLO_TAG_BUFF_SHARING_ITEM_ID)).thenReturn(true);

        SoloTagBuffSharingService.SharingPlan plan =
                sharing.capture(PartnerMode.SOLO_TAG, human, partner);
        when(human.getAllBuffs()).thenReturn(List.of());
        List<SoloTagBuffSharingService.SkillGrant> grants = new ArrayList<>();

        sharing.applyAfterExchange(plan, human, partner, grants::add);

        assertEquals(1, grants.size());
        assertEquals(human, grants.getFirst().recipient());
        assertEquals(4111002, grants.getFirst().skill().getId());
        assertEquals(30, grants.getFirst().level());
        verify(human).silentGiveBuffs(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void disabledFeatureDoesNotInspectOrApplyBuffs() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);

        SoloTagBuffSharingService.SharingPlan plan =
                sharing.capture(PartnerMode.SOLO_TAG, human, partner);
        sharing.applyAfterExchange(plan, human, partner);

        verify(human, never()).getAllBuffs();
        verify(partner, never()).getAllBuffs();
        verify(human, never()).silentGiveBuffs(org.mockito.ArgumentMatchers.anyList());
        verify(partner, never()).silentGiveBuffs(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void onlyEligibleReceivingProfileGetsOtherProfilesSkillBuffs() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        PlayerBuffValueHolder humanBuff = buff(BuffStat.MAGIC_GUARD, 20, 2001002);
        PlayerBuffValueHolder partnerBuff = buff(BuffStat.SHADOWPARTNER, 50, 4111002);
        when(human.getAllBuffs()).thenReturn(List.of(humanBuff));
        when(partner.getAllBuffs()).thenReturn(List.of(partnerBuff));
        when(human.haveItemEquipped(config.SOLO_TAG_BUFF_SHARING_ITEM_ID)).thenReturn(false);
        when(partner.haveItemEquipped(config.SOLO_TAG_BUFF_SHARING_ITEM_ID)).thenReturn(true);

        SoloTagBuffSharingService.SharingPlan plan =
                sharing.capture(PartnerMode.SOLO_TAG, human, partner);
        when(human.getAllBuffs()).thenReturn(List.of(partnerBuff));
        when(partner.getAllBuffs()).thenReturn(List.of(humanBuff));
        sharing.applyAfterExchange(plan, human, partner);

        ArgumentCaptor<List<Pair<Long, PlayerBuffValueHolder>>> applied = ArgumentCaptor.forClass(List.class);
        verify(human).silentGiveBuffs(applied.capture());
        assertTrue(applied.getValue().stream().anyMatch(pair -> pair.getRight().effect == humanBuff.effect));
        verify(partner, never()).silentGiveBuffs(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void carriedNonEquipmentItemQualifiesWithoutEquipmentCheck() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.SOLO_TAG_BUFF_SHARING_ENABLED = true;
        config.SOLO_TAG_BUFF_SHARING_ITEM_ID = 4000144;
        Character character = mock(Character.class);
        when(character.haveItemWithId(4000144, false)).thenReturn(true);

        assertTrue(new SoloTagBuffSharingService(config).eligible(character));
        verify(character, never()).haveItemEquipped(anyInt());
    }

    @Test
    void activeEntitlementUsesReadableDarkBlueText() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        Character character = mock(Character.class);
        when(character.haveItemEquipped(config.SOLO_TAG_BUFF_SHARING_ITEM_ID)).thenReturn(true);

        assertEquals("#bActive#k", new SoloTagBuffSharingService(config).entitlementStatus(character));
    }

    @Test
    void weakerOverlappingBuffIsAppliedBeforeStrongerBuff() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        PlayerBuffValueHolder strong = buff(BuffStat.WATK, 20, 1005);
        PlayerBuffValueHolder weak = buff(BuffStat.WATK, 5, 1101006);
        when(human.getAllBuffs()).thenReturn(List.of(strong, weak));
        when(partner.getAllBuffs()).thenReturn(List.of());
        when(partner.haveItemEquipped(config.SOLO_TAG_BUFF_SHARING_ITEM_ID)).thenReturn(true);

        SoloTagBuffSharingService.SharingPlan plan =
                sharing.capture(PartnerMode.SOLO_TAG, human, partner);
        when(human.getAllBuffs()).thenReturn(List.of());
        when(partner.getAllBuffs()).thenReturn(List.of(strong, weak));
        sharing.applyAfterExchange(plan, human, partner);

        ArgumentCaptor<List<Pair<Long, PlayerBuffValueHolder>>> applied = ArgumentCaptor.forClass(List.class);
        verify(human).silentGiveBuffs(applied.capture());
        assertTrue(applied.getValue().get(0).getRight().effect == weak.effect);
        assertTrue(applied.getValue().get(1).getRight().effect == strong.effect);
    }

    @Test
    void incomingClawBoosterDoesNotOverwriteExistingDaggerBooster() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        PlayerBuffValueHolder clawBooster = buff(BuffStat.BOOSTER, -2, 4101003);
        PlayerBuffValueHolder daggerBooster = buff(BuffStat.BOOSTER, -2, 4201002);
        when(human.getAllBuffs()).thenReturn(List.of(clawBooster));
        when(partner.getAllBuffs()).thenReturn(List.of(daggerBooster));
        when(partner.haveItemEquipped(config.SOLO_TAG_BUFF_SHARING_ITEM_ID)).thenReturn(true);

        SoloTagBuffSharingService.SharingPlan plan =
                sharing.capture(PartnerMode.SOLO_TAG, human, partner);
        when(human.getAllBuffs()).thenReturn(List.of(daggerBooster));
        when(partner.getAllBuffs()).thenReturn(List.of(clawBooster));

        sharing.applyAfterExchange(plan, human, partner);

        verify(human, never()).silentGiveBuffs(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void incomingBoosterStillTransfersWhenRecipientHasNoBooster() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        PlayerBuffValueHolder clawBooster = buff(BuffStat.BOOSTER, -2, 4101003);
        when(human.getAllBuffs()).thenReturn(List.of(clawBooster));
        when(partner.getAllBuffs()).thenReturn(List.of());
        when(partner.haveItemEquipped(config.SOLO_TAG_BUFF_SHARING_ITEM_ID)).thenReturn(true);

        SoloTagBuffSharingService.SharingPlan plan =
                sharing.capture(PartnerMode.SOLO_TAG, human, partner);
        when(human.getAllBuffs()).thenReturn(List.of());

        sharing.applyAfterExchange(plan, human, partner);

        ArgumentCaptor<List<Pair<Long, PlayerBuffValueHolder>>> applied =
                ArgumentCaptor.forClass(List.class);
        verify(human).silentGiveBuffs(applied.capture());
        assertTrue(applied.getValue().stream()
                .anyMatch(pair -> pair.getRight().effect == clawBooster.effect));
    }

    @Test
    void partyBuffTransfersWithoutBondItemWhileSelfBuffDoesNot() {
        AdventurerPartnerConfig config = enabledEquipConfig();
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config);
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        PlayerBuffValueHolder partyBuff = buff(BuffStat.WATK, 20, 1005);
        when(partyBuff.effect.isPartyBuff()).thenReturn(true);
        PlayerBuffValueHolder selfBuff = buff(BuffStat.BOOSTER, -3, 1101004);
        when(human.getAllBuffs()).thenReturn(List.of(partyBuff, selfBuff));
        when(partner.getAllBuffs()).thenReturn(List.of());

        SoloTagBuffSharingService.SharingPlan plan =
                sharing.capture(PartnerMode.SOLO_TAG, human, partner);
        when(human.getAllBuffs()).thenReturn(List.of());
        when(partner.getAllBuffs()).thenReturn(List.of(partyBuff, selfBuff));
        sharing.applyAfterExchange(plan, human, partner);

        ArgumentCaptor<List<Pair<Long, PlayerBuffValueHolder>>> applied = ArgumentCaptor.forClass(List.class);
        verify(human).silentGiveBuffs(applied.capture());
        assertTrue(applied.getValue().stream()
                .anyMatch(pair -> pair.getRight().effect == partyBuff.effect));
        assertTrue(applied.getValue().stream()
                .noneMatch(pair -> pair.getRight().effect == selfBuff.effect));
    }

    @Test
    void successfulPurchaseChargesConfiguredPriceAfterGrant() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.SOLO_TAG_BUFF_SHARING_ENABLED = true;
        config.SOLO_TAG_BUFF_SHARING_ITEM_ID = 4000144;
        config.SOLO_TAG_BUFF_SHARING_PRICE_MESOS = 12_345_678;
        Character buyer = mock(Character.class);
        when(buyer.getMeso()).thenReturn(20_000_000);
        when(buyer.canHold(4000144)).thenReturn(true);
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config, (character, itemId) -> true);

        String result = sharing.purchase(buyer);

        assertTrue(result.contains("12,345,678 mesos"));
        verify(buyer).gainMeso(-12_345_678, true, false, true);
    }

    @Test
    void failedGrantNeverChargesMesos() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.SOLO_TAG_BUFF_SHARING_ENABLED = true;
        config.SOLO_TAG_BUFF_SHARING_ITEM_ID = 4000144;
        Character buyer = mock(Character.class);
        when(buyer.getMeso()).thenReturn(Integer.MAX_VALUE);
        when(buyer.canHold(4000144)).thenReturn(true);
        SoloTagBuffSharingService sharing = new SoloTagBuffSharingService(config, (character, itemId) -> false);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> sharing.purchase(buyer));
        assertTrue(failure.getMessage().contains("could not be added"));

        verify(buyer, never()).gainMeso(anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    private static AdventurerPartnerConfig enabledEquipConfig() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.SOLO_TAG_BUFF_SHARING_ENABLED = true;
        config.SOLO_TAG_BUFF_SHARING_ITEM_ID = 1142073;
        return config;
    }

    private static PlayerBuffValueHolder buff(BuffStat stat, int value, int sourceId) {
        StatEffect effect = mock(StatEffect.class);
        when(effect.isSkill()).thenReturn(true);
        when(effect.getBuffSourceId()).thenReturn(sourceId);
        when(effect.getStatups()).thenReturn(List.of(new Pair<>(stat, value)));
        return new PlayerBuffValueHolder(1_000, effect);
    }
}
