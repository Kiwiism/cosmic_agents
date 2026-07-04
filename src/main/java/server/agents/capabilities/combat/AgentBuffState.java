package server.agents.capabilities.combat;

/**
 * Mutable runtime state for consumable-buff automation and skill-buff debug reporting.
 */
public final class AgentBuffState {
    private boolean consumablesEnabled = false;
    private boolean cheapMode = true;
    private long lastConsumableScanMs = 0L;
    private long lastConsumableActionAtMs = 0L;
    private String lastConsumableActionSummary = "no buff scans yet";
    private long lastSkillActionAtMs = 0L;
    private String lastSkillActionSummary = "no skill buff checks yet";

    public boolean consumablesEnabled() {
        return consumablesEnabled;
    }

    public void setConsumablesEnabled(boolean consumablesEnabled) {
        this.consumablesEnabled = consumablesEnabled;
    }

    public boolean cheapMode() {
        return cheapMode;
    }

    public void setCheapMode(boolean cheapMode) {
        this.cheapMode = cheapMode;
    }

    public long lastConsumableScanMs() {
        return lastConsumableScanMs;
    }

    public void setLastConsumableScanMs(long lastConsumableScanMs) {
        this.lastConsumableScanMs = lastConsumableScanMs;
    }

    public void resetLastConsumableScan() {
        lastConsumableScanMs = 0L;
    }

    public boolean consumableScanDue(long nowMs, long intervalMs) {
        return nowMs - lastConsumableScanMs >= intervalMs;
    }

    public long lastConsumableActionAtMs() {
        return lastConsumableActionAtMs;
    }

    public String lastConsumableActionSummary() {
        return lastConsumableActionSummary;
    }

    public void rememberConsumableAction(long atMs, String summary) {
        lastConsumableActionAtMs = atMs;
        lastConsumableActionSummary = summary;
    }

    public long lastSkillActionAtMs() {
        return lastSkillActionAtMs;
    }

    public String lastSkillActionSummary() {
        return lastSkillActionSummary;
    }

    public long lastSkillActionAgeMs(long nowMs) {
        return lastSkillActionAtMs > 0 ? Math.max(0L, nowMs - lastSkillActionAtMs) : -1L;
    }

    public void rememberSkillAction(long atMs, String summary) {
        lastSkillActionAtMs = atMs;
        lastSkillActionSummary = summary;
    }
}
