package server.partner;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerRosterQueryServiceTest {
    @Test
    void rosterReportsEligibilityAndSpecificRuntimeRejections() {
        StubRepository repository = new StubRepository(List.of(
                new PartnerRosterCandidate(2, 1, 0, "Pio", 28, 410),
                new PartnerRosterCandidate(3, 1, 0, "Yoona", 17, 200),
                new PartnerRosterCandidate(4, 1, 0, "Mai", 35, 310)));
        StubAvailability availability = new StubAvailability(Set.of(3), Set.of(), Set.of(2, 3, 4));
        PartnerRosterQueryService service = new PartnerRosterQueryService(repository, availability);

        List<PartnerRosterEntry> roster = service.listRoster(1, 0, 1);

        assertEquals(3, roster.size());
        assertTrue(roster.get(0).eligible());
        assertFalse(roster.get(1).eligible());
        assertEquals("This character is currently online.", roster.get(1).rejectionReason());
        assertTrue(roster.get(2).eligible());
    }

    @Test
    void symmetricExistingPairRejectsNewRegistration() {
        StubRepository repository = new StubRepository(List.of(
                new PartnerRosterCandidate(2, 1, 0, "Pio", 28, 410),
                new PartnerRosterCandidate(3, 1, 0, "Yoona", 17, 200)));
        PartnerLink link = new PartnerLink(
                10, 1, 0, 1, 2, PartnerMode.DOUBLE_PARTNER, true, Instant.now(), Instant.now());
        repository.links.put(1, link);
        repository.links.put(2, link);
        PartnerRosterQueryService service = new PartnerRosterQueryService(
                repository,
                new StubAvailability(Set.of(), Set.of(), Set.of(2, 3)));

        List<PartnerRosterEntry> roster = service.listRoster(1, 0, 1);

        assertEquals("This character is already your registered Partner.", roster.get(0).rejectionReason());
        assertEquals("Unregister your current Partner before selecting another.", roster.get(1).rejectionReason());
    }

    @Test
    void pendingWorldTransferIsVisibleWithARejectionReason() {
        StubRepository repository = new StubRepository(List.of(
                new PartnerRosterCandidate(2, 1, 0, "Pio", 28, 410, true)));
        PartnerRosterQueryService service = new PartnerRosterQueryService(
                repository,
                new StubAvailability(Set.of(), Set.of(), Set.of(2)));

        PartnerRosterEntry entry = service.listRoster(1, 0, 1).getFirst();

        assertFalse(entry.eligible());
        assertEquals("This character has a pending world transfer.", entry.rejectionReason());
    }

    private static final class StubAvailability implements PartnerRosterQueryService.RuntimeAvailability {
        private final Set<Integer> online;
        private final Set<Integer> leased;
        private final Set<Integer> loadable;

        private StubAvailability(Set<Integer> online, Set<Integer> leased, Set<Integer> loadable) {
            this.online = online;
            this.leased = leased;
            this.loadable = loadable;
        }

        @Override
        public boolean isOnline(int characterId) {
            return online.contains(characterId);
        }

        @Override
        public boolean isLeased(int characterId) {
            return leased.contains(characterId);
        }

        @Override
        public boolean canLoadCanonicalProfile(int characterId) {
            return loadable.contains(characterId);
        }
    }

    private static final class StubRepository implements AdventurerPartnerRepository {
        private final List<PartnerRosterCandidate> candidates;
        private final Map<Integer, PartnerLink> links = new HashMap<>();

        private StubRepository(List<PartnerRosterCandidate> candidates) {
            this.candidates = new ArrayList<>(candidates);
        }

        @Override
        public List<PartnerRosterCandidate> findRosterCandidates(int accountId,
                                                                 int worldId,
                                                                 int excludedCharacterId) {
            return candidates;
        }

        @Override
        public Optional<PartnerLink> findActiveLinkForCharacter(int characterId) {
            return Optional.ofNullable(links.get(characterId));
        }

        @Override
        public Optional<PartnerRosterCandidate> findCharacter(int characterId) {
            return candidates.stream().filter(candidate -> candidate.characterId() == characterId).findFirst();
        }

        @Override
        public PartnerLink registerLink(int requestingCharacterId,
                                        int partnerCharacterId,
                                        PartnerMode preferredMode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updatePreferredMode(long linkId, PartnerMode preferredMode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disableLink(long linkId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PartnerSessionRecord createSession(long linkId,
                                                  int playerActorCharacterId,
                                                  int partnerCharacterId,
                                                  PartnerMode mode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateSession(long sessionId,
                                  ProfileOrientation orientation,
                                  long generation,
                                  PartnerLifecycleStatus status,
                                  String failureReason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void closeSession(long sessionId,
                                 ProfileOrientation orientation,
                                 long generation,
                                 PartnerLifecycleStatus terminalStatus,
                                 String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int recoverOpenSessions(String reason) {
            throw new UnsupportedOperationException();
        }
    }
}
