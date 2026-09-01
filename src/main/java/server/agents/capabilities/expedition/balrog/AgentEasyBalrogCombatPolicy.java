package server.agents.capabilities.expedition.balrog;

import java.awt.Point;

/** Authored Easy Balrog formations; legality still comes from normal combat planning. */
final class AgentEasyBalrogCombatPolicy {
    private static final int[] HEAD_X_BY_ORDINAL = {
            220, 210, 200, 10, 35, 60, 85, 110, 135, 190, 180, 155
    };
    private static final int CLAW_FIRST_X = 360;
    private static final int CLAW_SPACING_X = 20;
    private static final int CLAW_Y = 258;
    private static final int HEAD_Y = -70;
    private static final int ARRIVAL_X_PX = 14;
    private static final int ARRIVAL_Y_PX = 20;

    private AgentEasyBalrogCombatPolicy() {
    }

    static Point clawAnchor(int ordinal) {
        requireOrdinal(ordinal);
        return new Point(CLAW_FIRST_X + ordinal * CLAW_SPACING_X, CLAW_Y);
    }

    static Point headAnchor(int ordinal) {
        requireOrdinal(ordinal);
        return new Point(HEAD_X_BY_ORDINAL[ordinal], HEAD_Y);
    }

    static boolean atAnchor(Point position, Point anchor) {
        return position != null && anchor != null
                && Math.abs(position.x - anchor.x) <= ARRIVAL_X_PX
                && Math.abs(position.y - anchor.y) <= ARRIVAL_Y_PX;
    }

    private static void requireOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= AgentBalrogDefinition.ROSTER_SIZE) {
            throw new IllegalArgumentException("an Easy Balrog roster slot is required");
        }
    }
}
