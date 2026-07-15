package server.agents.plans.mapleisland;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.objective.AmherstNpcInteractionDelay;
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.quest.MapleIslandSouthperryQuestCatalog;
import server.agents.plans.amherst.AmherstObjectiveHandlerRegistry;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanCardLoader;
import server.agents.plans.amherst.AmherstPlanObjectiveKind;
import server.agents.plans.amherst.AmherstPlanValidator;
import server.agents.testing.MutablePrimitiveGatewayFixture;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandFullPlanTest {
    private static final Path CARD = Path.of(
            "docs", "agents", "plans", "maple-island-full-mvp.plan.json");

    @Test
    void planCombinesBothVerifiedSegmentsAndEndsRestingNearShanks() throws Exception {
        AmherstPlanCard card = new AmherstPlanCardLoader(
                new ObjectMapper(), AmherstPlanValidator.fullMapleIsland()).load(CARD);
        Set<Integer> expectedQuests = new HashSet<>(AmherstQuestCatalog.requiredQuestIdSet());
        expectedQuests.addAll(MapleIslandSouthperryQuestCatalog.requiredQuestIdSet());

        assertEquals("maple-island-full-mvp", card.planId());
        assertEquals(10000, card.entryCriteria().requiredStartMapId());
        assertEquals(2000000, card.exitCriteria().finalMapId());
        assertEquals(expectedQuests, card.requiredQuestIds());
        assertEquals(Set.of(1046), card.exitCriteria().startOnlyQuestIds());
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_CHAIN, 1038)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1040));
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1039)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8020));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8020)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8021));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8022)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8023));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8023)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8024));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8025)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1041));
        assertEquals("southperry-right-relaxer", card.objectives().getLast().mode());
        assertEquals(1L, card.objectives().stream()
                .filter(objective -> objective.kind() == AmherstPlanObjectiveKind.STOP_PLAN)
                .count());

        MutablePrimitiveGatewayFixture fixture = new MutablePrimitiveGatewayFixture();
        AmherstObjectiveHandlerRegistry handlers = new AmherstObjectiveHandlerRegistry(
                fixture.gateway, AmherstNpcInteractionDelay.NONE, AmherstScopePolicy.fullMapleIsland());
        card.objectives().forEach(objective -> assertNotNull(handlers.create(card, objective)));
    }

    private static int index(AmherstPlanCard card, AmherstPlanObjectiveKind kind, int questId) {
        for (int index = 0; index < card.objectives().size(); index++) {
            var objective = card.objectives().get(index);
            if (objective.kind() == kind && objective.allQuestIds().contains(questId)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }
}
