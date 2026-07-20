package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VictoriaFirstJobMvpTestServiceTest {
    @Test
    void resolvesFiveSimpleJobAliasesAndOptionalBuildVariants() {
        assertEquals("warrior-standard-v1", bundle("warrior"));
        assertEquals("bowman-standard-v1", bundle("archer"));
        assertEquals("magician-standard-v1", bundle("mage"));
        assertEquals("thief-claw-standard-v1", bundle("thief"));
        assertEquals("pirate-gun-standard-v1", bundle("pirate"));
        assertEquals("thief-dagger-standard-v1", bundle("thief-dagger"));
        assertEquals("pirate-knuckle-standard-v1", bundle("pirate-knuckle"));
    }

    @Test
    void rejectsUnknownCareerInsteadOfSelectingSilently() {
        assertThrows(IllegalArgumentException.class,
                () -> VictoriaFirstJobMvpTestService.resolveBundle("beginner"));
    }

    @Test
    void resolvesAllThreeDocumentedStartVariantsAndRejectsUnknownOnes() {
        assertEquals("lv10", VictoriaFirstJobMvpTestService.resolveStartVariant("level10").variantId());
        assertEquals("lv9-olaf", VictoriaFirstJobMvpTestService.resolveStartVariant("olaf").variantId());
        assertEquals("lv9-grind", VictoriaFirstJobMvpTestService.resolveStartVariant("grind").variantId());
        assertThrows(IllegalArgumentException.class,
                () -> VictoriaFirstJobMvpTestService.resolveStartVariant("lv8"));
    }

    private static String bundle(String alias) {
        return VictoriaFirstJobMvpTestService.resolveBundle(alias).bundleId();
    }
}
