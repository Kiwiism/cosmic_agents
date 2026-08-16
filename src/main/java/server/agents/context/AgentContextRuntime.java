package server.agents.context;

import client.Character;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;
import server.agents.runtime.AgentRelationshipState;
import server.agents.runtime.AgentRuntimeEntry;

/** Read-only projection of identity, personality, and relationship context. */
public final class AgentContextRuntime {
    private AgentContextRuntime() {
    }

    public static AgentContextSnapshot snapshot(AgentRuntimeEntry entry) {
        if (entry == null || entry.bot() == null) {
            return AgentContextSnapshot.empty();
        }
        Character agent = entry.bot();
        AgentRelationshipState relationships = entry.relationshipState();
        Character interactionTarget = relationships.interactionTarget();
        AgentPersonalityState personality = entry.capabilityStates()
                .find(AgentPersonalityState.STATE_KEY).orElse(null);
        AgentPersonalityProfile profile = personality == null ? null : personality.profile();
        return new AgentContextSnapshot(
                agent.getId(), agent.getName(),
                profile == null ? "" : profile.profileId(),
                profile == null ? 0 : profile.profileVersion(),
                personality == null ? 0L : personality.behaviorSeed(),
                personality != null && personality.presentationEnabled(),
                interactionTarget == null ? 0 : interactionTarget.getId(),
                relationships.cohortId(), relationships.formationId());
    }
}
