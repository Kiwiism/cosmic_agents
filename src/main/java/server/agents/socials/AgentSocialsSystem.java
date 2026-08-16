package server.agents.socials;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatSocialFlow;
import server.agents.capabilities.social.AgentSocialRuntime;
import server.agents.context.AgentContextRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.AgentActivityTick;
import server.agents.runtime.interaction.AgentInteractionLeaseRuntime;
import server.agents.runtime.interaction.AgentInteractionLeaseState;

/** Stable Socials boundary for chat, trade, fame, and bounded interaction leases. */
public final class AgentSocialsSystem {
    private AgentSocialsSystem() {
    }

    public static AgentChatSocialFlow.SocialCallbacks socialCallbacks(AgentRuntimeEntry entry) {
        return AgentSocialRuntime.socialCallbacks(entry);
    }

    public static String beginChat(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        int participantId = AgentContextRuntime.snapshot(entry).interactionTargetCharacterId();
        return AgentInteractionLeaseRuntime.beginChat(entry, agent, participantId, nowMs);
    }

    public static void completeChat(AgentRuntimeEntry entry) {
        AgentInteractionLeaseRuntime.complete(entry, AgentInteractionLeaseState.Type.CHAT);
    }

    public static void reconcileTrade(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentInteractionLeaseRuntime.reconcileTrade(entry, agent, nowMs);
    }

    public static boolean interactionActive(AgentRuntimeEntry entry) {
        return AgentInteractionLeaseRuntime.active(entry);
    }

    public static AgentActivityTick tickInteraction(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        return AgentInteractionLeaseRuntime.tick(entry, agent, nowMs);
    }

    public static void cancelInteraction(
            AgentRuntimeEntry entry, Character agent, String reason, long nowMs) {
        AgentInteractionLeaseRuntime.cancel(entry, agent, reason, nowMs);
    }
}
