package server.partner;

import client.Character;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Ownership boundary for interactions with the physical Agent actor used by
 * Double Partner mode. Solo Tag has no second online actor and is unaffected.
 */
public final class PartnerInteractionPolicy {
    public static final String OWNER_ONLY_TRADE_MESSAGE =
            "That adventuring partner only accepts trades from their owner.";

    /**
     * Protects a partner character while the offline loader is publishing it
     * to the channel, world, and map but before its Agent runtime entry exists.
     */
    private static final ConcurrentHashMap<Integer, Integer> ACTIVATING_OWNER_BY_PARTNER_ID =
            new ConcurrentHashMap<>();

    private PartnerInteractionPolicy() {
    }

    /**
     * An Adventurer Partner never initiates a trade. Its owner must open the
     * trade window, which keeps autonomous Agent trade flows out of the
     * player's inventory.
     */
    public static boolean mayInitiateTrade(Character inviter, Character invitee) {
        return mayInitiateTrade(PartnerRuntimeRegistry.global(), inviter, invitee);
    }

    static boolean mayInitiateTrade(PartnerRuntimeRegistry runtimes,
                                    Character inviter,
                                    Character invitee) {
        if (inviter == null || invitee == null) {
            return false;
        }
        if (protectedOwnerId(runtimes, inviter) != null) {
            return false;
        }
        return isOwnerOrUnprotected(runtimes, inviter, invitee);
    }

    /** Revalidates an already-created trade before acceptance or completion. */
    public static boolean mayTradeTogether(Character first, Character second) {
        return mayTradeTogether(PartnerRuntimeRegistry.global(), first, second);
    }

    static boolean mayTradeTogether(PartnerRuntimeRegistry runtimes,
                                    Character first,
                                    Character second) {
        if (first == null || second == null) {
            return false;
        }
        return isOwnerOrUnprotected(runtimes, second, first)
                && isOwnerOrUnprotected(runtimes, first, second);
    }

    /** Used by other owner-only invitations such as a Party invite. */
    public static boolean isOwnerOrUnprotected(Character requester, Character target) {
        return isOwnerOrUnprotected(PartnerRuntimeRegistry.global(), requester, target);
    }

    public static boolean isProtectedPartner(Character actor) {
        return actor != null && protectedOwnerId(PartnerRuntimeRegistry.global(), actor) != null;
    }

    public static boolean isProtectedPartnerCharacterId(int characterId) {
        return protectedOwnerId(PartnerRuntimeRegistry.global(), characterId) != null;
    }

    static boolean isOwnerOrUnprotected(PartnerRuntimeRegistry runtimes,
                                        Character requester,
                                        Character target) {
        if (requester == null || target == null) {
            return false;
        }
        Integer ownerId = protectedOwnerId(runtimes, target);
        return ownerId == null || ownerId == requester.getId();
    }

    static boolean reservePendingActivation(int partnerCharacterId, int ownerCharacterId) {
        return ACTIVATING_OWNER_BY_PARTNER_ID.putIfAbsent(
                partnerCharacterId, ownerCharacterId) == null;
    }

    static void releasePendingActivation(int partnerCharacterId, int ownerCharacterId) {
        ACTIVATING_OWNER_BY_PARTNER_ID.remove(partnerCharacterId, ownerCharacterId);
    }

    static void clearPendingActivationsForTests() {
        ACTIVATING_OWNER_BY_PARTNER_ID.clear();
    }

    private static Integer protectedOwnerId(PartnerRuntimeRegistry runtimes, Character actor) {
        return protectedOwnerId(runtimes, actor.getId());
    }

    private static Integer protectedOwnerId(PartnerRuntimeRegistry runtimes, int actorId) {
        Integer activatingOwnerId = ACTIVATING_OWNER_BY_PARTNER_ID.get(actorId);
        if (activatingOwnerId != null) {
            return activatingOwnerId;
        }
        ActivePartnerSession session = runtimes.findByPartnerActorId(actorId).orElse(null);
        if (session != null) {
            return session.humanActor().getId();
        }
        return AgentRuntimeRegistry.partnerManagedOwnerIdByAgentCharacterId(actorId);
    }
}
