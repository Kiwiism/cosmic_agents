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
    private static final int RANGED_HEAD_HEIGHT = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogCombatPolicy.RANGED_HEAD_HEIGHT");
    private static final int MELEE_HEAD_HEIGHT = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogCombatPolicy.MELEE_HEAD_HEIGHT");
    private static final int BATTLE_LEFT_X_OFFSET = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogCombatPolicy.BATTLE_LEFT_X_OFFSET");
    private static final int BATTLE_MAX_X = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogCombatPolicy.BATTLE_MAX_X");
    // The released left claw occupies x=47..293 in the canonical WZ frame. Move the
    // initial formation beyond it before normal combat so its later appearance cannot
    // materialize around ranged jobs holding their firing distance.
    private static final int INITIAL_CLAW_SAFE_FIRST_X = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogCombatPolicy.INITIAL_CLAW_SAFE_FIRST_X");
    private static final int INITIAL_CLAW_SAFE_SPACING_X = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogCombatPolicy.INITIAL_CLAW_SAFE_SPACING_X");
    private static final int LOWER_PLATFORM_Y = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogCombatPolicy.LOWER_PLATFORM_Y");
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
        return new Point(x, -(ranged ? RANGED_HEAD_HEIGHT : MELEE_HEAD_HEIGHT));
    }

    static Point initialClawSafePoint(int ordinal) {
        requireOrdinal(ordinal);
        return new Point(
                INITIAL_CLAW_SAFE_FIRST_X + ordinal * INITIAL_CLAW_SAFE_SPACING_X,
                LOWER_PLATFORM_Y);
    }

    static boolean needsInitialClawStaging(Point position) {
        return position == null || position.x < INITIAL_CLAW_SAFE_FIRST_X;
    }

    static int battleMinX() {
        return -BATTLE_LEFT_X_OFFSET;
    }

    static int battleMaxX() {
        return BATTLE_MAX_X;
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
