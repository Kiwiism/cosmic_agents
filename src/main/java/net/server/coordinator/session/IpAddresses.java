package net.server.coordinator.session;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IpAddresses {
    private static final List<Pattern> LOCAL_ADDRESS_PATTERNS = loadLocalAddressPatterns();

    private static List<Pattern> loadLocalAddressPatterns() {
        return Stream.of("10\\.", "192\\.168\\.", "172\\.(1[6-9]|2[0-9]|3[0-1])\\.")
                .map(Pattern::compile)
                .collect(Collectors.toList());
    }

    public static boolean isLocalAddress(String inetAddress) {
        return inetAddress.startsWith("127.");
    }

    public static boolean isLanAddress(String inetAddress) {
        return LOCAL_ADDRESS_PATTERNS.stream()
                .anyMatch(pattern -> matchesPattern(pattern, inetAddress));
    }

    public static boolean isTailscaleAddress(String inetAddress) {
        // Tailscale CGNAT range 100.64.0.0/10  -> 100.64.x - 100.127.x
        return inetAddress.matches("^100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\..*");
    }

    private static boolean matchesPattern(Pattern pattern, String searchTerm) {
        return pattern.matcher(searchTerm).find();
    }
}
