package server.partner;

import client.Character;
import client.Skill;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerSessionSkillServiceTest {
    @Test
    void grantsExactDonorLevelToDatabaseAndLiveProfile() {
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        PartnerSessionSkillService service = new PartnerSessionSkillService(repository);
        Character recipient = mock(Character.class);
        Skill shadowPartner = new Skill(4111002);
        when(recipient.getProfileOwnerCharacterId()).thenReturn(20);
        when(recipient.getSkills()).thenReturn(Map.of());

        service.grant(7L, new SoloTagBuffSharingService.SkillGrant(
                recipient, shadowPartner, (byte) 30, 0, -1L));

        verify(repository).grantTemporarySkill(7L, 20, 4111002, 30, 0, -1L, null);
        verify(recipient).applyPartnerSessionSkill(20, shadowPartner, (byte) 30, 0, -1L);
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
