package server.partner;

import client.Character;

public interface ProfilePresentationService {
    default void prepare(Character firstProfile, Character secondProfile) {
    }

    default void discardPrepared(Character firstProfile, Character secondProfile) {
    }

    default void clearTemporarySkills(Character humanActor) {
    }

    RefreshMetrics refresh(Character humanActor,
                           Character partnerActorOrDormantProfile,
                           PartnerMode mode,
                           Character.ProfileExchangeResult exchangeResult);

    record RefreshMetrics(int packetCount,
                          long packetBytes,
                          long refreshDurationNs) {
        public static RefreshMetrics none() {
            return new RefreshMetrics(0, 0L, 0L);
        }
    }
}
