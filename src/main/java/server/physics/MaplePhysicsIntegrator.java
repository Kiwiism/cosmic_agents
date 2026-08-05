package server.physics;

import server.physics.foothold.FootholdSegment;

/**
 * Fixed-step Java translation of Journey Physics.cpp and FootholdTree.cpp.
 * Upstream: nmnsnv/maplestory-wasm, revision
 * bc0234fe7c7f53322453e7bdd79564d9aca4cd8b, AGPL-3.0-or-later.
 */
public final class MaplePhysicsIntegrator {
    private static final double POSITION_EPSILON = 1.0e-7;
    private static final double SUPPORT_SWEEP_STEP_PX = 1.0;
    private static final double SEAM_X_TOLERANCE_PX = 2.0;
    private static final double SEAM_Y_TOLERANCE_PX = 3.0;
    private static final double RECONCILE_Y_TOLERANCE_PX = 4.0;

    public void beginGroundedKnockbackSupport(PhysicsBody body) {
        if (body == null) {
            throw new IllegalArgumentException("body is required");
        }
        body.lockGroundedSupport();
    }

    public void endGroundedKnockbackSupport(PhysicsBody body, PhysicsTerrain terrain) {
        if (body == null || terrain == null) {
            throw new IllegalArgumentException("body and terrain are required");
        }
        if (!body.groundedSupportLocked()) {
            return;
        }
        FootholdSegment current = terrain.foothold(body.footholdId());
        GroundSupport support = strictReconciliationSupport(body, terrain, current);
        body.clearGroundedSupportLock();
        if (support == null) {
            body.setGrounded(false);
            return;
        }
        body.setPosition(body.x(), support.groundY());
        body.setFoothold(support.foothold().id(), support.foothold().slope(),
                support.foothold().layer());
        body.setGrounded(true);
    }

    public PhysicsStepResult step(PhysicsBody body, PhysicsInput input, PhysicsTerrain terrain) {
        if (body == null || input == null || terrain == null) {
            throw new IllegalArgumentException("body, input, and terrain are required");
        }
        boolean recovered = false;
        if (!finite(body)) {
            recoverInvalid(body, terrain);
            recovered = true;
        }

        int oldFoothold = body.footholdId();
        boolean wasGrounded = body.grounded();
        recovered |= updateFoothold(body, terrain);

        if (input.turnAtEdges()) {
            body.setFlag(PhysicsFlags.TURN_AT_EDGES);
        } else {
            body.clearFlag(PhysicsFlags.TURN_AT_EDGES);
        }
        if (input.checkBelow()) {
            body.setFlag(PhysicsFlags.CHECK_BELOW);
        }

        boolean unsupported = false;
        switch (body.mode()) {
            case NORMAL -> moveNormal(body, input);
            case FLYING -> moveFlying(body, input);
            case SWIMMING -> moveSwimming(body, input);
            case FIXED -> {
                body.setAcceleration(0.0, 0.0);
                body.setVelocity(0.0, 0.0);
            }
            case ICE -> {
                body.setAcceleration(0.0, 0.0);
                body.setVelocity(0.0, 0.0);
                unsupported = true;
            }
        }

        Collision collision = limitMovement(body, input, terrain);
        body.setPosition(body.x() + body.velocityX(), body.y() + body.velocityY());
        if (!finite(body)) {
            recoverInvalid(body, terrain);
            recovered = true;
        }

        return new PhysicsStepResult(
                body.x(), body.y(), body.velocityX(), body.velocityY(),
                body.footholdId(), body.footholdLayer(), body.grounded(),
                !wasGrounded && body.grounded(), wasGrounded && !body.grounded(),
                collision.hitWall, collision.reachedEdge,
                oldFoothold != body.footholdId(), recovered, unsupported);
    }

    private static void moveNormal(PhysicsBody body, PhysicsInput input) {
        double accelerationX = 0.0;
        double accelerationY = 0.0;
        double velocityX = body.velocityX();
        double velocityY = body.velocityY();
        if (body.grounded()) {
            accelerationY += input.verticalForce();
            accelerationX += input.horizontalForce();
            if (accelerationX == 0.0
                    && velocityX < MaplePhysicsConstants.STOP_EPSILON
                    && velocityX > -MaplePhysicsConstants.STOP_EPSILON) {
                velocityX = 0.0;
            } else {
                double inertia = velocityX / MaplePhysicsConstants.GROUND_SLIP;
                double slope = Math.max(-0.5, Math.min(0.5, body.footholdSlope()));
                accelerationX -= (MaplePhysicsConstants.GROUND_FRICTION
                        + MaplePhysicsConstants.SLOPE_FACTOR * (1.0 - slope * inertia)) * inertia;
            }
        } else if (!body.hasFlag(PhysicsFlags.NO_GRAVITY)) {
            accelerationY += MaplePhysicsConstants.GRAVITY;
        }
        body.setAcceleration(accelerationX, accelerationY);
        body.setVelocity(velocityX + accelerationX, velocityY + accelerationY);
    }

    private static void moveFlying(PhysicsBody body, PhysicsInput input) {
        double accelerationX = input.horizontalForce()
                - MaplePhysicsConstants.FLYING_FRICTION * body.velocityX();
        double accelerationY = input.verticalForce()
                - MaplePhysicsConstants.FLYING_FRICTION * body.velocityY();
        double velocityX = body.velocityX() + accelerationX;
        double velocityY = body.velocityY() + accelerationY;
        if (accelerationX == 0.0 && Math.abs(velocityX) < MaplePhysicsConstants.STOP_EPSILON) {
            velocityX = 0.0;
        }
        if (accelerationY == 0.0 && Math.abs(velocityY) < MaplePhysicsConstants.STOP_EPSILON) {
            velocityY = 0.0;
        }
        body.setAcceleration(accelerationX, accelerationY);
        body.setVelocity(velocityX, velocityY);
    }

    private static void moveSwimming(PhysicsBody body, PhysicsInput input) {
        double accelerationX = input.horizontalForce()
                - MaplePhysicsConstants.SWIMMING_FRICTION * body.velocityX();
        double accelerationY = input.verticalForce()
                - MaplePhysicsConstants.SWIMMING_FRICTION * body.velocityY();
        if (!body.hasFlag(PhysicsFlags.NO_GRAVITY)) {
            accelerationY += MaplePhysicsConstants.SWIMMING_GRAVITY;
        }
        double velocityX = body.velocityX() + accelerationX;
        double velocityY = body.velocityY() + accelerationY;
        if (accelerationX == 0.0 && Math.abs(velocityX) < MaplePhysicsConstants.STOP_EPSILON) {
            velocityX = 0.0;
        }
        if (accelerationY == 0.0 && Math.abs(velocityY) < MaplePhysicsConstants.STOP_EPSILON) {
            velocityY = 0.0;
        }
        body.setAcceleration(accelerationX, accelerationY);
        body.setVelocity(velocityX, velocityY);
    }

    private static Collision limitMovement(PhysicsBody body, PhysicsInput input,
                                           PhysicsTerrain terrain) {
        boolean hitWall = false;
        boolean reachedEdge = false;
        double velocityX = body.velocityX();
        double velocityY = body.velocityY();
        if (velocityX != 0.0) {
            boolean left = velocityX < 0.0;
            double currentX = body.x();
            double nextX = currentX + velocityX;
            double boundary = terrain.wallBoundary(body.footholdId(), left, body.y() + velocityY);
            boolean collision = crosses(currentX, nextX, boundary, left);
            if (!collision && body.hasFlag(PhysicsFlags.TURN_AT_EDGES)) {
                boundary = terrain.edgeBoundary(body.footholdId(), left);
                boundary += left ? input.leftEdgeInset() : -input.rightEdgeInset();
                collision = left ? nextX <= boundary : nextX >= boundary;
                reachedEdge = collision;
            }
            if (collision) {
                body.setPosition(boundary, body.y());
                body.setVelocity(0.0, velocityY);
                body.clearFlag(PhysicsFlags.TURN_AT_EDGES);
                hitWall = !reachedEdge;
                velocityX = 0.0;
            }
        }

        velocityX = body.velocityX();
        if (body.grounded() && body.velocityY() == 0.0 && velocityX != 0.0
                && body.groundedSupportLocked()) {
            GroundSupport support = sweepLockedGround(body, terrain, velocityX);
            if (support != null) {
                body.setPosition(body.x(), support.groundY());
                body.setFoothold(support.foothold().id(), support.foothold().slope(),
                        support.foothold().layer());
            } else {
                // This is a genuine edge, not a broken seam. End the constraint immediately so
                // the next fixed step applies normal airborne gravity. The current step does not
                // perform another foothold lookup, so clearing here cannot snap to a floor below.
                body.clearGroundedSupportLock();
                body.setGrounded(false);
            }
        } else if (body.grounded() && body.velocityY() == 0.0 && velocityX != 0.0) {
            double destinationX = body.x() + velocityX;
            FootholdSegment foothold = connectedGroundAt(
                    terrain, body.footholdId(), destinationX, velocityX);
            if (foothold != null) {
                body.setPosition(body.x(), foothold.groundY(destinationX));
                body.setFoothold(foothold.id(), foothold.slope(), foothold.layer());
            }
        }

        velocityY = body.velocityY();
        if (velocityY != 0.0) {
            FootholdSegment foothold = terrain.foothold(body.footholdId());
            double currentY = body.y();
            double nextY = currentY + velocityY;
            if (foothold != null && !foothold.wall()) {
                double groundCurrent = foothold.groundY(body.x());
                double groundNext = foothold.groundY(body.x() + velocityX);
                if (currentY <= groundCurrent && nextY >= groundNext) {
                    body.setPosition(body.x(), groundNext);
                    body.setVelocity(body.velocityX(), 0.0);
                    body.setGrounded(true);
                    return new Collision(hitWall, reachedEdge);
                }
            }
            PhysicsBounds bounds = terrain.bounds();
            if (nextY < bounds.top()) {
                body.setPosition(body.x(), bounds.top());
                body.setVelocity(body.velocityX(), 0.0);
            } else if (nextY > bounds.bottom()) {
                body.setPosition(body.x(), bounds.bottom());
                body.setVelocity(body.velocityX(), 0.0);
            }
        }
        return new Collision(hitWall, reachedEdge);
    }

    /**
     * Resolves the connected floor that will support a grounded body at its horizontal
     * destination. Waiting until the next fixed step to change footholds leaves the body at the
     * previous segment's height for one frame, which is visible as penetration when knockback
     * crosses onto an upslope.
     */
    private static FootholdSegment connectedGroundAt(PhysicsTerrain terrain,
                                                      int footholdId,
                                                      double destinationX,
                                                      double velocityX) {
        FootholdSegment current = terrain.foothold(footholdId);
        for (int remaining = 64; remaining > 0 && current != null && !current.wall(); remaining--) {
            if (current.containsX(destinationX)) {
                return current;
            }
            int adjacentId = velocityX < 0.0 ? current.previousId() : current.nextId();
            if (adjacentId == 0 || adjacentId == current.id()) {
                return null;
            }
            current = terrain.foothold(adjacentId);
        }
        return null;
    }

    private static GroundSupport sweepLockedGround(PhysicsBody body,
                                                   PhysicsTerrain terrain,
                                                   double velocityX) {
        FootholdSegment current = terrain.foothold(body.footholdId());
        if (current == null || current.wall()) {
            return null;
        }
        double startX = body.x();
        int steps = Math.max(1, (int) Math.ceil(Math.abs(velocityX) / SUPPORT_SWEEP_STEP_PX));
        GroundSupport support = new GroundSupport(current, current.groundY(
                Math.max(current.left(), Math.min(current.right(), startX))));
        for (int step = 1; step <= steps; step++) {
            double sampleX = startX + velocityX * step / steps;
            support = lockedSupportAt(terrain, support.foothold(), sampleX, velocityX);
            if (support == null) {
                return null;
            }
        }
        return support;
    }

    private static GroundSupport lockedSupportAt(PhysicsTerrain terrain,
                                                  FootholdSegment current,
                                                  double x,
                                                  double direction) {
        for (int remaining = 64; remaining > 0 && current != null && !current.wall(); remaining--) {
            if (current.containsX(x)) {
                return new GroundSupport(current, current.groundY(x));
            }
            boolean left = direction < 0.0;
            FootholdSegment adjacent = directOrSeamAdjacent(terrain, current, left);
            if (adjacent == null) {
                return null;
            }
            if (inStrictSeamGap(current, adjacent, x, left)) {
                double fromX = left ? current.left() : current.right();
                double toX = left ? adjacent.right() : adjacent.left();
                double fromY = current.groundY(fromX);
                double toY = adjacent.groundY(toX);
                double span = toX - fromX;
                double ratio = Math.abs(span) <= POSITION_EPSILON ? 1.0 : (x - fromX) / span;
                return new GroundSupport(current, fromY + (toY - fromY) * ratio);
            }
            current = adjacent;
        }
        return null;
    }

    private static FootholdSegment directOrSeamAdjacent(PhysicsTerrain terrain,
                                                         FootholdSegment current,
                                                         boolean left) {
        int adjacentId = left ? current.previousId() : current.nextId();
        if (adjacentId != 0 && adjacentId != current.id()) {
            FootholdSegment linked = terrain.foothold(adjacentId);
            if (linked != null && !linked.wall()) {
                return linked;
            }
        }
        double endpointX = left ? current.left() : current.right();
        double endpointY = current.groundY(endpointX);
        FootholdSegment best = null;
        double bestXGap = Double.POSITIVE_INFINITY;
        double bestYGap = Double.POSITIVE_INFINITY;
        for (FootholdSegment candidate : terrain.footholdsNear(endpointX, SEAM_X_TOLERANCE_PX)) {
            if (!strictSeamCandidate(current, candidate, left, endpointX, endpointY)) {
                continue;
            }
            double candidateX = left ? candidate.right() : candidate.left();
            double candidateY = candidate.groundY(candidateX);
            double xGap = Math.abs(candidateX - endpointX);
            double yGap = Math.abs(candidateY - endpointY);
            if (xGap < bestXGap || (xGap == bestXGap && (yGap < bestYGap
                    || (yGap == bestYGap && (best == null || candidate.id() < best.id()))))) {
                best = candidate;
                bestXGap = xGap;
                bestYGap = yGap;
            }
        }
        return best;
    }

    private static boolean strictSeamCandidate(FootholdSegment current,
                                                FootholdSegment candidate,
                                                boolean left,
                                                double endpointX,
                                                double endpointY) {
        if (candidate == null || candidate.wall() || candidate.id() == current.id()
                || candidate.layer() != current.layer()) {
            return false;
        }
        double candidateX = left ? candidate.right() : candidate.left();
        if (Math.abs(candidateX - endpointX) > SEAM_X_TOLERANCE_PX
                || Math.abs(candidate.groundY(candidateX) - endpointY) > SEAM_Y_TOLERANCE_PX) {
            return false;
        }
        return left ? candidate.left() < current.left() && candidateX <= endpointX + POSITION_EPSILON
                : candidate.right() > current.right() && candidateX >= endpointX - POSITION_EPSILON;
    }

    private static boolean inStrictSeamGap(FootholdSegment current,
                                           FootholdSegment adjacent,
                                           double x,
                                           boolean left) {
        double from = left ? current.left() : current.right();
        double to = left ? adjacent.right() : adjacent.left();
        if (Math.abs(to - from) > SEAM_X_TOLERANCE_PX) {
            return false;
        }
        return left ? x <= from && x >= to : x >= from && x <= to;
    }

    private static GroundSupport strictReconciliationSupport(PhysicsBody body,
                                                              PhysicsTerrain terrain,
                                                              FootholdSegment current) {
        if (current == null || current.wall()) {
            return null;
        }
        if (current.containsX(body.x())) {
            double ground = current.groundY(body.x());
            if (Math.abs(body.y() - ground) <= RECONCILE_Y_TOLERANCE_PX) {
                return new GroundSupport(current, ground);
            }
        }
        GroundSupport best = null;
        double bestDelta = Double.POSITIVE_INFINITY;
        for (boolean left : new boolean[]{true, false}) {
            FootholdSegment adjacent = directOrSeamAdjacent(terrain, current, left);
            if (adjacent == null || adjacent.layer() != current.layer()
                    || !adjacent.containsX(body.x())) {
                continue;
            }
            double ground = adjacent.groundY(body.x());
            double delta = Math.abs(body.y() - ground);
            if (delta <= RECONCILE_Y_TOLERANCE_PX && delta < bestDelta) {
                best = new GroundSupport(adjacent, ground);
                bestDelta = delta;
            }
        }
        return best;
    }

    private static boolean updateFoothold(PhysicsBody body, PhysicsTerrain terrain) {
        if (body.mode() == PhysicsMode.FIXED && body.footholdId() > 0) {
            return false;
        }
        FootholdSegment previous = terrain.foothold(body.footholdId());
        if (body.groundedSupportLocked()) {
            double direction = body.velocityX();
            if (direction == 0.0 && previous != null) {
                direction = body.x() < previous.left() ? -1.0 : 1.0;
            }
            GroundSupport support = lockedSupportAt(terrain, previous, body.x(), direction);
            if (support != null) {
                body.setPosition(body.x(), support.groundY());
                body.setFoothold(support.foothold().id(), support.foothold().slope(),
                        support.foothold().layer());
                body.setGrounded(true);
            } else {
                body.clearGroundedSupportLock();
                body.setGrounded(false);
            }
            return false;
        }
        int nextId = body.footholdId();
        boolean checkSlope = false;
        if (body.grounded()) {
            if (previous != null && Math.floor(body.x()) > previous.right()) {
                nextId = previous.nextId();
            } else if (previous != null && Math.ceil(body.x()) < previous.left()) {
                nextId = previous.previousId();
            }
            if (nextId == 0) {
                FootholdSegment below = terrain.findBelow(body.x(), body.y());
                nextId = below == null ? 0 : below.id();
            } else {
                checkSlope = true;
            }
        } else {
            FootholdSegment below = terrain.findBelow(body.x(), body.y());
            nextId = below == null ? 0 : below.id();
        }

        if (nextId == 0 && body.y() >= terrain.bounds().bottom()) {
            recoverToPrevious(body, previous, terrain.bounds());
            return true;
        }

        FootholdSegment next = terrain.foothold(nextId);
        double slope = next == null ? 0.0 : next.slope();
        double ground = next == null ? 0.0 : next.groundY(body.x());
        if (next != null && body.velocityY() == 0.0 && checkSlope) {
            double verticalDelta = Math.abs(slope);
            if (slope < 0.0) {
                verticalDelta *= ground - body.y();
            } else if (slope > 0.0) {
                verticalDelta *= body.y() - ground;
            }
            if ((previous != null && previous.slope() != 0.0) || slope != 0.0) {
                if ((body.velocityX() > 0.0 && verticalDelta <= body.velocityX())
                        || (body.velocityX() < 0.0 && verticalDelta >= body.velocityX())) {
                    body.setPosition(body.x(), ground);
                }
            }
        }
        body.setGrounded(next != null && Math.abs(body.y() - ground) <= POSITION_EPSILON);

        if (next != null && (body.jumpDownEnabled() || body.hasFlag(PhysicsFlags.CHECK_BELOW))) {
            FootholdSegment below = terrain.findBelow(body.x(), ground + 1.0);
            if (below != null) {
                double nextGround = below.groundY(body.x());
                body.setJumpDownEnabled(nextGround - ground < 600.0);
                body.setGroundBelow(ground + 1.0);
            } else {
                body.setJumpDownEnabled(false);
            }
            body.clearFlag(PhysicsFlags.CHECK_BELOW);
        }
        int layer = body.footholdLayer();
        if (next != null && (layer == 0 || body.grounded())) {
            layer = next.layer();
        }
        if (nextId == 0 && previous != null) {
            nextId = previous.id();
        }
        body.setFoothold(nextId, slope, layer);
        return false;
    }

    private static void recoverInvalid(PhysicsBody body, PhysicsTerrain terrain) {
        FootholdSegment foothold = terrain.foothold(body.footholdId());
        recoverToPrevious(body, foothold, terrain.bounds());
    }

    private static void recoverToPrevious(PhysicsBody body, FootholdSegment previous,
                                          PhysicsBounds bounds) {
        body.clearGroundedSupportLock();
        if (previous != null && !previous.wall()) {
            double x = Math.max(previous.left(), Math.min(previous.right(),
                    Double.isFinite(body.x()) ? body.x() : previous.x1()));
            double y = previous.groundY(x);
            body.setPosition(x, y);
            body.setVelocity(0.0, 0.0);
            body.setFoothold(previous.id(), previous.slope(), previous.layer());
            body.setGrounded(true);
            body.setJumpDownEnabled(false);
            body.setGroundBelow(y + 1.0);
            return;
        }
        double x = Double.isFinite(body.x()) ? bounds.clampX(body.x()) : bounds.left();
        body.setPosition(x, bounds.bottom());
        body.setVelocity(0.0, 0.0);
        body.setFoothold(0, 0.0, 0);
        body.setGrounded(true);
        body.setJumpDownEnabled(false);
        body.setGroundBelow(bounds.bottom() + 1.0);
    }

    private static boolean crosses(double current, double next, double boundary, boolean left) {
        return left ? current >= boundary && next <= boundary
                : current <= boundary && next >= boundary;
    }

    private static boolean finite(PhysicsBody body) {
        return Double.isFinite(body.x()) && Double.isFinite(body.y())
                && Double.isFinite(body.velocityX()) && Double.isFinite(body.velocityY());
    }

    private record Collision(boolean hitWall, boolean reachedEdge) {
    }

    private record GroundSupport(FootholdSegment foothold, double groundY) {
    }
}
