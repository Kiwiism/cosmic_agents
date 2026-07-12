package server.partner;

import java.time.Instant;

public record PartnerLink(long id,
                          int accountId,
                          int worldId,
                          int firstCharacterId,
                          int secondCharacterId,
                          PartnerMode preferredMode,
                          boolean enabled,
                          Instant createdAt,
                          Instant updatedAt) {
    public PartnerLink {
        if (firstCharacterId >= secondCharacterId) {
            throw new IllegalArgumentException("Partner character IDs must use canonical ascending order");
        }
    }

    public boolean contains(int characterId) {
        return firstCharacterId == characterId || secondCharacterId == characterId;
    }

    public int partnerOf(int characterId) {
        if (firstCharacterId == characterId) {
            return secondCharacterId;
        }
        if (secondCharacterId == characterId) {
            return firstCharacterId;
        }
        throw new IllegalArgumentException("Character " + characterId + " is not part of link " + id);
    }
}
