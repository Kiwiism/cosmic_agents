package server.physics;

/** Force intention supplied to one fixed physics step. */
public record PhysicsInput(double horizontalForce, double verticalForce,
                           boolean turnAtEdges, boolean checkBelow,
                           double leftEdgeInset, double rightEdgeInset) {
    public static final PhysicsInput NONE = new PhysicsInput(0.0, 0.0, false, false, 0.0, 0.0);

    public PhysicsInput(double horizontalForce, double verticalForce,
                        boolean turnAtEdges, boolean checkBelow) {
        this(horizontalForce, verticalForce, turnAtEdges, checkBelow, 0.0, 0.0);
    }

    public PhysicsInput {
        if (!Double.isFinite(horizontalForce) || !Double.isFinite(verticalForce)
                || !Double.isFinite(leftEdgeInset) || !Double.isFinite(rightEdgeInset)
                || leftEdgeInset < 0.0 || rightEdgeInset < 0.0) {
            throw new IllegalArgumentException(
                    "physics force must be finite and edge insets finite and non-negative");
        }
    }
}
