package server.agents.capabilities.equipment;

import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipmentAppearanceDiversityPolicyTest {
    @Test
    void selectsAnUnusedAppearanceCandidateWhenOneExists() {
        Equip usedBest = equip(1_002_001);
        Equip unusedNextBest = equip(1_002_002);

        List<Equip> selected = AgentEquipmentAppearanceDiversityPolicy.preferUnusedCandidates(
                List.of(usedBest, unusedNextBest), Set.of(usedBest.getItemId()));

        assertEquals(List.of(unusedNextBest), selected);
    }

    @Test
    void retainsTheOnlyCandidateRatherThanLeavingTheAgentUnequipped() {
        Equip shared = equip(1_002_001);
        List<Equip> candidates = List.of(shared);

        List<Equip> selected = AgentEquipmentAppearanceDiversityPolicy.preferUnusedCandidates(
                candidates, Set.of(shared.getItemId()));

        assertSame(candidates, selected);
    }

    private static Equip equip(int itemId) {
        Equip equip = mock(Equip.class);
        when(equip.getItemId()).thenReturn(itemId);
        return equip;
    }
}
