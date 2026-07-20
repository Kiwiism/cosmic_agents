package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentVictoriaTrainingRouteCatalogTest {
    @Test
    void resolvesInstructorToTrainingAndReturnRoutesOnePortalAtATime() {
        assertEquals(102000000, AgentVictoriaTrainingRouteCatalog.nextHop(102000003, 102010000));
        assertEquals(102000003, AgentVictoriaTrainingRouteCatalog.nextHop(102000000, 102000003));
        assertEquals(101010000, AgentVictoriaTrainingRouteCatalog.nextHop(101000000, 101010101));
        assertEquals(100000200, AgentVictoriaTrainingRouteCatalog.nextHop(100000201, 100040000));
        assertEquals(103000000, AgentVictoriaTrainingRouteCatalog.nextHop(103000003, 103010000));
        assertEquals(120000100, AgentVictoriaTrainingRouteCatalog.nextHop(120000101, 120010000));
        assertEquals(120000100, AgentVictoriaTrainingRouteCatalog.nextHop(120000101, 100040003));
        assertEquals(100040000, AgentVictoriaTrainingRouteCatalog.nextHop(100040003, 120000101));
        assertEquals(26, AgentVictoriaTrainingRouteCatalog.scriptedPortalId(101000000, 101000003));
        assertEquals(9, AgentVictoriaTrainingRouteCatalog.scriptedPortalId(100000200, 100000201));
    }

    @Test
    void resolvesEveryInstructorToItsVerifiedPotionShopAndBack() {
        assertEquals(102000000, AgentVictoriaTrainingRouteCatalog.nextHop(102000003, 102000002));
        assertEquals(102000000, AgentVictoriaTrainingRouteCatalog.nextHop(102000002, 102000003));
        assertEquals(101000000, AgentVictoriaTrainingRouteCatalog.nextHop(101000003, 101000002));
        assertEquals(101000000, AgentVictoriaTrainingRouteCatalog.nextHop(101000002, 101000003));
        assertEquals(100000200, AgentVictoriaTrainingRouteCatalog.nextHop(100000201, 100000102));
        assertEquals(100000100, AgentVictoriaTrainingRouteCatalog.nextHop(100000102, 100000201));
        assertEquals(103000000, AgentVictoriaTrainingRouteCatalog.nextHop(103000003, 103000002));
        assertEquals(103000000, AgentVictoriaTrainingRouteCatalog.nextHop(103000002, 103000003));
        assertEquals(120000100, AgentVictoriaTrainingRouteCatalog.nextHop(120000101, 120000200));
        assertEquals(120000100, AgentVictoriaTrainingRouteCatalog.nextHop(120000200, 120000101));
    }

    @Test
    void generalGraphConnectsTheLevel15SliceToTheRestOfVictoria() {
        assertNotNull(AgentVictoriaTrainingRouteCatalog.nextHop(104000000, 102010000));
    }
}
