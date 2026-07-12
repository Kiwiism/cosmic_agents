package server.partner;

import client.Character;
import client.Client;
import client.Job;
import client.profile.CharacterProfileRepository;
import config.AdventurerPartnerConfig;
import constants.skills.Beginner;
import org.junit.jupiter.api.Test;
import server.TimerManager;

import java.awt.Point;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdventurerPartnerLifecycleIntegrationTest {
    @Test
    void soloSwitchReleaseAndReinvitePreserveActorsAndCanonicalSaveOwners() throws Exception {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.ENABLED = true;
        config.SWITCH_COOLDOWN_MS = 0L;
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        CharacterProfileRepository profiles = mock(CharacterProfileRepository.class);
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerRuntimeRegistry runtimes = new PartnerRuntimeRegistry();
        PartnerRosterQueryService.RuntimeAvailability availability =
                mock(PartnerRosterQueryService.RuntimeAvailability.class);
        PartnerAgentLifecycleBridge agents = mock(PartnerAgentLifecycleBridge.class);
        PartnerTriggerPolicy triggerPolicy = mock(PartnerTriggerPolicy.class);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        when(presentation.refresh(
                any(Character.class), any(Character.class),
                eq(PartnerMode.SOLO_TAG), any(Character.ProfileExchangeResult.class)))
                .thenReturn(new ProfilePresentationService.RefreshMetrics(1, 16L, 100L));
        List<JournalEvent> journalEvents = new ArrayList<>();
        ProfileTransitionCoordinator transitions = new ProfileTransitionCoordinator(
                leases,
                presentation,
                new PartnerProfileCacheInvalidator(),
                (sessionId, orientation, generation, status, reason) ->
                        journalEvents.add(new JournalEvent(orientation, generation, status)),
                Character::exchangeProfileBindings,
                ignored -> { });
        AdventurerPartnerService service = new AdventurerPartnerService(
                config,
                repository,
                profiles,
                leases,
                runtimes,
                new PartnerRosterQueryService(repository, availability),
                agents,
                triggerPolicy,
                transitions);

        Character player = character(10, "Pio", 28, Job.ASSASSIN, 30_030);
        Character partner = character(20, "Yoona", 17, Job.MAGICIAN, 20_020);
        Character reloadedPartner = character(20, "Yoona", 17, Job.MAGICIAN, 20_020);
        player.setPosition(new Point(100, 50));
        partner.setPosition(new Point(900, 80));
        Point playerPosition = new Point(player.getPosition());
        Point partnerPosition = new Point(partner.getPosition());
        PartnerLink link = new PartnerLink(
                5L, 1, 0, 10, 20, PartnerMode.SOLO_TAG,
                true, Instant.now(), Instant.now());
        PartnerSessionRecord firstJournal = session(7L);
        PartnerSessionRecord secondJournal = session(8L);
        when(repository.findActiveLinkForCharacter(10)).thenReturn(Optional.of(link));
        when(repository.findActiveLinkForCharacter(20)).thenReturn(Optional.of(link));
        when(repository.findCharacter(20)).thenReturn(Optional.of(
                new PartnerRosterCandidate(20, 1, 0, "Yoona", 17, Job.MAGICIAN.getId())));
        when(repository.createSession(5L, 10, 20, PartnerMode.SOLO_TAG))
                .thenReturn(firstJournal, secondJournal);
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner, reloadedPartner);
        when(availability.isOnline(20)).thenReturn(false);
        when(availability.isLeased(20)).thenReturn(false);
        when(availability.canLoadCanonicalProfile(20)).thenReturn(true);
        when(triggerPolicy.validate(any(), any()))
                .thenReturn(new PartnerTriggerPolicy.Result(true, null));
        List<Integer> savedOwners = new ArrayList<>();
        doAnswer(invocation -> {
            Character holder = invocation.getArgument(0);
            assertEquals(holder.getId(), holder.getProfileOwnerCharacterId());
            savedOwners.add(holder.getProfileOwnerCharacterId());
            return null;
        }).when(profiles).saveCanonical(any(Character.class));

        TimerManager.getInstance().start();
        try {
            ActivePartnerSession first = service.activate(player, PartnerMode.SOLO_TAG);

            AdventurerPartnerService.TriggerResult switched =
                    service.handleSwitchTrigger(player, Beginner.NIMBLE_FEET);

            assertTrue(switched.handled());
            assertTrue(switched.switched());
            assertTrue(switched.message() == null, switched.message());
            assertEquals(ProfileOrientation.SWAPPED, first.runtime().bindings().orientation());
            assertEquals(20, player.getProfileOwnerCharacterId());
            assertEquals(10, partner.getProfileOwnerCharacterId());
            assertEquals(Job.MAGICIAN, player.getJob());
            assertEquals(Job.ASSASSIN, partner.getJob());
            assertEquals(playerPosition, player.getPosition());
            assertEquals(partnerPosition, partner.getPosition());

            service.release(player, "release while swapped");

            assertEquals(PartnerLifecycleStatus.CLOSED, first.runtime().status());
            assertEquals(10, player.getProfileOwnerCharacterId());
            assertEquals(20, partner.getProfileOwnerCharacterId());
            assertFalse(leases.isLeased(10));
            assertFalse(leases.isLeased(20));
            verify(presentation, times(2)).refresh(
                    any(Character.class), any(Character.class),
                    eq(PartnerMode.SOLO_TAG), any(Character.ProfileExchangeResult.class));
            assertEquals(List.of(10, 20), savedOwners);

            ActivePartnerSession second = service.activate(player, PartnerMode.SOLO_TAG);
            service.release(player, "release after reinvite");

            assertEquals(PartnerLifecycleStatus.CLOSED, second.runtime().status());
            assertFalse(leases.isLeased(10));
            assertFalse(leases.isLeased(20));
            assertEquals(List.of(10, 20, 10, 20), savedOwners);
            verify(presentation, times(2)).discardPrepared(
                    any(Character.class), any(Character.class));
            assertTrue(journalEvents.stream().anyMatch(event ->
                    event.orientation() == ProfileOrientation.SWAPPED
                            && event.status() == PartnerLifecycleStatus.ACTIVE));
            verify(profiles, times(2)).loadDetached(20, 0, 1);
        } finally {
            player.suspendProfileRuntimeTasks();
            partner.suspendProfileRuntimeTasks();
            reloadedPartner.suspendProfileRuntimeTasks();
            TimerManager.getInstance().stop();
        }
    }

    private static PartnerSessionRecord session(long id) {
        return new PartnerSessionRecord(
                id, 5L, 10, 20, PartnerMode.SOLO_TAG,
                ProfileOrientation.CANONICAL, 0L, PartnerLifecycleStatus.ACTIVATING,
                Instant.now(), Instant.now(), null, null);
    }

    private static Character character(int id,
                                       String name,
                                       int level,
                                       Job job,
                                       int hair) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Client client = mock(Client.class);
        when(client.getChannel()).thenReturn(1);
        when(rs.getInt("id")).thenReturn(id);
        when(rs.getInt("accountid")).thenReturn(1);
        when(rs.getString("name")).thenReturn(name);
        when(rs.getInt("level")).thenReturn(level);
        when(rs.getInt("job")).thenReturn(job.getId());
        when(rs.getInt("hair")).thenReturn(hair);
        when(rs.getInt("skincolor")).thenReturn(0);
        when(rs.getInt("str")).thenReturn(12);
        when(rs.getInt("dex")).thenReturn(5);
        when(rs.getInt("int")).thenReturn(4);
        when(rs.getInt("luk")).thenReturn(4);
        when(rs.getInt("hp")).thenReturn(50);
        when(rs.getInt("maxhp")).thenReturn(50);
        when(rs.getInt("mp")).thenReturn(5);
        when(rs.getInt("maxmp")).thenReturn(5);
        when(rs.getString("sp")).thenReturn("0,0,0,0,0,0,0,0,0,0");
        when(rs.getByte("world")).thenReturn((byte) 0);
        Character character = Character.loadCharacterEntryFromDB(rs, null);
        character.setClient(client);
        return character;
    }

    private record JournalEvent(ProfileOrientation orientation,
                                long generation,
                                PartnerLifecycleStatus status) {
    }
}
