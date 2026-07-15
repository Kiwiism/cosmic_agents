package server.maps.reservation;

public record CharacterSpaceScope(int worldId, int channelId, int mapId) {
    public CharacterSpaceScope {
        if (worldId < 0 || channelId < 0 || mapId <= 0) {
            throw new IllegalArgumentException("character space scope is invalid");
        }
    }
}
