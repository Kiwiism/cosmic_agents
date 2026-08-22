package server.agents.runtime.activity.control.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorActionAvailability;
import server.agents.runtime.activity.control.AgentDirectorActivityProjection;
import server.agents.runtime.activity.control.AgentDirectorEnergySnapshot;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposalService;
import server.agents.runtime.activity.control.proposal.AgentFileDirectorProposalStore;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDirectorChatServiceTest {
    @TempDir Path directory;

    @Test
    void modelSelectionCreatesProposalButDoesNotExecute() {
        AgentDirectorExecutiveView view = mock(AgentDirectorExecutiveView.class);
        AgentDirectorAction action = action();
        when(view.context()).thenReturn(context());
        when(view.contextRevision()).thenReturn("revision-1");
        when(view.actions()).thenReturn(List.of(action));
        when(view.energy()).thenReturn(new AgentDirectorEnergySnapshot(32, 70, 50, 10,
                "LOW", 1_000L));
        when(view.activity()).thenReturn(new AgentDirectorActivityProjection(
                "questing", "rest", "", "", "", ""));
        AgentDirectorProposalProvider provider = (ignored, prompt) -> Optional.of(
                new AgentDirectorModelSelection(action.actionId(), "energy is low", 10,
                        "test-model", 4));
        AgentDirectorChatService service = new AgentDirectorChatService(provider,
                new AgentDirectorProposalService(new AgentFileDirectorProposalStore(directory)));

        AgentDirectorChatResult result = service.respond(view, "let Mira recover", 1_000L);

        assertNotNull(result.proposal());
        assertEquals("test-model", result.provider());
        assertEquals("town-life:101000000", result.proposal().actionId());
    }

    @Test
    void trainingQuestionReturnsThreeCatalogGroundedChoicesWithoutProposal() {
        AgentDirectorExecutiveView view = mock(AgentDirectorExecutiveView.class);
        when(view.context()).thenReturn(new AgentWorldContext(1, 1_000L, 27, "Mira",
                16, 200, 101000000, 500, 500, 800, 800, 10_000, true, false,
                Set.of(), Set.of(), null, "", "", "", "VICTORIA", Map.of()));
        when(view.actions()).thenReturn(List.of(
                huntingAction(100020100, "Henesys Pig Farm"),
                huntingAction(101010101, "The Tree That Grew II"),
                huntingAction(104030001, "Mushroom Garden")));
        AgentDirectorChatService service = new AgentDirectorChatService(null,
                new AgentDirectorProposalService(new AgentFileDirectorProposalStore(directory)));

        AgentDirectorChatResult result = service.respond(
                view, "For lv16, what are the top 3 maps we can consider grinding?", 1_000L);

        assertNull(result.proposal());
        assertEquals(3, result.recommendations().size());
        assertEquals("hunting-map:100020100", result.recommendations().get(0).actionId());
        assertTrue(result.recommendations().stream().allMatch(
                AgentDirectorChatRecommendation::selectable));
        assertEquals("deterministic-training-catalog", result.provider());
    }

    private static AgentWorldContext context() {
        return new AgentWorldContext(1, 1_000L, 27, "Mira", 20, 200,
                101000000, 500, 500, 800, 800, 10_000, true, false,
                Set.of(), Set.of(), null, "", "", "", "VICTORIA", Map.of());
    }

    private static AgentDirectorAction action() {
        return new AgentDirectorAction(
                "town-life:101000000", "Visit Ellinia",
                AgentDirectorActionAvailability.RECOMMENDED, "recover energy safely",
                AgentWorldDirectiveType.START_ACTIVITY,
                server.agents.runtime.activity.session.AgentActivityKind.TOWN_LIFE,
                server.agents.runtime.activity.world.AgentWorldActivityRequestType.TOWN_LIFE_VISIT,
                "town-life:101000000", Map.of("mapId", "101000000"),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 200, false);
    }

    private static AgentDirectorAction huntingAction(int mapId, String mapName) {
        return new AgentDirectorAction(
                "hunting-map:" + mapId, "Hunt — " + mapName,
                AgentDirectorActionAvailability.AVAILABLE, "level-appropriate training map",
                AgentWorldDirectiveType.START_ACTIVITY,
                server.agents.runtime.activity.session.AgentActivityKind.HUNTING,
                server.agents.runtime.activity.world.AgentWorldActivityRequestType.FIELD_VISIT,
                "training-map:" + mapId, Map.of("mapId", Integer.toString(mapId)),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 500, false);
    }
}
