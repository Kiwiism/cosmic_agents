package server.agents.runtime;

import client.Character;

/**
 * Mutable, optional relationships for one Agent session.
 *
 * <p>None of these relationships owns the Agent. An Agent may have no human
 * interaction target and no follow target while remaining fully active.</p>
 */
public final class AgentRelationshipState {
    private volatile Character followTarget;
    private volatile Character interactionTarget;
    private volatile long cohortId;
    private volatile long formationId;

    public AgentRelationshipState(Character initialInteractionTarget, long cohortId, long formationId) {
        this.followTarget = initialInteractionTarget;
        this.interactionTarget = initialInteractionTarget;
        this.cohortId = cohortId;
        this.formationId = formationId;
    }

    public Character followTarget() {
        return followTarget;
    }

    public void setFollowTarget(Character followTarget) {
        this.followTarget = followTarget;
    }

    public Character interactionTarget() {
        return interactionTarget;
    }

    public void setInteractionTarget(Character interactionTarget) {
        this.interactionTarget = interactionTarget;
    }

    public long cohortId() {
        return cohortId;
    }

    public void setCohortId(long cohortId) {
        this.cohortId = cohortId;
    }

    public long formationId() {
        return formationId;
    }

    public void setFormationId(long formationId) {
        this.formationId = formationId;
    }
}
