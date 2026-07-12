package server.partner;

import client.Character;
import client.profile.CharacterProfileRepository;
import client.profile.CosmicCharacterProfileRepository;
import config.AdventurerPartnerConfig;
import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTransitionBarrierState;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Player-facing Partner Program lifecycle and trigger service. */
public final class AdventurerPartnerService {
    private static final Logger log = LoggerFactory.getLogger(AdventurerPartnerService.class);
    private static final AdventurerPartnerService INSTANCE = productionService();

    private final AdventurerPartnerConfig config;
    private final AdventurerPartnerRepository repository;
    private final CharacterProfileRepository profiles;
    private final ProfileLeaseRegistry leases;
    private final PartnerRuntimeRegistry runtimes;
    private final PartnerRosterQueryService rosterQuery;
    private final PartnerAgentLifecycleBridge agents;
    private final PartnerTriggerPolicy triggerPolicy;
    private final ProfileTransitionCoordinator transitions;

    public static AdventurerPartnerService getInstance() {
        return INSTANCE;
    }

    AdventurerPartnerService(AdventurerPartnerConfig config,
                             AdventurerPartnerRepository repository,
                             CharacterProfileRepository profiles,
                             ProfileLeaseRegistry leases,
                             PartnerRuntimeRegistry runtimes,
                             PartnerRosterQueryService rosterQuery,
                             PartnerAgentLifecycleBridge agents,
                             PartnerTriggerPolicy triggerPolicy,
                             ProfileTransitionCoordinator transitions) {
        this.config = config;
        this.repository = repository;
        this.profiles = profiles;
        this.leases = leases;
        this.runtimes = runtimes;
        this.rosterQuery = rosterQuery;
        this.agents = agents;
        this.triggerPolicy = triggerPolicy;
        this.transitions = transitions;
    }

    public boolean isEnabled() {
        return config.enabled;
    }

    public boolean isEnabledForNpc(int npcId) {
        return config.enabled && config.npcId == npcId;
    }

    public List<PartnerRosterEntry> roster(Character player) {
        requireEnabled();
        List<PartnerRosterEntry> roster = rosterQuery.listRoster(
                player.getAccountID(), player.getWorld(), player.getId());
        return roster.stream().map(entry -> validateRosterProfile(player, entry)).toList();
    }

    public Optional<PartnerLink> registeredLink(Character player) {
        return repository.findActiveLinkForCharacter(player.getId());
    }

    public Optional<PartnerRosterCandidate> findCharacter(int characterId) {
        return repository.findCharacter(characterId);
    }

    public PartnerLink register(Character player, int partnerCharacterId) {
        requireEnabled();
        requireNoActiveSession(player.getId());
        PartnerRosterEntry selected = rosterQuery.listRoster(
                        player.getAccountID(), player.getWorld(), player.getId()).stream()
                .filter(entry -> entry.characterId() == partnerCharacterId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("That character is not in your Partner roster."));
        if (!selected.eligible()) {
            throw new IllegalStateException(selected.rejectionReason());
        }
        Character validationHolder = null;
        try {
            validationHolder = profiles.loadDetachedForValidation(
                    partnerCharacterId, player.getWorld(), player.getClient().getChannel());
            validateLoadedProfile(player, partnerCharacterId, validationHolder);
            if (rosterQuery.isOnline(partnerCharacterId)) {
                throw new IllegalStateException("The selected Partner became online during registration.");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(
                    "The selected Partner's canonical profile could not be loaded.", failure);
        } finally {
            player.reattachAccountPersistenceOwner();
            if (validationHolder != null) {
                validationHolder.suspendProfileRuntimeTasks();
            }
        }
        PartnerMode initialMode = config.doublePartnerEnabled
                ? PartnerMode.DOUBLE_PARTNER : PartnerMode.SOLO_TAG;
        PartnerLink link = repository.registerLink(
                player.getId(), partnerCharacterId, initialMode);
        log.info("partner_registration registered link={} world={} characters=[{},{}]",
                link.id(), link.worldId(), link.firstCharacterId(), link.secondCharacterId());
        return link;
    }

    private PartnerRosterEntry validateRosterProfile(Character player, PartnerRosterEntry entry) {
        if (!entry.eligible()) {
            return entry;
        }
        Character validationHolder = null;
        try {
            validationHolder = profiles.loadDetachedForValidation(
                    entry.characterId(), player.getWorld(), player.getClient().getChannel());
            validateLoadedProfile(player, entry.characterId(), validationHolder);
            return entry;
        } catch (SQLException | RuntimeException failure) {
            log.info("partner_roster rejected character={} reason=canonical_profile_load",
                    entry.characterId());
            return PartnerRosterEntry.rejected(
                    entry.characterId(), entry.name(), entry.level(), entry.jobId(),
                    "This character's canonical profile could not be loaded.");
        } finally {
            player.reattachAccountPersistenceOwner();
            if (validationHolder != null) {
                validationHolder.suspendProfileRuntimeTasks();
            }
        }
    }

    public void changeMode(Character player, PartnerMode mode) {
        requireEnabled();
        requireModeEnabled(mode);
        requireNoActiveSession(player.getId());
        PartnerLink link = requireLink(player);
        repository.updatePreferredMode(link.id(), mode);
        log.info("partner_mode_changed link={} character={} mode={}", link.id(), player.getId(), mode);
    }

    public ActivePartnerSession activatePreferredMode(Character player) {
        PartnerLink link = requireLink(player);
        return activate(player, link.preferredMode());
    }

    public ActivePartnerSession activate(Character player, PartnerMode mode) {
        requireEnabled();
        requireModeEnabled(mode);
        requireNoActiveSession(player.getId());
        PartnerLink link = requireLink(player);
        int partnerCharacterId = link.partnerOf(player.getId());
        PartnerRosterCandidate partner = repository.findCharacter(partnerCharacterId)
                .orElseThrow(() -> new IllegalStateException("The registered Partner no longer exists."));
        validatePair(player, link, partner);
        rosterQuery.activationRejectionReason(
                        player.getAccountID(), player.getWorld(), player.getId(), partner, link)
                .ifPresent(reason -> {
                    throw new IllegalStateException(reason);
                });

        Character partnerHolder = null;
        PartnerAgentLifecycleBridge.SpawnedPartner spawned = null;
        PartnerSessionRecord journal = null;
        ActivePartnerSession active = null;
        AgentTransitionBarrierState.PauseLease activationPause = null;
        try {
            journal = repository.createSession(
                    link.id(), player.getId(), partnerCharacterId, mode);
            ProfileLeaseRegistry.LeaseResult initialLease = leases.acquire(
                    journal.id(),
                    Map.of(
                            player.getProfileOwnerCharacterId(), player.getId(),
                            partnerCharacterId, ProfileLeaseRegistry.DETACHED_ACTOR));
            if (!initialLease.acquired()) {
                throw new IllegalStateException(initialLease.rejectionReason());
            }
            if (rosterQuery.isOnline(partnerCharacterId)) {
                throw new IllegalStateException("The registered Partner became online during activation.");
            }

            if (mode == PartnerMode.SOLO_TAG) {
                partnerHolder = profiles.loadDetached(
                        partnerCharacterId, player.getWorld(), player.getClient().getChannel());
            } else {
                spawned = agents.spawnFollowing(player, partner.name());
                partnerHolder = spawned.character();
                activationPause = spawned.runtimeEntry().transitionBarrierState().pauseAndDrain();
                profiles.restoreTransientState(partnerHolder);
            }
            player.reattachAccountPersistenceOwner();
            validateLoadedProfile(player, partnerCharacterId, partnerHolder);
            transitions.prepareProfiles(player, partnerHolder);
            if (spawned != null) {
                transitions.prepareAgent(spawned.runtimeEntry(), partnerHolder);
            }

            PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                    journal.id(), link.id(), player.getId(),
                    mode == PartnerMode.DOUBLE_PARTNER ? partnerHolder.getId() : ProfileLeaseRegistry.DETACHED_ACTOR,
                    player.getProfileOwnerCharacterId(), partnerHolder.getProfileOwnerCharacterId(), mode);
            if (mode == PartnerMode.DOUBLE_PARTNER) {
                ProfileLeaseRegistry.LeaseResult attachedLease = leases.rebind(
                        journal.id(), runtime.profileToActorLeases());
                if (!attachedLease.acquired()) {
                    throw new IllegalStateException(attachedLease.rejectionReason());
                }
            }
            runtime.activate();
            active = new ActivePartnerSession(
                    link, runtime, player, partnerHolder, spawned == null ? null : spawned.runtimeEntry());
            if (!runtimes.register(active)) {
                throw new IllegalStateException("One of the Partner profiles already has an active session.");
            }
            repository.updateSession(
                    journal.id(), ProfileOrientation.CANONICAL, runtime.generation(),
                    PartnerLifecycleStatus.ACTIVE, null);
            if (mode == PartnerMode.SOLO_TAG) {
                partnerHolder.suspendProfileRuntimeTasks();
            }
            log.info("partner_activation link={} session={} player={} partner={} mode={}",
                    link.id(), journal.id(), player.getId(), partnerCharacterId, mode);
            return active;
        } catch (SQLException e) {
            cleanupFailedActivation(journal, active, spawned, partnerHolder, "Canonical profile load failed", e);
            throw new IllegalStateException("The Partner profile could not be loaded.", e);
        } catch (RuntimeException failure) {
            cleanupFailedActivation(journal, active, spawned, partnerHolder, safeReason(failure), failure);
            throw failure;
        } finally {
            player.reattachAccountPersistenceOwner();
            if (activationPause != null) {
                activationPause.close();
            }
        }
    }

    public TriggerResult handleSwitchTrigger(Character player, int skillId) {
        if (!config.enabled || !config.triggerSkillIds.contains(skillId)) {
            return TriggerResult.notHandled();
        }
        Optional<ActivePartnerSession> activeResult = runtimes.findByHumanActorId(player.getId());
        if (activeResult.isEmpty()) {
            return TriggerResult.notHandled();
        }
        ActivePartnerSession active = activeResult.get();
        log.info("partner_switch requested session={} generation={} actor={} skill={}",
                active.runtime().sessionId(), active.runtime().generation(), player.getId(), skillId);
        if (!active.tryEnterSwitchOperation()) {
            log.info("partner_switch rejected session={} generation={} reason=lifecycle_busy",
                    active.runtime().sessionId(), active.runtime().generation());
            return TriggerResult.rejected("A Partner lifecycle operation is already in progress.");
        }
        try {
            PartnerTriggerPolicy.Result validation = triggerPolicy.validate(config, active);
            if (!validation.allowed()) {
                log.info("partner_switch rejected session={} generation={} reason={}",
                        active.runtime().sessionId(), active.runtime().generation(), validation.reason());
                return TriggerResult.rejected(validation.reason());
            }
            long now = System.currentTimeMillis();
            if (!active.tryAcquireSwitchCooldown(now, config.switchCooldownMs)) {
                log.info("partner_switch rejected session={} generation={} reason=cooldown remainingMs={}",
                        active.runtime().sessionId(), active.runtime().generation(),
                        active.remainingSwitchCooldownMs(now));
                return TriggerResult.rejected(
                        "Partner switch is cooling down for " + active.remainingSwitchCooldownMs(now) + " ms.");
            }

            ProfileTransitionCoordinator.TransitionResult transition = transitions.transition(
                    active.runtime(), active.humanActor(), active.partnerActorOrDormantProfile(),
                    active.agentEntry(), active.runtime().generation());
            if (!transition.committed()) {
                log.info("partner_switch rejected session={} generation={} reason={}",
                        active.runtime().sessionId(), active.runtime().generation(), transition.reason());
                return TriggerResult.rejected(transition.reason());
            }
            return transition.presentationComplete()
                    ? TriggerResult.switched(config.applyOrdinaryTriggerBuff)
                    : TriggerResult.switchedWithRefreshWarning(transition.reason());
        } finally {
            active.exitLifecycleOperation();
        }
    }

    public void release(Character player, String reason) {
        ActivePartnerSession active = runtimes.findByHumanActorId(player.getId())
                .orElseThrow(() -> new IllegalStateException("No Partner session is active."));
        releaseActive(active, reason);
    }

    void releaseActive(ActivePartnerSession active, String reason) {
        active.enterLifecycleOperation();
        try {
            releaseActiveLocked(active, reason);
        } finally {
            active.exitLifecycleOperation();
        }
    }

    private void releaseActiveLocked(ActivePartnerSession active, String reason) {
        if (active.runtime().status().isTerminal()) {
            runtimes.remove(active);
            leases.releaseSession(active.runtime().sessionId());
            return;
        }
        if (active.runtime().status() != PartnerLifecycleStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Partner session is not releasable while " + active.runtime().status());
        }
        if (active.runtime().bindings().orientation() == ProfileOrientation.SWAPPED) {
            ProfileTransitionCoordinator.TransitionResult restore = transitions.transition(
                    active.runtime(), active.humanActor(), active.partnerActorOrDormantProfile(),
                    active.agentEntry(), active.runtime().generation());
            if (!restore.committed() || active.runtime().bindings().orientation() != ProfileOrientation.CANONICAL) {
                throw new IllegalStateException("Canonical Partner orientation could not be restored.");
            }
        }

        AgentTransitionBarrierState.PauseLease releasePause = null;
        if (active.runtime().mode() == PartnerMode.DOUBLE_PARTNER) {
            if (active.agentEntry() == null) {
                throw new IllegalStateException("Double Partner Agent runtime is unavailable for release");
            }
            releasePause = active.agentEntry().transitionBarrierState().pauseAndDrain();
        }
        try {
            long releaseGeneration = active.runtime().beginRelease();
            active.runtime().restoreCanonicalForRelease(releaseGeneration);
            RuntimeException failure = null;
            if (!active.isJournalClosed()) {
                try {
                    profiles.saveCanonical(active.humanActor());
                } catch (RuntimeException saveFailure) {
                    failure = saveFailure;
                }
                try {
                    profiles.saveCanonical(active.partnerActorOrDormantProfile());
                } catch (RuntimeException saveFailure) {
                    if (failure == null) {
                        failure = saveFailure;
                    } else {
                        failure.addSuppressed(saveFailure);
                    }
                }
            }
            if (failure != null) {
                deferRelease(active, releaseGeneration, reason, "Canonical save failed", failure);
                throw failure;
            }

            PartnerLifecycleStatus terminal = PartnerLifecycleStatus.CLOSED;
            if (!active.isJournalClosed()) {
                try {
                    repository.closeSession(
                            active.runtime().sessionId(), ProfileOrientation.CANONICAL,
                            active.runtime().generation(), terminal, reason);
                    active.markJournalClosed();
                } catch (RuntimeException journalFailure) {
                    deferRelease(active, releaseGeneration, reason, "Session journal close failed", journalFailure);
                    throw journalFailure;
                }
            }

            try {
                if (active.runtime().mode() == PartnerMode.DOUBLE_PARTNER) {
                    agents.release(new PartnerAgentLifecycleBridge.SpawnedPartner(
                            active.partnerActorOrDormantProfile(), active.agentEntry()));
                } else {
                    profiles.storeTransientStateForLogout(active.partnerActorOrDormantProfile());
                    active.partnerActorOrDormantProfile().suspendProfileRuntimeTasks();
                }
            } catch (RuntimeException releaseFailure) {
                deferRelease(active, releaseGeneration, reason, "Partner Agent release failed", releaseFailure);
                throw releaseFailure;
            }

            active.runtime().close(releaseGeneration, terminal);
            leases.releaseSession(active.runtime().sessionId());
            runtimes.remove(active);
            log.info("partner_release link={} session={} status={} reason={}",
                    active.link().id(), active.runtime().sessionId(), terminal, reason);
        } finally {
            if (releasePause != null) {
                releasePause.close();
            }
        }
    }

    private void deferRelease(ActivePartnerSession active,
                              long releaseGeneration,
                              String reason,
                              String stage,
                              RuntimeException failure) {
        try {
            active.runtime().abortRelease(releaseGeneration);
        } catch (RuntimeException stateFailure) {
            failure.addSuppressed(stateFailure);
        }
        if (!active.isJournalClosed()) {
            try {
                repository.updateSession(
                        active.runtime().sessionId(), ProfileOrientation.CANONICAL,
                        active.runtime().generation(), PartnerLifecycleStatus.ACTIVE,
                        stage + ": " + safeReason(failure));
            } catch (RuntimeException journalFailure) {
                failure.addSuppressed(journalFailure);
            }
        }
        log.warn("partner_release deferred link={} session={} stage={} reason={}",
                active.link().id(), active.runtime().sessionId(), stage, reason, failure);
    }

    Optional<ActivePartnerSession> activeSessionForActor(int actorCharacterId) {
        return runtimes.findByAnyActorId(actorCharacterId);
    }

    Optional<ActivePartnerSession> activeSessionForCharacter(int characterId) {
        Optional<ActivePartnerSession> byActor = runtimes.findByAnyActorId(characterId);
        return byActor.isPresent() ? byActor : runtimes.findByProfileOwnerId(characterId);
    }

    public void unregister(Character player) {
        requireEnabled();
        requireNoActiveSession(player.getId());
        PartnerLink link = requireLink(player);
        repository.disableLink(link.id());
        log.info("partner_unregistered link={} character={}", link.id(), player.getId());
    }

    private void validatePair(Character player, PartnerLink link, PartnerRosterCandidate partner) {
        if (!link.contains(player.getId()) || link.accountId() != player.getAccountID()
                || link.worldId() != player.getWorld()) {
            throw new IllegalStateException("The Partner link does not match this account and world.");
        }
        if (partner.accountId() != player.getAccountID() || partner.worldId() != player.getWorld()) {
            throw new IllegalStateException("The registered Partner must share the account and world.");
        }
        if (player.getProfileOwnerCharacterId() != player.getId()) {
            throw new IllegalStateException("The player must be in canonical profile orientation before activation.");
        }
        if (player.isProfileTransitioning() || player.isProfileSaving()) {
            throw new IllegalStateException("Wait for the current profile operation to finish.");
        }
        if (leases.isLeased(player.getProfileOwnerCharacterId())) {
            throw new IllegalStateException("The player's canonical profile is already leased.");
        }
        if (leases.isLeased(partner.characterId())) {
            throw new IllegalStateException("The Partner profile is already leased.");
        }
    }

    private void cleanupFailedActivation(PartnerSessionRecord journal,
                                         ActivePartnerSession active,
                                         PartnerAgentLifecycleBridge.SpawnedPartner spawned,
                                         Character partnerHolder,
                                         String reason,
                                         Throwable originalFailure) {
        if (active != null) {
            runtimes.remove(active);
        }
        if (journal != null) {
            leases.releaseSession(journal.id());
        }
        try {
            if (spawned != null) {
                agents.release(spawned);
            } else if (partnerHolder != null) {
                profiles.saveCanonical(partnerHolder);
                profiles.storeTransientStateForLogout(partnerHolder);
                partnerHolder.suspendProfileRuntimeTasks();
            }
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
        if (journal != null) {
            try {
                repository.closeSession(
                        journal.id(), ProfileOrientation.CANONICAL, 0L,
                        PartnerLifecycleStatus.FAILED, reason);
            } catch (RuntimeException journalFailure) {
                originalFailure.addSuppressed(journalFailure);
            }
        }
        log.warn("partner_activation failed link={} session={} reason={}",
                journal == null ? null : journal.linkId(), journal == null ? null : journal.id(), reason);
    }

    private void validateLoadedProfile(Character player, int expectedPartnerId, Character partnerHolder) {
        if (partnerHolder == null || partnerHolder.getId() != expectedPartnerId
                || partnerHolder.getProfileOwnerCharacterId() != expectedPartnerId
                || partnerHolder.getAccountID() != player.getAccountID()
                || partnerHolder.getWorld() != player.getWorld()) {
            throw new IllegalStateException("The canonical Partner profile failed ownership validation.");
        }
    }

    private PartnerLink requireLink(Character player) {
        return repository.findActiveLinkForCharacter(player.getId())
                .orElseThrow(() -> new IllegalStateException("No adventuring Partner is registered."));
    }

    private void requireNoActiveSession(int characterId) {
        if (runtimes.findByHumanActorId(characterId).isPresent()
                || runtimes.findByProfileOwnerId(characterId).isPresent()) {
            throw new IllegalStateException("End the active Partner session first.");
        }
    }

    private void requireModeEnabled(PartnerMode mode) {
        if ((mode == PartnerMode.SOLO_TAG && !config.soloTagEnabled)
                || (mode == PartnerMode.DOUBLE_PARTNER && !config.doublePartnerEnabled)) {
            throw new IllegalStateException("That Partner Program mode is disabled.");
        }
    }

    private void requireEnabled() {
        if (!config.enabled) {
            throw new IllegalStateException("The Adventurer Partner Program is disabled.");
        }
    }

    private static String safeReason(Throwable failure) {
        String message = failure.getMessage();
        String reason = message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
        return reason.length() <= 480 ? reason : reason.substring(0, 480);
    }

    private static AdventurerPartnerService productionService() {
        AdventurerPartnerRepository repository = JdbcAdventurerPartnerRepository.INSTANCE;
        ProfileLeaseRegistry leases = ProfileLeaseRegistry.global();
        PartnerRosterQueryService roster = new PartnerRosterQueryService(
                repository, new CosmicPartnerRuntimeAvailability(repository));
        return new AdventurerPartnerService(
                YamlConfig.config.adventurerPartner,
                repository,
                CosmicCharacterProfileRepository.INSTANCE,
                leases,
                PartnerRuntimeRegistry.global(),
                roster,
                CosmicPartnerAgentLifecycleBridge.INSTANCE,
                new PartnerTriggerPolicy(),
                new ProfileTransitionCoordinator(
                        leases,
                        CosmicProfilePresentationService.INSTANCE,
                        new PartnerProfileCacheInvalidator(),
                        new AsyncPartnerJournalSink(repository)));
    }

    public record TriggerResult(boolean handled,
                                boolean switched,
                                boolean applyOrdinaryTriggerBuff,
                                String message) {
        private static TriggerResult notHandled() {
            return new TriggerResult(false, false, false, null);
        }

        private static TriggerResult rejected(String message) {
            return new TriggerResult(true, false, false, message);
        }

        private static TriggerResult switched(boolean applyOrdinaryTriggerBuff) {
            return new TriggerResult(true, true, applyOrdinaryTriggerBuff, null);
        }

        private static TriggerResult switchedWithRefreshWarning(String message) {
            return new TriggerResult(true, true, false,
                    "Profiles switched, but the client refresh must be retried: " + message);
        }
    }
}
