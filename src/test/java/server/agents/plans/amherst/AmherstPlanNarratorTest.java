package server.agents.plans.amherst;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmherstPlanNarratorTest {
    @Test
    void narratesToddKillLootAndPeterTurnInWithNames() {
        AmherstPlanObjective kill = objective(
                AmherstPlanObjectiveKind.KILL_MOBS, 40000, 1035, null,
                List.of(4031802), List.of(9300018), List.of(1));
        AmherstPlanObjective complete = objective(
                AmherstPlanObjectiveKind.QUEST_COMPLETE, 40000, 1035, 2002,
                List.of(), List.of(), List.of());

        assertEquals("I'm going to Mushroom Town Training Ground to hunt 1 Tutorial Jr. Sentinel "
                        + "and collect Jr. Sentinel Shellpiece for Todd's Hunting Method.",
                AmherstPlanNarrator.message(kill));
        assertEquals("I'm going to Mushroom Town Training Ground to talk to Peter and complete "
                        + "Todd's Hunting Method.", AmherstPlanNarrator.message(complete));
    }

    @Test
    void narratesPioQuestStartBeforeReactorWork() {
        AmherstPlanObjective start = objective(
                AmherstPlanObjectiveKind.QUEST_START, 1000000, 1008, 10000,
                List.of(), List.of(), List.of());

        assertEquals("I'm going to Amherst to talk to Pio and start Pio's Collecting Recycled Goods.",
                AmherstPlanNarrator.message(start));
    }

    @Test
    void narratesQuestChainWithNpcName() {
        AmherstPlanObjective chain = new AmherstPlanObjective(
                "test", AmherstPlanObjectiveKind.QUEST_CHAIN, 0, 0, 1000000,
                null, List.of(1009, 1010), 12101, List.of(), null,
                List.of(), List.of(), List.of(), null, null);

        assertEquals("I'm going to Amherst to talk to Rain and work through "
                        + "Rain's Maple Quiz 1 quest chain.",
                AmherstPlanNarrator.message(chain));
    }

    @Test
    void narratesYoonaQuizOverrideAsAnAnswerRatherThanAnotherCashShopVisit() {
        AmherstPlanObjective quiz = objective(
                AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 1010000, 8023, 20100,
                List.of(), List.of(), List.of());

        assertEquals("I'm going to talk to Yoona and answer Yoona's Quiz on Shopping 3.",
                AmherstPlanNarrator.message(quiz));
    }

    private static AmherstPlanObjective objective(AmherstPlanObjectiveKind kind,
                                                   int mapId,
                                                   Integer questId,
                                                   Integer npcId,
                                                   List<Integer> itemIds,
                                                   List<Integer> mobIds,
                                                   List<Integer> counts) {
        return new AmherstPlanObjective("test", kind, 0, 0, mapId, questId, List.of(), npcId,
                List.of(), null, itemIds, mobIds, counts, null, null);
    }
}
