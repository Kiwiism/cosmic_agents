package server.agents.plans;

import java.util.HashMap;
import java.util.Map;

public final class AgentScriptRuntimeState {
    private String scriptId;
    private int stepIndex;
    private boolean stepEntered;
    private long waitUntilMs;
    private final Map<String, Integer> ints = new HashMap<>();

    public String scriptId() {
        return scriptId;
    }

    public int stepIndex() {
        return stepIndex;
    }

    public boolean stepEntered() {
        return stepEntered;
    }

    public void markStepEntered() {
        stepEntered = true;
    }

    public void advanceStep() {
        stepIndex++;
        stepEntered = false;
    }

    public int intValue(String key) {
        return ints.getOrDefault(key, 0);
    }

    public void setIntValue(String key, int value) {
        ints.put(key, value);
    }

    public void waitUntil(long waitUntilMs) {
        this.waitUntilMs = waitUntilMs;
    }

    public boolean waitDone(long nowMs) {
        return nowMs >= waitUntilMs;
    }

    public void reset(String newScriptId) {
        scriptId = newScriptId;
        stepIndex = 0;
        stepEntered = false;
        waitUntilMs = 0L;
        ints.clear();
    }
}
