package server.partner;

import client.Character;
import config.AdventurerPartnerConfig;
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
        assertTrue(menu.contains("Solo Tag Mode#k #r(Unprepared)"));
        assertTrue(menu.contains("Prepare Solo Tag"));
        assertTrue(menu.contains("Change to Double Partner Mode"));
        assertFalse(menu.contains("Invite my partner"));
        assertFalse(menu.contains("Change to Solo Tag Mode"));
    }

    @Test
    void preparedSoloModeDoesNotShowUnpreparedMarker() {
        overview(soloLink, AdventurerPartnerService.PartnerPresence.SOLO_TAG_READY);

        String menu = npc.mainMenu(player);

        assertTrue(menu.contains("Solo Tag ready"));
        assertFalse(menu.contains("(Unprepared)"));
        assertFalse(menu.contains("Prepare Solo Tag"));
    }

    @Test
    void changingLiveDoubleSessionToSoloRequiresLogoutConfirmation() {
        overview(doubleLink, AdventurerPartnerService.PartnerPresence.DOUBLE_PARTNER_ACTIVE);

        assertTrue(npc.soloChangeRequiresConfirmation(player));
        assertTrue(npc.soloChangeConfirmation(player).contains("remove the Partner Agent from the party"));
        assertTrue(npc.soloChangeConfirmation(player).contains("log them out"));

        overview(doubleLink, AdventurerPartnerService.PartnerPresence.ONLINE_INDEPENDENTLY);
        assertTrue(npc.soloChangeRequiresConfirmation(player));
        assertTrue(npc.soloChangeConfirmation(player).contains("will not interrupt that login"));
    }

    @Test
    void releaseConfirmationIsRequiredForSessionsAndOnlinePartners() {
        overview(doubleLink, AdventurerPartnerService.PartnerPresence.DOUBLE_PARTNER_ACTIVE);
        assertTrue(npc.releaseRequiresConfirmation(player));
        assertTrue(npc.releaseConfirmation(player).contains("log out cleanly"));

        overview(doubleLink, AdventurerPartnerService.PartnerPresence.ONLINE_INDEPENDENTLY);
        assertTrue(npc.releaseRequiresConfirmation(player));
        assertTrue(npc.releaseConfirmation(player).contains("will not interrupt that login"));
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

    @Test
    void enabledBuffSharingShowsPerCharacterStatusAndPurchaseOption() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.SOLO_TAG_BUFF_SHARING_ENABLED = true;
        config.SOLO_TAG_BUFF_SHARING_ITEM_ID = 1142073;
        config.SOLO_TAG_BUFF_SHARING_PRICE_MESOS = 10_000_000;
        SoloTagBuffSharingService sharing =
                new SoloTagBuffSharingService(config, (character, itemId) -> true);
        npc = new AdventurerPartnerNpcService(service, sharing);
        when(service.overview(player)).thenReturn(Optional.empty());

        String menu = npc.mainMenu(player);

        assertTrue(menu.contains("Solo Tag Self-Buff Bond: #dNot owned"));
        assertTrue(menu.contains("Purchase #t1142073# for 10,000,000 mesos"));
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
