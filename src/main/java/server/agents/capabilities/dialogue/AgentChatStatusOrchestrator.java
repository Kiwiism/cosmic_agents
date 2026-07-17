package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.capabilities.build.AgentBuildStatusRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.capabilities.dialogue.AgentStatusRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned chat status orchestration over runtime state adapters. Live
 * identity lookup, active-mode actions, and expression/reply side effects stay
 * behind integration boundaries.
 */
public final class AgentChatStatusOrchestrator {
    private AgentChatStatusOrchestrator() {
    }

    public static void markOwnerActive(AgentRuntimeEntry entry) {
        Character owner = AgentRelationshipRuntime.interactionTarget(entry);
        AgentChatStatusRuntime.markActive(
                AgentStatusStateRuntime.statusState(entry),
                owner != null ? owner.getPosition() : null,
                System.currentTimeMillis());
    }

    public static void checkBotStatus(AgentRuntimeEntry entry, Character bot) {
        AgentBuildStatusRuntime.checkBuildStatus(entry, bot);
    }

    public static void announceOwnerReturnedFromOffline(AgentRuntimeEntry entry) {
        AgentChatStatusRuntime.announceOfflineReturn(
                AgentStatusRuntime.offlineReturnActions(entry));
    }

    public static void tickAfkCheck(AgentRuntimeEntry entry, Character owner) {
        AgentChatStatusRuntime.tickAfkCheck(
                AgentStatusStateRuntime.afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                AgentStatusRuntime.afkReturnActions(entry));
    }

    public static void prepareActiveModeEntry(AgentRuntimeEntry entry) {
        AgentChatStatusRuntime.prepareActiveMode(AgentActiveModeRuntime.activeModeActions(entry));
    }

    public static boolean isOwnerIdle(AgentRuntimeEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(AgentStatusStateRuntime.statusState(entry));
    }

    public static int randomFidgetExpression() {
        return AgentChatStatusRuntime.randomFidgetExpression();
    }
}
