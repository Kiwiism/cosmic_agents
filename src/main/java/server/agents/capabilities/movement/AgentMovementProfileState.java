package server.agents.capabilities.movement;

import client.Character;

/**
 * Mutable movement-profile state for one live Agent runtime.
 */
public final class AgentMovementProfileState {
    private AgentMovementProfile profile = AgentMovementProfile.base();

    public AgentMovementProfile profile() {
        return profile != null ? profile : AgentMovementProfile.base();
    }

    public void setProfile(AgentMovementProfile profile) {
        this.profile = profile != null ? profile : AgentMovementProfile.base();
    }

    public void refreshFrom(Character character) {
        setProfile(AgentMovementProfile.fromCharacter(character));
    }
}
