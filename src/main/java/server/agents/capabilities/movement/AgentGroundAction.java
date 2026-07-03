package server.agents.capabilities.movement;

public record AgentGroundAction(Type type, int stepX) {
    private static final AgentGroundAction IDLE = new AgentGroundAction(Type.IDLE, 0);
    private static final AgentGroundAction CROUCH = new AgentGroundAction(Type.CROUCH, 0);

    public enum Type {
        IDLE,
        WALK,
        CROUCH,
        JUMP
    }

    public static AgentGroundAction idle() {
        return IDLE;
    }

    public static AgentGroundAction walk(int stepX) {
        return new AgentGroundAction(Type.WALK, stepX);
    }

    public static AgentGroundAction crouch() {
        return CROUCH;
    }

    public static AgentGroundAction jump(int stepX) {
        return new AgentGroundAction(Type.JUMP, stepX);
    }
}
