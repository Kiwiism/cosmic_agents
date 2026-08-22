package server.agents.social.conversation;

import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;
import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Agent OS application boundary that keeps external model work off runtime threads. */
public final class AgentSocialDialogueApplication {
    public enum SubmissionStatus {
        ACCEPTED,
        FALLBACK_QUEUED,
        SUPPRESSED
    }

    private static final String REQUEST_KEY = "social-dialogue";
    private static final Set<Integer> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private final AgentSocialDialogueService dialogueService;

    public AgentSocialDialogueApplication(AgentSocialDialogueService dialogueService) {
        if (dialogueService == null) {
            throw new IllegalArgumentException("dialogue service is required");
        }
        this.dialogueService = dialogueService;
    }

    public SubmissionStatus submit(
            AgentRuntimeEntry entry,
            DialogueRequest request,
            Consumer<DialogueResult> resultHandler) {
        if (entry == null || request == null || resultHandler == null) {
            throw new IllegalArgumentException("Agent dialogue submission inputs are required");
        }
        if (!request.observed()) {
            return SubmissionStatus.SUPPRESSED;
        }
        if (!dialogueService.modelEnabled()) {
            deliver(entry, dialogueService.fallback(request, "deterministic-mode"), resultHandler);
            return SubmissionStatus.FALLBACK_QUEUED;
        }
        int agentId = request.context().agentId();
        if (!IN_FLIGHT.add(agentId)) {
            deliver(entry, dialogueService.fallback(request, "model-in-flight"), resultHandler);
            return SubmissionStatus.FALLBACK_QUEUED;
        }
        AgentAsyncTaskGateway.Submission submission = AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.LLM_NETWORK,
                REQUEST_KEY,
                request.timeoutMs(),
                () -> {
                    try {
                        return dialogueService.generate(request);
                    } finally {
                        IN_FLIGHT.remove(agentId);
                    }
                },
                (completionEntry, completion) -> {
                    DialogueResult result;
                    if (completion.succeeded()) {
                        @SuppressWarnings("unchecked")
                        Optional<DialogueResult> generated = (Optional<DialogueResult>) completion.result();
                        result = generated == null || generated.isEmpty()
                                ? dialogueService.fallback(request, "model-empty")
                                : generated.get();
                    } else {
                        String reason = completion.status()
                                == server.agents.runtime.async.AgentAsyncCompletion.Status.TIMED_OUT
                                ? "model-timeout"
                                : "model-failed";
                        result = dialogueService.fallback(request, reason);
                    }
                    resultHandler.accept(result);
                });
        if (!submission.accepted()) {
            IN_FLIGHT.remove(agentId);
            deliver(entry, dialogueService.fallback(request, "model-queue-rejected"), resultHandler);
            return SubmissionStatus.FALLBACK_QUEUED;
        }
        return SubmissionStatus.ACCEPTED;
    }

    public static void clearAgentRuntimeState(int agentId) {
        IN_FLIGHT.remove(agentId);
    }

    private static void deliver(
            AgentRuntimeEntry entry,
            DialogueResult result,
            Consumer<DialogueResult> resultHandler) {
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            resultHandler.accept(result);
            return null;
        });
    }
}
