package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;

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
        if (!AgentBotModeStateRuntime.following(entry)
                || !runAiTick
                || AgentBotMovementStateRuntime.climbing(entry)
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
