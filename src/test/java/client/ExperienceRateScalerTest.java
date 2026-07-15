package client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperienceRateScalerTest {
    @Test
    void appliesTheConfiguredRateExactlyOnce() {
        assertEquals(500, ExperienceRateScaler.scale(500, 1));
        assertEquals(1500, ExperienceRateScaler.scale(500, 3));
        assertEquals(278, ExperienceRateScaler.scale(92.7, 3));
    }

    @Test
    void preservesLossesAndSaturatesOverflow() {
        assertEquals(-10, ExperienceRateScaler.scale(-10, 5));
        assertEquals(Integer.MAX_VALUE, ExperienceRateScaler.scale(Integer.MAX_VALUE, 2));
    }
}
