package server.agents.capabilities.partyquest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Local authoritative PQ entry contracts. Stage behavior remains in each PQ package. */
public final class AgentPartyQuestCatalog {
    private static final Map<String, AgentPartyQuestDefinition> DEFINITIONS = buildDefinitions();

    private AgentPartyQuestCatalog() {
    }

    public static AgentPartyQuestDefinition require(String questKey) {
        String key = AgentPartyQuestDefinition.normalize(questKey);
        AgentPartyQuestDefinition definition = DEFINITIONS.get(key);
        if (definition == null) throw new IllegalArgumentException("unsupported party quest: " + key);
        return definition;
    }

    public static AgentPartyQuestDefinition find(String questKey) {
        if (questKey == null || questKey.isBlank()) return null;
        return DEFINITIONS.get(questKey.trim().toLowerCase());
    }

    public static List<AgentPartyQuestDefinition> definitions() {
        return List.copyOf(DEFINITIONS.values());
    }

    private static Map<String, AgentPartyQuestDefinition> buildDefinitions() {
        Map<String, AgentPartyQuestDefinition> definitions = new LinkedHashMap<>();
        add(definitions, new AgentPartyQuestDefinition(
                "hpq", "HenesysPQ", 100_000_200, 910_010_000, 910_010_100,
                910_010_300, 100_000_200, 10, 255, 3, 6));
        add(definitions, new AgentPartyQuestDefinition(
                "kpq", "KerningPQ", 103_000_000, 103_000_800, 103_000_805,
                103_000_890, 103_000_000, 21, 30, 3, 4));
        add(definitions, new AgentPartyQuestDefinition(
                "lpq", "LudiPQ", 221_024_500, 922_010_100, 922_011_000,
                922_010_000, 221_024_500, 35, 50, 5, 6));
        add(definitions, new AgentPartyQuestDefinition(
                "lmpq", "LudiMazePQ", 220_000_000, 809_050_000, 809_050_016,
                809_050_017, 220_000_000, 51, 70, 3, 6));
        add(definitions, new AgentPartyQuestDefinition(
                "opq", "OrbisPQ", 200_080_101, 920_010_000, 920_011_300,
                920_011_200, 200_080_101, 51, 70, 5, 6));
        add(definitions, new AgentPartyQuestDefinition(
                "epq", "EllinPQ", 300_030_100, 930_000_000, 930_000_800,
                930_000_800, 300_030_100, 44, 55, 4, 6));
        add(definitions, new AgentPartyQuestDefinition(
                "ppq", "PiratePQ", 251_010_404, 925_100_000, 925_100_600,
                925_100_700, 251_010_404, 55, 100, 3, 6));
        return Collections.unmodifiableMap(definitions);
    }

    private static void add(Map<String, AgentPartyQuestDefinition> definitions,
                            AgentPartyQuestDefinition definition) {
        if (definitions.putIfAbsent(definition.questKey(), definition) != null) {
            throw new IllegalStateException("duplicate party-quest key: " + definition.questKey());
        }
    }
}
