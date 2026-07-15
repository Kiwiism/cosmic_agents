package server;

import config.WorldConfig;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemRestrictionPolicyTest {
    private WorldConfig world;
    private boolean allowAllUntradeable;
    private List<Integer> untradeableAllowlist;
    private boolean allowAllUniqueDuplicates;
    private List<Integer> uniqueAllowlist;

    @BeforeEach
    void setUp() {
        world = YamlConfig.config.worlds.get(0);
        allowAllUntradeable = world.allow_all_untradeable_items;
        untradeableAllowlist = world.untradeable_item_allowlist;
        allowAllUniqueDuplicates = world.allow_multiple_one_of_a_kind_items;
        uniqueAllowlist = world.multiple_one_of_a_kind_item_allowlist;

        world.allow_all_untradeable_items = false;
        world.untradeable_item_allowlist = new ArrayList<>();
        world.allow_multiple_one_of_a_kind_items = false;
        world.multiple_one_of_a_kind_item_allowlist = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        world.allow_all_untradeable_items = allowAllUntradeable;
        world.untradeable_item_allowlist = untradeableAllowlist;
        world.allow_multiple_one_of_a_kind_items = allowAllUniqueDuplicates;
        world.multiple_one_of_a_kind_item_allowlist = uniqueAllowlist;
    }

    @Test
    void restrictionsAreDeniedUnlessTheItemIsAllowlisted() {
        assertFalse(ItemRestrictionPolicy.allowsUntradeable(0, 100));
        assertFalse(ItemRestrictionPolicy.allowsMultipleOneOfAKind(0, 200));

        world.untradeable_item_allowlist = List.of(100);
        world.multiple_one_of_a_kind_item_allowlist = List.of(200);

        assertTrue(ItemRestrictionPolicy.allowsUntradeable(0, 100));
        assertFalse(ItemRestrictionPolicy.allowsUntradeable(0, 101));
        assertTrue(ItemRestrictionPolicy.allowsMultipleOneOfAKind(0, 200));
        assertFalse(ItemRestrictionPolicy.allowsMultipleOneOfAKind(0, 201));
    }

    @Test
    void broadOverridesRemainExplicitAndWorldScoped() {
        world.allow_all_untradeable_items = true;
        world.allow_multiple_one_of_a_kind_items = true;

        assertTrue(ItemRestrictionPolicy.allowsUntradeable(0, 999));
        assertTrue(ItemRestrictionPolicy.allowsMultipleOneOfAKind(0, 999));
        assertFalse(ItemRestrictionPolicy.allowsUntradeable(-1, 999));
    }
}
