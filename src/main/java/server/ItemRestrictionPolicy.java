package server;

import client.Character;
import config.WorldConfig;
import config.YamlConfig;

import java.util.List;

public final class ItemRestrictionPolicy {
    private ItemRestrictionPolicy() {
    }

    public static boolean allowsUntradeable(Character character, int itemId) {
        return character != null && allowsUntradeable(character.getWorld(), itemId);
    }

    public static boolean allowsUntradeable(int worldId, int itemId) {
        WorldConfig world = worldConfig(worldId);
        return world != null && (world.allow_all_untradeable_items
                || contains(world.untradeable_item_allowlist, itemId));
    }

    public static boolean allowsMultipleOneOfAKind(Character character, int itemId) {
        return character != null && allowsMultipleOneOfAKind(character.getWorld(), itemId);
    }

    public static boolean allowsMultipleOneOfAKind(int worldId, int itemId) {
        WorldConfig world = worldConfig(worldId);
        return world != null && (world.allow_multiple_one_of_a_kind_items
                || contains(world.multiple_one_of_a_kind_item_allowlist, itemId));
    }

    private static WorldConfig worldConfig(int worldId) {
        List<WorldConfig> worlds = YamlConfig.config.worlds;
        if (worlds == null || worldId < 0 || worldId >= worlds.size()) {
            return null;
        }
        return worlds.get(worldId);
    }

    private static boolean contains(List<Integer> itemIds, int itemId) {
        return itemIds != null && itemIds.contains(itemId);
    }
}
