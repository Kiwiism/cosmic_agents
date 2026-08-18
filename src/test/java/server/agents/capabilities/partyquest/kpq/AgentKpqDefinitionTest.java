package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
