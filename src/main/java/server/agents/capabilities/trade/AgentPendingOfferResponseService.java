package server.agents.capabilities.trade;

import client.Character;
import server.agents.integration.AgentBotTargetedCommandMatch;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class AgentPendingOfferResponseService {
    private AgentPendingOfferResponseService() {
    }

    public record Hooks(PendingOfferExpiry pendingOfferExpiry,
                        PendingOfferTargetCheck pendingOfferTargetCheck,
                        TargetedCommandResolver targetedCommandResolver,
                        PendingOfferResponseHandler pendingOfferResponseHandler,
                        SpeakerFeedback speakerFeedback) {
    }

    @FunctionalInterface
    public interface PendingOfferExpiry {
        void expire(BotEntry entry);
    }

    @FunctionalInterface
    public interface PendingOfferTargetCheck {
        boolean isTarget(BotEntry entry, Character speaker);
    }

    @FunctionalInterface
    public interface TargetedCommandResolver {
        AgentBotTargetedCommandMatch resolve(List<BotEntry> entries, String message);
    }

    @FunctionalInterface
    public interface PendingOfferResponseHandler {
        boolean handle(BotEntry entry, Character speaker, String message);
    }

    @FunctionalInterface
    public interface SpeakerFeedback {
        void send(Character speaker, String message);
    }

    public static boolean handlePendingOfferResponse(Collection<List<BotEntry>> entryGroups,
                                                     Character speaker,
                                                     String message,
                                                     Hooks hooks) {
        List<BotEntry> matches = new ArrayList<>();
        for (List<BotEntry> entries : entryGroups) {
            for (BotEntry entry : entries) {
                hooks.pendingOfferExpiry().expire(entry);
                if (hooks.pendingOfferTargetCheck().isTarget(entry, speaker)) {
                    matches.add(entry);
                }
            }
        }

        AgentBotTargetedCommandMatch targetedAgent = hooks.targetedCommandResolver().resolve(matches, message);
        if (targetedAgent.entry() != null) {
            return hooks.pendingOfferResponseHandler().handle(targetedAgent.entry(), speaker, targetedAgent.commandText());
        }
        if (targetedAgent.feedbackMessage() != null) {
            hooks.speakerFeedback().send(speaker, targetedAgent.feedbackMessage());
            return true;
        }

        if (matches.size() == 1) {
            return hooks.pendingOfferResponseHandler().handle(matches.get(0), speaker, message);
        }
        if (matches.size() > 1 && looksLikeConfirmation(message)) {
            hooks.speakerFeedback().send(speaker, "More than one bot is waiting on you. Say '<botname> yes' or '<slot> yes'.");
            return true;
        }

        return false;
    }

    static boolean looksLikeConfirmation(String message) {
        String normalized = message.trim().toLowerCase();
        return normalized.matches(".*\\b(yes|yep|yeah|yea|y|ok|sure|confirm|no|nope|nah|nvm|never\\s*mind|dont|don't|not\\s+now|skip)\\b.*");
    }
}
