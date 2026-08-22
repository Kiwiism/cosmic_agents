package server.agents.social.conversation;

import server.agents.social.contracts.DialogueMode;
import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;
import server.agents.social.provider.DeterministicDialogueProvider;
import server.agents.social.provider.DialogueProvider;
import server.agents.social.validation.DialogueOutputValidator;

import java.util.Optional;

/**
 * Chooses optional model enrichment for visible dialogue and guarantees a
 * deterministic catalog result whenever presentation was requested.
 */
public final class AgentSocialDialogueService {
    private volatile DialogueMode mode;
    private final DialogueProvider modelProvider;
    private final DeterministicDialogueProvider deterministicProvider;
    private final DialogueOutputValidator outputValidator = new DialogueOutputValidator();

    public AgentSocialDialogueService(
            DialogueMode mode,
            DialogueProvider modelProvider,
            DeterministicDialogueProvider deterministicProvider) {
        this.mode = mode == null ? DialogueMode.DETERMINISTIC_ONLY : mode;
        this.modelProvider = modelProvider;
        this.deterministicProvider = deterministicProvider == null
                ? new DeterministicDialogueProvider()
                : deterministicProvider;
    }

    public Optional<DialogueResult> generate(DialogueRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!request.observed()) {
            return Optional.empty();
        }
        if (mode == DialogueMode.DETERMINISTIC_ONLY || modelProvider == null) {
            return Optional.of(fallback(request, mode == DialogueMode.DETERMINISTIC_ONLY
                    ? "deterministic-mode"
                    : "model-unavailable"));
        }
        try {
            Optional<DialogueResult> generated = modelProvider.generate(request);
            if (generated != null && generated.isPresent()) {
                Optional<DialogueResult> validated = outputValidator.validate(request, generated.get());
                if (validated.isPresent()) {
                    return validated;
                }
            }
            return Optional.of(fallback(request, "model-empty-or-invalid"));
        } catch (RuntimeException ignored) {
            return Optional.of(fallback(request, "model-failed"));
        }
    }

    public boolean modelEnabled() {
        return mode == DialogueMode.DIALOGUE_ONLY && modelProvider != null;
    }

    public DialogueMode mode() {
        return mode;
    }

    public void setMode(DialogueMode mode) {
        this.mode = mode == null ? DialogueMode.DETERMINISTIC_ONLY : mode;
    }

    public DialogueResult fallback(DialogueRequest request, String reason) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        return deterministicProvider.generate(request, reason);
    }

}
