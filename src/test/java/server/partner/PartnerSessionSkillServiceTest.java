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
    void unionDoesNotReplaceAnExistingRecipientSkill() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        Skill shared = new Skill(4111002);
        when(first.getJob()).thenReturn(Job.CHIEFBANDIT);
        when(second.getJob()).thenReturn(Job.HERMIT);
        when(first.getSkills()).thenReturn(Map.of(
                shared, new Character.SkillEntry((byte) 10, 0, -1L)));
        when(second.getSkills()).thenReturn(Map.of(
                shared, new Character.SkillEntry((byte) 30, 0, -1L)));

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
    void unionDoesNotSynthesizeMissingSameJobSkillsThatCouldAffectPassives() {
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
        order.verify(repository).restoreTemporarySkills(7L);
        order.verify(second).cancelPartnerBuffFromSource(4111002);
        order.verify(second).restorePartnerSessionSkill(20, shadowPartner, null);
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
}
