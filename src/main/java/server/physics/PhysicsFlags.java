package server.physics;

/** Bit flags carried by a reusable physics body. */
public final class PhysicsFlags {
    public static final int NO_GRAVITY = 0x0001;
    public static final int TURN_AT_EDGES = 0x0002;
    public static final int CHECK_BELOW = 0x0004;

    private PhysicsFlags() {
    }
}
