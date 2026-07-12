package server.partner;

import java.time.Instant;

public record PartnerSessionRecord(long id,
                                   long linkId,
                                   int playerActorCharacterId,
                                   int partnerCharacterId,
                                   PartnerMode mode,
                                   ProfileOrientation orientation,
                                   long generation,
                                   PartnerLifecycleStatus status,
                                   Instant activatedAt,
                                   Instant lastTransitionAt,
                                   Instant closedAt,
                                   String failureReason) {
}
