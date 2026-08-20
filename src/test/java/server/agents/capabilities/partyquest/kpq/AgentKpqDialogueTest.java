package server.agents.capabilities.partyquest.kpq;

import client.Character;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentKpqDialogueTest {
    @Test
    void requiredNarrationIsVisibleToAnObserverOutsideTheParty() {
        Character speaker = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(speaker.getMap()).thenReturn(map);
        when(speaker.getId()).thenReturn(7);
        when(map.isObservedByPlayer()).thenReturn(true);

        AgentKpqDialogue.sayMapNow(speaker, "Coupons: 5/10.");

        verify(map).broadcastMessage(any(Packet.class));
    }
}
