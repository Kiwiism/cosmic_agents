package server.physics;

import server.physics.foothold.FootholdSegment;

/** Immutable terrain queries needed by the general physics kernel. */
public interface PhysicsTerrain {
    FootholdSegment foothold(int id);

    FootholdSegment findBelow(double x, double y);

    Iterable<FootholdSegment> footholdsNear(double x, double radius);

    double wallBoundary(int footholdId, boolean left, double y);

    double edgeBoundary(int footholdId, boolean left);

    PhysicsBounds bounds();
}
