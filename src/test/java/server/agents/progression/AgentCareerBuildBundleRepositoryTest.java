package server.agents.progression;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.build.profiles.AgentApBuildProfileRepository;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileRepository;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCareerBuildBundleRepositoryTest {
    @Test
    void defaultCatalogCoversEveryFirstJobBuildVariant() {
        AgentCareerBuildBundleRepository repository = AgentCareerBuildBundleRepository.defaultRepository();
        assertEquals(7, repository.all().size());
        assertEquals(Set.of(100, 200, 300, 400, 500), repository.all().stream()
                .map(AgentCareerBuildBundle::firstJobId).collect(Collectors.toSet()));

        for (AgentCareerBuildBundle bundle : repository.all()) {
            assertTrue(AgentApBuildProfileRepository.defaultRepository().find(bundle.apProfileId()).isPresent());
            assertTrue(AgentSpBuildProfileRepository.defaultRepository().find(bundle.spProfileId()).isPresent());
            assertEquals(4, bundle.instructorTrainingQuestIds().size());
            assertEquals(15, bundle.milestoneLevel());
        }
    }
}
