package server.partner;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PartnerRuntimeRegistry {
    private static final PartnerRuntimeRegistry GLOBAL = new PartnerRuntimeRegistry();

    private final ConcurrentHashMap<Integer, ActivePartnerSession> byHumanActorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ActivePartnerSession> byPartnerActorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ActivePartnerSession> byProfileOwnerId = new ConcurrentHashMap<>();

    public static PartnerRuntimeRegistry global() {
        return GLOBAL;
    }

    public boolean register(ActivePartnerSession session) {
        int humanActorId = session.humanActor().getId();
        int firstProfile = session.link().firstCharacterId();
        int secondProfile = session.link().secondCharacterId();
        synchronized (this) {
            if (byHumanActorId.containsKey(humanActorId)
                    || byProfileOwnerId.containsKey(firstProfile)
                    || byProfileOwnerId.containsKey(secondProfile)) {
                return false;
            }
            byHumanActorId.put(humanActorId, session);
            if (session.runtime().mode() == PartnerMode.DOUBLE_PARTNER) {
                byPartnerActorId.put(session.partnerActorOrDormantProfile().getId(), session);
            }
            byProfileOwnerId.put(firstProfile, session);
            byProfileOwnerId.put(secondProfile, session);
            return true;
        }
    }

    public Optional<ActivePartnerSession> findByHumanActorId(int characterId) {
        return Optional.ofNullable(byHumanActorId.get(characterId));
    }

    public Optional<ActivePartnerSession> findByProfileOwnerId(int characterId) {
        return Optional.ofNullable(byProfileOwnerId.get(characterId));
    }

    public Optional<ActivePartnerSession> findByAnyActorId(int characterId) {
        ActivePartnerSession human = byHumanActorId.get(characterId);
        return Optional.ofNullable(human != null ? human : byPartnerActorId.get(characterId));
    }

    public void remove(ActivePartnerSession session) {
        synchronized (this) {
            byHumanActorId.remove(session.humanActor().getId(), session);
            if (session.runtime().mode() == PartnerMode.DOUBLE_PARTNER) {
                byPartnerActorId.remove(session.partnerActorOrDormantProfile().getId(), session);
            }
            byProfileOwnerId.remove(session.link().firstCharacterId(), session);
            byProfileOwnerId.remove(session.link().secondCharacterId(), session);
        }
    }

    void clearForTests() {
        byHumanActorId.clear();
        byPartnerActorId.clear();
        byProfileOwnerId.clear();
    }
}
