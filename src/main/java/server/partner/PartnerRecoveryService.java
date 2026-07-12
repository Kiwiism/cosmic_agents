package server.partner;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ThreadManager;

import java.util.Optional;

/** Disconnect, Agent-failure, and administrative recovery boundary. */
public final class PartnerRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PartnerRecoveryService.class);
    private static final PartnerRecoveryService INSTANCE =
            new PartnerRecoveryService(AdventurerPartnerService.getInstance(), ProfileLeaseRegistry.global());

    private final AdventurerPartnerService service;
    private final ProfileLeaseRegistry leases;

    PartnerRecoveryService(AdventurerPartnerService service, ProfileLeaseRegistry leases) {
        this.service = service;
        this.leases = leases;
    }

    public static PartnerRecoveryService getInstance() {
        return INSTANCE;
    }

    public void onDisconnect(Character actor, boolean serverTransition) {
        if (actor == null) {
            return;
        }
        Optional<ActivePartnerSession> found = service.activeSessionForActor(actor.getId());
        if (found.isEmpty()) {
            return;
        }
        ActivePartnerSession active = found.get();
        if (active.runtime().status() == PartnerLifecycleStatus.RELEASING
                || active.runtime().status().isTerminal()) {
            return;
        }
        String reason = serverTransition ? "Channel transition recovery" : "Player disconnect recovery";
        recoverActive(active, actor.getId(), reason);
    }

    public void onAgentRuntimeRemoval(Character agent, String reason) {
        if (agent == null) {
            return;
        }
        Optional<ActivePartnerSession> found = service.activeSessionForActor(agent.getId());
        if (found.isEmpty()) {
            return;
        }
        ActivePartnerSession active = found.get();
        if (active.runtime().status() == PartnerLifecycleStatus.RELEASING
                || active.runtime().status().isTerminal()) {
            return;
        }
        ThreadManager.getInstance().newDatabaseTask(() ->
                recoverActive(active, agent.getId(), reason));
    }

    /**
     * Canonicalize and close a session before channel/Cash Shop/MTS code exports
     * actor-keyed buffs or saves the actor row. A failed recovery leaves the
     * session, profiles, and leases intact so the caller can safely abort.
     */
    public boolean recoverBeforeWorldExit(Character actor, String reason) {
        if (actor == null) {
            return true;
        }
        Optional<ActivePartnerSession> found = service.activeSessionForActor(actor.getId());
        if (found.isEmpty()) {
            return true;
        }
        ActivePartnerSession active = found.get();
        recoverActive(active, actor.getId(), reason);
        return active.runtime().status().isTerminal()
                && actor.getProfileOwnerCharacterId() == actor.getId()
                && !leases.isLeased(active.link().firstCharacterId())
                && !leases.isLeased(active.link().secondCharacterId());
    }

    private void recoverActive(ActivePartnerSession active, int actorId, String reason) {
        try {
            service.releaseActive(active, reason);
            log.info("partner_recovery completed session={} actor={} reason={}",
                    active.runtime().sessionId(), actorId, reason);
        } catch (RuntimeException failure) {
            if (active.runtime().status().isTerminal()) {
                log.error("partner_recovery closed with failure session={} actor={} reason={}",
                        active.runtime().sessionId(), actorId, reason, failure);
                return;
            }
            boolean requiresEffectCleanup = active.runtime().bindings().orientation()
                    == ProfileOrientation.SWAPPED;
            log.warn("partner_recovery retrying session={} actor={} reason={} effectCleanup={}",
                    active.runtime().sessionId(), actorId, reason, requiresEffectCleanup, failure);
            try {
                String retryReason = reason + " (retry)";
                if (requiresEffectCleanup) {
                    active.humanActor().discardUnsupportedProfileEffectsForRecovery();
                    active.partnerActorOrDormantProfile().discardUnsupportedProfileEffectsForRecovery();
                    retryReason = reason + " (forced effect cleanup)";
                }
                service.releaseActive(active, retryReason);
                log.info("partner_recovery retry completed session={} actor={} reason={}",
                        active.runtime().sessionId(), actorId, reason);
            } catch (RuntimeException forcedFailure) {
                failure.addSuppressed(forcedFailure);
                log.error("partner_recovery failed session={} actor={} reason={}",
                        active.runtime().sessionId(), actorId, reason, failure);
            }
        }
    }

    public String diagnose(Character actor) {
        if (actor == null) {
            return "No character is available for Partner diagnostics.";
        }
        Optional<ActivePartnerSession> found = service.activeSessionForActor(actor.getId());
        if (found.isEmpty()) {
            boolean leased = leases.isLeased(actor.getProfileOwnerCharacterId());
            return "No active Partner runtime for actor " + actor.getId()
                    + "; profileOwner=" + actor.getProfileOwnerCharacterId()
                    + "; leased=" + leased + ".";
        }
        ActivePartnerSession active = found.get();
        PartnerSessionRuntime runtime = active.runtime();
        return "Partner session=" + runtime.sessionId()
                + " link=" + runtime.linkId()
                + " mode=" + runtime.mode()
                + " status=" + runtime.status()
                + " orientation=" + runtime.bindings().orientation()
                + " generation=" + runtime.generation()
                + " humanActor=" + active.humanActor().getId()
                + " humanProfile=" + active.humanActor().getProfileOwnerCharacterId()
                + " partnerActor=" + runtime.partnerActorCharacterId()
                + " partnerProfile=" + active.partnerActorOrDormantProfile().getProfileOwnerCharacterId()
                + " leasesValid=" + leases.holds(runtime.sessionId(), runtime.bindings().playerActorProfileOwnerId())
                + "/" + leases.holds(runtime.sessionId(), runtime.bindings().partnerSlotProfileOwnerId()) + ".";
    }

    public String diagnoseCharacter(int characterId) {
        Optional<ActivePartnerSession> found = service.activeSessionForCharacter(characterId);
        if (found.isEmpty()) {
            return "No active Partner runtime for character/profile " + characterId
                    + "; leased=" + leases.isLeased(characterId) + ".";
        }
        return diagnose(found.get().humanActor());
    }

    public String recover(Character actor, String reason) {
        ActivePartnerSession active = service.activeSessionForActor(actor.getId())
                .orElseThrow(() -> new IllegalStateException("No active Partner session was found."));
        service.releaseActive(active, reason == null || reason.isBlank() ? "GM recovery" : reason);
        return "Recovered and closed Partner session " + active.runtime().sessionId() + ".";
    }

    public String recoverCharacter(int characterId, String reason) {
        ActivePartnerSession active = service.activeSessionForCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("No active Partner session was found."));
        service.releaseActive(active, reason == null || reason.isBlank() ? "GM recovery" : reason);
        return "Recovered and closed Partner session " + active.runtime().sessionId() + ".";
    }
}
