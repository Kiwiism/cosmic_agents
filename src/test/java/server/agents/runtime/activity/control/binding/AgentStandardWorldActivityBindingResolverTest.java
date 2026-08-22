package server.agents.runtime.activity.control.binding;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.facade.AgentStandardLiveActivityFacades;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentStandardWorldActivityBindingResolverTest {
    @Test
    void bindsEligibleKpqVisitWithoutStartingTheAggregateDuringPreflight() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getLevel()).thenReturn(25);
        AgentWorldDirective directive = new AgentWorldDirective(
                1, "join-kpq", 27, AgentWorldDirectiveType.START_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, AgentActivityKind.PARTY_QUEST,
                AgentWorldActivityRequestType.PARTY_QUEST_VISIT, "kpq",
                Map.of("scenarioId", "kpq", "partySize", "4", "maximumRuns", "1"),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                10, 1_000L, 0L, "join lobby");
        AgentStandardWorldActivityBindingResolver resolver =
                new AgentStandardWorldActivityBindingResolver(
                        new AgentWorldDirectiveRequestCompiler(),
                        AgentStandardLiveActivityFacades.registry());

        AgentWorldActivityBinding binding = resolver.bind(
                directive, mock(AgentRuntimeEntry.class), agent, null, "");

        assertTrue(binding.targetPreflight().inspect(
                "27", AgentActivityKind.PARTY_QUEST, 1_001L).ready());
        assertTrue(AgentStandardWorldActivityBindingResolver.supportedTargets()
                .contains(AgentActivityKind.PARTY_QUEST));
    }
}
