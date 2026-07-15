package server.partner;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class ActivePartnerSession {
    private final PartnerLink link;
    private final PartnerSessionRuntime runtime;
    private final Character humanActor;
    private final Character partnerActorOrDormantProfile;
    private final AgentRuntimeEntry agentEntry;
    private final AtomicBoolean switchReady;
    private final AtomicBoolean preparationNoticeSent = new AtomicBoolean();
    private final AtomicBoolean preparationCompletionScheduled = new AtomicBoolean();
    private final AtomicLong nextAllowedSwitchAtMs = new AtomicLong();
    private final AtomicLong nextCooldownNoticeAtMs = new AtomicLong();
    private final AtomicBoolean journalClosed = new AtomicBoolean();
    private final ReentrantLock lifecycleOperationLock = new ReentrantLock(true);

    public ActivePartnerSession(PartnerLink link,
                                PartnerSessionRuntime runtime,
                                Character humanActor,
                                Character partnerActorOrDormantProfile,
                                AgentRuntimeEntry agentEntry) {
        this(link, runtime, humanActor, partnerActorOrDormantProfile, agentEntry, true);
    }

    private ActivePartnerSession(PartnerLink link,
                                 PartnerSessionRuntime runtime,
                                 Character humanActor,
                                 Character partnerActorOrDormantProfile,
                                 AgentRuntimeEntry agentEntry,
                                 boolean switchReady) {
        this.link = link;
        this.runtime = runtime;
        this.humanActor = humanActor;
        this.partnerActorOrDormantProfile = partnerActorOrDormantProfile;
        this.agentEntry = agentEntry;
        this.switchReady = new AtomicBoolean(switchReady);
    }

    public static ActivePartnerSession preparing(PartnerLink link,
                                                  PartnerSessionRuntime runtime,
                                                  Character humanActor,
                                                  Character partnerActorOrDormantProfile,
                                                  AgentRuntimeEntry agentEntry) {
        return new ActivePartnerSession(
                link, runtime, humanActor, partnerActorOrDormantProfile, agentEntry, false);
    }

    public boolean isSwitchReady() {
        return switchReady.get();
    }

    public boolean markSwitchReady() {
        preparationNoticeSent.set(false);
        return switchReady.compareAndSet(false, true);
    }

    public void markSwitchUnavailable() {
        switchReady.set(false);
        preparationNoticeSent.set(false);
    }

    public boolean tryScheduleSwitchPreparation() {
        return !switchReady.get() && preparationCompletionScheduled.compareAndSet(false, true);
    }

    public boolean tryAcquirePreparationNotice() {
        if (switchReady.get() || !preparationNoticeSent.compareAndSet(false, true)) {
            return false;
        }
        if (switchReady.get()) {
            preparationNoticeSent.set(false);
            return false;
        }
        return true;
    }

    public boolean tryAcquireSwitchCooldown(long nowMs, long cooldownMs) {
        while (true) {
            long current = nextAllowedSwitchAtMs.get();
            if (nowMs < current) {
                return false;
            }
            long nonNegativeCooldown = Math.max(0L, cooldownMs);
            long nextAllowed = nonNegativeCooldown > Long.MAX_VALUE - nowMs
                    ? Long.MAX_VALUE : nowMs + nonNegativeCooldown;
            if (nextAllowedSwitchAtMs.compareAndSet(current, nextAllowed)) {
                nextCooldownNoticeAtMs.set(0L);
                return true;
            }
        }
    }

    public boolean tryAcquireCooldownNotice(long nowMs) {
        while (true) {
            long current = nextCooldownNoticeAtMs.get();
            if (nowMs < current) {
                return false;
            }
            long suppressUntil = Math.max(nowMs + 1L, nextAllowedSwitchAtMs.get());
            if (nextCooldownNoticeAtMs.compareAndSet(current, suppressUntil)) {
                return true;
            }
        }
    }

    public long remainingSwitchCooldownMs(long nowMs) {
        return Math.max(0L, nextAllowedSwitchAtMs.get() - nowMs);
    }

    public long switchCooldownDeadlineMs() {
        return nextAllowedSwitchAtMs.get();
    }

    public PartnerLink link() {
        return link;
    }

    public PartnerSessionRuntime runtime() {
        return runtime;
    }

    public Character humanActor() {
        return humanActor;
    }

    public Character partnerActorOrDormantProfile() {
        return partnerActorOrDormantProfile;
    }

    public AgentRuntimeEntry agentEntry() {
        return agentEntry;
    }

    public boolean isJournalClosed() {
        return journalClosed.get();
    }

    public void markJournalClosed() {
        journalClosed.set(true);
    }

    public boolean tryEnterSwitchOperation() {
        return !lifecycleOperationLock.isHeldByCurrentThread() && lifecycleOperationLock.tryLock();
    }

    public void enterLifecycleOperation() {
        lifecycleOperationLock.lock();
    }

    public void exitLifecycleOperation() {
        lifecycleOperationLock.unlock();
    }
}
