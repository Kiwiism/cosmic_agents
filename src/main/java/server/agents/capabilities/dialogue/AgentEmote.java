package server.agents.capabilities.dialogue;

public enum AgentEmote {
    ANNOYED(1),
    HAPPY(2),
    GLARE(3),
    SAD(4),
    ANGRY(5),
    DISTURBED(6),
    EMBARRASSED(7);

    private final int value;

    AgentEmote(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
