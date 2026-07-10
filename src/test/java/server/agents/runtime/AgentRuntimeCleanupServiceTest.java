package server.agents.runtime;

import server.agents.capabilities.recovery.AgentLeaderSafetyService;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.equipment.AgentAutoEquipThrottle;
import client.Character;
import client.keybind.KeyBinding;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRuntimeCleanupServiceTest {
    @Test
    void removesAllAgentRuntimeStateForLeader() {
        Character leader = character(77);
        Character firstAgent = character(88);
        Character secondAgent = character(89);
        ScheduledFuture<?> firstTask = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondTask = mock(ScheduledFuture.class);
        AgentRuntimeEntry firstEntry = new AgentRuntimeEntry(firstAgent, leader, firstTask);
        AgentRuntimeEntry secondEntry = new AgentRuntimeEntry(secondAgent, leader, secondTask);

        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentFormationService.formationsByLeaderId().clear();
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().clear();
        AgentRuntimeRegistry.entriesByLeaderId().put(leader.getId(), new java.util.concurrent.CopyOnWriteArrayList<>(List.of(firstEntry, secondEntry)));
        AgentFormationService.formationsByLeaderId().put(leader.getId(),
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STACK, 0, 0));
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().put(leader.getId(), new Point(1, 2));

        try {
            AgentRuntimeCleanupService.removeAgentsForLeader(leader.getId());

            assertFalse(AgentRuntimeRegistry.entriesByLeaderId().containsKey(leader.getId()));
            assertFalse(AgentFormationService.formationsByLeaderId().containsKey(leader.getId()));
            assertFalse(AgentLeaderSafetyService.townClusterAnchorsByLeaderId().containsKey(leader.getId()));
            verify(firstTask).cancel(false);
            verify(secondTask).cancel(false);
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
            AgentFormationService.formationsByLeaderId().clear();
            AgentLeaderSafetyService.townClusterAnchorsByLeaderId().clear();
        }
    }

    @Test
    void removesAgentRuntimeStateByCharacterId() {
        Character leader = character(77);
        Character agent = character(88);
        ScheduledFuture<?> task = mock(ScheduledFuture.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, task);

        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentFormationService.formationsByLeaderId().clear();
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().clear();
        AgentRuntimeRegistry.entriesByLeaderId().put(leader.getId(), new java.util.concurrent.CopyOnWriteArrayList<>(List.of(entry)));
        AgentFormationService.formationsByLeaderId().put(leader.getId(),
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STACK, 0, 0));
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().put(leader.getId(), new Point(1, 2));

        try {
            assertTrue(AgentRuntimeCleanupService.removeAgentByCharacterId(agent.getId()));

            assertFalse(AgentRuntimeRegistry.entriesByLeaderId().containsKey(leader.getId()));
            assertFalse(AgentFormationService.formationsByLeaderId().containsKey(leader.getId()));
            assertFalse(AgentLeaderSafetyService.townClusterAnchorsByLeaderId().containsKey(leader.getId()));
            verify(task).cancel(false);
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
            AgentFormationService.formationsByLeaderId().clear();
            AgentLeaderSafetyService.townClusterAnchorsByLeaderId().clear();
        }
    }

    @Test
    void cleanupAgentRuntimeStateAlsoClearsBotOnlyAutopotState() {
        Character agent = character(88);
        Map<Integer, KeyBinding> keymap = new LinkedHashMap<>();
        keymap.put(91, new KeyBinding(2, 2000002));
        keymap.put(92, new KeyBinding(2, 2000003));
        when(agent.getKeymap()).thenReturn(keymap);
        doAnswer(invocation -> {
            int key = invocation.getArgument(0);
            KeyBinding binding = invocation.getArgument(1);
            keymap.put(key, binding);
            return null;
        }).when(agent).changeKeybinding(anyInt(), any(KeyBinding.class));

        AgentRuntimeCleanupService.cleanupAgentRuntimeState(agent);

        verify(agent).setAutopotHpAlert(0f);
        verify(agent).setAutopotMpAlert(0f);
        assertTrue(keymap.get(91).getType() == 7 && keymap.get(92).getType() == 7);
    }

    @Test
    void removalClearsAgentScopedThrottleState() {
        Character leader = character(77);
        Character agent = character(88);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(entry);
        AgentAutoEquipThrottle.clearAgentRuntimeState(agent.getId());

        try {
            assertTrue(AgentAutoEquipThrottle.shouldRun(agent, 1_000L, false));
            assertFalse(AgentAutoEquipThrottle.shouldRun(agent, 1_000L, false));

            assertTrue(AgentRuntimeCleanupService.removeAgentByCharacterId(agent.getId()));

            assertTrue(AgentAutoEquipThrottle.shouldRun(agent, 1_000L, false));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
            AgentAutoEquipThrottle.clearAgentRuntimeState(agent.getId());
        }
    }

    @Test
    void staleCharacterCleanupDoesNotRemoveReplacementSession() {
        Character leader = character(77);
        Character staleAgent = character(88);
        Character replacementAgent = character(88);
        when(staleAgent.getKeymap()).thenReturn(new LinkedHashMap<>());
        AgentRuntimeEntry replacementEntry = new AgentRuntimeEntry(replacementAgent, leader, null);
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        AgentRuntimeRegistry.mutableEntriesForLeader(leader.getId()).add(replacementEntry);

        try {
            assertFalse(AgentRuntimeCleanupService.cleanupAgentRuntimeState(staleAgent));
            assertSame(replacementEntry,
                    AgentRuntimeRegistry.findByCharacterId(leader.getId(), replacementAgent.getId()));
        } finally {
            AgentRuntimeRegistry.entriesByLeaderId().clear();
        }
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
