package server.physics.foothold;

/**
 * Immutable platform segment translated from Journey Foothold.h/Foothold.cpp.
 * Source revision: bc0234fe7c7f53322453e7bdd79564d9aca4cd8b (AGPL-3.0-or-later).
 */
public record FootholdSegment(
        int id,
        int previousId,
        int nextId,
        int layer,
        int zMass,
        boolean forbidFallDown,
        double x1,
        double y1,
        double x2,
        double y2) {

    public FootholdSegment {
        if (id <= 0 || !(Double.isFinite(x1) && Double.isFinite(y1)
                && Double.isFinite(x2) && Double.isFinite(y2))) {
            throw new IllegalArgumentException("invalid foothold segment");
        }
    }

    public double left() { return Math.min(x1, x2); }
    public double right() { return Math.max(x1, x2); }
    public double top() { return Math.min(y1, y2); }
    public double bottom() { return Math.max(y1, y2); }
    public boolean wall() { return x1 == x2; }
    public boolean floor() { return y1 == y2; }
    public boolean leftEdge() { return previousId == 0; }
    public boolean rightEdge() { return nextId == 0; }
    public boolean containsX(double x) { return x >= left() && x <= right(); }
    public boolean containsY(double y) { return y >= top() && y <= bottom(); }

    public double slope() {
        return wall() ? 0.0 : (y2 - y1) / (x2 - x1);
    }

    public double groundY(double x) {
        return floor() ? y1 : slope() * (x - x1) + y1;
    }

    public boolean blocks(double verticalTop, double verticalBottom) {
        return wall() && top() <= verticalBottom && bottom() >= verticalTop;
    }
}
