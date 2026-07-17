package server.agents.integration;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** Boundary access to optional Agent relationships. */
public final class AgentRelationshipRuntime {
    private AgentRelationshipRuntime() {
    }

    public static Character followTarget(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.relationshipState().followTarget();
    }

    public static void setFollowTarget(AgentRuntimeEntry entry, Character target) {
        if (entry != null) {
            entry.relationshipState().setFollowTarget(target);
        }
    }

    public static Character interactionTarget(AgentRuntimeEntry entry) {
        return entry == null ? null : entry.relationshipState().interactionTarget();
    }

    public static void setInteractionTarget(AgentRuntimeEntry entry, Character target) {
        if (entry != null) {
            entry.relationshipState().setInteractionTarget(target);
        }
    }

    public static long cohortId(AgentRuntimeEntry entry) {
        return entry == null ? 0L : entry.relationshipState().cohortId();
    }

    public static void setCohortId(AgentRuntimeEntry entry, long cohortId) {
        if (entry != null) {
            entry.relationshipState().setCohortId(cohortId);
        }
    }

    public static long formationId(AgentRuntimeEntry entry) {
        return entry == null ? 0L : entry.relationshipState().formationId();
    }

    public static void setFormationId(AgentRuntimeEntry entry, long formationId) {
        if (entry != null) {
            entry.relationshipState().setFormationId(formationId);
        }
    }
}
