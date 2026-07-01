package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFormationStateRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTargetSnapshotServiceTest {
    @Test
    void capturesOwnerRawWhenNoOtherTargetExists() {
        Character owner = character(100, "Leader", new Point(10, 20));
        Character bot = character(200, "Agent", new Point(1, 2));
        BotEntry entry = new BotEntry(bot, owner, null);
        AgentBotFormationStateRuntime.setFollowOffsetX(entry, 60);

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
        BotEntry entry = new BotEntry(bot, owner, null);
        AgentBotModeStateRuntime.setFollowing(entry, true);
        AgentBotFormationStateRuntime.setFollowOffsetX(entry, -60);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(300, 400), false);

        AgentTargetSnapshot snapshot = AgentTargetSnapshotService.capture(
                entry,
                owner,
                AgentFormationService.defaultStagger(60, 120),
                (followBase, followAnchor, followAnchorPos, snapRange, map) -> new Point(followBase));

        assertEquals("move-target", snapshot.primaryTargetSource());
        assertEquals(new Point(300, 400), snapshot.primaryTargetPos());
        assertEquals(new Point(-50, 20), snapshot.followTargetPos());
    }

    private static Character character(int id, String name, Point position) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.getPosition()).thenReturn(new Point(position));
        when(character.getMapId()).thenReturn(100000000);
        when(character.getMap()).thenReturn(mock(MapleMap.class));
        return character;
    }
}
