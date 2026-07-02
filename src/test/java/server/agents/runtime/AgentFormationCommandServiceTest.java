package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFormationCommandServiceTest {
    @Test
    void returnsFalseWhenMessageIsNotFormationCommand() {
        Character leader = leader(1);

        assertFalse(AgentFormationCommandService.handleFormationCommand(
                leader,
                "hello",
                hooks(List.of(), new ArrayList<>(), new AtomicReference<>(defaultFormation()))));
    }

    @Test
    void sendsHelpToLeaderWhenNoEntries() {
        Character leader = leader(1);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentFormationCommandService.handleFormationCommand(
                leader,
                "formation",
                hooks(List.of(), calls, new AtomicReference<>(defaultFormation())));

        assertTrue(handled);
        assertEquals(List.of("leader:formations: stagger/split/random/spread/left/right <px>, stack, tight, loose | snap <px/on/off>"), calls);
    }

    @Test
    void reportsSnapStatusToFirstEntry() {
        Character leader = leader(1);
        BotEntry first = new BotEntry(mock(Character.class), leader, null);
        List<String> calls = new ArrayList<>();
        AtomicReference<AgentFormationService.FormationState> state =
                new AtomicReference<>(new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 50, 75));

        boolean handled = AgentFormationCommandService.handleFormationCommand(
                leader,
                "form snap",
                hooks(List.of(first), calls, state));

        assertTrue(handled);
        assertEquals(List.of("entry:snap: on (75px)"), calls);
    }

    @Test
    void updatesSnapOnUsingConfiguredDefaultWhenCurrentlyOff() {
        Character leader = leader(1);
        BotEntry first = new BotEntry(mock(Character.class), leader, null);
        List<String> calls = new ArrayList<>();
        AtomicReference<AgentFormationService.FormationState> state =
                new AtomicReference<>(new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 50, 0));

        boolean handled = AgentFormationCommandService.handleFormationCommand(
                leader,
                "formation snap on",
                hooks(List.of(first), calls, state));

        assertTrue(handled);
        assertEquals(120, state.get().snapRange());
        assertEquals(List.of("entry:snap: on (120px)"), calls);
    }

    @Test
    void appliesSpreadFormationWithExplicitPixels() {
        Character leader = leader(1);
        BotEntry first = new BotEntry(mock(Character.class), leader, null);
        List<String> calls = new ArrayList<>();
        AtomicReference<AgentFormationService.FormationState> state =
                new AtomicReference<>(defaultFormation());

        boolean handled = AgentFormationCommandService.handleFormationCommand(
                leader,
                "formation spread 90",
                hooks(List.of(first), calls, state));

        assertTrue(handled);
        assertEquals(AgentFormationService.FormationType.SPREAD, state.get().type());
        assertEquals(90, state.get().px());
        assertEquals(List.of("apply:SPREAD:90", "entry:formation: spread 90px"), calls);
    }

    private static AgentFormationCommandService.Hooks hooks(List<BotEntry> entries,
                                                            List<String> calls,
                                                            AtomicReference<AgentFormationService.FormationState> state) {
        return new AgentFormationCommandService.Hooks(
                leaderId -> entries,
                (leaderId, defaultFormation) -> state.get(),
                (leaderId, formation) -> state.set(formation),
                (tickEntries, formation) -> calls.add("apply:" + formation.type() + ":" + formation.px()),
                (entry, message) -> calls.add("entry:" + message),
                (leader, message) -> calls.add("leader:" + message),
                defaultFormation(),
                60,
                120);
    }

    private static AgentFormationService.FormationState defaultFormation() {
        return new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 60, 0);
    }

    private static Character leader(int id) {
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(id);
        return leader;
    }
}
