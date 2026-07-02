package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentLiveTickContextService {
    private AgentLiveTickContextService() {
    }

    public record Context(Point agentPosition,
                          Character followAnchor,
                          AgentTargetSnapshot targetSnapshot,
                          Point targetPosition) {
    }

    public record Hooks(MovementProfileRefresher movementProfileRefresher,
                        FollowAnchorResolver followAnchorResolver,
                        TargetSnapshotCapture targetSnapshotCapture,
                        LeaderMotionObserver leaderMotionObserver,
                        LeaderPositionRememberer leaderPositionRememberer,
                        MapChangeCleanup farmAnchorCleanup,
                        MapChangeCleanup patrolCleanup,
                        FollowActionWindowCleanup followActionWindowCleanup) {
    }

    @FunctionalInterface
    public interface MovementProfileRefresher {
        void refresh(BotEntry entry);
    }

    @FunctionalInterface
    public interface FollowAnchorResolver {
        Character resolve(BotEntry entry, Character leader);
    }

    @FunctionalInterface
    public interface TargetSnapshotCapture {
        AgentTargetSnapshot capture(BotEntry entry);
    }

    @FunctionalInterface
    public interface LeaderMotionObserver {
        void update(BotEntry entry, Point rawLeaderPosition);
    }

    @FunctionalInterface
    public interface LeaderPositionRememberer {
        void remember(BotEntry entry, Point rawLeaderPosition);
    }

    @FunctionalInterface
    public interface MapChangeCleanup {
        void clear(BotEntry entry, Character agent);
    }

    @FunctionalInterface
    public interface FollowActionWindowCleanup {
        void clearIfSettled(BotEntry entry, Point agentPosition, AgentTargetSnapshot targetSnapshot);
    }

    public static Context prepareLiveTickContext(BotEntry entry,
                                                 Character agent,
                                                 Character leader,
                                                 Hooks hooks) {
        hooks.movementProfileRefresher().refresh(entry);
        Point agentPosition = agent.getPosition();
        Character followAnchor = hooks.followAnchorResolver().resolve(entry, leader);
        AgentTargetSnapshot targetSnapshot = hooks.targetSnapshotCapture().capture(entry);
        Point rawLeaderPosition = targetSnapshot.rawOwnerPos();
        hooks.leaderMotionObserver().update(entry, rawLeaderPosition);
        hooks.leaderPositionRememberer().remember(entry, rawLeaderPosition);
        hooks.farmAnchorCleanup().clear(entry, agent);
        hooks.patrolCleanup().clear(entry, agent);
        hooks.followActionWindowCleanup().clearIfSettled(entry, agentPosition, targetSnapshot);
        return new Context(agentPosition, followAnchor, targetSnapshot, targetSnapshot.primaryTargetPos());
    }
}
