package server.security.typesafe;

import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Second opinion on a proposed character name after the substring blocklist and syntax check
 * passed: does it impersonate staff, or is it a disguised slur? Runs only when
 * {@code USE_TYPESAFE_NAME_SCREEN} is on; any failure allows the name, exactly as before.
 *
 * <p>Character creation happens on the login handler thread and is not latency-sensitive, so this
 * is a bounded blocking call (transport timeout plus one retry).
 */
public final class JevNameScreen {
    private static final Logger log = LoggerFactory.getLogger(JevNameScreen.class);
    static final String IMPERSONATES_STAFF = "impersonates_staff";
    static final String OFFENSIVE = "offensive";
    public static final String REQUEST_KIND = "name-screen";
    /** Noul probability at or above which a name is rejected; well above the 0.5 "cannot tell". */
    static final double REJECT_PROBABILITY = 0.7d;

    private JevNameScreen() {
    }

    public static boolean enabled() {
        return YamlConfig.config.server.USE_TYPESAFE_NAME_SCREEN;
    }

    /** True when the name may be used. Unconfigured, unavailable or failing judgments allow it. */
    public static boolean allows(String name) {
        if (!enabled() || name == null || name.isBlank()) {
            return true;
        }
        return allows(name, JevClient.runtime());
    }

    static boolean allows(String name, JevClient client) {
        if (client == null || !client.available()) {
            return true;
        }
        Optional<JevResponse> response = client.ask(request(name));
        if (response.isEmpty()) {
            return true;
        }
        double staff = response.get().yesProbability(IMPERSONATES_STAFF);
        double offensive = response.get().yesProbability(OFFENSIVE);
        boolean allowed = staff < REJECT_PROBABILITY && offensive < REJECT_PROBABILITY;
        if (!allowed) {
            log.info("[typesafe-name] rejected '{}' impersonates_staff={} offensive={}", name,
                    String.format(Locale.ROOT, "%.2f", staff), String.format(Locale.ROOT, "%.2f", offensive));
        }
        return allowed;
    }

    public static JevRequest request(String name) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("proposed_character_name", name.trim());
        state.put("context", "a player is creating a character on a MapleStory private server; "
                + "names are 3-12 letters and digits with no spaces, so disguises use digits, "
                + "repeated letters, or unusual capitalisation");
        Map<String, Object> staff = new LinkedHashMap<>();
        staff.put("question", "Does `proposed_character_name` claim or imitate a staff or official role "
                + "(GM, admin, moderator, owner, Nexon, Wizet, MapleStory, helper, support), including disguised spellings?");
        staff.put("not_true_for", "ordinary names that merely contain such letters as part of a real word or name");
        Map<String, Object> offensive = new LinkedHashMap<>();
        offensive.put("question", "Is `proposed_character_name` a slur, a sexual or hateful term, or a disguised spelling of one?");
        offensive.put("not_true_for", "innocent names that only contain an offensive substring, such as a surname that contains a rude syllable");
        return JevRequest.builder(state).kind(REQUEST_KIND)
                .noul(IMPERSONATES_STAFF, staff)
                .noul(OFFENSIVE, offensive)
                .build();
    }
}
