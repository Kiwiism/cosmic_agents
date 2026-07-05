package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentTargetedCommandMatch;
import server.agents.runtime.AgentRuntimeHandle;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class AgentTargetedChatRouteService {
    private AgentTargetedChatRouteService() {
    }

    public record Hooks<E extends AgentRuntimeHandle>(TargetedCommandResolver<E> targetedCommandResolver,
                        FollowTargetMatcher followTargetMatcher,
                        FollowTargetCommand<E> followTargetCommand,
                        ReplyChannelSetter<E> replyChannelSetter,
                        BooleanSupplier typoSuggesterEnabled,
                        TypoSuggester typoSuggester,
                        AgentReplyQueue<E> agentReplyQueue,
                        AgentChatHandler<E> agentChatHandler,
                        BooleanSupplier lastChatHandled,
                        LongSupplier nowMs,
                        LeaderResolver<E> leaderResolver,
                        OwnerCommandRecorder<E> ownerCommandRecorder,
                        BooleanSupplier llmEnabled,
                        LlmResponder<E> llmResponder,
                        LeaderMessage leaderMessage) {
    }

    @FunctionalInterface
    public interface TargetedCommandResolver<E extends AgentRuntimeHandle> {
        AgentTargetedCommandMatch<E> resolve(List<E> entries, String message);
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
    public interface ReplyChannelSetter<E extends AgentRuntimeHandle> {
        void set(E entry, AgentReplyChannel channel);
    }

    @FunctionalInterface
    public interface TypoSuggester {
        String suggest(String commandText);
    }

    @FunctionalInterface
    public interface AgentReplyQueue<E extends AgentRuntimeHandle> {
        void queue(E entry, String reply);
    }

    @FunctionalInterface
    public interface AgentChatHandler<E extends AgentRuntimeHandle> {
        void handle(E entry, String commandText);
    }

    @FunctionalInterface
    public interface LeaderResolver<E extends AgentRuntimeHandle> {
        Character resolve(E entry);
    }

    @FunctionalInterface
    public interface OwnerCommandRecorder<E extends AgentRuntimeHandle> {
        void record(E entry, String commandText, long commandAtMs);
    }

    @FunctionalInterface
    public interface LlmResponder<E extends AgentRuntimeHandle> {
        void maybeRespond(E entry, Character sender, String commandText);
    }

    @FunctionalInterface
    public interface LeaderMessage {
        void send(Character leader, String message);
    }

    public static <E extends AgentRuntimeHandle> boolean handleTargetedChat(Character leader,
                                             List<E> entries,
                                             String message,
                                             AgentReplyChannel channel,
                                             Hooks<E> hooks) {
        AgentTargetedCommandMatch<E> targetedAgent = hooks.targetedCommandResolver().resolve(entries, message);
        if (targetedAgent.entry() != null) {
            E entry = targetedAgent.entry();
            String commandText = targetedAgent.commandText();
            String followTargetToken = hooks.followTargetMatcher().match(commandText);
            if (followTargetToken != null) {
                hooks.followTargetCommand().apply(leader, List.of(entry), followTargetToken);
                return true;
            }

            hooks.replyChannelSetter().set(entry, channel);
            if (hooks.typoSuggesterEnabled().getAsBoolean()) {
                String typo = hooks.typoSuggester().suggest(commandText);
                if (typo != null) {
                    hooks.agentReplyQueue().queue(entry, "did you mean '" + typo + "'?");
                    return true;
                }
            }

            hooks.agentChatHandler().handle(entry, commandText);
            boolean matched = hooks.lastChatHandled().getAsBoolean();
            Character owner = hooks.leaderResolver().resolve(entry);
            if (matched && owner != null && leader.getId() == owner.getId()) {
                hooks.ownerCommandRecorder().record(entry, commandText, hooks.nowMs().getAsLong());
            }
            if (hooks.llmEnabled().getAsBoolean() && !matched) {
                hooks.llmResponder().maybeRespond(entry, leader, commandText);
            }
            return true;
        }
        if (targetedAgent.feedbackMessage() != null) {
            hooks.leaderMessage().send(leader, targetedAgent.feedbackMessage());
            return true;
        }

        return false;
    }
}
