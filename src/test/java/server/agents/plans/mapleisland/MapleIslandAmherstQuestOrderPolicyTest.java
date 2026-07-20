package server.agents.plans.mapleisland;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessRuntime;
import server.agents.capabilities.objective.MapleIslandObjectiveRandomnessSettings;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanObjectiveKind;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandAmherstQuestOrderPolicyTest {
    @Test
    void disabledVariationRetainsCanonicalOrder() throws Exception {
        AmherstPlanCard card = AgentMapleIslandPlanRuntime.fullCard();

        assertSame(card, MapleIslandAmherstQuestOrderPolicy.apply(
                card, new AgentRuntimeEntry(null, null, null)));
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1037)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1008));
    }

    @Test
    void seededAgentsUseBothSafeBlockOrdersWithoutChangingBlockContents() throws Exception {
        AmherstPlanCard canonical = AgentMapleIslandPlanRuntime.fullCard();
        Set<Boolean> pioFirstVariants = new HashSet<>();

        for (long seed = 0L; seed < 100L; seed++) {
            AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
            MapleIslandObjectiveRandomnessRuntime.configure(
                    entry, MapleIslandObjectiveRandomnessSettings.cohort(seed));
            AmherstPlanCard varied = MapleIslandAmherstQuestOrderPolicy.apply(canonical, entry);
            int pioStart = index(varied, AmherstPlanObjectiveKind.QUEST_START, 1008);
            int mariaComplete = index(varied, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1037);
            pioFirstVariants.add(pioStart < mariaComplete);

            assertTrue(index(varied, AmherstPlanObjectiveKind.QUEST_START, 1008)
                    < index(varied, AmherstPlanObjectiveKind.REACTOR_HIT, 1008));
            assertTrue(index(varied, AmherstPlanObjectiveKind.REACTOR_HIT, 1008)
                    < index(varied, AmherstPlanObjectiveKind.REACTOR_BOX_ITEMS, 1008));
            assertTrue(index(varied, AmherstPlanObjectiveKind.REACTOR_BOX_ITEMS, 1008)
                    < index(varied, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1008));
            assertTrue(index(varied, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1037)
                    < index(varied, AmherstPlanObjectiveKind.QUEST_CHAIN, 1038));
            assertTrue(index(varied, AmherstPlanObjectiveKind.QUEST_CHAIN, 1038)
                    < index(varied, AmherstPlanObjectiveKind.QUEST_START, 1040));
            assertEquals(canonical.objectives().stream().map(objective -> objective.objectiveId()).collect(
                            java.util.stream.Collectors.toSet()),
                    varied.objectives().stream().map(objective -> objective.objectiveId()).collect(
                            java.util.stream.Collectors.toSet()));
        }

        assertEquals(Set.of(false, true), pioFirstVariants);
    }

    private static int index(AmherstPlanCard card, AmherstPlanObjectiveKind kind, int questId) {
        for (int index = 0; index < card.objectives().size(); index++) {
            var objective = card.objectives().get(index);
            if (objective.kind() == kind && objective.allQuestIds().contains(questId)) {
                return index;
            }
        }
        throw new AssertionError("Missing objective " + kind + " quest " + questId);
    }
}
