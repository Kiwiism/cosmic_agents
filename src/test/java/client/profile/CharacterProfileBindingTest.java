package client.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CharacterProfileBindingTest {
    @Test
    void canonicalOwnerIsIndependentFromLaterActorBinding() {
        CharacterProfileBinding binding = new CharacterProfileBinding();
        binding.initializeCanonicalOwner(10);

        long generation = binding.rebind(10, 20, 0);

        assertEquals(20, binding.ownerCharacterId());
        assertEquals(1, generation);
    }

    @Test
    void staleGenerationCannotOverwriteNewerBinding() {
        CharacterProfileBinding binding = new CharacterProfileBinding();
        binding.initializeCanonicalOwner(10);
        binding.rebind(10, 20, 0);

        assertThrows(IllegalStateException.class, () -> binding.rebind(20, 10, 0));
        assertEquals(20, binding.ownerCharacterId());
    }

    @Test
    void mutationsAdvanceProfileVersionWithoutChangingOwner() {
        CharacterProfileBinding binding = new CharacterProfileBinding();
        binding.initializeCanonicalOwner(10);

        binding.markMutated();
        binding.markMutated();

        assertEquals(2, binding.version());
        assertEquals(10, binding.ownerCharacterId());
    }
}
