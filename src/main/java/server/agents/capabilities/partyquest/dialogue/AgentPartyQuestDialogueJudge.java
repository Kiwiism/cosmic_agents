package server.agents.capabilities.partyquest.dialogue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Understands what a human party member is asking during a party quest, as a handful of Nouls
 * over the message plus the stage context. Deterministic word-set policies remain the fast path;
 * this runs only when they did not match and the stage runtime's own gates already passed.
 */
public final class AgentPartyQuestDialogueJudge {
    private static final Logger log = LoggerFactory.getLogger(AgentPartyQuestDialogueJudge.class);
    public static final String REQUEST_KIND = "party-quest";
    private static final double YES_PROBABILITY = config.AgentTuning.doubleValue(
            "server.agents.capabilities.partyquest.dialogue.AgentPartyQuestDialogueJudge.YES_PROBABILITY");

    /** A yes/no statement about the message, keyed by the id the caller reads back. */
    public record Question(String id, String statement) {
        public Question {
            if (id == null || id.isBlank() || statement == null || statement.isBlank()) {
                throw new IllegalArgumentException("question id and statement are required");
            }
        }
    }

    /** Yes-probabilities per question id; empty when no judgment was obtained. */
    public record Verdict(Map<String, Double> probabilities, long latencyMs) {
        public Verdict {
            probabilities = probabilities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(probabilities));
        }

        public boolean yes(String questionId) {
            return yes(questionId, YES_PROBABILITY);
        }

        public boolean yes(String questionId, double threshold) {
            return probabilities.getOrDefault(questionId, 0.0d) >= threshold;
        }

        public String summary() {
            StringBuilder text = new StringBuilder();
            probabilities.forEach((id, probability) -> text.append(id).append('=')
                    .append(String.format(Locale.ROOT, "%.2f", probability)).append(' '));
            return text.append(latencyMs).append("ms").toString();
        }
    }

    private AgentPartyQuestDialogueJudge() {
    }

    public static Map<String, Object> state(String speakerName, String message, String partyQuest,
                                            String stage, String stageNeeds, String speakerRole) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("message", message == null ? "" : message.trim());
        Map<String, Object> speaker = new LinkedHashMap<>();
        speaker.put("name", speakerName);
        speaker.put("role", speakerRole);
        state.put("speaker", speaker);
        Map<String, Object> pq = new LinkedHashMap<>();
        pq.put("party_quest", partyQuest);
        pq.put("stage", stage);
        pq.put("stage_needs", stageNeeds);
        state.put("party_quest", pq);
        return state;
    }

    /** Non-blocking; completes with an empty verdict when the client is unavailable or fails. */
    public static CompletableFuture<Optional<Verdict>> judge(JevClient client, Map<String, Object> state,
                                                              List<Question> questions) {
        if (client == null || !client.available() || questions == null || questions.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return client.askAsync(request(state, questions)).thenApply(response -> response.map(AgentPartyQuestDialogueJudge::verdict))
                .exceptionally(failure -> {
                    log.debug("party-quest dialogue judgment failed", failure);
                    return Optional.empty();
                });
    }

    /** The request a judgment sends: one Noul per question over the stage state. */
    public static JevRequest request(Map<String, Object> state, List<Question> questions) {
        JevRequest.Builder builder = JevRequest.builder(state).kind(REQUEST_KIND);
        for (Question question : questions) {
            Map<String, Object> instructions = new LinkedHashMap<>();
            instructions.put("question", question.statement());
            instructions.put("inspect", "`message`, with `party_quest` and `speaker` as context");
            builder.noul(question.id(), instructions);
        }
        return builder.build();
    }

    static Verdict verdict(JevResponse response) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        response.answers().forEach((id, answer) -> probabilities.put(id, answer.yesProbability()));
        return new Verdict(probabilities, response.latencyMs());
    }
}
