package server.physics;

import server.physics.foothold.FootholdSegment;

/**
 * Fixed-step Java translation of Journey Physics.cpp and FootholdTree.cpp.
 * Upstream: nmnsnv/maplestory-wasm, revision
 * bc0234fe7c7f53322453e7bdd79564d9aca4cd8b, AGPL-3.0-or-later.
 */
public final class MaplePhysicsIntegrator {
    private static final double POSITION_EPSILON = 1.0e-7;

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
        if (body.grounded() && body.velocityY() == 0.0 && velocityX != 0.0) {
            FootholdSegment foothold = terrain.foothold(body.footholdId());
            double destinationX = body.x() + velocityX;
            if (foothold != null && !foothold.wall() && foothold.containsX(destinationX)) {
                body.setPosition(body.x(), foothold.groundY(destinationX));
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

    private static boolean updateFoothold(PhysicsBody body, PhysicsTerrain terrain) {
        if (body.mode() == PhysicsMode.FIXED && body.footholdId() > 0) {
            return false;
        }
        FootholdSegment previous = terrain.foothold(body.footholdId());
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
}
