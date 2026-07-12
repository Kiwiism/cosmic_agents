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

    default PartnerSessionSkillGrant grantTemporarySkill(
            long sessionId,
            int characterId,
            int skillId,
            int skillLevel,
            int masterLevel,
            long expiration,
            CharacterSkillState originalState) {
        throw new UnsupportedOperationException("Temporary Partner skills are not supported");
    }

    default List<PartnerSessionSkillGrant> findTemporarySkills(long sessionId) {
        return List.of();
    }

    default List<PartnerSessionSkillGrant> restoreTemporarySkills(long sessionId) {
        return List.of();
    }

    int recoverOpenSessions(String reason);

    int recoverOpenSessionsForLink(long linkId, String reason);

    record CharacterSkillState(int skillLevel, int masterLevel, long expiration) {
    }
}
