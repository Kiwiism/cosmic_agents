package server.agents.capabilities.equipment;

import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentEquipmentDpResultTest {

    @Test
    void exposesPicksScoreAndParetoCapFlag() {
        Equip equip = mock(Equip.class);
        AgentEquipmentScore score = new AgentEquipmentScore(123, 45);
        AgentEquipmentDpResult result = new AgentEquipmentDpResult(Map.of((short) -1, equip), score, false);

        assertEquals(equip, result.picks().get((short) -1));
        assertEquals(123, result.score().damage());
        assertEquals(45, result.score().statSum());
        assertFalse(result.paretoCapHit());
    }
}
