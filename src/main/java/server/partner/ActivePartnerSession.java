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
    private final AtomicLong nextAllowedSwitchAtMs = new AtomicLong();
    private final AtomicBoolean journalClosed = new AtomicBoolean();
    private final ReentrantLock lifecycleOperationLock = new ReentrantLock(true);

    public ActivePartnerSession(PartnerLink link,
                                PartnerSessionRuntime runtime,
                                Character humanActor,
                                Character partnerActorOrDormantProfile,
                                AgentRuntimeEntry agentEntry) {
        this.link = link;
        this.runtime = runtime;
        this.humanActor = humanActor;
        this.partnerActorOrDormantProfile = partnerActorOrDormantProfile;
        this.agentEntry = agentEntry;
    }

    public boolean tryAcquireSwitchCooldown(long nowMs, long cooldownMs) {
        while (true) {
            long current = nextAllowedSwitchAtMs.get();
            if (nowMs < current) {
                return false;
            }
            if (nextAllowedSwitchAtMs.compareAndSet(current, nowMs + Math.max(0L, cooldownMs))) {
                return true;
            }
        }
    }

    public long remainingSwitchCooldownMs(long nowMs) {
        return Math.max(0L, nextAllowedSwitchAtMs.get() - nowMs);
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
