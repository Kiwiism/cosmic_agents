package server.partner;

/** Database-backed skill state temporarily attached to a profile for one Partner session. */
public record PartnerSessionSkillGrant(long sessionId,
                                       int characterId,
                                       int skillId,
                                       Integer originalSkillLevel,
                                       Integer originalMasterLevel,
                                       Long originalExpiration,
                                       int grantedSkillLevel,
                                       int grantedMasterLevel,
                                       long grantedExpiration) {
    public boolean hadOriginalSkill() {
        return originalSkillLevel != null;
    }
}
