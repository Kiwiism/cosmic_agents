package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeHandle;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class AgentUntargetedChatRouteService {
    private AgentUntargetedChatRouteService() {
    }

    public record Hooks<E extends AgentRuntimeHandle>(FollowTargetMatcher followTargetMatcher,
                        FollowTargetCommand<E> followTargetCommand,
                        GroupSupplyRequestClassifier groupSupplyRequestClassifier,
                        GroupSupplyResponderSelector<E> groupSupplyResponderSelector,
                        ReplyChannelSetter<E> replyChannelSetter,
                        AgentChatHandler<E> agentChatHandler,
                        BooleanSupplier typoSuggesterEnabled,
                        TypoSuggester typoSuggester,
                        AgentReplyQueue<E> agentReplyQueue) {
    }

    @FunctionalInterface
    public interface FollowTargetMatcher {
        String match(String message);
    }

    @FunctionalInterface
    public interface FollowTargetCommand<E extends AgentRuntimeHandle> {
        void apply(Character leader, List<E> entries, String targetToken);
    }

    @FunctionalInterface
    public interface GroupSupplyRequestClassifier {
        boolean isGroupSupplyRequest(String message);
    }

    @FunctionalInterface
    public interface GroupSupplyResponderSelector<E extends AgentRuntimeHandle> {
        E select(Character leader, List<E> entries);
    }

    @FunctionalInterface
    public interface ReplyChannelSetter<E extends AgentRuntimeHandle> {
        void set(E entry, AgentReplyChannel channel);
    }

    @FunctionalInterface
    public interface AgentChatHandler<E extends AgentRuntimeHandle> {
        void handle(E entry, String message);
    }

    @FunctionalInterface
    public interface TypoSuggester {
        String suggest(String message);
    }

    @FunctionalInterface
    public interface AgentReplyQueue<E extends AgentRuntimeHandle> {
        void queue(E entry, String reply);
    }

    public static <E extends AgentRuntimeHandle> void handleUntargetedChat(Character leader,
                                            List<E> entries,
                                            String message,
                                            AgentReplyChannel channel,
                                            Hooks<E> hooks) {
        String followTargetToken = hooks.followTargetMatcher().match(message);
        if (followTargetToken != null) {
            hooks.followTargetCommand().apply(leader, entries, followTargetToken);
            return;
        }

        if (hooks.groupSupplyRequestClassifier().isGroupSupplyRequest(message)) {
            E responder = hooks.groupSupplyResponderSelector().select(leader, entries);
            if (responder != null) {
                hooks.replyChannelSetter().set(responder, channel);
                hooks.agentChatHandler().handle(responder, message);
            }
            return;
        }

        if (hooks.typoSuggesterEnabled().getAsBoolean()) {
            String typo = hooks.typoSuggester().suggest(message);
            if (typo != null) {
                E first = entries.get(0);
                hooks.replyChannelSetter().set(first, channel);
                hooks.agentReplyQueue().queue(first, "did you mean '" + typo + "'?");
                return;
            }
        }

        for (E entry : entries) {
            hooks.replyChannelSetter().set(entry, channel);
            hooks.agentChatHandler().handle(entry, message);
        }
    }
}
