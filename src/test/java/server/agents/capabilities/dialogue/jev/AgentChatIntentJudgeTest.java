package server.agents.capabilities.dialogue.jev;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.jev.AgentChatIntentJudge.Decision;
import server.agents.capabilities.dialogue.jev.AgentChatIntentJudge.Outcome;
import server.agents.integration.typesafe.JevAnswer;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;
import server.agents.integration.typesafe.TypeSafeSettings;
import server.agents.integration.typesafe.state.JevState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatIntentJudgeTest {
    private static final TypeSafeSettings SETTINGS = new TypeSafeSettings(
            "key", TypeSafeSettings.DEFAULT_ENDPOINT, TypeSafeSettings.DEFAULT_MODEL, 1_000);

    @Test
    void confidentArgumentFreeIntentActsWithItsCanonicalPhrase() {
        Decision decision = judge(choice("intent", "grind", 0.86), noul("addressed_to_bot", 0.9))
                .judge(state("go kill some stuff"));

        assertEquals(Outcome.ACT, decision.outcome());
        assertEquals(AgentChatIntent.GRIND, decision.intent());
        assertEquals("grind", decision.canonicalCommand());
        assertEquals(0.86d, decision.confidence(), 1e-9);
        assertTrue(decision.summary().contains("grind"));
    }

    @Test
    void argumentIntentIsOnlyAsConfidentAsItsWeakestAnswer() {
        Decision decision = judge(
                choice("intent", "trade_items", 0.9),
                choice("item_category", "scrolls", 0.55))
                .judge(state("hand over those scrolls"));

        assertEquals(Outcome.ASK, decision.outcome(), "0.55 sits between ASK and ACT thresholds");
        assertEquals("trade scrolls", decision.canonicalCommand());
        assertEquals(0.55d, decision.confidence(), 1e-9);
    }

    @Test
    void missingArgumentAnswerCannotAct() {
        Decision decision = judge(choice("intent", "unequip_slot", 0.95)).judge(state("take that off"));

        assertEquals(Outcome.IGNORE, decision.outcome());
        assertNull(decision.canonicalCommand());
    }

    @Test
    void chatIsNeverActedOnRegardlessOfConfidence() {
        Decision decision = judge(choice("intent", "chat_not_a_command", 0.99)).judge(state("lol gg"));

        assertEquals(Outcome.IGNORE, decision.outcome());
        assertEquals(AgentChatIntent.CHAT_NOT_A_COMMAND, decision.intent());
    }

    @Test
    void lowConfidenceIsIgnoredAndUnavailableClientReportsUnavailable() {
        assertEquals(Outcome.IGNORE, judge(choice("intent", "stop", 0.2)).judge(state("hmm")).outcome());

        JevClient unconfigured = new JevClient(TypeSafeSettings.disabled(), (request, settings) -> {
            throw new AssertionError("must not be called");
        });
        assertEquals(Outcome.UNAVAILABLE,
                new AgentChatIntentJudge(unconfigured, 0.75d, 0.45d, 3_000).judge(state("stop")).outcome());
    }

    @Test
    void requestCarriesEveryIntentOptionAndAllArgumentQuestions() {
        AtomicReference<JevRequest> sent = new AtomicReference<>();
        JevClient client = new JevClient(SETTINGS, (request, settings) -> {
            sent.set(request);
            return response(choice("intent", "stop", 0.9));
        });

        new AgentChatIntentJudge(client, 0.75d, 0.45d, 3_000).judge(state("halt"));

        JevRequest request = sent.get();
        assertEquals(1 + AgentChatIntentArgument.values().length + 1, request.questions().size());
        Map<String, Object> intentJson = request.questions().get(AgentChatIntentCatalog.INTENT_QUESTION).toJson();
        @SuppressWarnings("unchecked")
        Map<String, Object> criteria = (Map<String, Object>) intentJson.get("criteria");
        assertEquals(AgentChatIntent.values().length, criteria.size());
        assertTrue(criteria.containsKey("chat_not_a_command"));
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) request.state();
        assertEquals("halt", state.get("message"));
    }

    private static AgentChatIntentJudge judge(Map.Entry<String, JevAnswer>... answers) {
        JevClient client = new JevClient(SETTINGS, (request, settings) -> response(answers));
        return new AgentChatIntentJudge(client, 0.75d, 0.45d, 3_000);
    }

    @SafeVarargs
    private static JevResponse response(Map.Entry<String, JevAnswer>... answers) {
        Map<String, JevAnswer> map = new LinkedHashMap<>();
        for (Map.Entry<String, JevAnswer> answer : answers) {
            map.put(answer.getKey(), answer.getValue());
        }
        return new JevResponse("jev-1.13.0", map, 900L, 0L, 95L);
    }

    private static Map.Entry<String, JevAnswer> choice(String id, String option, double confidence) {
        return Map.entry(id, new JevAnswer("choice", option, Map.of(option, confidence), null, null, Map.of(), confidence));
    }

    private static Map.Entry<String, JevAnswer> noul(String id, double probability) {
        return Map.entry(id, new JevAnswer("noul", null, Map.of(), probability, null, Map.of(), null));
    }

    private static JevState state(String message) {
        return JevState.create().put("message", message, 100).put("bot", Map.of("name", "Jason"), 80);
    }
}
