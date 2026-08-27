package server.agents.capabilities.looting;

import client.Character;
import client.BotClient;
import client.Client;
import client.Job;
import org.junit.jupiter.api.Test;
import server.maps.MapItem;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentYetiClassBoxPriorityPolicyTest {
    @Test
    void allThreeBoxTiersMapToTheRelevantExplorerFamily() {
        assertTrue(AgentYetiClassBoxPriorityPolicy.matchesClass(character(110), 2022570));
        assertTrue(AgentYetiClassBoxPriorityPolicy.matchesClass(character(210), 2022576));
        assertTrue(AgentYetiClassBoxPriorityPolicy.matchesClass(character(320), 2022582));
        assertTrue(AgentYetiClassBoxPriorityPolicy.matchesClass(character(410), 2022583));
        assertTrue(AgentYetiClassBoxPriorityPolicy.matchesClass(character(520), 2022584));
        assertFalse(AgentYetiClassBoxPriorityPolicy.matchesClass(character(110), 2022581));
    }

    @Test
    void nonmatchingAgentWaitsWhileRelevantPartyMemberCanLootImmediately() {
        Character warrior = character(110);
        Character magician = character(210);
        when(warrior.getPartyMembersOnline()).thenReturn(List.of(warrior, magician));
        when(magician.getPartyMembersOnline()).thenReturn(List.of(warrior, magician));
        MapItem magicianBox = drop(2022571);

        assertEquals(AgentYetiClassBoxPriorityPolicy.RELEVANT_CLASS_PRIORITY_MS,
                AgentLootEligibility.requiredTargetLootAgeMs(
                        warrior, magicianBox, 3_000L));
        assertEquals(3_000L, AgentLootEligibility.requiredTargetLootAgeMs(
                magician, magicianBox, 3_000L));
    }

    @Test
    void nonmatchingAgentUsesOrdinaryLootTimingWhenNoRelevantMemberIsPresent() {
        Character warrior = character(110);
        when(warrior.getPartyMembersOnline()).thenReturn(List.of(warrior));

        assertEquals(3_000L, AgentYetiClassBoxPriorityPolicy.minimumTargetAgeMs(
                warrior, drop(2022571), 3_000L));
    }

    @Test
    void everyAgentWaitsSevenSecondsForARelevantHumanPartyMember() {
        Character warrior = character(110);
        Character magicianAgent = character(210);
        Character magicianPlayer = human(210);
        List<Character> party = List.of(warrior, magicianAgent, magicianPlayer);
        when(warrior.getPartyMembersOnline()).thenReturn(party);
        when(magicianAgent.getPartyMembersOnline()).thenReturn(party);
        MapItem magicianBox = drop(2022571);

        assertEquals(AgentYetiClassBoxPriorityPolicy.HUMAN_CLASS_PRIORITY_MS,
                AgentLootEligibility.requiredTargetLootAgeMs(
                        warrior, magicianBox, 3_000L));
        assertEquals(AgentYetiClassBoxPriorityPolicy.HUMAN_CLASS_PRIORITY_MS,
                AgentLootEligibility.requiredTargetLootAgeMs(
                        magicianAgent, magicianBox, 3_000L));
    }

    private static Character character(int jobId) {
        Character character = mock(Character.class);
        when(character.getJob()).thenReturn(Job.getById(jobId));
        when(character.getMapId()).thenReturn(106021500);
        when(character.getClient()).thenReturn(new BotClient(0, 0));
        return character;
    }

    private static Character human(int jobId) {
        Character character = mock(Character.class);
        when(character.getJob()).thenReturn(Job.getById(jobId));
        when(character.getMapId()).thenReturn(106021500);
        when(character.getClient()).thenReturn(mock(Client.class));
        return character;
    }

    private static MapItem drop(int itemId) {
        MapItem drop = mock(MapItem.class);
        when(drop.getItemId()).thenReturn(itemId);
        return drop;
    }
}
