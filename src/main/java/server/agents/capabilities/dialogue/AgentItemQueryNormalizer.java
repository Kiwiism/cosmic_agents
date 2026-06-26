package server.agents.capabilities.dialogue;

import java.util.ArrayList;
import java.util.List;

public final class AgentItemQueryNormalizer {
    private AgentItemQueryNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.toLowerCase()
                .replaceAll("[?!.,]+$", "")
                .replaceAll("[^a-z0-9 '\\-]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "";
        }
        List<String> tokens = new ArrayList<>(List.of(normalized.split(" ")));
        int lastIndex = tokens.size() - 1;
        tokens.set(lastIndex, singularizeToken(tokens.get(lastIndex)));
        return String.join(" ", tokens).trim();
    }

    private static String singularizeToken(String token) {
        if (token.length() <= 3 || !token.endsWith("s")) {
            return token;
        }
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        return token.substring(0, token.length() - 1);
    }
}
