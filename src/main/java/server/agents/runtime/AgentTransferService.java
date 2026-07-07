package server.agents.runtime;

import client.Character;
import server.agents.auth.AgentAuthorizationResult;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.registry.AgentResolvedCharacter;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class AgentTransferService {
    private AgentTransferService() {
    }

    public record Hooks(EntriesByLeader entriesByLeader,
                        AgentEntryByName agentEntryByName,
                        MapCharacterByName mapCharacterByName,
                        TransferAuthorization transferAuthorization,
                        ScheduledTaskCanceler scheduledTaskCanceler,
                        AgentStopper agentStopper,
                        AgentRegistrar agentRegistrar,
                        DelayedActionScheduler delayedActionScheduler,
                        LongSupplier transferGreetingDelayMs,
                        AgentSpeaker agentSpeaker,
                        TransferGreetingSupplier transferGreetingSupplier) {
    }

    @FunctionalInterface
    public interface EntriesByLeader {
        List<AgentRuntimeEntry> entries(int leaderCharId);
    }

    @FunctionalInterface
    public interface AgentEntryByName {
        AgentRuntimeEntry find(int leaderCharId, String agentName);
    }

    @FunctionalInterface
    public interface MapCharacterByName {
        Character find(Character leader, String targetName);
    }

    @FunctionalInterface
    public interface TransferAuthorization {
        AgentAuthorizationResult authorize(Character target, AgentResolvedCharacter agent);
    }

    @FunctionalInterface
    public interface ScheduledTaskCanceler {
        void cancel(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface AgentStopper {
        void stop(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface AgentRegistrar {
        void register(int leaderCharId, Character leader, Character agent);
    }

    @FunctionalInterface
    public interface DelayedActionScheduler {
        void schedule(long delayMs, Runnable action);
    }

    @FunctionalInterface
    public interface AgentSpeaker {
        void say(Character agent, String text);
    }

    @FunctionalInterface
    public interface TransferGreetingSupplier extends Supplier<String> {
    }

    public static String transferAgent(int leaderCharId,
                                       Character leader,
                                       String agentName,
                                       String targetName,
                                       Hooks hooks) {
        List<AgentRuntimeEntry> entries = hooks.entriesByLeader().entries(leaderCharId);
        if (entries == null) {
            return "You have no bots.";
        }
        AgentRuntimeEntry found = hooks.agentEntryByName().find(leaderCharId, agentName);
        if (found == null) {
            return "No bot named '" + agentName + "' in your group.";
        }

        Character target = hooks.mapCharacterByName().find(leader, targetName);
        if (target == null) {
            return "Player '" + targetName + "' not found in this map.";
        }
        if (target.getId() == leaderCharId) {
            return "That's you.";
        }

        Character agent = AgentBotRuntimeIdentityRuntime.bot(found);
        AgentAuthorizationResult auth = hooks.transferAuthorization().authorize(
                target,
                new AgentResolvedCharacter(
                        AgentBotRuntimeIdentityRuntime.botId(found),
                        AgentBotRuntimeIdentityRuntime.botName(found),
                        AgentBotRuntimeIdentityRuntime.botAccountId(found),
                        agent));
        if (!auth.allowed()) {
            return auth.failureMessage();
        }

        entries.remove(found);
        hooks.scheduledTaskCanceler().cancel(found);
        hooks.agentStopper().stop(found);

        hooks.agentRegistrar().register(target.getId(), target, agent);
        hooks.delayedActionScheduler().schedule(
                hooks.transferGreetingDelayMs().getAsLong(),
                () -> hooks.agentSpeaker().say(agent, hooks.transferGreetingSupplier().get()));
        return null;
    }
}
