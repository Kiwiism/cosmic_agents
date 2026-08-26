package net.server.channel.handlers;

import org.junit.jupiter.api.Test;
import server.combat.PhysicalContactDamagePolicy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TakeDamageHandlerDarkSightTest {
    @Test
    void darkSightNegatesOnlyPhysicalMonsterContact() {
        assertTrue(PhysicalContactDamagePolicy.isNegated(-1, true));
        assertFalse(PhysicalContactDamagePolicy.isNegated(0, true));
        assertFalse(PhysicalContactDamagePolicy.isNegated(1, true));
        assertFalse(PhysicalContactDamagePolicy.isNegated(-1, false));
    }
}
