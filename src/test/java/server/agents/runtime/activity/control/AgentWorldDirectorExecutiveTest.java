package server.agents.runtime.activity.control;

import client.Character;
import client.Job;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.world.AgentFileWorldDirectiveInbox;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldDirectiveStatus;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.journey.AgentFileJourneyJournalStore;
import server.agents.runtime.activity.outcome.AgentFileActivityOutcomeInbox;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;

import java.nio.file.Path;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWorldDirectorExecutiveTest {
    @TempDir Path directory;

    @Test
    void exposesConcreteHuntingActionsAndSubmitsWithRevisionAndIdempotency() {
        Character agent = agent();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions"));
        AgentFileWorldDirectiveInbox directives =
                new AgentFileWorldDirectiveInbox(directory.resolve("directives"));
        AgentFileActivityOutcomeInbox outcomes =
                new AgentFileActivityOutcomeInbox(directory.resolve("outcomes"));
        sessions.save(server.agents.runtime.activity.world.AgentWorldDirectorSession.create(
                27, AgentWorldDirectorMode.MANUAL, 1_000L));
        AgentWorldDirectorExecutive executive = new AgentWorldDirectorExecutive(
                new AgentWorldDirectorControlService(sessions, directives),
                sessions, directives,
                outcomes,
                new AgentFileJourneyJournalStore(directory.resolve("journey")),
                new AgentDirectorActionCatalog());

        AgentDirectorExecutiveView view = executive.view(entry, agent, 10, 1_001L);
        AgentDirectorAction hunting = view.actions().stream()
                .filter(action -> action.actionId().startsWith("hunting-map:"))
                .findFirst().orElseThrow();

        assertTrue(hunting.availability().executable());
        AgentDirectorAction progression = view.actions().stream()
                .filter(action -> action.actionId().equals("progression:level-16:mixed"))
                .findFirst().orElseThrow();
        assertTrue(progression.availability().executable());
        assertEquals("victoria-training", progression.requestId());
        assertEquals("16", progression.parameters().get("input.targetLevel"));
        assertEquals("true", progression.parameters().get("input.questsEnabled"));
        AgentDirectorAction individualQuest = view.actions().stream()
                .filter(action -> action.actionId().startsWith("individual-quest:"))
                .findFirst().orElseThrow();
        assertTrue(individualQuest.availability().executable());
        assertEquals("16", individualQuest.parameters().get("targetLevel"));
        assertFalse(view.contextRevision().isBlank());
        var submitted = executive.submit(entry, agent, hunting.actionId(),
                view.contextRevision(), "operator-27-hunt-1", "manual test",
                false, 1_002L);
        assertEquals(AgentWorldDirectiveStatus.PENDING, submitted.status());
        assertEquals(submitted, executive.submit(entry, agent, hunting.actionId(),
                executive.view(entry, agent, 10, 1_003L).contextRevision(),
                "operator-27-hunt-1", "manual test", false, 1_003L));
        AgentDirectorExecutiveView inFlight = executive.view(entry, agent, 10, 1_003L);
        assertTrue(inFlight.actions().stream().noneMatch(action ->
                action.availability().executable()));
        AgentDirectorAction town = inFlight.actions().stream()
                .filter(action -> action.actionId().startsWith("town-life:"))
                .findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> executive.submit(
                entry, agent, town.actionId(), inFlight.contextRevision(),
                "operator-27-town-1", "conflicting command", false, 1_004L));

        outcomes.publish("hunt:terminal", new AgentActivityTerminalOutcome(
                AgentActivityKind.HUNTING, AgentActivityPhase.COMPLETED,
                "hunt-session", "27", "done", false,
                1_000L, 1_004L, java.util.Map.of("kills", 10)), 1_004L);
        assertEquals(1, executive.view(entry, agent, 10, 1_005L)
                .pendingActivityOutcomes().size());
        assertTrue(executive.acknowledgeOutcome(
                27, "hunt:terminal", "reviewed", 1_006L).acknowledged());

        when(agent.getMapId()).thenReturn(0);
        AgentDirectorExecutiveView island = executive.view(entry, agent, 10, 1_007L);
        assertTrue(island.actions().stream().noneMatch(action ->
                action.actionId().startsWith("hunting-map:")
                        || action.actionId().startsWith("town-life:")));
        assertTrue(island.actions().stream().anyMatch(action ->
                action.requestId().equals("maple-island-full-mvp")));
    }

    @Test
    void exposesRemoteIndividualQuestsBecauseQuestRuntimeOwnsTravelToStart() {
        Character agent = agent();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory.resolve("remote-sessions"));
        sessions.save(server.agents.runtime.activity.world.AgentWorldDirectorSession.create(
                27, AgentWorldDirectorMode.MANUAL, 1_000L));
        AgentWorldDirectorExecutive executive = new AgentWorldDirectorExecutive(
                new AgentWorldDirectorControlService(sessions,
                        new AgentFileWorldDirectiveInbox(directory.resolve("remote-directives"))),
                sessions, new AgentFileWorldDirectiveInbox(directory.resolve("remote-directives")),
                new AgentFileActivityOutcomeInbox(directory.resolve("remote-outcomes")),
                new AgentFileJourneyJournalStore(directory.resolve("remote-journey")),
                new AgentDirectorActionCatalog());

        AgentDirectorExecutiveView view = executive.view(entry, agent, 10, 1_001L);
        int remoteQuestId = server.agents.progression.AgentVictoriaIndividualQuestCatalog
                .available(agent).stream()
                .filter(option -> !option.localStart())
                .findFirst().orElseThrow().questId();

        AgentDirectorAction remoteQuest = view.actions().stream()
                .filter(action -> action.actionId().equals("individual-quest:" + remoteQuestId))
                .findFirst().orElseThrow();
        assertTrue(remoteQuest.availability().executable());
    }

    private static Character agent() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getName()).thenReturn("DirectorAgent");
        when(agent.getLevel()).thenReturn(15);
        when(agent.getJob()).thenReturn(Job.WARRIOR);
        when(agent.getMapId()).thenReturn(104000000);
        when(agent.getHp()).thenReturn(300);
        when(agent.getMaxHp()).thenReturn(300);
        when(agent.getMp()).thenReturn(100);
        when(agent.getMaxMp()).thenReturn(100);
        when(agent.getMeso()).thenReturn(10_000);
        when(agent.isAlive()).thenReturn(true);
        when(agent.getExp()).thenReturn(50);
        when(agent.getRemainingAp()).thenReturn(0);
        when(agent.getRemainingSp()).thenReturn(0);
        EnumMap<InventoryType, Inventory> inventories = new EnumMap<>(InventoryType.class);
        for (InventoryType type : InventoryType.values()) {
            inventories.put(type, new Inventory(agent, type, (byte) 24));
        }
        when(agent.getInventory(org.mockito.ArgumentMatchers.any(InventoryType.class)))
                .thenAnswer(invocation -> inventories.get(invocation.getArgument(0)));
        return agent;
    }
}
