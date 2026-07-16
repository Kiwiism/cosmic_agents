package server.partner;

import client.Character;
import client.Job;
import client.Skill;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class PartnerSessionSkillServiceTest {
    @Test
    void grantsExactDonorLevelToDatabaseAndLiveProfile() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character recipient = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        when(recipient.getProfileOwnerCharacterId()).thenReturn(20);
        when(recipient.getSkills()).thenReturn(Map.of());
        when(repository.grantTemporarySkill(7L, 20, 4111002, 30, 0, -1L, null))
                .thenReturn(new PartnerSessionSkillGrant(
                        7L, 20, 4111002, null, null, null, 30, 0, -1L));

        service.grant(7L, new SoloTagBuffSharingService.SkillGrant(
                recipient, shadowPartner, (byte) 30, 0, -1L));

        verify(repository).grantTemporarySkill(7L, 20, 4111002, 30, 0, -1L, null);
        verify(recipient).applyPartnerSessionSkill(20, shadowPartner, (byte) 30, 0, -1L);
        verify(recipient).markPartnerSessionSkillBorrowed(20, 4111002, true);
    }

    @Test
    void unionPreloadsOnlyMissingCrossJobSkillsIntoBothProfiles() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character bandit = mock(Character.class);
        Character hermit = mock(Character.class);
        Skill mesoGuard = new Skill(4211005);
        Skill shadowPartner = new Skill(4111002);
        when(bandit.getProfileOwnerCharacterId()).thenReturn(10);
        when(hermit.getProfileOwnerCharacterId()).thenReturn(20);
        when(bandit.getJob()).thenReturn(Job.CHIEFBANDIT);
        when(hermit.getJob()).thenReturn(Job.HERMIT);
        when(bandit.getSkills()).thenReturn(Map.of(
                mesoGuard, new Character.SkillEntry((byte) 20, 0, -1L)));
        when(hermit.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));
        when(repository.grantTemporarySkill(7L, 10, 4111002, 30, 0, -1L, null))
                .thenReturn(new PartnerSessionSkillGrant(
                        7L, 10, 4111002, null, null, null, 30, 0, -1L));
        when(repository.grantTemporarySkill(7L, 20, 4211005, 20, 0, -1L, null))
                .thenReturn(new PartnerSessionSkillGrant(
                        7L, 20, 4211005, null, null, null, 20, 0, -1L));

        service.prepareUnion(7L, bandit, hermit);

        verify(bandit).applyPartnerSessionSkill(10, shadowPartner, (byte) 30, 0, -1L, false);
        verify(bandit).markPartnerSessionSkillBorrowed(10, 4111002, true);
        verify(hermit).applyPartnerSessionSkill(20, mesoGuard, (byte) 20, 0, -1L, false);
        verify(hermit).markPartnerSessionSkillBorrowed(20, 4211005, true);
        verify(bandit).sendPacket(any(Packet.class));
        verify(hermit).sendPacket(any(Packet.class));
    }

    @Test
    void unionNormalizesAnExistingCrossJobSkillAndPreservesItsOriginalLevel() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        Skill shared = new Skill(4111002);
        when(first.getProfileOwnerCharacterId()).thenReturn(10);
        when(first.getJob()).thenReturn(Job.CHIEFBANDIT);
        when(second.getJob()).thenReturn(Job.HERMIT);
        when(first.getSkills()).thenReturn(Map.of(
                shared, new Character.SkillEntry((byte) 10, 0, -1L)));
        when(second.getSkills()).thenReturn(Map.of(
                shared, new Character.SkillEntry((byte) 30, 0, -1L)));
        when(repository.grantTemporarySkill(
                7L, 10, 4111002, 30, 0, -1L,
                new AdventurerPartnerRepository.CharacterSkillState((byte) 10, 0, -1L)))
                .thenReturn(new PartnerSessionSkillGrant(
                        7L, 10, 4111002, 10, 0, -1L, 30, 0, -1L));

        service.prepareUnion(7L, first, second);

        verify(first).applyPartnerSessionSkill(
                10, shared, (byte) 30, 0, -1L, false);
        verify(first).markPartnerSessionSkillBorrowed(10, 4111002, true);
        verify(first).sendPacket(any(Packet.class));
    }

    @Test
    void unionIgnoresZeroLevelSourceSkills() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character bandit = mock(Character.class);
        Character hermit = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        when(bandit.getJob()).thenReturn(Job.CHIEFBANDIT);
        when(hermit.getJob()).thenReturn(Job.HERMIT);
        when(bandit.getSkills()).thenReturn(Map.of());
        when(hermit.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 0, 0, -1L)));

        service.prepareUnion(7L, bandit, hermit);

        verify(repository, never()).grantTemporarySkill(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
        verify(bandit, never()).sendPacket(any(Packet.class));
    }

    @Test
    void zeroLevelRecipientSkillIsProvisionedAndPreservedForRestoration() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character recipient = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        Character.SkillEntry zeroLevel = new Character.SkillEntry((byte) 0, 0, -1L);
        when(recipient.getProfileOwnerCharacterId()).thenReturn(20);
        when(recipient.getSkills()).thenReturn(Map.of(shadowPartner, zeroLevel));

        service.grant(7L, new SoloTagBuffSharingService.SkillGrant(
                recipient, shadowPartner, (byte) 20, 0, -1L));

        verify(repository).grantTemporarySkill(
                7L, 20, 4111002, 20, 0, -1L,
                new AdventurerPartnerRepository.CharacterSkillState((byte) 0, 0, -1L));
        verify(recipient).applyPartnerSessionSkill(
                20, shadowPartner, (byte) 20, 0, -1L);
        verify(recipient).markPartnerSessionSkillBorrowed(20, 4111002, true);
    }

    @Test
    void unionDoesNotSynthesizeMissingSameJobSkillsThatCouldBeMutatedAsCanonical() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        when(first.getJob()).thenReturn(Job.HERMIT);
        when(second.getJob()).thenReturn(Job.HERMIT);
        when(first.getSkills()).thenReturn(Map.of());
        when(second.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));

        service.prepareUnion(7L, first, second);

        verify(repository, never()).grantTemporarySkill(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void spChangeUpdatesTheBorrowedCopyAndAnnouncesItsNewLevel() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character source = mock(Character.class);
        Character recipient = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        when(source.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 12, 0, -1L)));
        when(recipient.getProfileOwnerCharacterId()).thenReturn(20);
        when(recipient.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 11, 0, -1L)));
        when(recipient.isPartnerSessionBorrowedSkill(4111002)).thenReturn(true);
        when(repository.grantTemporarySkill(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(4111002),
                org.mockito.ArgumentMatchers.eq(12),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(-1L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PartnerSessionSkillGrant(
                        7L, 20, 4111002, null, null, null, 12, 0, -1L));

        boolean synchronizedSkill = service.synchronizeUnionSkill(
                7L, source, recipient, shadowPartner);

        org.junit.jupiter.api.Assertions.assertTrue(synchronizedSkill);
        verify(recipient).applyPartnerSessionSkill(
                20, shadowPartner, (byte) 12, 0, -1L);
        verify(recipient).markPartnerSessionSkillBorrowed(20, 4111002, true);
    }

    @Test
    void spChangeDoesNotRaiseABorrowedSelfBuffAboveItsBondTier() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character source = mock(Character.class);
        Character recipient = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        Character.SkillEntry borrowed = new Character.SkillEntry((byte) 20, 0, -1L);
        when(source.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));
        when(recipient.getProfileOwnerCharacterId()).thenReturn(20);
        when(recipient.getSkills()).thenReturn(Map.of(shadowPartner, borrowed));
        when(recipient.isPartnerSessionBorrowedSkill(4111002)).thenReturn(true);
        when(repository.grantTemporarySkill(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(4111002),
                org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(-1L),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PartnerSessionSkillGrant(
                        7L, 20, 4111002, null, null, null, 20, 0, -1L));

        boolean synchronizedSkill = service.synchronizeUnionSkill(
                7L, source, recipient, shadowPartner, 20);

        org.junit.jupiter.api.Assertions.assertTrue(synchronizedSkill);
        verify(recipient, never()).applyPartnerSessionSkill(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyByte(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void firstSpInANewCrossJobSkillAddsItToTheUnion() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character source = mock(Character.class);
        Character recipient = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        when(source.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 1, 0, -1L)));
        when(recipient.getProfileOwnerCharacterId()).thenReturn(20);
        when(recipient.getJob()).thenReturn(Job.CHIEFBANDIT);
        when(recipient.getSkills()).thenReturn(Map.of());
        when(repository.grantTemporarySkill(7L, 20, 4111002, 1, 0, -1L, null))
                .thenReturn(new PartnerSessionSkillGrant(
                        7L, 20, 4111002, null, null, null, 1, 0, -1L));

        boolean synchronizedSkill = service.synchronizeUnionSkill(
                7L, source, recipient, shadowPartner);

        org.junit.jupiter.api.Assertions.assertTrue(synchronizedSkill);
        verify(recipient).applyPartnerSessionSkill(
                20, shadowPartner, (byte) 1, 0, -1L);
        verify(recipient).markPartnerSessionSkillBorrowed(20, 4111002, true);
    }

    @Test
    void sessionGrantNeverOverwritesAnExistingCanonicalSkill() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character recipient = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        when(recipient.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 5, 10, -1L)));

        service.grant(7L, new SoloTagBuffSharingService.SkillGrant(
                recipient, shadowPartner, (byte) 30, 30, -1L));

        verify(repository, never()).grantTemporarySkill(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
        verify(recipient, never()).applyPartnerSessionSkill(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyByte(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void releaseCancelsBorrowedBuffBeforeRemovingNewSkill() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        PartnerSessionSkillGrant grant = new PartnerSessionSkillGrant(
                7L, 20, 4111002, null, null, null, 30, 0, -1L);
        when(first.getProfileOwnerCharacterId()).thenReturn(10);
        when(second.getProfileOwnerCharacterId()).thenReturn(20);
        when(second.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));
        when(repository.findTemporarySkills(7L)).thenReturn(List.of(grant));
        when(repository.restoreTemporarySkills(7L)).thenReturn(List.of(grant));

        service.restore(7L, first, second);

        InOrder order = inOrder(repository, second);
        order.verify(second).cancelPartnerBuffFromSource(4111002);
        order.verify(second).restorePartnerSessionSkill(20, shadowPartner, null);
        order.verify(repository).restoreTemporarySkills(7L);
    }

    @Test
    void releaseRestoresPreexistingSkillLevelInsteadOfDeletingIt() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        Skill magicGuard = new Skill(2001002);
        PartnerSessionSkillGrant grant = new PartnerSessionSkillGrant(
                8L, 10, 2001002, 5, 0, -1L, 20, 0, -1L);
        when(first.getProfileOwnerCharacterId()).thenReturn(10);
        when(second.getProfileOwnerCharacterId()).thenReturn(20);
        when(first.getSkills()).thenReturn(Map.of(
                magicGuard, new Character.SkillEntry((byte) 20, 0, -1L)));
        when(repository.findTemporarySkills(8L)).thenReturn(List.of(grant));
        when(repository.restoreTemporarySkills(8L)).thenReturn(List.of(grant));

        service.restore(8L, first, second);

        var state = org.mockito.ArgumentCaptor.forClass(Character.SkillEntry.class);
        verify(first).restorePartnerSessionSkill(
                org.mockito.ArgumentMatchers.eq(10),
                org.mockito.ArgumentMatchers.eq(magicGuard),
                state.capture());
        assertEquals(5, state.getValue().skillevel);
    }

    @Test
    void unequippingBondRemovesOnlyBorrowedForeignSelfBuffSkills() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        SoloTagBuffSharingService buffSharing = mock(SoloTagBuffSharingService.class);
        Character holder = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        Skill nativeSelfBuff = new Skill(4211005);
        PartnerSessionSkillGrant foreignGrant = new PartnerSessionSkillGrant(
                7L, 10, shadowPartner.getId(), null, null, null, 20, 0, -1L);
        PartnerSessionSkillGrant nativeGrant = new PartnerSessionSkillGrant(
                7L, 10, nativeSelfBuff.getId(), null, null, null, 20, 0, -1L);
        when(holder.getProfileOwnerCharacterId()).thenReturn(10);
        when(holder.getJob()).thenReturn(Job.CHIEFBANDIT);
        when(holder.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 20, 0, -1L),
                nativeSelfBuff, new Character.SkillEntry((byte) 20, 0, -1L)));
        when(holder.isPartnerSessionBorrowedSkill(shadowPartner.getId())).thenReturn(true);
        when(holder.isPartnerSessionBorrowedSkill(nativeSelfBuff.getId())).thenReturn(true);
        when(repository.findTemporarySkills(7L)).thenReturn(List.of(foreignGrant, nativeGrant));
        when(buffSharing.isLearnedSelfBuffSkill(shadowPartner, 20)).thenReturn(true);
        when(buffSharing.isLearnedSelfBuffSkill(nativeSelfBuff, 20)).thenReturn(true);

        service.removeBorrowedSelfBuffSkills(7L, holder, buffSharing);

        InOrder order = inOrder(repository, holder);
        order.verify(repository).suspendTemporarySkill(7L, 10, shadowPartner.getId());
        order.verify(holder).cancelPartnerBuffFromSource(shadowPartner.getId());
        order.verify(holder).restorePartnerSessionSkill(10, shadowPartner, null);
        verify(repository, never()).suspendTemporarySkill(7L, 10, nativeSelfBuff.getId());
    }

    @Test
    void runtimeRestoreFailureLeavesDurableSkillJournalForRetry() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        PartnerSessionSkillGrant grant = new PartnerSessionSkillGrant(
                7L, 20, 4111002, null, null, null, 30, 0, -1L);
        when(first.getProfileOwnerCharacterId()).thenReturn(10);
        when(second.getProfileOwnerCharacterId()).thenReturn(20);
        when(second.getSkills()).thenReturn(Map.of(
                shadowPartner, new Character.SkillEntry((byte) 30, 0, -1L)));
        when(repository.findTemporarySkills(7L)).thenReturn(List.of(grant));
        org.mockito.Mockito.doThrow(new IllegalStateException("runtime restore failed"))
                .when(second).restorePartnerSessionSkill(20, shadowPartner, null);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.restore(7L, first, second));

        verify(repository, never()).restoreTemporarySkills(7L);
    }
}
