package server.agents.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentJourneyContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void copiesManifestParticipantsAndEventAttributes() {
        List<AgentJourneyManifest.Participant> participants =
                new java.util.ArrayList<>(List.of(
                        new AgentJourneyManifest.Participant(10, "TestAgent", "warrior")));
        AgentJourneyManifest manifest = new AgentJourneyManifest(
                1, "run-1", "victoria-lv10-20", "full", 100L, 20, participants);
        participants.clear();

        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("questId", 1000);
        AgentJourneyEventRecord event = new AgentJourneyEventRecord(
                1, "run-1", 1L, 110L, 10, "TestAgent",
                "quest", "quest.completed", "objective-1", 100000000,
                true, attributes);
        attributes.clear();

        assertEquals(1, manifest.participants().size());
        assertEquals(1000, event.attributes().get("questId"));
    }

    @Test
    void rejectsInvalidOperationalBounds() {
        assertThrows(IllegalArgumentException.class, () -> new AgentJourneyConfig(
                true, 127, 5_000L, 45_000L, 300_000L, 60_000L, 8, 100));
        assertThrows(IllegalArgumentException.class, () -> new AgentJourneyConfig(
                true, 8_192, 5_000L, 4_000L, 300_000L, 60_000L, 8, 100));
        assertThrows(IllegalArgumentException.class, () -> new AgentJourneyConfig(
                true, 8_192, 5_000L, 45_000L, 300_000L, 60_000L, 3, 100));
    }

    @Test
    void rejectsIdentitiesThatCannotSatisfyTheVersionedSchemas() {
        assertThrows(IllegalArgumentException.class, () -> new AgentJourneyEventRecord(
                1, "run-1", 0L, 110L, 10, "TestAgent",
                "quest", "quest.completed", "", 100000000, true, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new AgentJourneyManifest(
                1, "run-1", "victoria-lv10-20", "unknown", 100L, 20,
                List.of(new AgentJourneyManifest.Participant(10, "TestAgent", "warrior"))));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentJourneyManifest.Participant(0, "", ""));
    }

    @Test
    void allPublishedJourneySchemasAreValidJson() throws Exception {
        for (String schema : List.of(
                "journey-manifest-v1.schema.json",
                "journey-event-v1.schema.json",
                "journey-snapshot-v1.schema.json",
                "journey-report-v1.schema.json",
                "journey-failure-episode-v1.schema.json")) {
            String resource = "/agents/journey/schemas/" + schema;
            try (InputStream input = AgentJourneyContractTest.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new AssertionError("Missing journey schema " + resource);
                }
                MAPPER.readTree(input);
            }
        }
    }
}
