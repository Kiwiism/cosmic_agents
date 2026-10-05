package server.agents.capabilities.dialogue.jev;

import server.agents.integration.typesafe.JevAnswer;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the single fan-out request for a companion chat message and turns the typed answers
 * back into the canonical command phrase the deterministic dispatcher already understands.
 */
public final class AgentChatIntentCatalog {
    public static final String INTENT_QUESTION = "intent";
    public static final String ADDRESSED_QUESTION = "addressed_to_bot";
    public static final String REQUEST_KIND = "chat-intent";

    private AgentChatIntentCatalog() {
    }

    /** All questions are asked together; argument answers are read only for the chosen intent. */
    public static JevRequest request(Map<String, Object> state) {
        JevRequest.Builder builder = JevRequest.builder(state).kind(REQUEST_KIND);
        Map<String, Object> intentInstructions = new LinkedHashMap<>();
        intentInstructions.put("question", "Which companion command does `message` express?");
        intentInstructions.put("inspect", "`message`, using `speaker`, `bot` and `recent_turns` only as context");
        intentInstructions.put("focus", "Judge the message as an instruction from a player to a companion bot. "
                + "Ordinary conversation, jokes, or talk aimed at other players is chat_not_a_command.");
        Map<String, Object> intentCriteria = new LinkedHashMap<>();
        for (AgentChatIntent intent : AgentChatIntent.values()) {
            intentCriteria.put(intent.optionId(), intent.criteria());
        }
        builder.choice(INTENT_QUESTION, intentInstructions, intentCriteria);
        for (AgentChatIntentArgument argument : AgentChatIntentArgument.values()) {
            Map<String, Object> instructions = new LinkedHashMap<>();
            instructions.put("question", argument.question());
            instructions.put("inspect", "`message`");
            instructions.put("note", "Answer as if the message were that kind of command; the answer is only used when it is.");
            builder.choice(argument.questionId(), instructions, argument.criteria());
        }
        Map<String, Object> addressed = new LinkedHashMap<>();
        addressed.put("question", "Is `message` directed at the companion bot named in `bot.name` (or at the speaker's bots in general)?");
        addressed.put("inspect", "`message`, `bot.name`, `speaker.is_owner`");
        builder.noul(ADDRESSED_QUESTION, addressed);
        return builder.build();
    }

    /** A resolved intent with the confidence of its least certain part (intent and argument). */
    public record Resolution(AgentChatIntent intent, String canonicalCommand, double confidence,
                             double addressedProbability) {
    }

    public static Optional<Resolution> resolve(JevResponse response) {
        Optional<JevAnswer> intentAnswer = response.answer(INTENT_QUESTION);
        if (intentAnswer.isEmpty()) {
            return Optional.empty();
        }
        AgentChatIntent intent = intentAnswer.get().mostLikelyOption()
                .map(AgentChatIntent::fromOptionId)
                .orElse(null);
        if (intent == null) {
            return Optional.empty();
        }
        double confidence = intentAnswer.get().confidenceOrZero();
        double addressed = response.yesProbability(ADDRESSED_QUESTION);
        if (!intent.actionable()) {
            return Optional.of(new Resolution(intent, null, confidence, addressed));
        }
        AgentChatIntentArgument argument = intent.argument();
        if (argument == null) {
            return Optional.of(new Resolution(intent, intent.canonicalCommand(), confidence, addressed));
        }
        Optional<JevAnswer> argumentAnswer = response.answer(argument.questionId());
        if (argumentAnswer.isEmpty()) {
            return Optional.of(new Resolution(intent, null, 0.0d, addressed));
        }
        String phrase = argumentAnswer.get().mostLikelyOption().map(argument::phraseFor).orElse(null);
        if (phrase == null) {
            return Optional.of(new Resolution(intent, null, 0.0d, addressed));
        }
        // One wrong argument spoils the command, so the call is only as confident as its weakest part.
        double combined = Math.min(confidence, argumentAnswer.get().confidenceOrZero());
        return Optional.of(new Resolution(intent, canonicalCommand(intent, phrase), combined, addressed));
    }

    static String canonicalCommand(AgentChatIntent intent, String argumentPhrase) {
        return switch (intent) {
            case TRADE_ITEMS -> "trade " + argumentPhrase;
            case DROP_ITEMS -> "drop " + argumentPhrase;
            case UNEQUIP_SLOT -> "unequip " + argumentPhrase;
            default -> argumentPhrase;
        };
    }
}
