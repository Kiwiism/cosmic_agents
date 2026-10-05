package server.agents.capabilities.trade.jev;

import server.agents.integration.typesafe.JevAnswer;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Classifies a free-form reply to a pending item offer ("nah keep it", "sure go ahead", "wait
 * which item?") as accept, decline, question, or unrelated. Accept and decline map onto the
 * canonical yes/no replies the offer service already handles, so no new transfer code exists.
 */
public final class AgentOfferReplyJudge {
    private static final double ACT_CONFIDENCE = config.AgentTuning.doubleValue(
            "server.agents.capabilities.trade.jev.AgentOfferReplyJudge.ACT_CONFIDENCE");
    static final String REPLY_QUESTION = "reply";
    public static final String REQUEST_KIND = "offer-reply";

    public enum Reply {
        ACCEPT("yes"), DECLINE("no"), QUESTION(null), UNRELATED(null);

        private final String canonicalReply;

        Reply(String canonicalReply) {
            this.canonicalReply = canonicalReply;
        }

        /** The phrase the offer service's own regexes recognise, or {@code null}. */
        public String canonicalReply() {
            return canonicalReply;
        }

        static Reply fromOptionId(String optionId) {
            for (Reply reply : values()) {
                if (reply.name().toLowerCase(Locale.ROOT).equals(optionId)) {
                    return reply;
                }
            }
            return UNRELATED;
        }
    }

    public record Decision(Reply reply, double confidence, boolean actionable, long latencyMs) {
        public String summary() {
            return reply.name().toLowerCase(Locale.ROOT)
                    + " conf=" + String.format(Locale.ROOT, "%.2f", confidence)
                    + (actionable ? " ACT" : " IGNORE") + " " + latencyMs + "ms";
        }
    }

    private final JevClient client;
    private final double actConfidence;

    public AgentOfferReplyJudge(JevClient client) {
        this(client, ACT_CONFIDENCE);
    }

    public AgentOfferReplyJudge(JevClient client, double actConfidence) {
        this.client = client;
        this.actConfidence = actConfidence;
    }

    public static Map<String, Object> state(String message, String speakerName, String botName,
                                            String itemName, boolean botRequestingItem) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("message", message == null ? "" : message.trim());
        state.put("speaker", speakerName);
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("bot", botName);
        offer.put("item", itemName);
        offer.put("direction", botRequestingItem
                ? "the bot asked the speaker to hand over the speaker's item"
                : "the bot offered its own item to the speaker");
        offer.put("awaiting", "the speaker's yes or no");
        state.put("pending_offer", offer);
        return state;
    }

    /** Blocking; run on the Agent async gateway. */
    public Optional<Decision> judge(Map<String, Object> state) {
        return client.ask(request(state)).flatMap(this::decide);
    }

    /** The single Choice a reply judgment sends. */
    public static JevRequest request(Map<String, Object> state) {
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("question", "How does `message` answer the `pending_offer`?");
        instructions.put("inspect", "`message`, using `pending_offer.direction` to read who gives what");
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("accept", Map.of("what", "agrees to the offer or tells the bot to go ahead",
                "examples", List.of("sure", "ok go ahead", "yeah give it", "trade it over")));
        criteria.put("decline", Map.of("what", "refuses the offer or tells the bot to keep the item",
                "examples", List.of("nah", "keep it", "no thanks", "not now")));
        criteria.put("question", Map.of("what", "asks something about the offer instead of answering it",
                "examples", List.of("which item?", "what stats does it have?", "why?")));
        criteria.put("unrelated", Map.of("what", "talks about something else or to someone else",
                "examples", List.of("brb", "lol", "anyone selling steelies")));
        return JevRequest.builder(state).kind(REQUEST_KIND)
                .choice(REPLY_QUESTION, instructions, criteria)
                .build();
    }

    Optional<Decision> decide(JevResponse response) {
        Optional<JevAnswer> answer = response.answer(REPLY_QUESTION);
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        Reply reply = answer.get().mostLikelyOption().map(Reply::fromOptionId).orElse(Reply.UNRELATED);
        double confidence = answer.get().confidenceOrZero();
        boolean actionable = reply.canonicalReply() != null && confidence >= actConfidence;
        return Optional.of(new Decision(reply, confidence, actionable, response.latencyMs()));
    }
}
