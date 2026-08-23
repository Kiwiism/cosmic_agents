package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqDefinitionTest {
    @Test
    void usesAuthoritativeClotoQuestionAnswers() {
        assertEquals(List.of(10, 35, 20, 25, 25, 30, 8), AgentKpqDefinition.couponTargets());
    }

    @Test
    void stageDefinitionsAddOnePositionAtEachStage() {
        assertEquals(4, AgentKpqDefinition.combinationStage(2).positions().size());
        assertEquals(5, AgentKpqDefinition.combinationStage(3).positions().size());
        assertEquals(6, AgentKpqDefinition.combinationStage(4).positions().size());
    }

    @Test
    void stageTwoRopesUseTopRowThenBottomRowPlayerFacingNumbers() {
        AgentKpqDefinition.CombinationStage stage = AgentKpqDefinition.combinationStage(2);

        assertTrue(stage.center(1).y < stage.center(3).y);
        assertTrue(stage.center(2).y < stage.center(4).y);
        assertTrue(stage.center(1).x < stage.center(2).x);
        assertTrue(stage.center(3).x < stage.center(4).x);
    }

    @Test
    void translatesNpcScriptAnswerIndexesIntoOccupiedPositions() {
        assertEquals(List.of(2, 3, 4), AgentKpqDefinition.answerCombination(2, 0));
        assertEquals(List.of(1, 2, 3), AgentKpqDefinition.answerCombination(2, 3));
        assertEquals(List.of(3, 4, 5), AgentKpqDefinition.answerCombination(3, 0));
        assertEquals(List.of(4, 5, 6), AgentKpqDefinition.answerCombination(4, 0));
        assertEquals(List.of(1, 2, 3), AgentKpqDefinition.answerCombination(4, 19));
    }
}
