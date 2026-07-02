package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;

import java.util.List;

public final class AgentChatIngressService {
    private AgentChatIngressService() {
    }

    public record Hooks(PendingOfferRoute pendingOfferRoute,
                        RecruitRoute recruitRoute,
                        TransferRoute transferRoute,
                        FormationRoute formationRoute,
                        EntriesForLeader entriesForLeader,
                        DismissRoute dismissRoute,
                        TargetedRoute targetedRoute,
                        UntargetedRoute untargetedRoute) {
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
    public interface EntriesForLeader {
        List<BotEntry> entries(int leaderCharId);
    }

    @FunctionalInterface
    public interface DismissRoute {
        boolean handle(Character leader, String message);
    }

    @FunctionalInterface
    public interface TargetedRoute {
        boolean handle(Character leader, List<BotEntry> entries, String message, AgentReplyChannel channel);
    }

    @FunctionalInterface
    public interface UntargetedRoute {
        void handle(Character leader, List<BotEntry> entries, String message, AgentReplyChannel channel);
    }

    public static void handleChat(Character leader, String message, AgentReplyChannel channel, Hooks hooks) {
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

        List<BotEntry> entries = hooks.entriesForLeader().entries(leader.getId());
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
