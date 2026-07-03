package server.agents.capabilities.equipment;

import client.Job;
import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipmentScoringPolicyTest {
    @Test
    void mageUsefulGearScoreIgnoresDexAndValuesInt() {
        Equip dexOverall = mock(Equip.class);
        when(dexOverall.getDex()).thenReturn((short) 10);
        Equip intOverall = mock(Equip.class);
        when(intOverall.getInt()).thenReturn((short) 5);

        assertTrue(AgentEquipmentScoringPolicy.usefulStatSum(intOverall, Job.MAGICIAN)
                > AgentEquipmentScoringPolicy.usefulStatSum(dexOverall, Job.MAGICIAN));
    }

    @Test
    void expectedDamageReducesToMidMinusWdefBelowMin() {
        assertEquals(60.0, AgentEquipmentScoringPolicy.expectedDamageAfterDef(100, 15), 0.01);
    }

    @Test
    void expectedDamageClampsToOneWhenWdefExceedsMax() {
        assertEquals(1.0, AgentEquipmentScoringPolicy.expectedDamageAfterDef(100, 200), 0.01);
    }

    @Test
    void expectedDamagePreservesUpperTailWhenWdefExceedsMid() {
        assertEquals(4.6, AgentEquipmentScoringPolicy.expectedDamageAfterDef(100, 80), 0.01);
    }

    @Test
    void expectedDamageDifferentiatesCandidatesAgainstHighWdefMob() {
        double weaker = AgentEquipmentScoringPolicy.expectedDamageAfterDef(100, 80);
        double stronger = AgentEquipmentScoringPolicy.expectedDamageAfterDef(120, 80);
        assertTrue(stronger > weaker + 0.5,
                "expected stronger > weaker by margin; got " + stronger + " vs " + weaker);
    }
}
