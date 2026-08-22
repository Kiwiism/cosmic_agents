package server.agents.runtime.activity.control.proposal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorActionAvailability;
import server.agents.runtime.activity.control.AgentDirectorActivityProjection;
import server.agents.runtime.activity.control.AgentDirectorEnergySnapshot;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.AgentWorldDirectorApplication;
import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentDirectorProposalServiceTest {
    @TempDir Path directory;

    @Test
    void persistsAndExecutesOnlyAfterRevisionRecheck() {
        AgentDirectorProposalService service = new AgentDirectorProposalService(
                new AgentFileDirectorProposalStore(directory));
        AgentDirectorExecutiveView view = mock(AgentDirectorExecutiveView.class);
        AgentWorldContext context = context();
        AgentDirectorAction action = action();
        when(view.context()).thenReturn(context);
        when(view.contextRevision()).thenReturn("revision-1");
        when(view.actions()).thenReturn(List.of(action));
        when(view.energy()).thenReturn(new AgentDirectorEnergySnapshot(55, 20, 60, 10,
                "STEADY", 1_000L));
        when(view.activity()).thenReturn(new AgentDirectorActivityProjection(
                "idle", "awaiting decision", "", "", "", ""));

        AgentDirectorProposal proposal = service.propose(
                view, action.actionId(), AgentDirectorProposalSource.LLM,
                "safe next step", -5, 1_000L);
        assertEquals(AgentDirectorProposalStatus.PENDING,
                service.list(27, 1_001L).getFirst().status());

        AgentWorldDirectorApplication application = mock(AgentWorldDirectorApplication.class);
        AgentWorldDirectiveEnvelope envelope = mock(AgentWorldDirectiveEnvelope.class);
        when(application.view(27, 12, 1_002L)).thenReturn(view);
        when(application.execute(27, action.actionId(), "revision-1",
                "proposal:" + proposal.proposalId(), "safe next step", false, 1_002L))
                .thenReturn(envelope);

        var approved = service.approve(
                application, 27, proposal.proposalId(), false, 1_002L);
        assertEquals(AgentDirectorProposalStatus.EXECUTED, approved.proposal().status());
        verify(application).execute(27, action.actionId(), "revision-1",
                "proposal:" + proposal.proposalId(), "safe next step", false, 1_002L);
    }

    @Test
    void marksChangedContextStaleWithoutExecution() {
        AgentDirectorProposalService service = new AgentDirectorProposalService(
                new AgentFileDirectorProposalStore(directory));
        AgentDirectorExecutiveView proposedView = view("revision-1");
        AgentDirectorProposal proposal = service.proposeRecommended(
                proposedView, AgentDirectorProposalSource.POLICY, 1_000L);
        AgentWorldDirectorApplication application = mock(AgentWorldDirectorApplication.class);
        AgentDirectorExecutiveView changedView = view("revision-2");
        when(application.view(27, 12, 1_002L)).thenReturn(changedView);

        assertThrows(AgentDirectorProposalService.StaleProposalException.class,
                () -> service.approve(application, 27, proposal.proposalId(), false, 1_002L));
        assertEquals(AgentDirectorProposalStatus.STALE,
                service.list(27, 1_003L).getFirst().status());
    }

    private static AgentDirectorExecutiveView view(String revision) {
        AgentDirectorExecutiveView view = mock(AgentDirectorExecutiveView.class);
        when(view.context()).thenReturn(context());
        when(view.contextRevision()).thenReturn(revision);
        when(view.actions()).thenReturn(List.of(action()));
        when(view.energy()).thenReturn(new AgentDirectorEnergySnapshot(55, 20, 60, 10,
                "STEADY", 1_000L));
        when(view.activity()).thenReturn(new AgentDirectorActivityProjection(
                "idle", "awaiting decision", "", "", "", ""));
        return view;
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
}
