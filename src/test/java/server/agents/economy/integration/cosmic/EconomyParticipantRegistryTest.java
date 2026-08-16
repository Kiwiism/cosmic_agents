package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.session.CommerceParticipant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyParticipantRegistryTest {
    @Test
    void releaseEndsSessionMembershipButKeepsDurableInventoryProtectionBinding() {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(101);
        EconomyParticipantRegistry registry = new EconomyParticipantRegistry(id -> character);
        CommerceParticipant profile = profile("agent-1");

        registry.admitted(profile, character);
        assertTrue(registry.isAdmittedCharacter(101));
        assertTrue(registry.matchesBinding("agent-1", 101));

        registry.released(profile, character);
        assertFalse(registry.isAdmittedCharacter(101));
        assertTrue(registry.isBoundCharacter(101));
        assertTrue(registry.isBoundAgent("agent-1"));
        assertTrue(registry.byBoundCharacterId(101).isPresent());
    }

    @Test
    void rejectsConflictingLogicalIdentityBinding() {
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        when(first.getId()).thenReturn(101);
        when(second.getId()).thenReturn(102);
        EconomyParticipantRegistry registry = new EconomyParticipantRegistry(id -> first);
        CommerceParticipant profile = profile("agent-1");
        registry.admitted(profile, first);

        assertThrows(IllegalStateException.class, () -> registry.admitted(profile, second));
        assertTrue(registry.matchesBinding("agent-1", 101));
        assertFalse(registry.isBoundCharacter(102));
    }

    private static CommerceParticipant profile(String id) {
        return new CommerceParticipant(id, "BEGINNER", .5, .5, .5, .5, .5, .5, 24, .5, .5);
    }
}
