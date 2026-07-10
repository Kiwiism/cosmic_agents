package server.agents.capabilities.follow;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.function.LongSupplier;

public final class AgentFollowTargetCommandService {
    private AgentFollowTargetCommandService() {
    }

    public record Hooks(FollowTargetResolver followTargetResolver,
                        ReplySelector replySelector,
                        AgentReplyQueue agentReplyQueue,
                        LongSupplier followDelayMs,
                        DelayedActionScheduler delayedActionScheduler,
                        AgentAutoEquip agentAutoEquip,
                        PotionShareCheck potionShareCheck,
                        FollowStarter followStarter) {
    }

    @FunctionalInterface
    public interface FollowTargetResolver {
        Character resolve(Character leader, String targetToken);
    }

    @FunctionalInterface
    public interface ReplySelector {
        String select(Character target);
    }

    @FunctionalInterface
    public interface AgentReplyQueue {
        void queue(AgentRuntimeEntry entry, String reply);
    }

    @FunctionalInterface
    public interface DelayedActionScheduler {
        void schedule(long delayMs, Runnable action);
    }

    @FunctionalInterface
    public interface AgentAutoEquip {
        void autoEquip(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface PotionShareCheck {
        void check(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface FollowStarter {
        void start(AgentRuntimeEntry entry, Character target);
    }

    public static boolean applyFollowTargetCommand(Character leader,
                                                   List<? extends AgentRuntimeEntry> entries,
                                                   String targetToken,
                                                   Hooks hooks) {
        Character target = hooks.followTargetResolver().resolve(leader, targetToken);
        if (target == null) {
            return true;
        }

        for (AgentRuntimeEntry entry : entries) {
            if (entry == null || !AgentRuntimeIdentityRuntime.hasBot(entry)
                    || AgentRuntimeIdentityRuntime.botIs(entry, target.getId())) {
                continue;
            }
            hooks.agentReplyQueue().queue(entry, hooks.replySelector().select(target));
            hooks.delayedActionScheduler().schedule(hooks.followDelayMs().getAsLong(), () -> {
                hooks.agentAutoEquip().autoEquip(entry);
                hooks.potionShareCheck().check(entry);
                hooks.followStarter().start(entry, target);
            });
        }
        return true;
    }
}
