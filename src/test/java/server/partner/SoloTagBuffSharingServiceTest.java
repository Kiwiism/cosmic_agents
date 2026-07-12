package server.partner;

import client.BuffStat;
import client.Character;
import config.AdventurerPartnerConfig;
import net.server.PlayerBuffValueHolder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.StatEffect;
import tools.Pair;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoloTagBuffSharingServiceTest {
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
        when(human.haveItemEquipped(config.soloTagBuffSharingItemId)).thenReturn(false);
        when(partner.haveItemEquipped(config.soloTagBuffSharingItemId)).thenReturn(true);

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
        config.soloTagBuffSharingEnabled = true;
        config.soloTagBuffSharingItemId = 4000144;
        Character character = mock(Character.class);
        when(character.haveItemWithId(4000144, false)).thenReturn(true);

        assertTrue(new SoloTagBuffSharingService(config).eligible(character));
        verify(character, never()).haveItemEquipped(anyInt());
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
        when(partner.haveItemEquipped(config.soloTagBuffSharingItemId)).thenReturn(true);

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
        config.soloTagBuffSharingEnabled = true;
        config.soloTagBuffSharingItemId = 4000144;
        config.soloTagBuffSharingPriceMesos = 12_345_678;
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
        config.soloTagBuffSharingEnabled = true;
        config.soloTagBuffSharingItemId = 4000144;
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
        config.soloTagBuffSharingEnabled = true;
        config.soloTagBuffSharingItemId = 1142073;
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
