package server.agents.plans.mapleisland.dialogue;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.conversation.AgentConversationModelContext;
import server.agents.capabilities.dialogue.conversation.AgentConversationSelectionContext;
import server.agents.capabilities.dialogue.conversation.AgentConversationTopicModel;
import server.agents.capabilities.dialogue.semantic.AgentSemanticDialogueAct;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapleIslandConversationTopicProviderTest {
    private final MapleIslandConversationTopicProvider provider =
            new MapleIslandConversationTopicProvider();

    @Test
    void pioStoryletObservesQuestStateWithoutIssuingGameplayCommands() {
        AgentRuntimeEntry first = entry(1, "Aeri", 1_000_000);
        AgentRuntimeEntry second = entry(2, "Beni", 1_000_000);
        when(first.bot().getQuestStatus(1_008)).thenReturn((byte) 1);
        when(second.bot().getQuestStatus(1_008)).thenReturn((byte) 1);
        AgentConversationTopicModel model = model(MapleIslandConversationTopicProvider.PIO_BOXES);

        int utility = model.utility(new AgentConversationSelectionContext(first, second, 1_000L, 7L));
        AgentSemanticDialogueAct act = model.produce(context(first, second, 1, 7L));

        assertNotEquals(AgentConversationTopicModel.UNAVAILABLE, utility);
        assertEquals(MapleIslandConversationTopicProvider.PIO_BOXES, act.topicId());
        assertEquals("true", act.parameters().get("presentationOnly"));
        assertTrue(act.actKey().equals("both_waiting") || act.actKey().equals("look_farther"));
    }

    @Test
    void rainQuizStoryletCarriesOnlyAnUnconsumedPresentationHint() {
        AgentRuntimeEntry first = entry(1, "Aeri", 1_000_000);
        AgentRuntimeEntry second = entry(2, "Beni", 1_000_000);
        when(first.bot().getQuestStatus(1_009)).thenReturn((byte) 1);
        when(second.bot().getQuestStatus(1_015)).thenReturn((byte) 2);
        AgentConversationTopicModel model = model(MapleIslandConversationTopicProvider.RAIN_QUIZ);
        long seed = availableSeed(model, first, second);

        AgentSemanticDialogueAct act = model.produce(context(first, second, 1, seed));

        assertEquals(MapleIslandConversationTopicProvider.RAIN_QUIZ, act.topicId());
        assertEquals("quiz_hesitation", act.parameters().get("presentationHint"));
        assertEquals((byte) 1, first.bot().getQuestStatus(1_009));
    }

    @Test
    void yoonaStoryletCanExplainTheCashShopGuideWithoutChangingQuestState() {
        AgentRuntimeEntry first = entry(1, "Aeri", 1_010_000);
        AgentRuntimeEntry second = entry(2, "Beni", 1_010_000);
        when(first.bot().getQuestStatus(8_020)).thenReturn((byte) 1);
        when(second.bot().getQuestStatus(8_020)).thenReturn((byte) 2);
        when(second.bot().getItemQuantity(4_031_180, false)).thenReturn(1);
        AgentConversationTopicModel model = model(MapleIslandConversationTopicProvider.YOONA_QUIZ);
        long seed = availableSeed(model, first, second);

        AgentSemanticDialogueAct act = model.produce(context(first, second, 1, seed));

        assertEquals("find_guide", act.actKey());
        assertEquals("cash_shop_search", act.parameters().get("presentationHint"));
        assertEquals((byte) 1, first.bot().getQuestStatus(8_020));
    }

    @Test
    void rainStoryletCanPublishTheAuthoritativeAnswerChain() {
        AgentRuntimeEntry completed = entry(1, "Aeri", 1_000_000);
        AgentRuntimeEntry active = entry(2, "Beni", 1_000_000);
        completeRange(completed.bot(), 1_009, 1_015);
        when(active.bot().getQuestStatus(1_009)).thenReturn((byte) 1);
        AgentConversationTopicModel model = model(MapleIslandConversationTopicProvider.RAIN_QUIZ);
        long seed = availableSeedMatching(model, completed, active, 3);

        AgentSemanticDialogueAct act = model.produce(context(completed, active, 1, seed));

        assertEquals("answer_public", act.actKey());
        assertTrue(model.templates().get(act.actKey()).stream()
                .anyMatch(template -> template.contains("Southperry")));
    }

    @Test
    void yoonaStoryletCanPublishTheAuthoritativeAnswerChain() {
        AgentRuntimeEntry completed = entry(1, "Aeri", 1_010_000);
        AgentRuntimeEntry active = entry(2, "Beni", 1_010_000);
        completeRange(completed.bot(), 8_020, 8_025);
        when(active.bot().getQuestStatus(8_021)).thenReturn((byte) 1);
        when(active.bot().getItemQuantity(4_031_180, false)).thenReturn(1);
        AgentConversationTopicModel model = model(MapleIslandConversationTopicProvider.YOONA_QUIZ);
        long seed = availableSeedMatching(model, completed, active, 3);

        AgentSemanticDialogueAct act = model.produce(context(completed, active, 1, seed));

        assertEquals("answer_public", act.actKey());
        assertTrue(model.templates().get(act.actKey()).stream()
                .anyMatch(template -> template.contains("30 days")));
    }

    private AgentConversationTopicModel model(String topicId) {
        return provider.topicModels().stream()
                .filter(candidate -> candidate.definition().topicId().equals(topicId))
                .findFirst().orElseThrow();
    }

    private static long availableSeed(AgentConversationTopicModel model,
                                      AgentRuntimeEntry first,
                                      AgentRuntimeEntry second) {
        for (long seed = 0; seed < 1_000; seed++) {
            if (model.utility(new AgentConversationSelectionContext(
                    first, second, 1_000L, seed)) != AgentConversationTopicModel.UNAVAILABLE) {
                return seed;
            }
        }
        throw new AssertionError("No deterministic availability seed found");
    }

    private static long availableSeedMatching(AgentConversationTopicModel model,
                                              AgentRuntimeEntry first,
                                              AgentRuntimeEntry second,
                                              int divisor) {
        for (long seed = 0; seed < 10_000; seed++) {
            if (Math.floorMod(seed, divisor) == 0
                    && model.utility(new AgentConversationSelectionContext(
                    first, second, 1_000L, seed)) != AgentConversationTopicModel.UNAVAILABLE) {
                return seed;
            }
        }
        throw new AssertionError("No deterministic matching availability seed found");
    }

    private static void completeRange(Character character, int firstQuestId, int lastQuestId) {
        for (int questId = firstQuestId; questId <= lastQuestId; questId++) {
            when(character.getQuestStatus(questId)).thenReturn((byte) 2);
        }
    }

    private static AgentConversationModelContext context(AgentRuntimeEntry first,
                                                         AgentRuntimeEntry second,
                                                         int turn,
                                                         long seed) {
        return new AgentConversationModelContext(first, second, 7L, "test",
                turn, 4, 1_000L + turn, seed);
    }

    private static AgentRuntimeEntry entry(int id, String name, int mapId) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getMapId()).thenReturn(mapId);
        return new AgentRuntimeEntry(character, null, null);
    }
}
