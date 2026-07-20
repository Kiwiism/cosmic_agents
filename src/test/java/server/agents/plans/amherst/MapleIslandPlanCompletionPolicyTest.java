package server.agents.plans.amherst;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.objective.AgentPlanCompletionMode;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessSettings;
import server.agents.testing.MutablePrimitiveGatewayFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandPlanCompletionPolicyTest {
    private static final MapleIslandRelaxerSpotCatalog.Pool FALLBACK =
            MapleIslandRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT;

    @Test
    void someSouthperryIdleAgentsChooseTheFaceHolePool() {
        MutablePrimitiveGatewayFixture fixture = new MutablePrimitiveGatewayFixture();
        boolean foundFaceHoleSelection = false;

        for (long seed = 1L; seed <= 100L; seed++) {
            MapleIslandObjectiveRandomnessRuntime.configure(
                    fixture.entry, MapleIslandObjectiveRandomnessSettings.cohort(seed));
            if (MapleIslandPlanCompletionPolicy.INSTANCE.selectMode(
                    fixture.entry, MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID)
                    != AgentPlanCompletionMode.IDLE) {
                continue;
            }
            if (MapleIslandPlanCompletionPolicy.INSTANCE.selectRestSpotPool(
                    fixture.entry,
                    MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID,
                    AgentPlanCompletionMode.IDLE,
                    FALLBACK) == MapleIslandRelaxerSpotCatalog.Pool.SOUTHPERRY_FACE_HOLES) {
                foundFaceHoleSelection = true;
                break;
            }
        }

        assertTrue(foundFaceHoleSelection);
    }

    @Test
    void nonIdleAndNonSouthperryCompletionsKeepTheirConfiguredPool() {
        MutablePrimitiveGatewayFixture fixture = new MutablePrimitiveGatewayFixture();
        MapleIslandObjectiveRandomnessRuntime.configure(
                fixture.entry, MapleIslandObjectiveRandomnessSettings.cohort(1L));

        assertEquals(FALLBACK, MapleIslandPlanCompletionPolicy.INSTANCE.selectRestSpotPool(
                fixture.entry,
                MapleIslandSouthperryQuestCatalog.FINAL_MAP_ID,
                AgentPlanCompletionMode.SIT,
                FALLBACK));
        assertEquals(FALLBACK, MapleIslandPlanCompletionPolicy.INSTANCE.selectRestSpotPool(
                fixture.entry,
                MapleIslandRelaxerSpotCatalog.AMHERST_MAP_ID,
                AgentPlanCompletionMode.IDLE,
                FALLBACK));
    }
}
