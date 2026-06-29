package server.bots;

public enum Emote {
    ANNOYED(1),
    HAPPY(2),
    GLARE(3),
    SAD(4),
    ANGRY(5),
    DISTURBED(6),
    EMBARRASSED(7);

    private final int value;

    Emote(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
