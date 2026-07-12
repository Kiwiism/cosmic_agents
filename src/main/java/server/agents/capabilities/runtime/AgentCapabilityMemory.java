package server.agents.capabilities.runtime;

import java.util.HashMap;
import java.util.Map;

/** Transient state owned by one capability frame; never persisted. */
public final class AgentCapabilityMemory {
    private final Map<String, Object> values = new HashMap<>();

    public int intValue(String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    public void putInt(String key, int value) {
        values.put(key, value);
    }

    public long longValue(String key, long defaultValue) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    public void putLong(String key, long value) {
        values.put(key, value);
    }

    public boolean booleanValue(String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    public String stringValue(String key) {
        Object value = values.get(key);
        return value instanceof String string ? string : null;
    }

    public void putString(String key, String value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }
}
