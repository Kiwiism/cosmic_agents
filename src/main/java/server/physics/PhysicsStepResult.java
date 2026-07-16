package server.physics;

/** Observable transitions produced by one fixed physics step. */
public record PhysicsStepResult(
        double x,
        double y,
        double velocityX,
        double velocityY,
        int footholdId,
        int footholdLayer,
        boolean grounded,
        boolean landed,
        boolean leftGround,
        boolean hitWall,
        boolean reachedEdge,
        boolean changedFoothold,
        boolean recovered,
        boolean unsupportedMode) {
}
