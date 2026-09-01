package server.agents.capabilities.supplies;

/**
 * Mutable potion-check and potion-share state for a live Agent.
 */
public final class AgentPotionSupplyState {
    private int potCheckTimerMs = 0;
    private int mpRecoveryTimerMs = 0;
    private long diseaseSignature = 0L;
    private long diseaseCureDueAtMs = 0L;
    private boolean hpShareRequested = false;
    private boolean mpShareRequested = false;

    public int potCheckTimerMs() {
        return potCheckTimerMs;
    }

    public void setPotCheckTimerMs(int potCheckTimerMs) {
        this.potCheckTimerMs = potCheckTimerMs;
    }

    public int mpRecoveryTimerMs() {
        return mpRecoveryTimerMs;
    }

    public void setMpRecoveryTimerMs(int mpRecoveryTimerMs) {
        this.mpRecoveryTimerMs = mpRecoveryTimerMs;
    }

    public long diseaseSignature() {
        return diseaseSignature;
    }

    public long diseaseCureDueAtMs() {
        return diseaseCureDueAtMs;
    }

    public void scheduleDiseaseCure(long signature, long dueAtMs) {
        diseaseSignature = signature;
        diseaseCureDueAtMs = dueAtMs;
    }

    public void clearDiseaseCure() {
        diseaseSignature = 0L;
        diseaseCureDueAtMs = 0L;
    }

    public boolean hpShareRequested() {
        return hpShareRequested;
    }

    public void setHpShareRequested(boolean hpShareRequested) {
        this.hpShareRequested = hpShareRequested;
    }

    public boolean mpShareRequested() {
        return mpShareRequested;
    }

    public void setMpShareRequested(boolean mpShareRequested) {
        this.mpShareRequested = mpShareRequested;
    }

    public boolean shareRequested(boolean forHp) {
        return forHp ? hpShareRequested : mpShareRequested;
    }

    public void setShareRequested(boolean forHp, boolean requested) {
        if (forHp) {
            hpShareRequested = requested;
        } else {
            mpShareRequested = requested;
        }
    }

    public void clearAllShareRequests() {
        hpShareRequested = false;
        mpShareRequested = false;
    }
}
