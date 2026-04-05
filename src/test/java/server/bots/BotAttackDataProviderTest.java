package server.bots;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotAttackDataProviderTest {
    @Test
    void shouldMatchOpenStoryBodyAndAfterimageAttackTimingInputs() {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();

        assertTrue(provider.getBodyStanceDurationMs("swingO1") > 0);
        assertTrue(provider.getBodyStanceDelayBeforeFrameMs("swingO1", 2) > 0);
        assertTrue(provider.getBodyActionDurationMs("doublefire") > 0);
        assertTrue(provider.getBodyActionAttackDelayMs("doublefire", 0) >= 0);
        assertEquals(5, provider.getBodyActionId("swingO1"));
        assertEquals(6, provider.getBodyActionId("swingO2"));
        assertEquals(7, provider.getBodyActionId("swingO3"));
        assertEquals(16, provider.getBodyActionId("stabO1"));
        assertEquals(17, provider.getBodyActionId("stabO2"));
        assertEquals(32, provider.getBodyActionId("proneStab"));
        assertEquals(56, provider.getBodyActionId("avenger"));
        assertEquals(69, provider.getBodyActionId("genesis"));
        assertEquals(77, provider.getBodyActionId("handgun"));
        assertEquals(86, provider.getBodyActionId("doublefire"));
        assertEquals(6, BotAttackExecutionProvider.basicAttackDirectionId("swingO2", "swingO1"));
        BotAttackExecutionProvider.CloseRangePacketFields closeRangeFields =
                BotAttackExecutionProvider.mimicCloseRangePacketFields("stabO1", "swingO1", false);
        assertEquals(0, closeRangeFields.display());
        assertEquals(16, closeRangeFields.direction());
        assertEquals(0, closeRangeFields.stance());
        assertEquals(0x80, BotAttackExecutionProvider.mimicCloseRangePacketFields("stabO1", "swingO1", true).stance());

        BotAttackDataProvider.NormalAttackProfile profile = provider.getNormalAttackProfile(1302077);
        assertNotNull(profile);
        assertTrue(profile.getSourceActions().contains("swingO1"));
        assertTrue(profile.getAfterimageFirstFrame("swingO1") > 0);
    }

    @Test
    void shouldPreferExplicitBodyActionTimingOverSkillAnimationDelay() {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        int rawActionHitDelayMs = provider.getBodyActionAttackDelayMs("doublefire", 0);
        int rawActionDurationMs = provider.getBodyActionDurationMs("doublefire");
        BotAttackExecutionProvider.SkillAttackTiming timing =
                BotAttackExecutionProvider.resolveSkillAttackTiming("doublefire", 999, 4, 4, 300, 590);

        assertTrue(rawActionDurationMs > 0);
        assertTrue(rawActionHitDelayMs >= 0);
        assertEquals(adjustedDelay(rawActionHitDelayMs), timing.hitDelayMs());
        assertEquals(Math.max(BotMovementManager.delayAfterCurrentTick(adjustedDelay(rawActionDurationMs)), 590),
                timing.cooldownMs());
    }

    private static int adjustedDelay(int rawDelayMs) {
        if (rawDelayMs <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(rawDelayMs / 1.3f));
    }

    @Test
    void shouldFilterWeaponActionsToLegalAttackGroupAnimations() {
        BotAttackExecutionProvider.BasicAttackSpec attackSpec =
                BotAttackExecutionProvider.basicAttackSpec(1, client.inventory.WeaponType.GENERAL1H_SWING);

        List<String> actions = BotAttackExecutionProvider.resolveAttackActions(attackSpec,
                List.of("swingOF", "stabO1", "proneStab", "swingO3", "stabOF"));

        assertEquals(List.of("stabO1", "swingO3"), actions);
    }
}
