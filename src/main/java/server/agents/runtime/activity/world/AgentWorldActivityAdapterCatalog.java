package server.agents.runtime.activity.world;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Read-only lifecycle coverage inventory; it never instantiates an adapter. */
public final class AgentWorldActivityAdapterCatalog {
    private final Map<AgentActivityKind, Coverage> coverage;

    public AgentWorldActivityAdapterCatalog(List<Coverage> coverage) {
        if (coverage == null) throw new IllegalArgumentException("adapter coverage is required");
        EnumMap<AgentActivityKind, Coverage> indexed = new EnumMap<>(AgentActivityKind.class);
        for (Coverage value : coverage) {
            if (value == null || indexed.putIfAbsent(value.kind(), value) != null) {
                throw new IllegalArgumentException("one adapter coverage entry per activity is required");
            }
        }
        this.coverage = Map.copyOf(indexed);
    }

    public static AgentWorldActivityAdapterCatalog current() {
        return new AgentWorldActivityAdapterCatalog(List.of(
                new Coverage(AgentActivityKind.QUESTING,
                        "server.agents.runtime.activity.session.adapter.QuestPlanActivitySessionAdapter",
                        true, "standard session adapter exists"),
                new Coverage(AgentActivityKind.HUNTING,
                        "server.agents.runtime.activity.session.adapter.FieldActivitySessionAdapter",
                        true, "standard session adapter exists"),
                new Coverage(AgentActivityKind.TOWN_LIFE,
                        "server.agents.runtime.activity.session.adapter.TownLifeActivitySessionAdapter",
                        true, "standard session adapter exists"),
                new Coverage(AgentActivityKind.COMMERCE,
                        "server.agents.runtime.activity.session.adapter.EconomyActivitySessionAdapter",
                        true, "standard session adapter exists"),
                new Coverage(AgentActivityKind.PARTY_QUEST, "", false,
                        "KPQ has a primary controller but no standard activity session adapter")));
    }

    public Coverage coverage(AgentActivityKind kind) {
        return coverage.get(kind);
    }

    public List<Coverage> all() {
        return coverage.values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.kind().name())).toList();
    }

    public record Coverage(
            AgentActivityKind kind, String adapterClassName, boolean complete, String evidence) {
        public Coverage {
            if (kind == null) throw new IllegalArgumentException("activity kind is required");
            adapterClassName = adapterClassName == null ? "" : adapterClassName.trim();
            evidence = evidence == null ? "" : evidence.trim();
            if (complete && adapterClassName.isEmpty()) {
                throw new IllegalArgumentException("complete coverage requires an adapter class");
            }
        }
    }
}
