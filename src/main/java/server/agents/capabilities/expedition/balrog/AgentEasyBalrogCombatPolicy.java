package server.agents.capabilities.expedition.balrog;

import server.agents.field.AgentBalrogTestFixtureService;

import java.awt.Point;

/** The one authored Easy Balrog exception: head stations on the upper-left platform. */
final class AgentEasyBalrogCombatPolicy {
    private static final int RANGED_HEAD_FIRST_X = -100;
    // The WZ head body begins near x=274. Keep close-range jobs at the right edge of the
    // upper-left foothold (which ends near x=230) so their ordinary skill rectangles intersect it.
    private static final int MELEE_HEAD_FIRST_X = 198;
    private static final int RANGED_HEAD_SPACING_X = 12;
    private static final int MELEE_HEAD_SPACING_X = 2;
    private static final int HEAD_Y = -70;
    private static final int ARRIVAL_X_PX = 14;
    private static final int ARRIVAL_Y_PX = 20;

    private AgentEasyBalrogCombatPolicy() {
    }

    static boolean isRanged(AgentBalrogTestFixtureService.WeaponClass weaponClass) {
        return switch (weaponClass) {
            case WAND, STAFF, BOW, CROSSBOW, CLAW, GUN -> true;
            default -> false;
        };
    }

    static Point headAnchor(int ordinal, boolean ranged) {
        requireOrdinal(ordinal);
        int x = ranged
                ? RANGED_HEAD_FIRST_X + ordinal * RANGED_HEAD_SPACING_X
                : MELEE_HEAD_FIRST_X + ordinal * MELEE_HEAD_SPACING_X;
        return new Point(x, HEAD_Y);
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
