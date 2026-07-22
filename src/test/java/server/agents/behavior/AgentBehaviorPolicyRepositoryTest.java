package server.agents.behavior;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBehaviorPolicyRepositoryTest {
    @Test
    void coversEveryDurablePersonalityIdentity() {
        AgentBehaviorPolicyRepository repository = AgentBehaviorPolicyRepository.defaultRepository();
        Set<String> identities = Set.of("efficient-v1", "relaxed-v1", "restless-v1", "explorer-v1");

        assertEquals(identities, identities.stream()
                .map(repository::resolve)
                .map(AgentBehaviorPolicyProfile::personalityProfileId)
                .collect(Collectors.toSet()));
        assertTrue(identities.stream().map(repository::resolve)
                .allMatch(policy -> policy.response().maxMs() >= policy.response().minMs()));
    }
}
