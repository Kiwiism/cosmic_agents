package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.plans.AgentTask;

import java.awt.Point;
import java.util.function.IntFunction;

public final class AgentScriptTaskCompletionService {
    private AgentScriptTaskCompletionService() {
    }

    public static boolean isComplete(AgentRuntimeEntry entry,
                                     AgentTask task,
                                     int normalMoveArrivalDistance,
                                     IntFunction<Character> followTargetResolver) {
        return switch (task.type()) {
            case MOVE_TO -> !AgentBotMoveTargetStateRuntime.hasMoveTarget(entry)
                    || isNear(AgentBotRuntimeIdentityRuntime.botPosition(entry), task.point(),
                    task.precise() ? 8 : normalMoveArrivalDistance);
            case FOLLOW_UNTIL_NEAR -> {
                Character target = followTargetResolver.apply(task.targetCharacterId());
                yield target != null
                        && AgentBotRuntimeIdentityRuntime.botMapId(entry) == target.getMapId()
                        && isNear(AgentBotRuntimeIdentityRuntime.botPosition(entry), target.getPosition(), task.nearPx());
            }
            case FOLLOW_OWNER, FOLLOW_TARGET, GRIND, STOP, DROP_ITEM -> true;
        };
    }

    private static boolean isNear(Point source, Point target, int distance) {
        return source != null && target != null
                && Math.abs(source.x - target.x) <= distance
                && Math.abs(source.y - target.y) <= distance;
    }
}
