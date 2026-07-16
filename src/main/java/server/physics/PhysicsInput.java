package server.physics;

/** Force intention supplied to one fixed physics step. */
public record PhysicsInput(double horizontalForce, double verticalForce,
                           boolean turnAtEdges, boolean checkBelow) {
    public static final PhysicsInput NONE = new PhysicsInput(0.0, 0.0, false, false);

    public PhysicsInput {
        if (!Double.isFinite(horizontalForce) || !Double.isFinite(verticalForce)) {
            throw new IllegalArgumentException("physics force must be finite");
        }
    }
}
