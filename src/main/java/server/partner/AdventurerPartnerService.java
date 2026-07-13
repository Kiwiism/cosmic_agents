package server.partner;

import client.Character;
import client.Skill;
import client.profile.CharacterProfileRepository;
import client.profile.CosmicCharacterProfileRepository;
import config.AdventurerPartnerConfig;
import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.TimerManager;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTransitionBarrierState;
import tools.PacketCreator;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final PartnerSessionSkillService sessionSkills;
    private final SoloTagBuffSharingService buffSharing;
    private final PreparationScheduler preparationScheduler;

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
        this(config, repository, profiles, leases, runtimes, rosterQuery, agents,
                triggerPolicy, transitions, new PartnerSessionSkillService(repository));
    }

    AdventurerPartnerService(AdventurerPartnerConfig config,
                             AdventurerPartnerRepository repository,
                             CharacterProfileRepository profiles,
                             ProfileLeaseRegistry leases,
                             PartnerRuntimeRegistry runtimes,
                             PartnerRosterQueryService rosterQuery,
                             PartnerAgentLifecycleBridge agents,
                             PartnerTriggerPolicy triggerPolicy,
                             ProfileTransitionCoordinator transitions,
                             PartnerSessionSkillService sessionSkills) {
        this(config, repository, profiles, leases, runtimes, rosterQuery, agents,
                triggerPolicy, transitions, sessionSkills,
                (task, delayMs) -> TimerManager.getInstance().schedule(task, delayMs));
    }

    AdventurerPartnerService(AdventurerPartnerConfig config,
                             AdventurerPartnerRepository repository,
                             CharacterProfileRepository profiles,
                             ProfileLeaseRegistry leases,
                             PartnerRuntimeRegistry runtimes,
                             PartnerRosterQueryService rosterQuery,
                             PartnerAgentLifecycleBridge agents,
                             PartnerTriggerPolicy triggerPolicy,
                             ProfileTransitionCoordinator transitions,
                             PartnerSessionSkillService sessionSkills,
                             PreparationScheduler preparationScheduler) {
        this.config = config;
        this.repository = repository;
        this.profiles = profiles;
        this.leases = leases;
        this.runtimes = runtimes;
        this.rosterQuery = rosterQuery;
        this.agents = agents;
        this.triggerPolicy = triggerPolicy;
        this.transitions = transitions;
        this.sessionSkills = sessionSkills;
        this.buffSharing = new SoloTagBuffSharingService(config);
        this.preparationScheduler = preparationScheduler;
    }

    public boolean isEnabled() {
        return config.ENABLED;
    }

    public boolean isEnabledForNpc(int npcId) {
        return config.ENABLED && config.NPC_ID == npcId;
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

    public Optional<PartnerOverview> overview(Character player) {
        Optional<PartnerLink> foundLink = registeredLink(player);
        if (foundLink.isEmpty()) {
            return Optional.empty();
        }
        PartnerLink link = foundLink.get();
        int partnerCharacterId = link.partnerOf(player.getId());
        PartnerRosterCandidate partner = repository.findCharacter(partnerCharacterId)
                .orElse(new PartnerRosterCandidate(
                        partnerCharacterId, link.accountId(), link.worldId(),
                        "Unknown character", 0, -1));
        Optional<ActivePartnerSession> active = runtimes.findByProfileOwnerId(player.getId());
        PartnerPresence presence;
        PartnerMode currentMode = link.preferredMode();
        if (active.isPresent()) {
            ActivePartnerSession session = active.get();
            currentMode = session.runtime().mode();
            if (session.runtime().status() != PartnerLifecycleStatus.ACTIVE) {
                presence = PartnerPresence.RECOVERY_REQUIRED;
            } else if (session.runtime().mode() == PartnerMode.SOLO_TAG) {
                presence = PartnerPresence.SOLO_TAG_READY;
            } else if (session.humanActor().getMap() == session.partnerActorOrDormantProfile().getMap()) {
                presence = PartnerPresence.DOUBLE_PARTNER_ACTIVE;
            } else {
                presence = PartnerPresence.DOUBLE_PARTNER_OTHER_MAP;
            }
        } else if (agents.hasPartnerAgent(player.getId(), partnerCharacterId)
                || leases.isLeased(link.firstCharacterId())
                || leases.isLeased(link.secondCharacterId())) {
            presence = PartnerPresence.RECOVERY_REQUIRED;
        } else if (rosterQuery.isOnline(partnerCharacterId)) {
            presence = PartnerPresence.ONLINE_INDEPENDENTLY;
        } else {
            presence = PartnerPresence.OFFLINE;
        }
        return Optional.of(new PartnerOverview(link, partner, currentMode, presence));
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
        PartnerMode initialMode = config.SOLO_TAG_ENABLED
                ? PartnerMode.SOLO_TAG : PartnerMode.DOUBLE_PARTNER;
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

    public ActivePartnerSession changeToSoloTag(Character player) {
        requireModeEnabled(PartnerMode.SOLO_TAG);
        PartnerLink link = requireLink(player);
        releaseOrReset(player, "Changed to Solo Tag Mode");
        repository.updatePreferredMode(link.id(), PartnerMode.SOLO_TAG);
        log.info("partner_mode_changed link={} character={} mode={}",
                link.id(), player.getId(), PartnerMode.SOLO_TAG);
        try {
            return activate(player, PartnerMode.SOLO_TAG);
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "Solo Tag Mode is selected, but it could not be prepared: " + safeReason(failure), failure);
        }
    }

    public void changeToDoublePartner(Character player) {
        requireModeEnabled(PartnerMode.DOUBLE_PARTNER);
        PartnerLink link = requireLink(player);
        releaseOrReset(player, "Changed to Double Partner Mode");
        repository.updatePreferredMode(link.id(), PartnerMode.DOUBLE_PARTNER);
        log.info("partner_mode_changed link={} character={} mode={}",
                link.id(), player.getId(), PartnerMode.DOUBLE_PARTNER);
    }

    public ActivePartnerSession prepareSoloTag(Character player) {
        PartnerLink link = requireLink(player);
        if (link.preferredMode() != PartnerMode.SOLO_TAG) {
            throw new IllegalStateException("Change to Solo Tag Mode before preparing it.");
        }
        Optional<ActivePartnerSession> active = runtimes.findByProfileOwnerId(player.getId());
        if (active.isPresent()) {
            if (active.get().runtime().mode() == PartnerMode.SOLO_TAG
                    && active.get().runtime().status() == PartnerLifecycleStatus.ACTIVE) {
                return active.get();
            }
            throw new IllegalStateException("Release the active Partner session first.");
        }
        return activate(player, PartnerMode.SOLO_TAG);
    }

    public ActivePartnerSession activatePreferredMode(Character player) {
        PartnerLink link = requireLink(player);
        return activate(player, link.preferredMode());
    }

    public ActivePartnerSession activate(Character player, PartnerMode mode) {
        return activate(player, mode, true);
    }

    ActivePartnerSession beginDoublePartnerInvite(Character player) {
        return activate(player, PartnerMode.DOUBLE_PARTNER, false);
    }

    private ActivePartnerSession activate(Character player,
                                           PartnerMode mode,
                                           boolean readyOnCompletion) {
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
                spawned = agents.spawnFollowing(player, partnerCharacterId, partner.name());
                partnerHolder = spawned.character();
                activationPause = spawned.runtimeEntry().transitionBarrierState().pauseAndDrain();
                profiles.restoreTransientState(partnerHolder);
            }
            player.reattachAccountPersistenceOwner();
            validateLoadedProfile(player, partnerCharacterId, partnerHolder);

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
            active = ActivePartnerSession.preparing(
                    link, runtime, player, partnerHolder, spawned == null ? null : spawned.runtimeEntry());
            if (!runtimes.register(active)) {
                throw new IllegalStateException("One of the Partner profiles already has an active session.");
            }
            repository.updateSession(
                    journal.id(), ProfileOrientation.CANONICAL, runtime.generation(),
                    PartnerLifecycleStatus.ACTIVE, null);
            transitions.prepareSkillUnion(journal.id(), player, partnerHolder);
            if (mode == PartnerMode.SOLO_TAG) {
                partnerHolder.suspendProfileRuntimeTasks();
                transitions.prepareSessionSkills(
                        journal.id(), mode, player, partnerHolder);
            }
            transitions.prepareProfiles(player, partnerHolder);
            if (spawned != null) {
                transitions.prepareAgent(spawned.runtimeEntry(), partnerHolder);
            }
            if (activationPause != null) {
                activationPause.close();
                activationPause = null;
            }
            if (readyOnCompletion) {
                completeSwitchPreparation(active, player, false);
            }
            log.info("partner_activation link={} session={} player={} partner={} mode={}",
                    link.id(), journal.id(), player.getId(), partnerCharacterId, mode);
            return active;
        } catch (SQLException e) {
            cleanupFailedActivation(
                    player, journal, active, spawned, partnerHolder, "Canonical profile load failed", e);
            throw new IllegalStateException("The Partner profile could not be loaded.", e);
        } catch (RuntimeException failure) {
            cleanupFailedActivation(
                    player, journal, active, spawned, partnerHolder, safeReason(failure), failure);
            throw failure;
        } finally {
            player.reattachAccountPersistenceOwner();
            if (activationPause != null) {
                try {
                    activationPause.close();
                } catch (RuntimeException pauseFailure) {
                    log.warn("partner_activation pause_release_failed player={}",
                            player.getId(), pauseFailure);
                }
            }
        }
    }

    public void completeDoublePartnerInvite(Character player) {
        Optional<ActivePartnerSession> found = runtimes.findByHumanActorId(player.getId());
        if (found.isEmpty() || found.get().runtime().mode() != PartnerMode.DOUBLE_PARTNER) {
            return;
        }
        ActivePartnerSession active = found.get();
        long delayMs = config.DOUBLE_PARTNER_READY_DELAY_MS;
        if (delayMs == 0L) {
            completeSwitchPreparation(active, player, true);
            return;
        }
        if (!active.tryScheduleSwitchPreparation()) {
            return;
        }
        try {
            preparationScheduler.schedule(
                    () -> completeScheduledSwitchPreparation(active, player), delayMs);
        } catch (RuntimeException schedulingFailure) {
            log.warn("partner_preparation scheduling_failed session={} delayMs={}",
                    active.runtime().sessionId(), delayMs, schedulingFailure);
            completeSwitchPreparation(active, player, true);
        }
    }

    private void completeScheduledSwitchPreparation(ActivePartnerSession active,
                                                    Character player) {
        active.enterLifecycleOperation();
        try {
            if (active.runtime().status() != PartnerLifecycleStatus.ACTIVE
                    || active.isJournalClosed()
                    || runtimes.findByHumanActorId(player.getId()).orElse(null) != active) {
                return;
            }
            completeSwitchPreparation(active, player, true);
        } finally {
            active.exitLifecycleOperation();
        }
    }

    private void completeSwitchPreparation(ActivePartnerSession active,
                                           Character player,
                                           boolean announce) {
        if (!active.markSwitchReady()) {
            return;
        }
        resetTriggerSkillCooldowns(player);
        if (announce) {
            player.message(active.partnerActorOrDormantProfile().getName()
                    + " is ready. Double Partner Mode can now switch roles with Nimble Feet.");
        }
    }

    private void resetTriggerSkillCooldowns(Character player) {
        for (int skillId : config.TRIGGER_SKILL_IDS) {
            player.removeCooldown(skillId);
            player.sendPacket(PacketCreator.skillCooldown(skillId, 0));
        }
    }

    public void onSkillPointAssigned(Character source, Skill skill) {
        if (!config.ENABLED || source == null || skill == null
                || source.isPartnerSessionBorrowedSkill(skill.getId())) {
            return;
        }
        Optional<ActivePartnerSession> found =
                runtimes.findByProfileOwnerId(source.getProfileOwnerCharacterId());
        if (found.isEmpty()) {
            return;
        }
        ActivePartnerSession active = found.get();
        active.enterLifecycleOperation();
        try {
            if (active.runtime().status() != PartnerLifecycleStatus.ACTIVE
                    || active.isJournalClosed()) {
                return;
            }
            int sourceOwnerId = source.getProfileOwnerCharacterId();
            Character recipient;
            if (active.humanActor().getProfileOwnerCharacterId() == sourceOwnerId) {
                recipient = active.partnerActorOrDormantProfile();
            } else if (active.partnerActorOrDormantProfile().getProfileOwnerCharacterId()
                    == sourceOwnerId) {
                recipient = active.humanActor();
            } else {
                return;
            }
            int level = source.getSkillLevel(skill);
            boolean synchronizedUnion = sessionSkills.synchronizeUnionSkill(
                    active.runtime().sessionId(), source, recipient, skill);
            if (!synchronizedUnion) {
                if (active.runtime().mode() != PartnerMode.SOLO_TAG
                        || !config.SOLO_TAG_BUFF_SHARING_ENABLED
                        || !buffSharing.isLearnedSelfBuffSkill(skill, level)) {
                    return;
                }
                sessionSkills.grant(
                        active.runtime().sessionId(),
                        new SoloTagBuffSharingService.SkillGrant(
                                recipient,
                                skill,
                                (byte) level,
                                source.getMasterLevel(skill),
                                source.getSkillExpiration(skill)));
            }
            log.info("partner_session_skill synchronized session={} sourceProfile={} "
                            + "recipientProfile={} skill={} level={}",
                    active.runtime().sessionId(), sourceOwnerId,
                    recipient.getProfileOwnerCharacterId(), skill.getId(), level);
        } catch (RuntimeException failure) {
            log.warn("partner_self_buff_skill synchronization_failed sourceProfile={} skill={}",
                    source.getProfileOwnerCharacterId(), skill.getId(), failure);
            source.message("Your skill was raised, but the Partner copy could not be updated. "
                    + "Release and prepare Solo Tag again before switching.");
        } finally {
            active.exitLifecycleOperation();
        }
    }

    public TriggerResult handleSwitchTrigger(Character player, int skillId) {
        if (!config.ENABLED || !config.TRIGGER_SKILL_IDS.contains(skillId)) {
            return TriggerResult.notHandled();
        }
        Optional<ActivePartnerSession> activeResult = runtimes.findByHumanActorId(player.getId());
        if (activeResult.isEmpty()) {
            return TriggerResult.notHandled();
        }
        ActivePartnerSession active = activeResult.get();
        log.info("partner_switch requested session={} generation={} actor={} skill={}",
                active.runtime().sessionId(), active.runtime().generation(), player.getId(), skillId);
        if (!active.isSwitchReady()) {
            if (active.tryAcquirePreparationNotice()) {
                log.info("partner_switch rejected session={} generation={} reason=partner_preparing",
                        active.runtime().sessionId(), active.runtime().generation());
                return TriggerResult.rejected(
                        "Your Partner is still logging in. Wait for Agent E's ready signal.");
            }
            return TriggerResult.rejected(null);
        }
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
            if (!active.tryAcquireSwitchCooldown(now, config.SWITCH_COOLDOWN_MS)) {
                long remainingMs = active.remainingSwitchCooldownMs(now);
                if (active.tryAcquireCooldownNotice(now)) {
                    log.info("partner_switch rejected session={} generation={} reason=cooldown remainingMs={}",
                            active.runtime().sessionId(), active.runtime().generation(), remainingMs);
                    return TriggerResult.rejected(
                            "Partner switch is cooling down for " + remainingMs + " ms.");
                }
                return TriggerResult.rejected(null);
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
                    ? TriggerResult.switched(config.APPLY_ORDINARY_TRIGGER_BUFF)
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

    public ReleaseResult releaseOrReset(Character player, String reason) {
        PartnerLink link = requireLink(player);
        Optional<ActivePartnerSession> active = runtimes.findByProfileOwnerId(player.getId());
        if (active.isPresent()) {
            long sessionId = active.get().runtime().sessionId();
            releaseActive(active.get(), reason);
            return new ReleaseResult(true, false, 0, 0, false, sessionId);
        }

        int partnerCharacterId = link.partnerOf(player.getId());
        boolean partnerAgentPresent = agents.hasPartnerAgent(player.getId(), partnerCharacterId);
        boolean independentlyOnline = !partnerAgentPresent && rosterQuery.isOnline(partnerCharacterId);
        boolean orphanAgentReleased = agents.releasePartnerAgent(player.getId(), partnerCharacterId);

        Set<Long> staleSessionIds = new HashSet<>();
        leases.leaseForProfile(link.firstCharacterId())
                .ifPresent(lease -> staleSessionIds.add(lease.sessionId()));
        leases.leaseForProfile(link.secondCharacterId())
                .ifPresent(lease -> staleSessionIds.add(lease.sessionId()));
        int recoveredSessions = repository.recoverOpenSessionsForLink(link.id(), reason);
        staleSessionIds.forEach(leases::releaseSession);
        player.reattachAccountPersistenceOwner();
        log.info("partner_reset link={} character={} orphanAgentReleased={} recoveredSessions={} "
                        + "staleLeaseSessions={} independentlyOnline={}",
                link.id(), player.getId(), orphanAgentReleased, recoveredSessions,
                staleSessionIds.size(), independentlyOnline);
        return new ReleaseResult(
                false, orphanAgentReleased, recoveredSessions, staleSessionIds.size(),
                independentlyOnline, null);
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
            active.markSwitchUnavailable();
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

            try {
                sessionSkills.restore(
                        active.runtime().sessionId(),
                        active.humanActor(),
                        active.partnerActorOrDormantProfile());
            } catch (RuntimeException skillRestoreFailure) {
                deferRelease(active, releaseGeneration, reason,
                        "Temporary Partner skill restoration failed", skillRestoreFailure);
                throw skillRestoreFailure;
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
            discardPreparedProfiles(active.humanActor(), active.partnerActorOrDormantProfile());
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
        PartnerLink link = requireLink(player);
        releaseOrReset(player, "Unregistered through Agent E");
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

    private void cleanupFailedActivation(Character player,
                                          PartnerSessionRecord journal,
                                          ActivePartnerSession active,
                                         PartnerAgentLifecycleBridge.SpawnedPartner spawned,
                                         Character partnerHolder,
                                         String reason,
                                         Throwable originalFailure) {
        if (active != null) {
            runtimes.remove(active);
        }
        discardPreparedProfiles(player, partnerHolder);
        boolean cleanupComplete = true;
        if (journal != null && partnerHolder != null) {
            try {
                sessionSkills.restore(journal.id(), player, partnerHolder);
            } catch (RuntimeException skillRestoreFailure) {
                originalFailure.addSuppressed(skillRestoreFailure);
                cleanupComplete = false;
            }
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
            cleanupComplete = false;
        }
        if (journal != null && cleanupComplete) {
            try {
                repository.closeSession(
                        journal.id(), ProfileOrientation.CANONICAL, 0L,
                        PartnerLifecycleStatus.FAILED, reason);
            } catch (RuntimeException journalFailure) {
                originalFailure.addSuppressed(journalFailure);
                cleanupComplete = false;
            }
        }
        if (journal != null && cleanupComplete) {
            leases.releaseSession(journal.id());
        } else if (journal != null) {
            try {
                repository.updateSession(
                        journal.id(), ProfileOrientation.CANONICAL, 0L,
                        PartnerLifecycleStatus.ACTIVATING,
                        "Activation cleanup incomplete: " + safeReason(originalFailure));
            } catch (RuntimeException journalFailure) {
                originalFailure.addSuppressed(journalFailure);
            }
        }
        log.warn("partner_activation failed link={} session={} reason={}",
                journal == null ? null : journal.linkId(), journal == null ? null : journal.id(), reason);
    }

    private void discardPreparedProfiles(Character firstProfile, Character secondProfile) {
        try {
            transitions.discardPreparedProfiles(firstProfile, secondProfile);
        } catch (RuntimeException cacheFailure) {
            log.warn("partner_presentation_cache discard_failed firstActor={} secondActor={}",
                    firstProfile == null ? null : firstProfile.getId(),
                    secondProfile == null ? null : secondProfile.getId(), cacheFailure);
        }
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
        if ((mode == PartnerMode.SOLO_TAG && !config.SOLO_TAG_ENABLED)
                || (mode == PartnerMode.DOUBLE_PARTNER && !config.DOUBLE_PARTNER_ENABLED)) {
            throw new IllegalStateException("That Partner Program mode is disabled.");
        }
    }

    private void requireEnabled() {
        if (!config.ENABLED) {
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
        PartnerSessionSkillService sessionSkills = new PartnerSessionSkillService(repository);
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
                        new AsyncPartnerJournalSink(repository),
                        sessionSkills),
                sessionSkills);
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

    public enum PartnerPresence {
        OFFLINE,
        ONLINE_INDEPENDENTLY,
        SOLO_TAG_READY,
        DOUBLE_PARTNER_ACTIVE,
        DOUBLE_PARTNER_OTHER_MAP,
        RECOVERY_REQUIRED;

        public boolean active() {
            return this == SOLO_TAG_READY
                    || this == DOUBLE_PARTNER_ACTIVE
                    || this == DOUBLE_PARTNER_OTHER_MAP;
        }
    }

    public record PartnerOverview(PartnerLink link,
                                  PartnerRosterCandidate partner,
                                  PartnerMode currentMode,
                                  PartnerPresence presence) {
    }

    public record ReleaseResult(boolean activeSessionReleased,
                                boolean orphanAgentReleased,
                                int recoveredSessions,
                                int staleLeaseSessions,
                                boolean partnerOnlineIndependently,
                                Long releasedSessionId) {
        public boolean changedRuntimeState() {
            return activeSessionReleased || orphanAgentReleased
                    || recoveredSessions > 0 || staleLeaseSessions > 0;
        }
    }

    @FunctionalInterface
    interface PreparationScheduler {
        void schedule(Runnable task, long delayMs);
    }
}
