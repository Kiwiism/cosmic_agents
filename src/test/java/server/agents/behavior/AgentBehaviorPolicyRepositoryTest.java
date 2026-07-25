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
        assertEquals(60, repository.resolve("relaxed-v1").crowd().avoidPercent());
        assertEquals(40, repository.resolve("explorer-v1").crowd().avoidPercent());
        assertEquals(35, repository.resolve("efficient-v1").targeting().claimAvoidPercent());
        assertEquals(50, repository.resolve("relaxed-v1").targeting().claimAvoidPercent());
        assertEquals(20, repository.resolve("restless-v1").targeting().claimAvoidPercent());
        assertEquals(45, repository.resolve("explorer-v1").targeting().claimAvoidPercent());
        assertEquals(25, alternativeTargetPercent(repository.resolve("efficient-v1")));
        assertEquals(45, alternativeTargetPercent(repository.resolve("relaxed-v1")));
        assertEquals(35, alternativeTargetPercent(repository.resolve("restless-v1")));
        assertEquals(60, alternativeTargetPercent(repository.resolve("explorer-v1")));
    }

    private static int alternativeTargetPercent(AgentBehaviorPolicyProfile policy) {
        AgentBehaviorPolicyProfile.Targeting targeting = policy.targeting();
        int total = targeting.bestWeight() + targeting.nearWeight() + targeting.middleWeight();
        return (targeting.nearWeight() + targeting.middleWeight()) * 100 / total;
    }
}
