package server.agents.diagnostics.skillprobe;

import client.Job;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parsed arguments of {@code !skillprobe <agent> [key=value ...]}.
 *
 * <p>Keys: {@code job} (id or enum name), {@code level}, {@code mob} (dummy monster id),
 * {@code map} (probe map id), {@code weapon} (item id to equip before probing).
 * Flags: {@code nobuffs} skips support-skill casts, {@code noshape} probes the Agent as-is even
 * when job/level are given, {@code keepmobs} leaves the dummy monsters alive afterwards.</p>
 */
public record SkillProbeOptions(String agentName,
                                Job job,
                                int level,
                                int mobId,
                                int mapId,
                                int weaponItemId,
                                boolean probeBuffs,
                                boolean shape,
                                boolean keepMobs,
                                boolean profileBuild,
                                int loopSeconds,
                                boolean loopSpecialMoves) {
    /** Snail: harmless, low HP, spawns anywhere with a foothold. */
    public static final int DEFAULT_MOB_ID = 100100;
    /** GM Map: private, flat, no natural spawns. */
    public static final int DEFAULT_MAP_ID = 180000000;

    public static SkillProbeOptions parse(String[] params) {
        if (params == null || params.length < 1 || params[0].isBlank()) {
            throw new IllegalArgumentException("Syntax: !skillprobe <agent> [job=<id|name>] [level=<n>] "
                    + "[mob=<id>] [map=<id>] [weapon=<itemId>] [build=max|profile] [loop=<seconds>] [nobuffs] [noshape] [keepmobs]");
        }
        String agentName = params[0];
        Job job = null;
        int level = 0;
        int mobId = DEFAULT_MOB_ID;
        int mapId = DEFAULT_MAP_ID;
        int weaponItemId = 0;
        boolean probeBuffs = true;
        boolean shape = true;
        boolean keepMobs = false;
        boolean profileBuild = false;
        int loopSeconds = 0;
        boolean loopSpecialMoves = true;
        List<String> unknown = new ArrayList<>();
        for (int i = 1; i < params.length; i++) {
            String token = params[i];
            int eq = token.indexOf('=');
            String key = (eq < 0 ? token : token.substring(0, eq)).toLowerCase(Locale.ROOT);
            String value = eq < 0 ? "" : token.substring(eq + 1);
            switch (key) {
                case "job" -> job = parseJob(value);
                case "level", "lv" -> level = parseInt(key, value);
                case "mob" -> mobId = parseInt(key, value);
                case "map" -> mapId = parseInt(key, value);
                case "weapon" -> weaponItemId = parseInt(key, value);
                case "nobuffs" -> probeBuffs = false;
                case "noshape" -> shape = false;
                case "keepmobs" -> keepMobs = true;
                case "loop" -> loopSeconds = parseInt(key, value);
                case "special" -> loopSpecialMoves = !"off".equalsIgnoreCase(value);
                case "build" -> profileBuild = switch (value.toLowerCase(Locale.ROOT)) {
                    case "profile" -> true;
                    case "max" -> false;
                    default -> throw new IllegalArgumentException("build must be 'max' or 'profile'");
                };
                default -> unknown.add(token);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown option(s): " + String.join(" ", unknown));
        }
        if (level < 0 || level > 200) {
            throw new IllegalArgumentException("level must be between 1 and 200");
        }
        if (job != null && level == 0) {
            level = defaultLevelFor(job);
        }
        return new SkillProbeOptions(agentName, job, level, mobId, mapId, weaponItemId, probeBuffs, shape, keepMobs, profileBuild, loopSeconds, loopSpecialMoves);
    }

    /** True when the caller asked for the Agent to be shaped to a job/level before probing. */
    public boolean wantsShaping() {
        return shape && (job != null || level > 0);
    }

    static Job parseJob(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("job needs an id or a name, e.g. job=fighter or job=110");
        }
        try {
            Job byId = Job.getById(Integer.parseInt(value));
            if (byId == null) {
                throw new IllegalArgumentException("Unknown job id " + value);
            }
            return byId;
        } catch (NumberFormatException notNumeric) {
            try {
                return Job.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown job '" + value + "'");
            }
        }
    }

    /** Lowest level at which the job's full tree can be learned: 10/30/70/120 by advancement. */
    static int defaultLevelFor(Job job) {
        int id = job.getId();
        if (id == 0) {
            return 10;
        }
        if (id % 100 == 0) {
            return 10;
        }
        return switch (id % 10) {
            case 0 -> 30;
            case 1 -> 70;
            default -> 120;
        };
    }

    private static int parseInt(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(key + " needs a number, got '" + value + "'");
        }
    }
}
