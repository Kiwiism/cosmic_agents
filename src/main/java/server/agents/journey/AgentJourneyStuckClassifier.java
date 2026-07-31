package server.agents.journey;

import java.awt.Point;
import java.util.List;

/** Pure classifiers shared by the live projection and deterministic tests. */
final class AgentJourneyStuckClassifier {
    private AgentJourneyStuckClassifier() {
    }

    static boolean hasAlternatingMapLoop(List<Integer> maps, int required) {
        if (maps == null || required < 4 || maps.size() < required) {
            return false;
        }
        int start = maps.size() - required;
        int first = maps.get(start);
        int second = maps.get(start + 1);
        if (first == second) {
            return false;
        }
        for (int index = start; index < maps.size(); index++) {
            int expected = (index - start) % 2 == 0 ? first : second;
            if (maps.get(index) != expected) {
                return false;
            }
        }
        return true;
    }

    static boolean hasLocalPositionOscillation(List<Point> positions) {
        if (positions == null || positions.size() < 8) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int movement = 0;
        Point previous = null;
        for (Point position : positions) {
            minX = Math.min(minX, position.x);
            maxX = Math.max(maxX, position.x);
            minY = Math.min(minY, position.y);
            maxY = Math.max(maxY, position.y);
            if (previous != null) {
                movement += Math.abs(position.x - previous.x)
                        + Math.abs(position.y - previous.y);
            }
            previous = position;
        }
        return maxX - minX <= 80 && maxY - minY <= 80 && movement >= 80;
    }
}
