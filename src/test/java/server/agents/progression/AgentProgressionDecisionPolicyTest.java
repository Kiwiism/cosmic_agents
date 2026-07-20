package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentProgressionDecisionPolicyTest {
    private final AgentProgressionProfileRepository profiles =
            AgentProgressionProfileRepository.defaultRepository();

    @Test
    void loadsVersionedProfilesAndGivesQuestersMoreQuestDecisionsThanGrinders() {
        assertEquals(5, profiles.all().size());
        AgentProgressionProfile quester = profiles.find("quester-v1").orElseThrow();
        AgentProgressionProfile grinder = profiles.find("grinder-v1").orElseThrow();

        assertTrue(AgentProgressionDecisionPolicy.questDecisionPercent(quester)
                > AgentProgressionDecisionPolicy.questDecisionPercent(grinder));
        assertEquals(67, AgentProgressionDecisionPolicy.questDecisionPercent(quester, 35));
        assertEquals(5, AgentProgressionDecisionPolicy.questDecisionPercent(grinder, 35));
        assertEquals("balanced-v1", profiles.defaultProfile().profileId());
    }

    @Test
    void profileWeightedMapSelectionIsDeterministicAndRecordsItsProfile() {
        AgentVictoriaTrainingMapSelector selector = new AgentVictoriaTrainingMapSelector(
                AgentVictoriaTrainingCatalogRepository.defaultRepository());
        AgentProgressionProfile explorer = profiles.find("explorer-v1").orElseThrow();

        AgentVictoriaTrainingMapSelector.Selection first = selector.select(
                20, 100000003, Map.of(), null, explorer, 123).orElseThrow();
        AgentVictoriaTrainingMapSelector.Selection repeated = selector.select(
                20, 100000003, Map.of(), null, explorer, 123).orElseThrow();

        assertEquals(first.map().mapId(), repeated.map().mapId());
        assertTrue(first.reason().contains("personality=explorer-v1"));
    }

    @Test
    void differentCharacterSeedsCanProduceControlledMapVariation() {
        AgentVictoriaTrainingMapSelector selector = new AgentVictoriaTrainingMapSelector(
                AgentVictoriaTrainingCatalogRepository.defaultRepository());
        AgentProgressionProfile explorer = profiles.find("explorer-v1").orElseThrow();

        int first = selector.select(20, 0, Map.of(), null, explorer, 1).orElseThrow().map().mapId();
        int second = selector.select(20, 0, Map.of(), null, explorer, 3).orElseThrow().map().mapId();

        assertNotEquals(first, second);
    }
}
