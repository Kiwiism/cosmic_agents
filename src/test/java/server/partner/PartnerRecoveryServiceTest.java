package server.partner;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerRecoveryServiceTest {
    @Test
    void disconnectRoutesCanonicalRecoveryThroughLifecycleService() {
        AdventurerPartnerService service = mock(AdventurerPartnerService.class);
        ActivePartnerSession active = activeSession();
        Character actor = active.humanActor();
        when(service.activeSessionForActor(10)).thenReturn(Optional.of(active));
        PartnerRecoveryService recovery = new PartnerRecoveryService(
                service, new ProfileLeaseRegistry());

        recovery.onDisconnect(actor, false);

        verify(service).releaseActive(active, "Player disconnect recovery");
    }

    @Test
    void diagnosticsExposeBindingGenerationWithoutPrivateProfileData() {
        AdventurerPartnerService service = mock(AdventurerPartnerService.class);
        ActivePartnerSession active = activeSession();
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        assertTrue(leases.acquire(7L, active.runtime().profileToActorLeases()).acquired());
        when(service.activeSessionForActor(10)).thenReturn(Optional.of(active));
        PartnerRecoveryService recovery = new PartnerRecoveryService(service, leases);

        String diagnostics = recovery.diagnose(active.humanActor());

        assertTrue(diagnostics.contains("session=7"));
        assertTrue(diagnostics.contains("orientation=CANONICAL"));
        assertTrue(diagnostics.contains("leasesValid=true/true"));
    }

    private static ActivePartnerSession activeSession() {
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        when(human.getId()).thenReturn(10);
        when(partner.getId()).thenReturn(20);
        when(human.getProfileOwnerCharacterId()).thenReturn(10);
        when(partner.getProfileOwnerCharacterId()).thenReturn(20);
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        runtime.activate();
        return new ActivePartnerSession(null, runtime, human, partner, null);
    }
}
