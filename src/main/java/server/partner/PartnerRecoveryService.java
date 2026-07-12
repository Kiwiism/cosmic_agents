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
        if (active.runtime().status() != PartnerLifecycleStatus.ACTIVE) {
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
        if (found.isEmpty() || found.get().runtime().status() != PartnerLifecycleStatus.ACTIVE) {
            return;
        }
        ActivePartnerSession active = found.get();
        ThreadManager.getInstance().newDatabaseTask(() ->
                recoverActive(active, agent.getId(), reason));
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
            log.warn("partner_recovery retrying after discarding unsupported effects session={} actor={} reason={}",
                    active.runtime().sessionId(), actorId, reason, failure);
            try {
                active.humanActor().discardUnsupportedProfileEffectsForRecovery();
                active.partnerActorOrDormantProfile().discardUnsupportedProfileEffectsForRecovery();
                service.releaseActive(active, reason + " (forced effect cleanup)");
                log.info("partner_recovery forced completion session={} actor={} reason={}",
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
