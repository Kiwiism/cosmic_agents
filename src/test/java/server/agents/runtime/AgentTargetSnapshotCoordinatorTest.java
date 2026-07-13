package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentFormationStateRuntime;
import server.agents.capabilities.movement.AgentTargetSnapshot;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTargetSnapshotCoordinatorTest {
    @Test
    void resolvesFollowAnchorFromRuntimeRegistrySiblings() {
        Character leader = character(100, "Leader", new Point(10, 20));
        Character agent = character(200, "Agent", new Point(1, 2));
        Character sibling = character(300, "Sibling", new Point(30, 40));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentRuntimeEntry siblingEntry = new AgentRuntimeEntry(sibling, leader, null);
        AgentRuntimeRegistry.clear();
        AgentRuntimeRegistry.registerEntry(leader.getId(), siblingEntry);
        AgentModeStateRuntime.startFollowing(entry, sibling.getId());

        try {
            assertSame(sibling, AgentTargetSnapshotCoordinator.resolveFollowAnchor(entry, leader));
        } finally {
            AgentRuntimeRegistry.clear();
        }
    }

    @Test
    void capturesTargetSnapshotFromRuntimeRegistryAndFormationState() {
        Character leader = character(100, "Leader", new Point(10, 20));
        Character agent = character(200, "Agent", new Point(1, 2));
        Character sibling = character(300, "Sibling", new Point(50, 60));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentRuntimeEntry siblingEntry = new AgentRuntimeEntry(sibling, leader, null);
        AgentFormationService.FormationState formation =
                new AgentFormationService.FormationState(AgentFormationService.FormationType.RIGHT, 40, 0);
        AgentRuntimeRegistry.clear();
        AgentFormationService.formationsByLeaderId().clear();
        AgentRuntimeRegistry.registerEntry(leader.getId(), siblingEntry);
        AgentFormationService.formationsByLeaderId().put(leader.getId(), formation);
        AgentModeStateRuntime.startFollowing(entry, sibling.getId());
        AgentFormationStateRuntime.setFollowOffsetX(entry, formation.offsetFor(0, 1));

        try {
            AgentTargetSnapshot snapshot = AgentTargetSnapshotCoordinator.captureTargetSnapshot(entry);

            assertEquals("Sibling", snapshot.followAnchorName());
            assertEquals(new Point(90, 60), snapshot.followBasePos());
            assertEquals("follow-target", snapshot.primaryTargetSource());
            assertEquals(formation, snapshot.formation());
        } finally {
            AgentRuntimeRegistry.clear();
            AgentFormationService.formationsByLeaderId().clear();
        }
    }

    private static Character character(int id, String name, Point position) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.isLoggedinWorld()).thenReturn(true);
        when(character.getPosition()).thenReturn(new Point(position));
        when(character.getMapId()).thenReturn(100000000);
        when(character.getMap()).thenReturn(mock(MapleMap.class));
        return character;
    }
}
