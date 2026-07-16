package server.physics;

/** Mutable, entity-independent precise physics state. */
public final class PhysicsBody {
    private double x;
    private double y;
    private double velocityX;
    private double velocityY;
    private double accelerationX;
    private double accelerationY;
    private int footholdId;
    private double footholdSlope;
    private int footholdLayer;
    private double groundBelow;
    private boolean grounded;
    private boolean jumpDownEnabled;
    private PhysicsMode mode;
    private int flags;

    public PhysicsBody(double x, double y, PhysicsMode mode) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("physics position must be finite");
        }
        this.x = x;
        this.y = y;
        this.mode = mode == null ? PhysicsMode.NORMAL : mode;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double velocityX() { return velocityX; }
    public double velocityY() { return velocityY; }
    public double accelerationX() { return accelerationX; }
    public double accelerationY() { return accelerationY; }
    public int footholdId() { return footholdId; }
    public double footholdSlope() { return footholdSlope; }
    public int footholdLayer() { return footholdLayer; }
    public double groundBelow() { return groundBelow; }
    public boolean grounded() { return grounded; }
    public boolean jumpDownEnabled() { return jumpDownEnabled; }
    public PhysicsMode mode() { return mode; }
    public int flags() { return flags; }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setVelocity(double velocityX, double velocityY) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    public void setAcceleration(double accelerationX, double accelerationY) {
        this.accelerationX = accelerationX;
        this.accelerationY = accelerationY;
    }

    public void setFoothold(int footholdId, double slope, int layer) {
        this.footholdId = footholdId;
        this.footholdSlope = slope;
        this.footholdLayer = layer;
    }

    public void setGrounded(boolean grounded) { this.grounded = grounded; }
    public void setGroundBelow(double groundBelow) { this.groundBelow = groundBelow; }
    public void setJumpDownEnabled(boolean enabled) { this.jumpDownEnabled = enabled; }
    public void setMode(PhysicsMode mode) { this.mode = mode; }
    public void setFlags(int flags) { this.flags = flags; }
    public void setFlag(int flag) { flags |= flag; }
    public void clearFlag(int flag) { flags &= ~flag; }
    public boolean hasFlag(int flag) { return (flags & flag) != 0; }
}
