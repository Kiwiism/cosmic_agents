package server.agents.capabilities.combat;

import java.awt.Point;
import java.awt.Rectangle;

public final class AgentMobTouchPolicy {
    static final int TUTORIAL_JR_SENTINEL_ID = 9300018;

    private AgentMobTouchPolicy() {
    }

    public static Rectangle botTouchSweepBounds(Point previousPos, Point currentPos, int sweepHeight) {
        Point from = previousPos == null ? currentPos : previousPos;
        if (from == null || currentPos == null) {
            return new Rectangle(0, 0, 1, 1);
        }

        int left = Math.min(from.x, currentPos.x);
        int right = Math.max(from.x, currentPos.x);
        int top = Math.min(from.y, currentPos.y) - sweepHeight;
        int bottom = Math.max(from.y, currentPos.y);
        return inclusiveRectangle(left, top, right, bottom);
    }

    public static boolean lowerHalfIntersects(Rectangle mobBounds, Rectangle botBounds) {
        if (mobBounds == null || botBounds == null) {
            return false;
        }
        int lowerHeight = Math.max(1, mobBounds.height / 2);
        Rectangle mobLowerHalf = new Rectangle(mobBounds.x, mobBounds.y + mobBounds.height - lowerHeight,
                mobBounds.width, lowerHeight);
        return mobLowerHalf.intersects(botBounds);
    }

    public static Rectangle inclusiveRectangle(int left, int top, int right, int bottom) {
        return new Rectangle(left, top, Math.max(1, right - left + 1), Math.max(1, bottom - top + 1));
    }

    public static boolean ignoresTouchDamage(int mobId) {
        return mobId == TUTORIAL_JR_SENTINEL_ID;
    }
}
