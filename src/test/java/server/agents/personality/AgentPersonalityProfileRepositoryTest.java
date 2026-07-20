package server.agents.personality;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPersonalityProfileRepositoryTest {
    @Test
    void loadsTheFourVersionedPresentationArchetypes() {
        AgentPersonalityProfileRepository repository =
                AgentPersonalityProfileRepository.defaultRepository();

        Set<String> ids = repository.all().stream()
                .map(AgentPersonalityProfile::profileId)
                .collect(Collectors.toSet());

        assertEquals(Set.of("efficient-v1", "relaxed-v1", "restless-v1", "explorer-v1"), ids);
        assertTrue(repository.all().stream().allMatch(profile -> profile.profileVersion() == 1));
    }

    @Test
    void deterministicAssignmentIsStableForACharacter() {
        AgentPersonalityProfileRepository repository =
                AgentPersonalityProfileRepository.defaultRepository();

        assertEquals(repository.deterministicFor(73), repository.deterministicFor(73));
    }
}
