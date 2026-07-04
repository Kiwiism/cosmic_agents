package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotTargetedCommandMatch;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class AgentTargetedChatRouteService {
    private AgentTargetedChatRouteService() {
    }

    public record Hooks(TargetedCommandResolver targetedCommandResolver,
                        FollowTargetMatcher followTargetMatcher,
                        FollowTargetCommand followTargetCommand,
                        ReplyChannelSetter replyChannelSetter,
                        BooleanSupplier typoSuggesterEnabled,
                        TypoSuggester typoSuggester,
                        AgentReplyQueue agentReplyQueue,
                        AgentChatHandler agentChatHandler,
                        BooleanSupplier lastChatHandled,
                        LongSupplier nowMs,
                        OwnerCommandRecorder ownerCommandRecorder,
                        BooleanSupplier llmEnabled,
                        LlmResponder llmResponder,
                        LeaderMessage leaderMessage) {
    }

    @FunctionalInterface
    public interface TargetedCommandResolver {
        AgentBotTargetedCommandMatch resolve(List<BotEntry> entries, String message);
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
    public interface ReplyChannelSetter {
        void set(BotEntry entry, AgentReplyChannel channel);
    }

    @FunctionalInterface
    public interface TypoSuggester {
        String suggest(String commandText);
    }

    @FunctionalInterface
    public interface AgentReplyQueue {
        void queue(BotEntry entry, String reply);
    }

    @FunctionalInterface
    public interface AgentChatHandler {
        void handle(BotEntry entry, String commandText);
    }

    @FunctionalInterface
    public interface OwnerCommandRecorder {
        void record(BotEntry entry, String commandText, long commandAtMs);
    }

    @FunctionalInterface
    public interface LlmResponder {
        void maybeRespond(BotEntry entry, Character sender, String commandText);
    }

    @FunctionalInterface
    public interface LeaderMessage {
        void send(Character leader, String message);
    }

    public static boolean handleTargetedChat(Character leader,
                                             List<BotEntry> entries,
                                             String message,
                                             AgentReplyChannel channel,
                                             Hooks hooks) {
        AgentBotTargetedCommandMatch targetedAgent = hooks.targetedCommandResolver().resolve(entries, message);
        if (targetedAgent.entry() != null) {
            BotEntry entry = targetedAgent.entry();
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
            Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
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
