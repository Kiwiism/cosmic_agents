package server.partner;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartnerInteractionPolicyTest {
    @BeforeEach
    void resetRuntimeFallbacks() {
        clearRuntimeFallbacks();
    }

    @AfterEach
    void clearRuntimeFallbacks() {
        AgentRuntimeRegistry.entriesByLeaderId().clear();
        PartnerInteractionPolicy.clearPendingActivationsForTests();
    }

    @Test
    void doublePartnerTradeMustBeOpenedByItsPhysicalOwner() {
        PartnerRuntimeRegistry runtimes = new PartnerRuntimeRegistry();
        Character owner = character(10);
        Character partner = character(20);
        Character outsider = character(30);
        registerDoublePartner(runtimes, owner, partner);

        assertTrue(PartnerInteractionPolicy.mayInitiateTrade(runtimes, owner, partner));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(runtimes, outsider, partner));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(runtimes, partner, owner));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(runtimes, partner, outsider));

        assertTrue(PartnerInteractionPolicy.mayTradeTogether(runtimes, owner, partner));
        assertTrue(PartnerInteractionPolicy.mayTradeTogether(runtimes, partner, owner));
        assertFalse(PartnerInteractionPolicy.mayTradeTogether(runtimes, outsider, partner));
        assertFalse(PartnerInteractionPolicy.mayTradeTogether(runtimes, partner, outsider));
    }

    @Test
    void unrelatedCharactersAndSoloTagActorRemainUnrestricted() {
        PartnerRuntimeRegistry runtimes = new PartnerRuntimeRegistry();
        Character first = character(40);
        Character second = character(50);

        assertTrue(PartnerInteractionPolicy.mayInitiateTrade(runtimes, first, second));
        assertTrue(PartnerInteractionPolicy.mayTradeTogether(runtimes, first, second));
        assertTrue(PartnerInteractionPolicy.isOwnerOrUnprotected(runtimes, first, second));
    }

    @Test
    void lifecycleMarkerProtectsAgentBeforePartnerSessionRegistration() {
        PartnerRuntimeRegistry runtimes = new PartnerRuntimeRegistry();
        Character owner = character(60);
        Character refreshedOwnerActor = character(60);
        Character partner = character(70);
        Character outsider = character(80);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(partner, owner, null);
        entry.markPartnerManaged();
        AgentRuntimeRegistry.entriesByLeaderId().put(
                owner.getId(), new CopyOnWriteArrayList<>(List.of(entry)));

        assertTrue(PartnerInteractionPolicy.mayInitiateTrade(
                runtimes, refreshedOwnerActor, partner));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(
                runtimes, outsider, partner));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(
                runtimes, partner, owner));
        assertTrue(PartnerInteractionPolicy.mayTradeTogether(
                runtimes, refreshedOwnerActor, partner));
        assertFalse(PartnerInteractionPolicy.mayTradeTogether(
                runtimes, outsider, partner));
        assertTrue(PartnerInteractionPolicy.isProtectedPartnerCharacterId(partner.getId()));
    }

    @Test
    void nullParticipantsFailClosed() {
        PartnerRuntimeRegistry runtimes = new PartnerRuntimeRegistry();
        Character actor = character(90);

        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(runtimes, null, actor));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(runtimes, actor, null));
        assertFalse(PartnerInteractionPolicy.mayTradeTogether(runtimes, null, actor));
        assertFalse(PartnerInteractionPolicy.mayTradeTogether(runtimes, actor, null));
        assertFalse(PartnerInteractionPolicy.isOwnerOrUnprotected(runtimes, null, actor));
        assertFalse(PartnerInteractionPolicy.isOwnerOrUnprotected(runtimes, actor, null));
    }

    @Test
    void pendingActivationProtectsPartnerAndCompetingReservationsFailClosed() {
        PartnerRuntimeRegistry runtimes = new PartnerRuntimeRegistry();
        Character owner = character(100);
        Character competingOwner = character(110);
        Character partner = character(120);

        assertTrue(PartnerInteractionPolicy.reservePendingActivation(
                partner.getId(), owner.getId()));
        assertFalse(PartnerInteractionPolicy.reservePendingActivation(
                partner.getId(), competingOwner.getId()));

        assertTrue(PartnerInteractionPolicy.mayInitiateTrade(runtimes, owner, partner));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(
                runtimes, competingOwner, partner));
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(runtimes, partner, owner));

        PartnerInteractionPolicy.releasePendingActivation(
                partner.getId(), competingOwner.getId());
        assertFalse(PartnerInteractionPolicy.mayInitiateTrade(
                runtimes, competingOwner, partner));

        PartnerInteractionPolicy.releasePendingActivation(partner.getId(), owner.getId());
        assertTrue(PartnerInteractionPolicy.mayInitiateTrade(
                runtimes, competingOwner, partner));
    }

    private static void registerDoublePartner(PartnerRuntimeRegistry runtimes,
                                              Character owner,
                                              Character partner) {
        PartnerLink link = new PartnerLink(
                1L, 1, 0, 10, 20, PartnerMode.DOUBLE_PARTNER,
                true, Instant.now(), Instant.now());
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                2L, 1L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        ActivePartnerSession active = new ActivePartnerSession(
                link, runtime, owner, partner, null);
        assertTrue(runtimes.register(active));
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
