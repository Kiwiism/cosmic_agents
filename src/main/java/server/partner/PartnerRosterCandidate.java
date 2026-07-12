package server.partner;

public record PartnerRosterCandidate(int characterId,
                                     int accountId,
                                     int worldId,
                                     String name,
                                     int level,
                                     int jobId,
                                     boolean pendingWorldTransfer) {
    public PartnerRosterCandidate(int characterId,
                                  int accountId,
                                  int worldId,
                                  String name,
                                  int level,
                                  int jobId) {
        this(characterId, accountId, worldId, name, level, jobId, false);
    }
}
