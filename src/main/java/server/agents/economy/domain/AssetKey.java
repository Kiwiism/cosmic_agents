package server.agents.economy.domain;

import java.util.Objects;

public record AssetKey(AssetType type, String identifier) {
    public static final AssetKey MESO = new AssetKey(AssetType.MESO, "MESO");

    public AssetKey {
        Objects.requireNonNull(type);
        if (identifier == null || identifier.isBlank()) throw new IllegalArgumentException("identifier is required");
    }

    public static AssetKey item(int itemId) {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be positive");
        return new AssetKey(AssetType.ITEM, Integer.toString(itemId));
    }
}
