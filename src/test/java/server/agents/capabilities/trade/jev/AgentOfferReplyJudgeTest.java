package server.agents.capabilities.trade.jev;

import org.junit.jupiter.api.Test;
import server.agents.integration.typesafe.JevAnswer;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevResponse;
import server.agents.integration.typesafe.TypeSafeSettings;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOfferReplyJudgeTest {
    private static final TypeSafeSettings SETTINGS = new TypeSafeSettings(
            "key", TypeSafeSettings.DEFAULT_ENDPOINT, TypeSafeSettings.DEFAULT_MODEL, 1_000);

    @Test
    void confidentAcceptAndDeclineMapToTheCanonicalReplies() {
        assertEquals("yes", judge("accept", 0.9).reply().canonicalReply());
        assertEquals("no", judge("decline", 0.8).reply().canonicalReply());
        assertTrue(judge("accept", 0.9).actionable());
    }

    @Test
    void questionsAndUnrelatedChatterAreNeverActionable() {
        AgentOfferReplyJudge.Decision question = judge("question", 0.97);
        assertEquals(AgentOfferReplyJudge.Reply.QUESTION, question.reply());
        assertNull(question.reply().canonicalReply());
        assertFalse(question.actionable());
        assertFalse(judge("unrelated", 0.99).actionable());
    }

    @Test
    void lowConfidenceLeavesTheOfferPending() {
        AgentOfferReplyJudge.Decision decision = judge("accept", 0.5);
        assertFalse(decision.actionable());
        assertTrue(decision.summary().contains("IGNORE"));
    }

    @Test
    void stateDescribesWhoGivesWhat() {
        Map<String, Object> state = AgentOfferReplyJudge.state("keep it", "ganre", "Jason", "Red Whip", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> offer = (Map<String, Object>) state.get("pending_offer");
        assertEquals("Red Whip", offer.get("item"));
        assertTrue(String.valueOf(offer.get("direction")).contains("offered its own item"));
    }

    private static AgentOfferReplyJudge.Decision judge(String option, double confidence) {
        JevClient client = new JevClient(SETTINGS, (request, settings) -> new JevResponse("jev", Map.of(
                AgentOfferReplyJudge.REPLY_QUESTION,
                new JevAnswer("choice", option, Map.of(option, confidence), null, null, Map.of(), confidence)),
                300L, 0L, 80L));
        Optional<AgentOfferReplyJudge.Decision> decision = new AgentOfferReplyJudge(client, 0.7d)
                .judge(AgentOfferReplyJudge.state("whatever", "ganre", "Jason", "Red Whip", true));
        return decision.orElseThrow();
    }
}
