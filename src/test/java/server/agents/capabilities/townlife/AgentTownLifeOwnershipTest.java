package server.agents.capabilities.townlife;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeOwnershipTest {
    @Test
    void localLifecycleRejectsCrossTownOwnership() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(77);
        when(agent.getMapId()).thenReturn(100000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);

        AgentTownLifeSessionResult result = AgentTownLifeLifecycleRuntime.start(
                entry, agent, AgentTownLifeVisitRequest.leisure(103000000),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, agent.getId());

        assertEquals(AgentTownLifeSessionResult.Status.REJECTED_NOT_LOCAL, result.status());
        assertFalse(AgentTownLifeRuntime.active(entry));
    }

    @Test
    void extensionRegistryRejectsDuplicateHandlers() {
        AgentTownLifeActivityExtension extension = extension("kerning-local-event");

        assertThrows(IllegalArgumentException.class,
                () -> new AgentTownLifeActivityExtensionRegistry(List.of(extension, extension)));
    }

    @Test
    void genericTownLifePackageDoesNotImportProgressionOrPlans() throws Exception {
        Path root = Path.of("src", "main", "java", "server", "agents", "capabilities", "townlife");
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("import server.agents.progression."), file::toString);
                assertFalse(source.contains("import server.agents.plans."), file::toString);
                assertFalse(source.contains("import server.agents.capabilities.combat."), file::toString);
                assertFalse(source.contains("import server.agents.capabilities.shop."), file::toString);
                assertFalse(source.contains("import server.agents.capabilities.trade."), file::toString);
                assertFalse(source.contains(".travelTo("), file::toString);
                assertFalse(source.contains(".changeMap("), file::toString);
                assertFalse(source.contains(".gainItem("), file::toString);
            }
        }
    }

    @Test
    void localActivityStateAndVenuesCannotCarryCrossMapDestinations() throws Exception {
        String state = Files.readString(Path.of("src", "main", "java", "server", "agents",
                "capabilities", "townlife", "AgentTownLifeState.java"));
        String profile = Files.readString(Path.of("src", "main", "java", "server", "agents",
                "capabilities", "townlife", "AgentTownLifeProfile.java"));

        assertFalse(state.contains("destinationMapId"));
        String venueRecord = profile.substring(profile.indexOf("public record Venue("),
                profile.indexOf("public record VenueSpot("));
        assertFalse(venueRecord.contains("destinationMapId"));
        assertTrue(profile.substring(profile.indexOf("public record Facility(")).contains(
                "destinationMapId"));
    }

    @Test
    void profilesUseCanonicalActivitiesAndKerningNeedsNoJavaHandler() {
        AgentTownLifeProfile kerning = AgentTownLifeProfileRepository.defaultRepository()
                .require(103000000);

        assertEquals(2, kerning.schemaVersion());
        assertTrue(kerning.extensions().activityHandlers().isEmpty());
        assertTrue(kerning.venues().stream()
                .flatMap(venue -> venue.affordances().stream())
                .allMatch(affordance -> AgentTownLifeState.Activity.valueOf(affordance.name()) != null));
    }

    private static AgentTownLifeActivityExtension extension(String id) {
        return new AgentTownLifeActivityExtension() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Result start(Context context) {
                return Result.ACTIVE;
            }

            @Override
            public Result tick(Context context) {
                return Result.SUCCEEDED;
            }
        };
    }
}
