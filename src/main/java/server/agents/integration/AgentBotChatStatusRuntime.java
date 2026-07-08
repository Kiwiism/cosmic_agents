package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned status facade over temporary bot-side status state and side
 * effects. Legacy bot package callers should delegate here until their call
 * sites move into Agent modules.
 */
public final class AgentBotChatStatusRuntime {
    private AgentBotChatStatusRuntime() {
    }

    public static void markOwnerActive(AgentRuntimeEntry entry) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        AgentChatStatusRuntime.markActive(
                AgentBotStatusRuntime.statusState(entry),
                owner != null ? owner.getPosition() : null,
                System.currentTimeMillis());
    }

    public static void checkBotStatus(AgentRuntimeEntry entry, Character bot) {
        AgentBotBuildStatusRuntime.checkBuildStatus(entry, bot);
    }

    public static void announceOwnerReturnedFromOffline(AgentRuntimeEntry entry) {
        AgentChatStatusRuntime.announceOfflineReturn(
                AgentBotStatusRuntime.offlineReturnActions(AgentRuntimeIdentityRuntime.bot(entry)));
    }

    public static void tickAfkCheck(AgentRuntimeEntry entry, Character owner) {
        AgentChatStatusRuntime.tickAfkCheck(
                AgentBotStatusRuntime.afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                AgentBotStatusRuntime.afkReturnActions(entry));
    }

    public static void prepareActiveModeEntry(AgentRuntimeEntry entry) {
        AgentChatStatusRuntime.prepareActiveMode(AgentBotActiveModeRuntime.activeModeActions(entry));
    }

    public static boolean isOwnerIdle(AgentRuntimeEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(AgentBotStatusRuntime.statusState(entry));
    }

    public static int randomFidgetExpression() {
        return AgentChatStatusRuntime.randomFidgetExpression();
    }
}
