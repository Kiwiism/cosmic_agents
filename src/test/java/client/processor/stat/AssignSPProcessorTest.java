package client.processor.stat;

import client.Character;
import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignSPProcessorTest {
    @Test
    void staleSkillWindowCannotAllocateSpToBorrowedSkillOrTriggerAutoban() {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.isPartnerSessionBorrowedSkill(4111002)).thenReturn(true);

        boolean allowed = AssignSPProcessor.canSPAssign(client, 4111002);

        assertFalse(allowed);
        verify(player).message(
                "That skill belongs to your Partner. Close and reopen the Skill window after switching.");
        verify(client).sendPacket(any(Packet.class));
        verify(client, never()).disconnect(true, false);
    }
}
