package server.partner;

import client.Character;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdventurerPartnerNpcServiceTest {
    private AdventurerPartnerService service;
    private AdventurerPartnerNpcService npc;
    private Character player;
    private PartnerLink doubleLink;
    private PartnerLink soloLink;
    private PartnerRosterCandidate partner;

    @BeforeEach
    void setUp() {
        service = mock(AdventurerPartnerService.class);
        npc = new AdventurerPartnerNpcService(service);
        player = mock(Character.class);
        when(player.getId()).thenReturn(10);
        doubleLink = link(PartnerMode.DOUBLE_PARTNER);
        soloLink = link(PartnerMode.SOLO_TAG);
        partner = new PartnerRosterCandidate(20, 1, 0, "KiwiAgent", 40, 422);
    }

    @Test
    void unregisteredMenuOnlyOffersRegistrationAndHelpActions() {
        when(service.overview(player)).thenReturn(Optional.empty());

        String menu = npc.mainMenu(player);

        assertTrue(menu.contains("No adventuring Partner is registered"));
        assertTrue(menu.contains("Register an adventuring partner"));
        assertFalse(menu.contains("Unregister adventuring partner"));
        assertFalse(menu.contains("Invite my partner"));
        assertFalse(menu.contains("View my registered partner"));
    }

    @Test
    void inactiveDoubleMenuShowsPartnerDetailsToggleAndInvite() {
        overview(doubleLink, AdventurerPartnerService.PartnerPresence.OFFLINE);

        String menu = npc.mainMenu(player);

        assertTrue(menu.contains("Registered Partner: KiwiAgent"));
        assertTrue(menu.contains("Level 40 Shadower"));
        assertTrue(menu.contains("Status: #dOffline"));
        assertTrue(menu.contains("Current Mode: #bDouble Partner Mode"));
        assertTrue(menu.contains("Change to Solo Tag Mode"));
        assertTrue(menu.contains("Invite my partner"));
        assertTrue(menu.contains("Release my partner"));
        assertFalse(menu.contains("Prepare Solo Tag"));
    }

    @Test
    void activeDoubleMenuHidesDuplicateInvite() {
        overview(doubleLink, AdventurerPartnerService.PartnerPresence.DOUBLE_PARTNER_ACTIVE);

        String menu = npc.mainMenu(player);

        assertTrue(menu.contains("Double Partner active"));
        assertFalse(menu.contains("Invite my partner"));
        assertTrue(menu.contains("Change to Solo Tag Mode"));
        assertTrue(menu.contains("Release my partner"));
    }

    @Test
    void inactiveSoloMenuShowsPreparationAndDirectDoubleToggle() {
        overview(soloLink, AdventurerPartnerService.PartnerPresence.OFFLINE);

        String menu = npc.mainMenu(player);

        assertTrue(menu.contains("Current Mode: #bSolo Tag Mode"));
        assertTrue(menu.contains("Prepare Solo Tag"));
        assertTrue(menu.contains("Change to Double Partner Mode"));
        assertFalse(menu.contains("Invite my partner"));
        assertFalse(menu.contains("Change to Solo Tag Mode"));
    }

    @Test
    void recoveryMenuOnlyOffersResetInsteadOfAnotherActivation() {
        overview(doubleLink, AdventurerPartnerService.PartnerPresence.RECOVERY_REQUIRED);

        String menu = npc.mainMenu(player);

        assertTrue(menu.contains("Recovery required - use Release/reset"));
        assertTrue(menu.contains("Release my partner"));
        assertFalse(menu.contains("Invite my partner"));
        assertFalse(menu.contains("Prepare Solo Tag"));
    }

    private void overview(PartnerLink link, AdventurerPartnerService.PartnerPresence presence) {
        when(service.overview(player)).thenReturn(Optional.of(
                new AdventurerPartnerService.PartnerOverview(link, partner, link.preferredMode(), presence)));
    }

    private static PartnerLink link(PartnerMode mode) {
        return new PartnerLink(
                5L, 1, 0, 10, 20, mode, true, Instant.now(), Instant.now());
    }
}
