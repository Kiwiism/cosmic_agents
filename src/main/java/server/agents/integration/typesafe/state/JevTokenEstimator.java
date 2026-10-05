package server.agents.integration.typesafe.state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Cheap token estimate from serialised length; the API's {@code usage} field is the truth. */
public final class JevTokenEstimator {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int CHARS_PER_TOKEN = config.AgentTuning.intValue(
            "server.agents.integration.typesafe.state.JevTokenEstimator.CHARS_PER_TOKEN");

    private JevTokenEstimator() {
    }

    public static int estimate(Object value) {
        if (value == null) {
            return 0;
        }
        String text;
        if (value instanceof String string) {
            text = string;
        } else {
            try {
                text = JSON.writeValueAsString(value);
            } catch (JsonProcessingException failure) {
                text = String.valueOf(value);
            }
        }
        return Math.max(1, (text.length() + CHARS_PER_TOKEN - 1) / Math.max(1, CHARS_PER_TOKEN));
    }
}
