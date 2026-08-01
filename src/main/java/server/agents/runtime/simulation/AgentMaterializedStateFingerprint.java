package server.agents.runtime.simulation;

import client.Character;

import java.awt.Point;

/** Exact-world state that mutation-free abstract execution must preserve. */
public record AgentMaterializedStateFingerprint(
        int mapId,
        int x,
        int y,
        int chairId,
        int mesos,
        int level,
        int experience) {

    public static AgentMaterializedStateFingerprint capture(Character agent) {
        if (agent == null || agent.getPosition() == null) {
            return null;
        }
        Point position = agent.getPosition();
        return new AgentMaterializedStateFingerprint(
                agent.getMapId(),
                position.x,
                position.y,
                agent.getChair(),
                agent.getMeso(),
                agent.getLevel(),
                agent.getExp());
    }
}
