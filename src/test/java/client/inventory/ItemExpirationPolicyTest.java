package client.inventory;

import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemExpirationPolicyTest {
    private boolean itemsNeverExpire;
    private boolean petsNeverExpire;

    @BeforeEach
    void rememberConfiguration() {
        itemsNeverExpire = YamlConfig.config.server.ITEMS_NEVER_EXPIRE;
        petsNeverExpire = YamlConfig.config.server.PETS_NEVER_EXPIRE;
        YamlConfig.config.server.ITEMS_NEVER_EXPIRE = false;
        YamlConfig.config.server.PETS_NEVER_EXPIRE = false;
    }

    @AfterEach
    void restoreConfiguration() {
        YamlConfig.config.server.ITEMS_NEVER_EXPIRE = itemsNeverExpire;
        YamlConfig.config.server.PETS_NEVER_EXPIRE = petsNeverExpire;
    }

    @Test
    void requestedExpirationIsPreservedByDefault() {
        Item item = new Item(2000000, (short) 1, (short) 1);

        item.setExpiration(123456789L);

        assertEquals(123456789L, item.getExpiration());
    }

    @Test
    void permanenceRequiresAnExplicitToggle() {
        YamlConfig.config.server.ITEMS_NEVER_EXPIRE = true;
        Item item = new Item(2000000, (short) 1, (short) 1);

        item.setExpiration(123456789L);

        assertEquals(-1, item.getExpiration());
    }

    @Test
    void petPermanenceUsesThePetSpecificRepresentation() {
        YamlConfig.config.server.ITEMS_NEVER_EXPIRE = true;
        YamlConfig.config.server.PETS_NEVER_EXPIRE = true;
        Item pet = new Item(5000000, (short) 1, (short) 1);

        pet.setExpiration(123456789L);

        assertEquals(Long.MAX_VALUE, pet.getExpiration());
    }
}
