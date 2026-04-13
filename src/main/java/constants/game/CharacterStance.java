package constants.game;

public final class CharacterStance {
    public static final int WALK_RIGHT_STANCE = 2;
    public static final int WALK_LEFT_STANCE = 3;
    public static final int STAND_RIGHT_STANCE = 4;
    public static final int STAND_LEFT_STANCE = 5;
    public static final int JUMP_RIGHT_STANCE = 6;
    public static final int JUMP_LEFT_STANCE = 7;
    public static final int PRONE_RIGHT_STANCE = 10;
    public static final int PRONE_LEFT_STANCE = 11;
    public static final int PRONE_STANCE = PRONE_RIGHT_STANCE;
    public static final int LADDER_STANCE = 15;
    public static final int ROPE_STANCE = 16;
    public static final int DEAD_RIGHT_STANCE = 18;
    public static final int DEAD_LEFT_STANCE = 19;

    private CharacterStance() {
    }

    public static boolean isFacingLeft(int stance) {
        return stance == WALK_LEFT_STANCE
                || stance == STAND_LEFT_STANCE
                || stance == JUMP_LEFT_STANCE
                || stance == PRONE_LEFT_STANCE
                || stance == DEAD_LEFT_STANCE;
    }

    public static boolean isStanding(int stance) {
        return stance == STAND_RIGHT_STANCE || stance == STAND_LEFT_STANCE;
    }

    public static boolean isWalking(int stance) {
        return stance == WALK_RIGHT_STANCE || stance == WALK_LEFT_STANCE;
    }

    public static boolean isJumping(int stance) {
        return stance == JUMP_RIGHT_STANCE || stance == JUMP_LEFT_STANCE;
    }

    public static boolean isProne(int stance) {
        return stance == PRONE_RIGHT_STANCE || stance == PRONE_LEFT_STANCE;
    }

    public static boolean isClimbing(int stance) {
        return stance == ROPE_STANCE || stance == LADDER_STANCE;
    }

    public static boolean isDead(int stance) {
        return stance == DEAD_RIGHT_STANCE || stance == DEAD_LEFT_STANCE;
    }

}
