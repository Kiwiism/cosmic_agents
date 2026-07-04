package server.agents.runtime;

public final class AgentMovementBroadcastState {
    private boolean valid = false;
    private int x = 0;
    private int y = 0;
    private int velocityX = 0;
    private int velocityY = 0;
    private int stance = 0;
    private int footholdId = 0;

    public boolean valid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean matches(int x, int y, int velocityX, int velocityY, int stance, int footholdId) {
        return valid
                && this.x == x
                && this.y == y
                && this.velocityX == velocityX
                && this.velocityY == velocityY
                && this.stance == stance
                && this.footholdId == footholdId;
    }

    public void record(int x, int y, int velocityX, int velocityY, int stance, int footholdId) {
        valid = true;
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.stance = stance;
        this.footholdId = footholdId;
    }

    public int x() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int y() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int velocityX() {
        return velocityX;
    }

    public void setVelocityX(int velocityX) {
        this.velocityX = velocityX;
    }

    public int velocityY() {
        return velocityY;
    }

    public void setVelocityY(int velocityY) {
        this.velocityY = velocityY;
    }

    public int stance() {
        return stance;
    }

    public void setStance(int stance) {
        this.stance = stance;
    }

    public int footholdId() {
        return footholdId;
    }

    public void setFootholdId(int footholdId) {
        this.footholdId = footholdId;
    }
}
