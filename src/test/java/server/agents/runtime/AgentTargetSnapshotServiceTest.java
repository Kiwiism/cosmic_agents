package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentFormationStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTargetSnapshotServiceTest {
    @Test
    void capturesOwnerRawWhenNoOtherTargetExists() {
        Character owner = character(100, "Leader", new Point(10, 20));
        Character bot = character(200, "Agent", new Point(1, 2));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentFormationStateRuntime.setFollowOffsetX(entry, 60);

        AgentTargetSnapshot snapshot = AgentTargetSnapshotService.capture(
                entry,
                owner,
                AgentFormationService.defaultStagger(60, 120),
                (followBase, followAnchor, followAnchorPos, snapRange, map) -> new Point(followBase));

        assertEquals("owner-raw", snapshot.primaryTargetSource());
        assertEquals(new Point(10, 20), snapshot.primaryTargetPos());
        assertEquals(new Point(70, 20), snapshot.followBasePos());
        assertEquals("Leader", snapshot.followAnchorName());
    }

    @Test
    void moveTargetTakesPrecedenceOverFollowTarget() {
        Character owner = character(100, "Leader", new Point(10, 20));
        Character bot = character(200, "Agent", new Point(1, 2));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentFormationStateRuntime.setFollowOffsetX(entry, -60);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(300, 400), false);

        AgentTargetSnapshot snapshot = AgentTargetSnapshotService.capture(
                entry,
                owner,
                AgentFormationService.defaultStagger(60, 120),
                (followBase, followAnchor, followAnchorPos, snapRange, map) -> new Point(followBase));

        assertEquals("move-target", snapshot.primaryTargetSource());
        assertEquals(new Point(300, 400), snapshot.primaryTargetPos());
        assertEquals(new Point(-50, 20), snapshot.followTargetPos());
    }

    @Test
    void resolvesFollowAnchorAndFormationFromRuntimeInputs() {
        Character owner = character(100, "Leader", new Point(10, 20));
        Character bot = character(200, "Agent", new Point(1, 2));
        Character sibling = character(300, "Sibling", new Point(50, 60));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentRuntimeEntry siblingEntry = new AgentRuntimeEntry(sibling, owner, null);
        AgentModeStateRuntime.startFollowing(entry, sibling.getId());
        AgentFormationService.FormationState customFormation =
                new AgentFormationService.FormationState(AgentFormationService.FormationType.RIGHT, 40, 120);
        AgentFormationStateRuntime.setFollowOffsetX(entry, customFormation.offsetFor(0, 1));
        AgentTargetSnapshot snapshot = AgentTargetSnapshotService.capture(
                entry,
                List.of(entry, siblingEntry),
                Map.of(owner.getId(), customFormation),
                AgentFormationService.defaultStagger(60, 120),
                (followBase, followAnchor, followAnchorPos, snapRange, map) -> new Point(followBase));

        assertEquals("Sibling", snapshot.followAnchorName());
        assertEquals(new Point(90, 60), snapshot.followBasePos());
        assertEquals(customFormation, snapshot.formation());
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
