package server.agents.capabilities.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentSkillReportFlowTest {
    @Test
    void reportsBeginnerSkills() {
        AgentSkillReportFlow.SkillReportDecision decision = AgentSkillReportFlow.reportSkills(
                true,
                0,
                List.of(new AgentSkillReportFlow.SkillLine(1000, "Three Snails", 1)),
                2,
                Map.of());

        assertEquals(List.of("beginner: Three Snails lv1 | 2 beginner SP left"), decision.replies());
        assertFalse(decision.requestSkillTreeChoice());
        assertFalse(decision.clearPendingAction());
    }

    @Test
    void reportsNoBeginnerSkills() {
        AgentSkillReportFlow.SkillReportDecision decision = AgentSkillReportFlow.reportSkills(
                true, 0, List.of(), 3, Map.of());

        assertEquals(List.of("no learned beginner skills yet 3 beginner SP left"), decision.replies());
        assertFalse(decision.requestSkillTreeChoice());
    }

    @Test
    void reportsNoJobSkillsWithRemainingSp() {
        AgentSkillReportFlow.SkillReportDecision decision = AgentSkillReportFlow.reportSkills(
                false, 4, List.of(), 0, Map.of());

        assertEquals(List.of("no job skills yet 4 SP left"), decision.replies());
        assertFalse(decision.requestSkillTreeChoice());
    }

    @Test
    void reportsSingleSkillTreeDirectly() {
        AgentSkillReportFlow.SkillReportDecision decision = AgentSkillReportFlow.reportSkills(
                false, 0, List.of(), 0, skillTrees(110));

        assertEquals(List.of("fighter (110): Power Strike lv10"), decision.replies());
        assertFalse(decision.requestSkillTreeChoice());
    }

    @Test
    void requestsChoiceForMultipleSkillTrees() {
        AgentSkillReportFlow.SkillReportDecision decision = AgentSkillReportFlow.reportSkills(
                false, 0, List.of(), 0, skillTrees(110, 111));

        assertEquals(List.of("which skill tree? fighter (110), crusader (111)"), decision.replies());
        assertTrue(decision.requestSkillTreeChoice());
        assertFalse(decision.clearPendingAction());
    }

    @Test
    void skillTreeChoiceClearsPendingWhenNoSkillsRemain() {
        AgentSkillReportFlow.SkillReportDecision decision =
                AgentSkillReportFlow.resolveSkillTreeChoice(Map.of(), "110");

        assertEquals(List.of("no job skills yet"), decision.replies());
        assertTrue(decision.clearPendingAction());
    }

    @Test
    void skillTreeChoiceKeepsPendingOnInvalidChoice() {
        AgentSkillReportFlow.SkillReportDecision decision =
                AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees(110, 111), "pirate");

        assertEquals(List.of("which skill tree? fighter (110), crusader (111)"), decision.replies());
        assertFalse(decision.clearPendingAction());
    }

    @Test
    void skillTreeChoiceClearsPendingOnValidChoice() {
        AgentSkillReportFlow.SkillReportDecision decision =
                AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees(110, 111), "crusader");

        assertEquals(List.of("crusader (111): Power Strike lv10"), decision.replies());
        assertTrue(decision.clearPendingAction());
    }

    private static Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees(int... treeIds) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> trees = new LinkedHashMap<>();
        for (int treeId : treeIds) {
            trees.put(treeId, List.of(new AgentSkillReportFlow.SkillLine(1000000, "Power Strike", 10)));
        }
        return trees;
    }
}
