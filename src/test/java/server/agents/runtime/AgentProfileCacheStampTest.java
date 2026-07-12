package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentProfileCacheStampTest {
    @Test
    void cacheStampIncludesCanonicalProfileOwnerAndMutationVersion() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(
                mock(Character.class), mock(Character.class), null);

        entry.markProfileCachesCurrent(20, 7L);

        assertTrue(entry.profileCachesMatch(20, 7L));
        assertFalse(entry.profileCachesMatch(10, 7L));
        assertFalse(entry.profileCachesMatch(20, 8L));
    }
}
