package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeHandle;

import java.util.List;

public final class AgentChatIngressService {
    private AgentChatIngressService() {
    }

    public record Hooks<E extends AgentRuntimeHandle>(PendingOfferRoute pendingOfferRoute,
                        RecruitRoute recruitRoute,
                        TransferRoute transferRoute,
                        FormationRoute formationRoute,
                        EntriesForLeader<E> entriesForLeader,
                        DismissRoute dismissRoute,
                        TargetedRoute<E> targetedRoute,
                        UntargetedRoute<E> untargetedRoute) {
    }

    @FunctionalInterface
    public interface PendingOfferRoute {
        boolean handle(Character speaker, String message);
    }

    @FunctionalInterface
    public interface RecruitRoute {
        boolean handle(Character leader, String message);
    }

    @FunctionalInterface
    public interface TransferRoute {
        boolean handle(Character leader, String message);
    }

    @FunctionalInterface
    public interface FormationRoute {
        boolean handle(Character leader, String message);
    }

    @FunctionalInterface
    public interface EntriesForLeader<E extends AgentRuntimeHandle> {
        List<E> entries(int leaderCharId);
    }

    @FunctionalInterface
    public interface DismissRoute {
        boolean handle(Character leader, String message);
    }

    @FunctionalInterface
    public interface TargetedRoute<E extends AgentRuntimeHandle> {
        boolean handle(Character leader, List<E> entries, String message, AgentReplyChannel channel);
    }

    @FunctionalInterface
    public interface UntargetedRoute<E extends AgentRuntimeHandle> {
        void handle(Character leader, List<E> entries, String message, AgentReplyChannel channel);
    }

    public static <E extends AgentRuntimeHandle> void handleChat(
            Character leader,
            String message,
            AgentReplyChannel channel,
            Hooks<E> hooks) {
        if (hooks.pendingOfferRoute().handle(leader, message)) {
            return;
        }
        if (hooks.recruitRoute().handle(leader, message)) {
            return;
        }
        if (hooks.transferRoute().handle(leader, message)) {
            return;
        }
        if (hooks.formationRoute().handle(leader, message)) {
            return;
        }

        List<E> entries = hooks.entriesForLeader().entries(leader.getId());
        if (entries == null || entries.isEmpty()) {
            return;
        }

        if (hooks.dismissRoute().handle(leader, message)) {
            return;
        }
        if (hooks.targetedRoute().handle(leader, entries, message, channel)) {
            return;
        }
        hooks.untargetedRoute().handle(leader, entries, message, channel);
    }
}
