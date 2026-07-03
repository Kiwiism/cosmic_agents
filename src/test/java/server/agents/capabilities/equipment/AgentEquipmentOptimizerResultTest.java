package server.agents.capabilities.equipment;

import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AgentEquipmentOptimizerResultTest {
    @Test
    void carriesChosenWeaponAndSlotPicks() {
        Equip weapon = mock(Equip.class);
        Equip glove = mock(Equip.class);
        Map<Short, Equip> picks = Map.of((short) -8, glove);

        AgentEquipmentOptimizerResult result = new AgentEquipmentOptimizerResult(weapon, picks);

        assertSame(weapon, result.weapon());
        assertSame(picks, result.picks());
    }
}
