package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class AgentUntargetedChatRouteService {
    private AgentUntargetedChatRouteService() {
    }

    public record Hooks(FollowTargetMatcher followTargetMatcher,
                        FollowTargetCommand followTargetCommand,
                        GroupSupplyRequestClassifier groupSupplyRequestClassifier,
                        GroupSupplyResponderSelector groupSupplyResponderSelector,
                        ReplyChannelSetter replyChannelSetter,
                        AgentChatHandler agentChatHandler,
                        BooleanSupplier typoSuggesterEnabled,
                        TypoSuggester typoSuggester,
                        AgentReplyQueue agentReplyQueue) {
    }

    @FunctionalInterface
    public interface FollowTargetMatcher {
        String match(String message);
    }

    @FunctionalInterface
    public interface FollowTargetCommand {
        void apply(Character leader, List<BotEntry> entries, String targetToken);
    }

    @FunctionalInterface
    public interface GroupSupplyRequestClassifier {
        boolean isGroupSupplyRequest(String message);
    }

    @FunctionalInterface
    public interface GroupSupplyResponderSelector {
        BotEntry select(Character leader, List<BotEntry> entries);
    }

    @FunctionalInterface
    public interface ReplyChannelSetter {
        void set(BotEntry entry, AgentReplyChannel channel);
    }

    @FunctionalInterface
    public interface AgentChatHandler {
        void handle(BotEntry entry, String message);
    }

    @FunctionalInterface
    public interface TypoSuggester {
        String suggest(String message);
    }

    @FunctionalInterface
    public interface AgentReplyQueue {
        void queue(BotEntry entry, String reply);
    }

    public static void handleUntargetedChat(Character leader,
                                            List<BotEntry> entries,
                                            String message,
                                            AgentReplyChannel channel,
                                            Hooks hooks) {
        String followTargetToken = hooks.followTargetMatcher().match(message);
        if (followTargetToken != null) {
            hooks.followTargetCommand().apply(leader, entries, followTargetToken);
            return;
        }

        if (hooks.groupSupplyRequestClassifier().isGroupSupplyRequest(message)) {
            BotEntry responder = hooks.groupSupplyResponderSelector().select(leader, entries);
            if (responder != null) {
                hooks.replyChannelSetter().set(responder, channel);
                hooks.agentChatHandler().handle(responder, message);
            }
            return;
        }

        if (hooks.typoSuggesterEnabled().getAsBoolean()) {
            String typo = hooks.typoSuggester().suggest(message);
            if (typo != null) {
                BotEntry first = entries.get(0);
                hooks.replyChannelSetter().set(first, channel);
                hooks.agentReplyQueue().queue(first, "did you mean '" + typo + "'?");
                return;
            }
        }

        for (BotEntry entry : entries) {
            hooks.replyChannelSetter().set(entry, channel);
            hooks.agentChatHandler().handle(entry, message);
        }
    }
}
