package server.life;

import constants.id.MobId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EpqPoisonFlowerCaptureServiceTest {
    @Test
    void usesTheAuthoredStrictlyBelowFortyPercentThreshold() {
        Monster flower = mock(Monster.class);
        when(flower.getId()).thenReturn(MobId.POISON_FLOWER);
        when(flower.isAlive()).thenReturn(true);
        when(flower.getMaxHp()).thenReturn(7_500);
        when(flower.getHp()).thenReturn(3_000);
        assertFalse(EpqPoisonFlowerCaptureService.ready(flower));

        when(flower.getHp()).thenReturn(2_999);
        assertTrue(EpqPoisonFlowerCaptureService.ready(flower));
    }
}
