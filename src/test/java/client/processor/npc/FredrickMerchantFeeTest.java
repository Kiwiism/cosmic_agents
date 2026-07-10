package client.processor.npc;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FredrickMerchantFeeTest {
    @Test
    void appliesExistingDailyStorageFeeToPositiveAndNegativeBalances() {
        long now = System.currentTimeMillis();
        Timestamp tenDaysAgo = new Timestamp(now - Duration.ofDays(10).toMillis());

        assertEquals(900, FredrickProcessor.netMerchantMeso(1_000, tenDaysAgo, now));
        assertEquals(-900, FredrickProcessor.netMerchantMeso(-1_000, tenDaysAgo, now));
    }

    @Test
    void clampsMissingFutureAndExpiredStorageTimestamps() {
        long now = System.currentTimeMillis();

        assertEquals(1_000, FredrickProcessor.netMerchantMeso(1_000, null, now));
        assertEquals(1_000, FredrickProcessor.netMerchantMeso(1_000,
                new Timestamp(now + Duration.ofDays(1).toMillis()), now));
        assertEquals(0, FredrickProcessor.netMerchantMeso(1_000,
                new Timestamp(now - Duration.ofDays(101).toMillis()), now));
    }
}
