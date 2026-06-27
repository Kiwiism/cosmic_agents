package server.agents.capabilities.dialogue;

import constants.string.CharsetConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Sanitizes Agent chat text for the v83 client chat charset.
 */
public final class AgentChatTextSanitizer {
    private static final Logger log = LoggerFactory.getLogger(AgentChatTextSanitizer.class);

    private static final Map<java.lang.Character, String> CHAT_CHAR_FALLBACKS = Map.ofEntries(
            Map.entry('—', "-"),
            Map.entry('–', "-"),
            Map.entry('‘', "'"),
            Map.entry('’', "'"),
            Map.entry('“', "\""),
            Map.entry('”', "\""),
            Map.entry('…', "..."),
            Map.entry(' ', " "));

    private static final java.nio.charset.CharsetEncoder CHAT_ENCODER =
            CharsetConstants.CHARSET.newEncoder();

    private AgentChatTextSanitizer() {
    }

    public static synchronized String sanitize(String text) {
        if (text == null || CHAT_ENCODER.canEncode(text)) {
            return text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (CHAT_ENCODER.canEncode(c)) {
                sb.append(c);
            } else {
                sb.append(CHAT_CHAR_FALLBACKS.getOrDefault(c, "?"));
            }
        }
        String cleaned = sb.toString();
        log.warn("Bot chat had non-encodable char(s) (would show as '?'): \"{}\" -> \"{}\"", text, cleaned);
        return cleaned;
    }
}
