package server.agents.runtime;

import client.Character;
import net.server.world.Party;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotModeStateRuntime;
import server.bots.BotEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowAnchorServiceTest {
    @Test
    void returnsNullWhenLeaderIsMissing() {
        BotEntry entry = new BotEntry(character(200, true), null, null);

        assertNull(AgentFollowAnchorService.resolve(entry, null, List.of()));
    }

    @Test
    void returnsLeaderWhenNoExplicitTargetExists() {
        Character leader = character(100, true);
        BotEntry entry = new BotEntry(character(200, true), leader, null);

        assertSame(leader, AgentFollowAnchorService.resolve(entry, leader, List.of()));
    }

    @Test
    void returnsLeaderWhenTargetIsLeaderOrSelf() {
        Character leader = character(100, true);
        Character agent = character(200, true);
        BotEntry entry = new BotEntry(agent, leader, null);

        AgentBotModeStateRuntime.setFollowTargetId(entry, leader.getId());
        assertSame(leader, AgentFollowAnchorService.resolve(entry, leader, List.of()));

        AgentBotModeStateRuntime.setFollowTargetId(entry, agent.getId());
        assertSame(leader, AgentFollowAnchorService.resolve(entry, leader, List.of()));
    }

    @Test
    void returnsOnlinePartyMemberWhenTargetMatchesPartyMember() {
        Character leader = character(100, true);
        Character partyMember = character(300, true);
        Character siblingAgent = character(300, true);
        BotEntry entry = new BotEntry(character(200, true), leader, null);
        AgentBotModeStateRuntime.setFollowTargetId(entry, partyMember.getId());
        when(leader.getParty()).thenReturn(mock(Party.class));
        when(leader.getPartyMembersOnline()).thenReturn(List.of(partyMember));

        Character resolved = AgentFollowAnchorService.resolve(
                entry,
                leader,
                List.of(new BotEntry(siblingAgent, leader, null)));

        assertSame(partyMember, resolved);
    }

    @Test
    void returnsOnlineSiblingAgentWhenNoPartyMemberMatches() {
        Character leader = character(100, true);
        Character siblingAgent = character(300, true);
        BotEntry entry = new BotEntry(character(200, true), leader, null);
        AgentBotModeStateRuntime.setFollowTargetId(entry, siblingAgent.getId());

        Character resolved = AgentFollowAnchorService.resolve(
                entry,
                leader,
                List.of(new BotEntry(siblingAgent, leader, null)));

        assertSame(siblingAgent, resolved);
    }

    @Test
    void fallsBackToLeaderWhenTargetIsMissingOrOffline() {
        Character leader = character(100, true);
        Character offlineSibling = character(300, false);
        BotEntry entry = new BotEntry(character(200, true), leader, null);
        AgentBotModeStateRuntime.setFollowTargetId(entry, offlineSibling.getId());

        Character resolved = AgentFollowAnchorService.resolve(
                entry,
                leader,
                List.of(new BotEntry(offlineSibling, leader, null)));

        assertSame(leader, resolved);
    }

    @Test
    void resolveTargetReturnsNullWhenLeaderIsMissing() {
        BotEntry entry = new BotEntry(character(200, true), null, null);

        assertNull(AgentFollowAnchorService.resolveTarget(entry, null, 300, List.of()));
    }

    @Test
    void resolveTargetReturnsLeaderForMissingLeaderTargetOrSelfTarget() {
        Character leader = character(100, true);
        Character agent = character(200, true);
        BotEntry entry = new BotEntry(agent, leader, null);

        assertSame(leader, AgentFollowAnchorService.resolveTarget(entry, leader, 0, List.of()));
        assertSame(leader, AgentFollowAnchorService.resolveTarget(entry, leader, leader.getId(), List.of()));
        assertSame(leader, AgentFollowAnchorService.resolveTarget(entry, leader, agent.getId(), List.of()));
    }

    @Test
    void resolveTargetUsesOnlinePartyMemberBeforeSibling() {
        Character leader = character(100, true);
        Character partyMember = character(300, true);
        Character siblingAgent = character(300, true);
        BotEntry entry = new BotEntry(character(200, true), leader, null);
        when(leader.getParty()).thenReturn(mock(Party.class));
        when(leader.getPartyMembersOnline()).thenReturn(List.of(partyMember));

        Character resolved = AgentFollowAnchorService.resolveTarget(
                entry,
                leader,
                partyMember.getId(),
                List.of(new BotEntry(siblingAgent, leader, null)));

        assertSame(partyMember, resolved);
    }

    @Test
    void resolveTargetUsesOnlineSiblingWhenPartyDoesNotMatch() {
        Character leader = character(100, true);
        Character siblingAgent = character(300, true);
        BotEntry entry = new BotEntry(character(200, true), leader, null);

        Character resolved = AgentFollowAnchorService.resolveTarget(
                entry,
                leader,
                siblingAgent.getId(),
                List.of(new BotEntry(siblingAgent, leader, null)));

        assertSame(siblingAgent, resolved);
    }

    private static Character character(int id, boolean loggedIn) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.isLoggedinWorld()).thenReturn(loggedIn);
        return character;
    }
}
