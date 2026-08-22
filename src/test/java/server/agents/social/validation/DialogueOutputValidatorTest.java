package server.agents.social.validation;

import org.junit.jupiter.api.Test;
import server.agents.social.contracts.DialogueContextSnapshot;
import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;
import server.agents.social.contracts.DialogueStyleSnapshot;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueOutputValidatorTest {
    @Test
    void stripsSpeakerPrefixQuotesAndNewlines() {
        DialogueOutputValidator validator = new DialogueOutputValidator();

        DialogueResult result = validator.validate(request(), DialogueResult.model(
                "\"Mina: yo  there\nfriend\"", "test:model", 1)).orElseThrow();

        assertEquals("yo there friend", result.displayText());
    }

    @Test
    void rejectsOversizedOutput() {
        assertTrue(new DialogueOutputValidator().validate(
                request(), DialogueResult.model("x".repeat(65), "test:model", 1)).isEmpty());
    }

    private static DialogueRequest request() {
        return new DialogueRequest(
                "validation",
                "casual.general",
                "Alice",
                "hello",
                new DialogueContextSnapshot(
                        100,
                        1,
                        "Mina",
                        "relaxed-v1",
                        new DialogueStyleSnapshot("friendly-casual-v1", 1, "friendly",
                                20, 40, 40, 80, 10, List.of()),
                        "between activities",
                        "casual acquaintance",
                        100,
                        Map.of()),
                List.of(),
                List.of("hey"),
                true,
                64,
                2_000);
    }
}
