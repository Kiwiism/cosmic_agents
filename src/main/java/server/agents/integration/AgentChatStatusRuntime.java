package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned status facade over temporary bot-side status state and side
 * effects. Legacy bot package callers should delegate here until their call
 * sites move into Agent modules.
 */
public final class AgentChatStatusRuntime {
    private AgentChatStatusRuntime() {
    }

    public static void markOwnerActive(AgentRuntimeEntry entry) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        server.agents.capabilities.dialogue.AgentChatStatusRuntime.markActive(
                AgentStatusRuntime.statusState(entry),
                owner != null ? owner.getPosition() : null,
                System.currentTimeMillis());
    }

    public static void checkBotStatus(AgentRuntimeEntry entry, Character bot) {
        AgentBotBuildStatusRuntime.checkBuildStatus(entry, bot);
    }

    public static void announceOwnerReturnedFromOffline(AgentRuntimeEntry entry) {
        server.agents.capabilities.dialogue.AgentChatStatusRuntime.announceOfflineReturn(
                AgentStatusRuntime.offlineReturnActions(AgentRuntimeIdentityRuntime.bot(entry)));
    }

    public static void tickAfkCheck(AgentRuntimeEntry entry, Character owner) {
        server.agents.capabilities.dialogue.AgentChatStatusRuntime.tickAfkCheck(
                AgentStatusRuntime.afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                AgentStatusRuntime.afkReturnActions(entry));
    }

    public static void prepareActiveModeEntry(AgentRuntimeEntry entry) {
        server.agents.capabilities.dialogue.AgentChatStatusRuntime.prepareActiveMode(AgentBotActiveModeRuntime.activeModeActions(entry));
    }

    public static boolean isOwnerIdle(AgentRuntimeEntry entry) {
        return server.agents.capabilities.dialogue.AgentChatStatusRuntime.isOwnerIdle(AgentStatusRuntime.statusState(entry));
    }

    public static int randomFidgetExpression() {
        return server.agents.capabilities.dialogue.AgentChatStatusRuntime.randomFidgetExpression();
    }
}
