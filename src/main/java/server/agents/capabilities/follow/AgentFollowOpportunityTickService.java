package server.agents.capabilities.follow;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentFollowOpportunityTickService {
    private AgentFollowOpportunityTickService() {
    }

    public record Result(boolean consumedTick, Point targetPos) {
    }

    public record Hooks(LocalOpportunityAttack localOpportunityAttack,
                        int followDistance) {
    }

    @FunctionalInterface
    public interface LocalOpportunityAttack {
        Result attack(AgentRuntimeEntry entry,
                      Character agent,
                      Point agentPosition,
                      Point currentTargetPosition,
                      Point followTargetPosition);
    }

    public static Result tickFollowOpportunity(AgentRuntimeEntry entry,
                                               Character agent,
                                               Point agentPosition,
                                               Point currentTargetPosition,
                                               Point followTargetPosition,
                                               Character followAnchor,
                                               boolean runAiTick,
                                               Hooks hooks) {
        if (!AgentModeStateRuntime.following(entry)
                || !runAiTick
                || AgentMovementStateRuntime.climbing(entry)
                || followAnchor == null
                || agent.getMapId() != followAnchor.getMapId()
                || Math.abs(agentPosition.x - followAnchor.getPosition().x) > hooks.followDistance() * 5) {
            return new Result(false, currentTargetPosition);
        }

        return hooks.localOpportunityAttack().attack(
                entry,
                agent,
                agentPosition,
                currentTargetPosition,
                followTargetPosition);
    }
}
