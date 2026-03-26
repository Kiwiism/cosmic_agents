package server.bots;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BotMobHitboxProviderTest {
    @TempDir
    private Path wzPath;

    @BeforeEach
    void setWzPath() throws IOException {
        Path mobDir = wzPath.resolve("wz/Mob.wz");
        Files.createDirectories(mobDir);
        Files.writeString(mobDir.resolve("0100100.img.xml"), """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <imgdir name="0100100.img">
                  <imgdir name="info">
                    <int name="level" value="1"/>
                  </imgdir>
                  <imgdir name="stand">
                    <canvas name="0" width="50" height="30">
                      <vector name="lt" x="-20" y="-30"/>
                      <vector name="rb" x="10" y="0"/>
                    </canvas>
                  </imgdir>
                </imgdir>
                """);
        System.setProperty("wz-path", wzPath.resolve("wz").toString());
    }

    @Test
    void shouldUseStandZeroBoundsForRightFacingMob() {
        Rectangle bounds = BotMobHitboxProvider.getInstance().getMobBounds(100100, new Point(100, 200), false);

        assertNotNull(bounds);
        assertEquals(new Rectangle(80, 170, 30, 30), bounds);
    }

    @Test
    void shouldMirrorBoundsForLeftFacingMob() {
        Rectangle bounds = BotMobHitboxProvider.getInstance().getMobBounds(100100, new Point(100, 200), true);

        assertNotNull(bounds);
        assertEquals(new Rectangle(90, 170, 30, 30), bounds);
    }
}
