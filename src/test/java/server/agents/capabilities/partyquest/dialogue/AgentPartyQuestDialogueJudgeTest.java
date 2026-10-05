package server.agents.capabilities.partyquest.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.integration.typesafe.JevAnswer;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;
import server.agents.integration.typesafe.TypeSafeSettings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyQuestDialogueJudgeTest {
    private static final TypeSafeSettings SETTINGS = new TypeSafeSettings(
            "key", TypeSafeSettings.DEFAULT_ENDPOINT, TypeSafeSettings.DEFAULT_MODEL, 1_000);

    @Test
    void asksOneNoulPerQuestionOverTheStageContext() throws Exception {
        AtomicReference<JevRequest> sent = new AtomicReference<>();
        JevClient client = new JevClient(SETTINGS, (request, settings) -> {
            sent.set(request);
            return new JevResponse("jev", Map.of(
                    "asks_coupon_count", new JevAnswer("noul", null, Map.of(), 0.84d, null, Map.of(), null),
                    "says_ready", new JevAnswer("noul", null, Map.of(), 0.12d, null, Map.of(), null)),
                    400L, 0L, 90L);
        });
        Map<String, Object> state = AgentPartyQuestDialogueJudge.state("ganre", "wait how many do i need again",
                "Kerning City Party Quest", "Stage 1", "report coupon counts", "party member");

        Optional<AgentPartyQuestDialogueJudge.Verdict> verdict = AgentPartyQuestDialogueJudge.judge(client, state, List.of(
                new AgentPartyQuestDialogueJudge.Question("asks_coupon_count", "Is the speaker asking how many coupons they need?"),
                new AgentPartyQuestDialogueJudge.Question("says_ready", "Is the speaker saying they are ready?"))).get();

        assertEquals(2, sent.get().questions().size());
        assertEquals("noul", sent.get().questions().get("asks_coupon_count").type());
        assertTrue(verdict.isPresent());
        assertTrue(verdict.get().yes("asks_coupon_count", 0.7d));
        assertFalse(verdict.get().yes("says_ready", 0.7d));
        assertTrue(verdict.get().summary().contains("asks_coupon_count=0.84"));
    }

    @Test
    void unavailableClientYieldsEmptyWithoutBlocking() throws Exception {
        JevClient client = new JevClient(TypeSafeSettings.disabled(), (request, settings) -> {
            throw new AssertionError("must not be called");
        });

        Optional<AgentPartyQuestDialogueJudge.Verdict> verdict = AgentPartyQuestDialogueJudge.judge(client,
                Map.of("message", "hi"), List.of(new AgentPartyQuestDialogueJudge.Question("q", "Anything?"))).get();

        assertTrue(verdict.isEmpty());
    }
}
