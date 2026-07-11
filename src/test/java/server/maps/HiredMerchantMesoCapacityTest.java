package server.maps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiredMerchantMesoCapacityTest {
    @Test
    void acceptsProceedsThatExactlyReachMesoCap() {
        assertTrue(HiredMerchant.canHoldMerchantMesos(Integer.MAX_VALUE - 100, 100));
    }

    @Test
    void rejectsProceedsThatExceedMesoCap() {
        assertFalse(HiredMerchant.canHoldMerchantMesos(Integer.MAX_VALUE - 99, 100));
        assertFalse(HiredMerchant.canHoldMerchantMesos(Integer.MAX_VALUE, 1));
    }
}
