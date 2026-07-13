package server.partner;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTransitionBarrierState;

import java.util.Map;
import java.util.List;

/** Atomic Partner profile-binding transition orchestration. */
public final class ProfileTransitionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ProfileTransitionCoordinator.class);

    private final ProfileLeaseRegistry leases;
    private final ProfilePresentationService presentation;
    private final PartnerProfileCacheInvalidator cacheInvalidator;
    private final PartnerJournalSink journal;
    private final ProfileExchangeOperation exchangeOperation;
    private final DerivedProfileRebuildOperation rebuildOperation;
    private final ProfileTransitionLockManager profileLocks;
    private final SoloTagBuffSharingService buffSharing;
    private final PartnerSessionSkillService sessionSkills;

    public ProfileTransitionCoordinator(ProfileLeaseRegistry leases,
                                        ProfilePresentationService presentation,
                                        PartnerProfileCacheInvalidator cacheInvalidator,
                                        PartnerJournalSink journal) {
        this(leases, presentation, cacheInvalidator, journal, Character::exchangeProfileBindings);
    }

    public ProfileTransitionCoordinator(ProfileLeaseRegistry leases,
                                        ProfilePresentationService presentation,
                                        PartnerProfileCacheInvalidator cacheInvalidator,
                                        PartnerJournalSink journal,
                                        PartnerSessionSkillService sessionSkills) {
        this(leases, presentation, cacheInvalidator, journal,
                Character::exchangeProfileBindings, Character::rebuildDerivedProfileStats,
                SoloTagBuffSharingService.INSTANCE, sessionSkills);
    }

    ProfileTransitionCoordinator(ProfileLeaseRegistry leases,
                                 ProfilePresentationService presentation,
                                 PartnerProfileCacheInvalidator cacheInvalidator,
                                 PartnerJournalSink journal,
                                 ProfileExchangeOperation exchangeOperation) {
        this(leases, presentation, cacheInvalidator, journal, exchangeOperation,
                Character::rebuildDerivedProfileStats, SoloTagBuffSharingService.INSTANCE);
    }

    ProfileTransitionCoordinator(ProfileLeaseRegistry leases,
                                 ProfilePresentationService presentation,
                                 PartnerProfileCacheInvalidator cacheInvalidator,
                                 PartnerJournalSink journal,
                                 ProfileExchangeOperation exchangeOperation,
                                 DerivedProfileRebuildOperation rebuildOperation) {
        this(leases, presentation, cacheInvalidator, journal, exchangeOperation,
                rebuildOperation, SoloTagBuffSharingService.INSTANCE);
    }

    ProfileTransitionCoordinator(ProfileLeaseRegistry leases,
                                 ProfilePresentationService presentation,
                                 PartnerProfileCacheInvalidator cacheInvalidator,
                                 PartnerJournalSink journal,
                                 ProfileExchangeOperation exchangeOperation,
                                 DerivedProfileRebuildOperation rebuildOperation,
                                 SoloTagBuffSharingService buffSharing) {
        this(leases, presentation, cacheInvalidator, journal, exchangeOperation,
                rebuildOperation, buffSharing, null);
    }

    ProfileTransitionCoordinator(ProfileLeaseRegistry leases,
                                 ProfilePresentationService presentation,
                                 PartnerProfileCacheInvalidator cacheInvalidator,
                                 PartnerJournalSink journal,
                                 ProfileExchangeOperation exchangeOperation,
                                 DerivedProfileRebuildOperation rebuildOperation,
                                 SoloTagBuffSharingService buffSharing,
                                 PartnerSessionSkillService sessionSkills) {
        this.leases = leases;
        this.presentation = presentation;
        this.cacheInvalidator = cacheInvalidator;
        this.journal = journal;
        this.exchangeOperation = exchangeOperation;
        this.rebuildOperation = rebuildOperation;
        this.profileLocks = new ProfileTransitionLockManager();
        this.buffSharing = buffSharing;
        this.sessionSkills = sessionSkills;
    }

    public void prepareProfiles(Character firstProfile, Character secondProfile) {
        presentation.prepare(firstProfile, secondProfile);
    }

    public void prepareSkillUnion(long sessionId,
                                  Character firstProfile,
                                  Character secondProfile) {
        if (sessionSkills != null) {
            sessionSkills.prepareUnion(sessionId, firstProfile, secondProfile);
        }
    }

    public void prepareSessionSkills(long sessionId,
                                     PartnerMode mode,
                                     Character humanProfile,
                                     Character partnerProfile) {
        if (sessionSkills == null) {
            return;
        }
        buffSharing.prepareSessionSkills(
                mode,
                humanProfile,
                partnerProfile,
                request -> sessionSkills.grant(sessionId, request));
    }

    public void prepareAgent(AgentRuntimeEntry entry, Character agentProfile) {
        cacheInvalidator.invalidate(entry, agentProfile);
    }

    public void discardPreparedProfiles(Character firstProfile, Character secondProfile) {
        presentation.discardPrepared(firstProfile, secondProfile);
    }

    public TransitionResult transition(PartnerSessionRuntime session,
                                       Character humanActor,
                                       Character partnerActorOrDormantProfile,
                                       AgentRuntimeEntry agentEntry,
                                       long expectedGeneration) {
        long startedNs = System.nanoTime();
        PartnerSessionRuntime.TransitionToken token;
        try {
            token = session.beginSwap(expectedGeneration);
        } catch (RuntimeException rejected) {
            return TransitionResult.rejected(rejected.getMessage());
        }

        AgentTransitionBarrierState.PauseLease agentPause = null;
        boolean exchanged = false;
        boolean profileTasksSuspended = false;
        boolean leasesRebound = false;
        boolean profileTasksRestored = false;
        boolean humanTransitionWindow = false;
        boolean partnerTransitionWindow = false;
        Character.ProfileExchangeResult exchangeResult = null;
        long cacheRefreshNs = 0L;
        SoloTagBuffSharingService.SharingPlan buffSharingPlan =
                SoloTagBuffSharingService.SharingPlan.none();
        try {
            humanActor.enterProfileTransitionWindow();
            humanTransitionWindow = true;
            partnerActorOrDormantProfile.enterProfileTransitionWindow();
            partnerTransitionWindow = true;
            validateBindingsAndLeases(session, token, humanActor, partnerActorOrDormantProfile);
            if (session.mode() == PartnerMode.DOUBLE_PARTNER) {
                if (agentEntry == null) {
                    throw new IllegalStateException("Double Partner Agent runtime is unavailable");
                }
                agentPause = agentEntry.transitionBarrierState().pauseAndDrain();
            }

            humanActor.suspendProfileRuntimeTasks();
            partnerActorOrDormantProfile.suspendProfileRuntimeTasks();
            profileTasksSuspended = true;
            buffSharingPlan = buffSharing.capture(
                    session.mode(), humanActor, partnerActorOrDormantProfile);

            try (ProfileTransitionLockManager.LockHandle ignored = profileLocks.lockProfiles(List.of(
                    token.before().playerActorProfileOwnerId(),
                    token.before().partnerSlotProfileOwnerId()))) {
                exchangeResult = exchangeOperation.exchange(humanActor, partnerActorOrDormantProfile);
            }
            exchanged = true;
            buffSharing.applyAfterExchange(
                    buffSharingPlan,
                    humanActor,
                    partnerActorOrDormantProfile,
                    request -> {
                        if (sessionSkills != null) {
                            sessionSkills.grant(session.sessionId(), request);
                        }
                    });

            Map<Integer, Integer> desiredLeases = session.profileToActorLeases(token.after());
            ProfileLeaseRegistry.LeaseResult leaseResult = leases.rebind(session.sessionId(), desiredLeases);
            if (!leaseResult.acquired()) {
                throw new IllegalStateException(leaseResult.rejectionReason());
            }
            leasesRebound = true;
            session.commitSwap(token);

            rebuildOperation.rebuild(humanActor);
            rebuildOperation.rebuild(partnerActorOrDormantProfile);

            humanActor.resumeProfileRuntimeTasks();
            if (session.mode() == PartnerMode.DOUBLE_PARTNER) {
                partnerActorOrDormantProfile.resumeProfileRuntimeTasks();
                profileTasksRestored = true;
                long cacheStartedNs = System.nanoTime();
                cacheInvalidator.invalidate(agentEntry, partnerActorOrDormantProfile);
                cacheRefreshNs = System.nanoTime() - cacheStartedNs;
            } else {
                partnerActorOrDormantProfile.suspendProfileRuntimeTasks();
                profileTasksRestored = true;
            }

            ProfilePresentationService.RefreshMetrics metrics;
            try {
                metrics = presentation.refresh(
                        humanActor, partnerActorOrDormantProfile, session.mode(), exchangeResult);
            } catch (RuntimeException refreshFailure) {
                journal.record(
                        session.sessionId(), session.bindings().orientation(), session.generation(),
                        PartnerLifecycleStatus.ACTIVE, "Client refresh failed: " + safeReason(refreshFailure));
                log.warn("Partner switch committed but presentation refresh failed session={} generation={}",
                        session.sessionId(), session.generation(), refreshFailure);
                return TransitionResult.committedWithRefreshFailure(
                        session.generation(), exchangeResult.lockDurationNs(),
                        agentPause == null ? 0L : agentPause.drainDurationNs(), safeReason(refreshFailure));
            }

            journal.record(
                    session.sessionId(), session.bindings().orientation(), session.generation(),
                    PartnerLifecycleStatus.ACTIVE, null);
            log.info("partner_switch accepted session={} generation={} leftOwner={} rightOwner={} "
                            + "agentPauseNs={} lockNs={} cacheRefreshNs={} refreshNs={} packets={} bytes={}",
                    session.sessionId(), session.generation(),
                    exchangeResult.leftProfileOwnerCharacterId(),
                    exchangeResult.rightProfileOwnerCharacterId(),
                    agentPause == null ? 0L : agentPause.drainDurationNs(),
                    exchangeResult.lockDurationNs(), cacheRefreshNs, metrics.refreshDurationNs(),
                    metrics.packetCount(), metrics.packetBytes());
            return TransitionResult.committed(
                    session.generation(), exchangeResult.lockDurationNs(),
                    agentPause == null ? 0L : agentPause.drainDurationNs(), metrics);
        } catch (RuntimeException failure) {
            if (!exchanged && bindingsMatch(
                    token.after(), humanActor, partnerActorOrDormantProfile)) {
                exchanged = true;
                exchangeResult = new Character.ProfileExchangeResult(
                        humanActor.getProfileOwnerCharacterId(),
                        partnerActorOrDormantProfile.getProfileOwnerCharacterId(),
                        humanActor.getProfileBindingGeneration(),
                        partnerActorOrDormantProfile.getProfileBindingGeneration(),
                        System.nanoTime() - startedNs);
                log.warn("Partner binding exchange became authoritative before a post-exchange failure "
                                + "session={} generation={}",
                        session.sessionId(), token.generation());
            }
            if (!exchanged) {
                if (profileTasksSuspended) {
                    humanActor.resumeProfileRuntimeTasks();
                    if (session.mode() == PartnerMode.DOUBLE_PARTNER) {
                        partnerActorOrDormantProfile.resumeProfileRuntimeTasks();
                    }
                }
                session.abortSwap(token);
                journal.record(
                        session.sessionId(), session.bindings().orientation(), session.generation(),
                        PartnerLifecycleStatus.ACTIVE, "Switch rejected: " + safeReason(failure));
                return TransitionResult.rejected(safeReason(failure));
            }

            log.error("Partner switch failed after binding exchange session={} generation={}",
                    session.sessionId(), session.generation(), failure);
            try {
                if (session.status() == PartnerLifecycleStatus.SWAPPING) {
                    session.commitSwap(token);
                }
                if (!leasesRebound) {
                    ProfileLeaseRegistry.LeaseResult recoveredLeases = leases.rebind(
                            session.sessionId(), session.profileToActorLeases(token.after()));
                    if (!recoveredLeases.acquired()) {
                        throw new IllegalStateException(recoveredLeases.rejectionReason());
                    }
                }
                rebuildOperation.rebuild(humanActor);
                rebuildOperation.rebuild(partnerActorOrDormantProfile);
                if (!profileTasksRestored) {
                    humanActor.resumeProfileRuntimeTasks();
                    if (session.mode() == PartnerMode.DOUBLE_PARTNER) {
                        partnerActorOrDormantProfile.resumeProfileRuntimeTasks();
                    } else {
                        partnerActorOrDormantProfile.suspendProfileRuntimeTasks();
                    }
                }
                if (session.mode() == PartnerMode.DOUBLE_PARTNER) {
                    long cacheStartedNs = System.nanoTime();
                    cacheInvalidator.invalidate(agentEntry, partnerActorOrDormantProfile);
                    cacheRefreshNs = System.nanoTime() - cacheStartedNs;
                }
                ProfilePresentationService.RefreshMetrics recoveredRefresh = presentation.refresh(
                        humanActor, partnerActorOrDormantProfile, session.mode(), exchangeResult);
                journal.record(
                        session.sessionId(), session.bindings().orientation(), session.generation(),
                        PartnerLifecycleStatus.ACTIVE,
                        "Recovered after post-exchange failure: " + safeReason(failure));
                log.warn("partner_switch post_exchange_recovered session={} generation={} cacheRefreshNs={} "
                                + "refreshNs={} packets={} bytes={}",
                        session.sessionId(), session.generation(), cacheRefreshNs,
                        recoveredRefresh.refreshDurationNs(), recoveredRefresh.packetCount(),
                        recoveredRefresh.packetBytes());
                return TransitionResult.committed(
                        session.generation(),
                        exchangeResult == null ? System.nanoTime() - startedNs : exchangeResult.lockDurationNs(),
                        agentPause == null ? 0L : agentPause.drainDurationNs(), recoveredRefresh);
            } catch (RuntimeException recoveryFailure) {
                failure.addSuppressed(recoveryFailure);
                journal.record(
                        session.sessionId(), session.bindings().orientation(), session.generation(),
                        PartnerLifecycleStatus.ACTIVE, "Post-exchange failure: " + safeReason(failure));
                return TransitionResult.committedWithRefreshFailure(
                        session.generation(),
                        exchangeResult == null ? System.nanoTime() - startedNs : exchangeResult.lockDurationNs(),
                        agentPause == null ? 0L : agentPause.drainDurationNs(), safeReason(failure));
            }
        } finally {
            RuntimeException cleanupFailure = null;
            try {
                if (agentPause != null) {
                    agentPause.close();
                }
            } catch (RuntimeException failure) {
                cleanupFailure = failure;
            }
            try {
                if (partnerTransitionWindow) {
                    partnerActorOrDormantProfile.exitProfileTransitionWindow();
                }
            } catch (RuntimeException failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
            try {
                if (humanTransitionWindow) {
                    humanActor.exitProfileTransitionWindow();
                }
            } catch (RuntimeException failure) {
                cleanupFailure = append(cleanupFailure, failure);
            }
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
        }
    }

    private void validateBindingsAndLeases(PartnerSessionRuntime session,
                                           PartnerSessionRuntime.TransitionToken token,
                                           Character humanActor,
                                           Character partnerActorOrDormantProfile) {
        if (humanActor == null || partnerActorOrDormantProfile == null) {
            throw new IllegalStateException("Both Partner profiles must be preloaded");
        }
        if (humanActor.getProfileOwnerCharacterId() != token.before().playerActorProfileOwnerId()
                || partnerActorOrDormantProfile.getProfileOwnerCharacterId()
                != token.before().partnerSlotProfileOwnerId()) {
            throw new IllegalStateException("Live profile bindings do not match the Partner session");
        }
        if (!leases.holds(session.sessionId(), token.before().playerActorProfileOwnerId())
                || !leases.holds(session.sessionId(), token.before().partnerSlotProfileOwnerId())) {
            throw new IllegalStateException("Partner profile lease is missing or stale");
        }
        String humanBlock = humanActor.profileTransitionBlockReason();
        if (humanBlock != null) {
            throw new IllegalStateException(humanBlock);
        }
        String partnerBlock = partnerActorOrDormantProfile.profileTransitionBlockReason();
        if (partnerBlock != null) {
            throw new IllegalStateException(partnerBlock);
        }
    }

    private static boolean bindingsMatch(PartnerSessionRuntime.ProfileBindings expected,
                                         Character humanActor,
                                         Character partnerActorOrDormantProfile) {
        return humanActor != null && partnerActorOrDormantProfile != null
                && humanActor.getProfileOwnerCharacterId()
                == expected.playerActorProfileOwnerId()
                && partnerActorOrDormantProfile.getProfileOwnerCharacterId()
                == expected.partnerSlotProfileOwnerId();
    }

    private static String safeReason(Throwable failure) {
        String message = failure.getMessage();
        String reason = message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
        return reason.length() <= 480 ? reason : reason.substring(0, 480);
    }

    private static RuntimeException append(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    @FunctionalInterface
    interface ProfileExchangeOperation {
        Character.ProfileExchangeResult exchange(Character left, Character right);
    }

    @FunctionalInterface
    interface DerivedProfileRebuildOperation {
        void rebuild(Character character);
    }

    public record TransitionResult(boolean committed,
                                   boolean presentationComplete,
                                   long generation,
                                   long lockDurationNs,
                                   long agentPauseDurationNs,
                                   ProfilePresentationService.RefreshMetrics refreshMetrics,
                                   String reason) {
        private static TransitionResult rejected(String reason) {
            return new TransitionResult(false, false, -1L, 0L, 0L,
                    ProfilePresentationService.RefreshMetrics.none(), reason);
        }

        private static TransitionResult committed(long generation,
                                                  long lockDurationNs,
                                                  long agentPauseDurationNs,
                                                  ProfilePresentationService.RefreshMetrics metrics) {
            return new TransitionResult(true, true, generation, lockDurationNs,
                    agentPauseDurationNs, metrics, null);
        }

        private static TransitionResult committedWithRefreshFailure(long generation,
                                                                    long lockDurationNs,
                                                                    long agentPauseDurationNs,
                                                                    String reason) {
            return new TransitionResult(true, false, generation, lockDurationNs,
                    agentPauseDurationNs, ProfilePresentationService.RefreshMetrics.none(), reason);
        }
    }
}
