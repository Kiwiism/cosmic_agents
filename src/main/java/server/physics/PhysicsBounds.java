package server.physics;

/** Inclusive movement bounds used by a physics terrain. */
public record PhysicsBounds(double left, double right, double top, double bottom) {
    public PhysicsBounds {
        if (!(Double.isFinite(left) && Double.isFinite(right)
                && Double.isFinite(top) && Double.isFinite(bottom))
                || left > right || top > bottom) {
            throw new IllegalArgumentException("invalid physics bounds");
        }
    }

    public double clampX(double x) {
        return Math.max(left, Math.min(right, x));
    }

    public double clampY(double y) {
        return Math.max(top, Math.min(bottom, y));
    }
}
