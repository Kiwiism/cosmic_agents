package server.agents.plans.amherst;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmherstPlanCardLoaderTest {
    private static final Path CARD = Path.of("docs", "agents", "plans",
            "maple-island-amherst-subphase.plan.json");

    @TempDir
    Path tempDir;

    @Test
    void loadsExistingCardWithStableGeneratedObjectiveIds() throws Exception {
        AmherstPlanCard first = new AmherstPlanCardLoader().load(CARD);
        AmherstPlanCard second = new AmherstPlanCardLoader().load(CARD);

        assertEquals("maple-island-amherst-subphase", first.planId());
        assertFalse(first.objectives().isEmpty());
        assertEquals(first.objectives().stream().map(AmherstPlanObjective::objectiveId).toList(),
                second.objectives().stream().map(AmherstPlanObjective::objectiveId).toList());
        assertTrue(first.objectives().stream().map(AmherstPlanObjective::objectiveId).distinct().count()
                == first.objectives().size());
        assertEquals(25, first.objectives().size());
        assertTrue(first.objectives().stream().anyMatch(objective -> objective.mapId() == 30000
                && objective.kind() == AmherstPlanObjectiveKind.QUEST_START
                && Integer.valueOf(1032).equals(objective.questId())));
        int pioStart = indexOf(first, AmherstPlanObjectiveKind.QUEST_START, 1008);
        int pioReactor = indexOf(first, AmherstPlanObjectiveKind.REACTOR_HIT, 1008);
        assertTrue(pioStart < pioReactor);
        assertTrue(first.objectives().stream().anyMatch(objective -> objective.mapId() == 30001
                && objective.kind() == AmherstPlanObjectiveKind.QUEST_COMPLETE
                && Integer.valueOf(1032).equals(objective.questId())));
        assertTrue(first.objectives().stream().anyMatch(objective -> objective.mapId() == 50000
                && objective.kind() == AmherstPlanObjectiveKind.QUEST_CHAIN
                && objective.questIds().equals(java.util.List.of(1036))));
    }

    private static int indexOf(AmherstPlanCard card, AmherstPlanObjectiveKind kind, int questId) {
        for (int index = 0; index < card.objectives().size(); index++) {
            AmherstPlanObjective objective = card.objectives().get(index);
            if (objective.kind() == kind && Integer.valueOf(questId).equals(objective.questId())) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    @Test
    void rejectsUnknownObjectiveKindWithStructuredError() throws Exception {
        String json = Files.readString(CARD).replaceFirst("quest-start", "unknown-kind");
        Path invalid = tempDir.resolve("unknown.json");
        Files.writeString(invalid, json);

        AmherstPlanValidationException failure = assertThrows(AmherstPlanValidationException.class,
                () -> new AmherstPlanCardLoader().load(invalid));

        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.UNKNOWN_OBJECTIVE_KIND));
    }

    @Test
    void rejectsForbiddenMapAndShanksPolicyRemoval() throws Exception {
        String json = Files.readString(CARD)
                .replaceFirst("\"mapId\": 10000", "\"mapId\": 1010000")
                .replace("\"npcId\": 22000", "\"npcId\": 22001");
        Path invalid = tempDir.resolve("scope.json");
        Files.writeString(invalid, json);

        AmherstPlanValidationException failure = assertThrows(AmherstPlanValidationException.class,
                () -> new AmherstPlanCardLoader().load(invalid));

        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.FORBIDDEN_MAP));
        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.path().equals("exitCriteria.forbiddenActions")));
    }

    @Test
    void rejectsDuplicateIdsExcludedQuestShanksAndOffIslandTravel() throws Exception {
        String json = Files.readString(CARD)
                .replaceFirst("\\{ \"kind\": \"quest-start\"",
                        "{ \"objectiveId\": \"duplicate\", \"kind\": \"quest-start\"")
                .replaceFirst("\\{ \"kind\": \"quest-complete\"",
                        "{ \"objectiveId\": \"duplicate\", \"kind\": \"quest-complete\"")
                .replaceFirst("\"questId\": 1021", "\"questId\": 1028")
                .replaceFirst("\"npcId\": 2000", "\"npcId\": 22000")
                .replaceFirst("\"mapId\": 20000", "\"mapId\": 999999999");
        Path invalid = tempDir.resolve("multiple-scope-errors.json");
        Files.writeString(invalid, json);

        AmherstPlanValidationException failure = assertThrows(AmherstPlanValidationException.class,
                () -> new AmherstPlanCardLoader().load(invalid));

        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.DUPLICATE_OBJECTIVE_ID));
        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.FORBIDDEN_QUEST));
        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.FORBIDDEN_NPC));
        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.FORBIDDEN_MAP));
    }

    @Test
    void everyExistingPlanKindMapsToOneTypedObjectiveCommand() throws Exception {
        AmherstPlanCard card = new AmherstPlanCardLoader().load(CARD);
        var fixture = new server.agents.testing.MutablePrimitiveGatewayFixture();
        AmherstObjectiveHandlerRegistry handlers = new AmherstObjectiveHandlerRegistry(fixture.gateway);

        for (AmherstPlanObjective objective : card.objectives()) {
            AmherstObjectiveExecution execution = handlers.create(card, objective);
            assertEquals(objective.objectiveId(), execution.objectiveId());
            assertFalse(execution.invocation().commandType().isBlank());
        }
    }

    @Test
    void nullJsonIsReportedAsStructuredMalformedInput() throws Exception {
        Path invalid = tempDir.resolve("null.json");
        Files.writeString(invalid, "null");

        AmherstPlanValidationException failure = assertThrows(AmherstPlanValidationException.class,
                () -> new AmherstPlanCardLoader().load(invalid));

        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.MALFORMED_JSON));
    }
}
