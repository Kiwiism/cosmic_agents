package server.bots;

import java.util.HashMap;
import java.util.Map;

public final class BotScriptRuntime {
    public String scriptId = null;
    public int stepIndex = 0;
    public boolean stepEntered = false;
    public long waitUntilMs = 0L;
    public final Map<String, Integer> ints = new HashMap<>();

    public void reset(String newScriptId) {
        scriptId = newScriptId;
        stepIndex = 0;
        stepEntered = false;
        waitUntilMs = 0L;
        ints.clear();
    }
}
