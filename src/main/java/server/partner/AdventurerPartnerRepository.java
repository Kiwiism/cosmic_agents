package server.partner;

import java.util.List;
import java.util.Optional;

public interface AdventurerPartnerRepository {
    List<PartnerRosterCandidate> findRosterCandidates(int accountId, int worldId, int excludedCharacterId);

    Optional<PartnerRosterCandidate> findCharacter(int characterId);

    Optional<PartnerLink> findActiveLinkForCharacter(int characterId);

    PartnerLink registerLink(int requestingCharacterId, int partnerCharacterId, PartnerMode preferredMode);

    void updatePreferredMode(long linkId, PartnerMode preferredMode);

    void disableLink(long linkId);

    PartnerSessionRecord createSession(long linkId,
                                       int playerActorCharacterId,
                                       int partnerCharacterId,
                                       PartnerMode mode);

    void updateSession(long sessionId,
                       ProfileOrientation orientation,
                       long generation,
                       PartnerLifecycleStatus status,
                       String failureReason);

    void closeSession(long sessionId,
                      ProfileOrientation orientation,
                      long generation,
                      PartnerLifecycleStatus terminalStatus,
                      String reason);

    int recoverOpenSessions(String reason);

    int recoverOpenSessionsForLink(long linkId, String reason);
}
