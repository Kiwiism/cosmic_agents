package server.partner;

import client.Character;
import client.Client;
import config.AdventurerPartnerConfig;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerTriggerPolicyTest {
    @Test
    void soloSwitchAcceptsHealthyCanonicalInteractionState() {
        ActivePartnerSession active = soloSession(character(true), character(true));

        PartnerTriggerPolicy.Result result = new PartnerTriggerPolicy().validate(config(), active);

        assertTrue(result.allowed());
    }

    @Test
    void deadSavingAndNpcConversationStatesAreRejected() {
        Character dead = character(false);
        assertFalse(new PartnerTriggerPolicy().validate(
                config(), soloSession(dead, character(true))).allowed());

        Character saving = character(true);
        when(saving.profileTransitionBlockReason()).thenReturn("A canonical profile save is in progress.");
        assertFalse(new PartnerTriggerPolicy().validate(
                config(), soloSession(saving, character(true))).allowed());

        Character talking = character(true);
        Client client = mock(Client.class);
        when(client.getCM()).thenReturn(mock(scripting.npc.NPCConversationManager.class));
        when(talking.getClient()).thenReturn(client);
        assertFalse(new PartnerTriggerPolicy().validate(
                config(), soloSession(talking, character(true))).allowed());
    }

    @Test
    void doubleSwitchExplainsThatPartnerMustReturnToTheSameMap() {
        Character human = character(true);
        Character partner = character(true);
        when(human.getProfileOwnerCharacterId()).thenReturn(10);
        when(partner.getProfileOwnerCharacterId()).thenReturn(20);
        when(partner.getName()).thenReturn("KiwiAgent");
        when(human.getMap()).thenReturn(mock(MapleMap.class));
        when(partner.getMap()).thenReturn(mock(MapleMap.class));
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        runtime.activate();
        PartnerLink link = new PartnerLink(
                5L, 1, 0, 10, 20, PartnerMode.DOUBLE_PARTNER,
                true, Instant.now(), Instant.now());
        ActivePartnerSession active = new ActivePartnerSession(
                link, runtime, human, partner, mock(AgentRuntimeEntry.class));

        PartnerTriggerPolicy.Result result = new PartnerTriggerPolicy().validate(config(), active);

        assertFalse(result.allowed());
        assertTrue(result.reason().contains("KiwiAgent is too far away"));
        assertTrue(result.reason().contains("same map"));
    }

    private static AdventurerPartnerConfig config() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.ENABLED = true;
        return config;
    }

    private static Character character(boolean alive) {
        Character character = mock(Character.class);
        when(character.isLoggedin()).thenReturn(true);
        when(character.isAlive()).thenReturn(alive);
        when(character.getProfileOwnerCharacterId()).thenReturn(alive ? 10 : 30);
        when(character.profileTransitionBlockReason()).thenReturn(null);
        return character;
    }

    private static ActivePartnerSession soloSession(Character human, Character dormant) {
        when(human.getProfileOwnerCharacterId()).thenReturn(10);
        when(dormant.getProfileOwnerCharacterId()).thenReturn(20);
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, ProfileLeaseRegistry.DETACHED_ACTOR,
                10, 20, PartnerMode.SOLO_TAG);
        runtime.activate();
        PartnerLink link = new PartnerLink(
                5L, 1, 0, 10, 20, PartnerMode.SOLO_TAG,
                true, Instant.now(), Instant.now());
        return new ActivePartnerSession(link, runtime, human, dormant, null);
    }
}
