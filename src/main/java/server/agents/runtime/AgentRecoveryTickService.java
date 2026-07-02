package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotShopStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentRecoveryTickService {
    private AgentRecoveryTickService() {
    }

    public record Hooks(FollowMapSync followMapSync,
                        PartyRecovery partyRecovery,
                        TargetRecovery targetRecovery) {
    }

    @FunctionalInterface
    public interface FollowMapSync {
        boolean sync(BotEntry entry, Character agent, Character followAnchor);
    }

    @FunctionalInterface
    public interface PartyRecovery {
        boolean recover(BotEntry entry, Character agent, Character followAnchor);
    }

    @FunctionalInterface
    public interface TargetRecovery {
        boolean recover(BotEntry entry, Character agent, Point targetPosition);
    }

    public static boolean tickRecovery(BotEntry entry,
                                       Character agent,
                                       Character followAnchor,
                                       Point targetPosition,
                                       Hooks hooks) {
        if (!AgentBotShopStateRuntime.shopVisitPending(entry)
                && hooks.followMapSync().sync(entry, agent, followAnchor)) {
            return true;
        }
        if (hooks.partyRecovery().recover(entry, agent, followAnchor)) {
            return true;
        }
        return hooks.targetRecovery().recover(entry, agent, targetPosition);
    }
}
