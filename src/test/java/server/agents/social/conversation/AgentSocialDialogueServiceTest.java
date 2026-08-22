package server.agents.social.conversation;

import org.junit.jupiter.api.Test;
import server.agents.social.contracts.DialogueContextSnapshot;
import server.agents.social.contracts.DialogueMode;
import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;
import server.agents.social.provider.DeterministicDialogueProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentSocialDialogueServiceTest {
    @Test
    void deterministicModeNeverCallsModel() {
        AtomicInteger calls = new AtomicInteger();
        AgentSocialDialogueService service = new AgentSocialDialogueService(
                DialogueMode.DETERMINISTIC_ONLY,
                request -> {
                    calls.incrementAndGet();
                    return Optional.of(DialogueResult.model("model", "test-model", 1));
                },
                new DeterministicDialogueProvider());

        DialogueResult result = service.generate(request(true)).orElseThrow();

        assertEquals(0, calls.get());
        assertEquals(DialogueResult.Source.DETERMINISTIC, result.source());
        assertEquals("deterministic-mode", result.fallbackReason());
    }

    @Test
    void dialogueModeUsesValidModelResult() {
        AgentSocialDialogueService service = new AgentSocialDialogueService(
                DialogueMode.DIALOGUE_ONLY,
                request -> Optional.of(DialogueResult.model("hey there", "local:qwen", 12)),
                new DeterministicDialogueProvider());

        DialogueResult result = service.generate(request(true)).orElseThrow();

        assertEquals("hey there", result.displayText());
        assertEquals(DialogueResult.Source.MODEL, result.source());
    }

    @Test
    void unavailableOrFailedModelFallsBackToCatalog() {
        AgentSocialDialogueService unavailable = new AgentSocialDialogueService(
                DialogueMode.DIALOGUE_ONLY, null, new DeterministicDialogueProvider());
        AgentSocialDialogueService failed = new AgentSocialDialogueService(
                DialogueMode.DIALOGUE_ONLY,
                request -> { throw new IllegalStateException("offline"); },
                new DeterministicDialogueProvider());

        assertEquals("model-unavailable", unavailable.generate(request(true)).orElseThrow().fallbackReason());
        assertEquals("model-failed", failed.generate(request(true)).orElseThrow().fallbackReason());
    }

    @Test
    void unobservedDialogueDoesNotCallEitherProvider() {
        AtomicInteger calls = new AtomicInteger();
        AgentSocialDialogueService service = new AgentSocialDialogueService(
                DialogueMode.DIALOGUE_ONLY,
                request -> {
                    calls.incrementAndGet();
                    return Optional.empty();
                },
                new DeterministicDialogueProvider());

        assertFalse(service.generate(request(false)).isPresent());
        assertEquals(0, calls.get());
    }

    @Test
    void invalidModelOutputFallsBackToCatalog() {
        AgentSocialDialogueService service = new AgentSocialDialogueService(
                DialogueMode.DIALOGUE_ONLY,
                request -> Optional.of(DialogueResult.model("x".repeat(65), "local:qwen", 1)),
                new DeterministicDialogueProvider());

        DialogueResult result = service.generate(request(true)).orElseThrow();

        assertEquals(DialogueResult.Source.DETERMINISTIC, result.source());
        assertEquals("model-empty-or-invalid", result.fallbackReason());
    }

    private static DialogueRequest request(boolean observed) {
        return new DialogueRequest(
                "request-1",
                "casual.greeting",
                "Alice",
                "hello",
                new DialogueContextSnapshot(
                        100,
                        3,
                        "Mina",
                        "balanced",
                        new server.agents.social.contracts.DialogueStyleSnapshot(
                                "friendly-casual-v1", 1, "friendly and casual",
                                25, 45, 40, 80, 15, List.of("yo")),
                        "resting in town",
                        "familiar player",
                        72,
                        Map.of("map", "Henesys")),
                List.of(),
                List.of("yo", "hey"),
                observed,
                64,
                2_000);
    }
}
