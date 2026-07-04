package server.agents.capabilities.movement;

/**
 * Mutable movement intent, facing, crouch, and packet velocity state for one live Agent runtime.
 */
public final class AgentMovementInputState {
    private int moveDirection;
    private int velocityX;
    private int velocityY;
    private int facingDirection = 1;
    private boolean crouching;
    private boolean wasMovingX;

    public int moveDirection() {
        return moveDirection;
    }

    public void setMoveDirection(int moveDirection) {
        this.moveDirection = Integer.compare(moveDirection, 0);
    }

    public void clearMoveDirection() {
        moveDirection = 0;
    }

    public int velocityX() {
        return velocityX;
    }

    public int velocityY() {
        return velocityY;
    }

    public boolean hasVelocity() {
        return velocityX != 0 || velocityY != 0;
    }

    public void setVelocity(int velocityX, int velocityY) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        if (velocityX != 0) {
            setFacingDirection(velocityX);
        }
    }

    public int facingDirection() {
        return facingDirection;
    }

    public int facingDirectionSign() {
        return facingDirection >= 0 ? 1 : -1;
    }

    public void setFacingDirection(int facingDirection) {
        this.facingDirection = facingDirection >= 0 ? 1 : -1;
    }

    public boolean crouching() {
        return crouching;
    }

    public void setCrouching(boolean crouching) {
        this.crouching = crouching;
    }

    public boolean wasMovingX() {
        return wasMovingX;
    }

    public void setWasMovingX(boolean wasMovingX) {
        this.wasMovingX = wasMovingX;
    }
}
