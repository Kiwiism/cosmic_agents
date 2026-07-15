package server.maps.reservation;

public record CharacterSpaceOwner(String type, int id) {
    public static final String CHARACTER = "character";
    public static final String TEST_STALL = "test-stall";

    public CharacterSpaceOwner {
        if (type == null || type.isBlank() || id <= 0) {
            throw new IllegalArgumentException("character space owner is invalid");
        }
    }

    public static CharacterSpaceOwner character(int characterId) {
        return new CharacterSpaceOwner(CHARACTER, characterId);
    }

    public static CharacterSpaceOwner testStall(int runtimeId) {
        return new CharacterSpaceOwner(TEST_STALL, runtimeId);
    }
}
