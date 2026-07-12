package server.partner;

public record PartnerRosterEntry(int characterId,
                                 String name,
                                 int level,
                                 int jobId,
                                 boolean eligible,
                                 String rejectionReason) {
    public static PartnerRosterEntry eligible(int characterId, String name, int level, int jobId) {
        return new PartnerRosterEntry(characterId, name, level, jobId, true, null);
    }

    public static PartnerRosterEntry rejected(int characterId,
                                              String name,
                                              int level,
                                              int jobId,
                                              String reason) {
        return new PartnerRosterEntry(characterId, name, level, jobId, false, reason);
    }
}
