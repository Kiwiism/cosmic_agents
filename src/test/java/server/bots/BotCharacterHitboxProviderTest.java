package server.bots;

import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.bots.combat.BotCharacterHitboxProvider;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BotCharacterHitboxProviderTest {
    @Test
    void shouldUnionCharacterCanvasBoundsForRightFacingStand() {
        Rectangle bounds = BotCharacterHitboxProvider.getInstance()
                .getBotBounds(CharacterStance.STAND_RIGHT_STANCE, new Point(100, 200));

        assertNotNull(bounds);
        assertEquals(new Rectangle(84, 169, 21, 41), bounds);
    }

    @Test
    void shouldMirrorCharacterBoundsForLeftFacingStand() {
        Rectangle bounds = BotCharacterHitboxProvider.getInstance()
                .getBotBounds(CharacterStance.STAND_LEFT_STANCE, new Point(100, 200));

        assertNotNull(bounds);
        assertEquals(new Rectangle(95, 169, 21, 41), bounds);
    }

}
