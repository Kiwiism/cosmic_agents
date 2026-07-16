package server.physics;

/**
 * Constants translated from Journey Physics.cpp.
 * Source: https://github.com/nmnsnv/maplestory-wasm at
 * bc0234fe7c7f53322453e7bdd79564d9aca4cd8b, AGPL-3.0-or-later.
 */
public final class MaplePhysicsConstants {
    public static final int STEP_MS = 8;
    public static final double GRAVITY = 0.14;
    public static final double SWIMMING_GRAVITY = 0.03;
    public static final double GROUND_FRICTION = 0.30;
    public static final double SLOPE_FACTOR = 0.10;
    public static final double GROUND_SLIP = 3.00;
    public static final double FLYING_FRICTION = 0.05;
    public static final double SWIMMING_FRICTION = 0.08;
    public static final double STOP_EPSILON = 0.10;

    private MaplePhysicsConstants() {
    }
}
