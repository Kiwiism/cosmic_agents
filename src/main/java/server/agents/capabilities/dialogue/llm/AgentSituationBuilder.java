package server.agents.capabilities.dialogue.llm;

import client.Character;
import constants.game.ExpTable;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyMemberSnapshot;
import server.agents.integration.AgentPartySnapshot;
import server.life.Monster;
import server.maps.MapleMap;

import java.util.LinkedHashMap;

/**
 * Builds a short "current situation" snapshot inlined into the LLM prompt so
 * bots can answer where/what/who questions accurately instead of hallucinating.
 * Queried fresh at every call — never cached. All reads are best-effort; any
 * null/missing piece is silently skipped so a partial state still produces a
 * useful block.
 */
public final class AgentSituationBuilder {
    private AgentSituationBuilder() {}

    public static String build(Character agent,
                               MapleMap map,
                               boolean grinding,
                               boolean following,
                               boolean farmAnchorInCurrentMap,
                               String lastOwnerCommand,
                               long lastOwnerCommandAtMs,
                               long nowMs) {
        if (agent == null) return "";
        StringBuilder sb = new StringBuilder(256);
        sb.append("[Where you are now]\n");

        if (map != null) {
            String name = map.getMapName();
            String street = map.getStreetName();
            if (name != null && !name.isBlank()) {
                sb.append("Map: ").append(name);
                if (street != null && !street.isBlank() && !street.equalsIgnoreCase(name)) {
                    sb.append(" (").append(street).append(')');
                }
                sb.append('\n');
            }
        }

        sb.append("Status: ").append(describeActivity(grinding, following, farmAnchorInCurrentMap)).append('\n');

        int lvl = agent.getLevel();
        int pct = expPercent(agent, lvl);
        sb.append("Level ").append(lvl);
        if (pct >= 0) sb.append(", ").append(pct).append("% to next");
        sb.append('\n');

        String mobs = describeMobs(map);
        if (!mobs.isEmpty()) sb.append("Mobs around: ").append(mobs).append('\n');

        String party = describeParty(agent);
        if (!party.isEmpty()) sb.append("Party: ").append(party).append('\n');

        if (lastOwnerCommand != null && !lastOwnerCommand.isBlank()) {
            sb.append("Last command from owner: \"").append(lastOwnerCommand).append('"')
                    .append(" (").append(ago(nowMs - lastOwnerCommandAtMs))
                    .append(" ago)\n");
        }
        return sb.toString();
    }

    private static String describeActivity(boolean grinding, boolean following, boolean farmAnchorInCurrentMap) {
        if (grinding) {
            if (farmAnchorInCurrentMap) {
                return "grinding (camping this spot)";
            }
            return "grinding";
        }
        if (following) return "following owner";
        return "standing around, no orders";
    }

    private static int expPercent(Character bot, int lvl) {
        try {
            if (lvl >= 200) return -1;
            int needed = ExpTable.getExpNeededForLevel(lvl);
            if (needed <= 0) return -1;
            long pct = (100L * Math.max(0, bot.getExp())) / needed;
            return (int) Math.min(99, pct);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String describeMobs(MapleMap map) {
        if (map == null) return "";
        // Preserve discovery order so the same map produces stable text.
        LinkedHashMap<String, int[]> counts = new LinkedHashMap<>();
        try {
            for (Monster mob : server.agents.perception.AgentMapPerception.monsters(map)) {
                if (mob == null || !mob.isAlive()) continue;
                String key = mob.getName() + "|" + mob.getLevel();
                counts.computeIfAbsent(key, k -> new int[1])[0]++;
            }
        } catch (Throwable t) {
            return "";
        }
        if (counts.isEmpty()) return "";
        // Top 4 by count
        record Entry(String name, int lvl, int count) {}
        java.util.List<Entry> list = new java.util.ArrayList<>(counts.size());
        for (var e : counts.entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            int lvl = 0;
            try { lvl = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
            list.add(new Entry(parts[0], lvl, e.getValue()[0]));
        }
        list.sort((a, b) -> Integer.compare(b.count(), a.count()));
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Entry e : list) {
            if (shown == 4) { sb.append(", ..."); break; }
            if (shown > 0) sb.append(", ");
            sb.append(e.name()).append(" lv").append(e.lvl()).append(" x").append(e.count());
            shown++;
        }
        return sb.toString();
    }

    private static String describeParty(Character bot) {
        AgentPartySnapshot party = AgentPartyGatewayRuntime.party().snapshot(bot);
        if (party == null) return "";
        int myMapId = bot.getMapId();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (AgentPartyMemberSnapshot m : party.members()) {
            if (m == null || m.id() == bot.getId()) continue;
            if (!first) sb.append(", ");
            sb.append(m.name());
            if (m.leader()) sb.append(" (leader)");
            if (m.mapId() != myMapId) sb.append(" (elsewhere)");
            first = false;
        }
        return sb.toString();
    }

    /** Compact relative-time string: "12s", "4m", "2h", "3d". */
    public static String ago(long deltaMs) {
        if (deltaMs < 0) deltaMs = 0;
        long s = deltaMs / 1000;
        if (s < 60) return s + "s";
        long m = s / 60;
        if (m < 60) return m + "m";
        long h = m / 60;
        if (h < 24) return h + "h";
        return (h / 24) + "d";
    }
}
