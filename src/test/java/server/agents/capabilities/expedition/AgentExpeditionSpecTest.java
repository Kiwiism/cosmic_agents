package server.agents.capabilities.expedition;

import org.junit.jupiter.api.Test;
import server.expeditions.ExpeditionType;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentExpeditionSpecTest {
    @Test
    void supportsThirtyMembersAcrossFiveSixMemberParties() {
        List<String> names = IntStream.rangeClosed(1, 30)
                .mapToObj(index -> "Exped" + index).toList();
        AgentExpeditionSpec spec = new AgentExpeditionSpec(
                "future", "Future Expedition", ExpeditionType.ZAKUM,
                1, 2, 3, 6, names, List.of(1), List.of(), List.of(2));

        assertEquals(30, spec.participantCount());
        assertEquals(5, spec.partyCount());
        assertEquals(0, AgentExpeditionLobbyService.partyIndex(5, 6));
        assertEquals(1, AgentExpeditionLobbyService.partyIndex(6, 6));
        assertEquals(4, AgentExpeditionLobbyService.partyIndex(29, 6));
    }

    @Test
    void rejectsMoreThanThirtyMembers() {
        List<String> names = IntStream.rangeClosed(1, 31)
                .mapToObj(index -> "Exped" + index).toList();
        assertThrows(IllegalArgumentException.class, () -> new AgentExpeditionSpec(
                "too-many", "Too Many", ExpeditionType.ZAKUM,
                1, 2, 3, 6, names, List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsAFormationLargerThanTheSelectedExpeditionAllows() {
        List<String> names = IntStream.rangeClosed(1, 8)
                .mapToObj(index -> "Ariant" + index).toList();
        assertThrows(IllegalArgumentException.class, () -> new AgentExpeditionSpec(
                "oversized-ariant", "Oversized Ariant", ExpeditionType.ARIANT,
                1, 2, 3, 6, names, List.of(), List.of(), List.of()));
    }
}
