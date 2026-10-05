package server.agents.capabilities.dialogue.jev;

import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;
import server.agents.integration.typesafe.state.JevState;

import java.util.Locale;
import java.util.Optional;

/**
 * Pure decision logic: given the state blocks for one unmatched chat message, ask System One and
 * turn the typed answers into an {@link Outcome}. No Cosmic types, no side effects, so the whole
 * policy is unit-testable with a fake transport.
 */
public final class AgentChatIntentJudge {
    private static final double ACT_CONFIDENCE = config.AgentTuning.doubleValue(
            "server.agents.capabilities.dialogue.jev.AgentChatIntentJudge.ACT_CONFIDENCE");
    private static final double ASK_CONFIDENCE = config.AgentTuning.doubleValue(
            "server.agents.capabilities.dialogue.jev.AgentChatIntentJudge.ASK_CONFIDENCE");
    private static final int STATE_BUDGET_TOKENS = config.AgentTuning.intValue(
            "server.agents.capabilities.dialogue.jev.AgentChatIntentJudge.STATE_BUDGET_TOKENS");

    public enum Outcome {
        /** Confident, actionable: re-dispatch {@link Decision#canonicalCommand()}. */
        ACT,
        /** Plausible but not confident: ask "did you mean ...?". */
        ASK,
        /** Chat, unaddressed, or unconfident: leave the message to the social fallback. */
        IGNORE,
        /** No judgment was obtained (unconfigured, breaker open, timeout, failure). */
        UNAVAILABLE
    }

    public record Decision(Outcome outcome,
                           AgentChatIntent intent,
                           String canonicalCommand,
                           double confidence,
                           double addressedProbability,
                           long latencyMs,
                           long inputTokens,
                           String droppedStateKeys) {
        public static Decision unavailable() {
            return new Decision(Outcome.UNAVAILABLE, null, null, 0.0d, 0.0d, 0L, 0L, "");
        }

        /** One line for shadow logs: {@code regex miss -> jev grind (0.82) ACT}. */
        public String summary() {
            return (intent == null ? "-" : intent.optionId())
                    + " conf=" + String.format(Locale.ROOT, "%.2f", confidence)
                    + " addressed=" + String.format(Locale.ROOT, "%.2f", addressedProbability)
                    + " -> " + outcome
                    + (canonicalCommand == null ? "" : " [" + canonicalCommand + "]")
                    + " " + latencyMs + "ms " + inputTokens + "tok"
                    + (droppedStateKeys.isEmpty() ? "" : " dropped=" + droppedStateKeys);
        }
    }

    private final JevClient client;
    private final double actConfidence;
    private final double askConfidence;
    private final int stateBudgetTokens;

    public AgentChatIntentJudge(JevClient client) {
        this(client, ACT_CONFIDENCE, ASK_CONFIDENCE, STATE_BUDGET_TOKENS);
    }

    public AgentChatIntentJudge(JevClient client, double actConfidence, double askConfidence, int stateBudgetTokens) {
        this.client = client;
        this.actConfidence = actConfidence;
        this.askConfidence = Math.min(askConfidence, actConfidence);
        this.stateBudgetTokens = stateBudgetTokens;
    }

    /** Blocking; run it on the async gateway, never on a packet or mailbox thread. */
    public Decision judge(JevState state) {
        JevState.Built built = state.build(stateBudgetTokens);
        JevRequest request = AgentChatIntentCatalog.request(built.state());
        Optional<JevResponse> response = client.ask(request);
        if (response.isEmpty()) {
            return Decision.unavailable();
        }
        return decide(response.get(), String.join(",", built.droppedKeys()));
    }

    Decision decide(JevResponse response, String droppedKeys) {
        Optional<AgentChatIntentCatalog.Resolution> resolved = AgentChatIntentCatalog.resolve(response);
        if (resolved.isEmpty()) {
            return new Decision(Outcome.IGNORE, null, null, 0.0d, 0.0d,
                    response.latencyMs(), response.inputTokens(), droppedKeys);
        }
        AgentChatIntentCatalog.Resolution resolution = resolved.get();
        Outcome outcome;
        if (!resolution.intent().actionable() || resolution.canonicalCommand() == null) {
            outcome = Outcome.IGNORE;
        } else if (resolution.confidence() >= actConfidence) {
            outcome = Outcome.ACT;
        } else if (resolution.confidence() >= askConfidence) {
            outcome = Outcome.ASK;
        } else {
            outcome = Outcome.IGNORE;
        }
        return new Decision(outcome, resolution.intent(), resolution.canonicalCommand(),
                resolution.confidence(), resolution.addressedProbability(),
                response.latencyMs(), response.inputTokens(), droppedKeys);
    }
}
