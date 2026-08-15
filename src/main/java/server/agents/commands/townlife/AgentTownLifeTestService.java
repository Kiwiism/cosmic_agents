package server.agents.commands.townlife;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeEntryRequest;
import server.agents.capabilities.townlife.AgentTownLifeExitRequest;
import server.agents.capabilities.townlife.AgentTownLifeProfile;
import server.agents.capabilities.townlife.AgentTownLifeProfileRepository;
import server.agents.capabilities.townlife.AgentTownLifeProfileValidator;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionResult;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRequest;
import server.agents.runtime.townlife.AgentTownLifeVisitLeaseRuntime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Operator-owned harness for bounded TownLife sessions using already registered local Agents. */
public final class AgentTownLifeTestService {
    private static final String TUNING_PREFIX =
            "server.agents.commands.townlife.AgentTownLifeTestService.";
    private static final int DEFAULT_AGENT_LIMIT = tuningInt("DEFAULT_AGENT_LIMIT");
    private static final int MAX_AGENT_LIMIT = tuningInt("MAX_AGENT_LIMIT");
    private static final long MIN_DURATION_MS = tuningLong("MIN_DURATION_MS");
    private static final long MAX_DURATION_MS = tuningLong("MAX_DURATION_MS");
    private static final long DEFAULT_GRACEFUL_TIMEOUT_MS =
            tuningLong("DEFAULT_GRACEFUL_TIMEOUT_MS");

    private AgentTownLifeTestService() {
    }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || params == null || params.length < 2) {
            return usage();
        }
        return switch (params[1].toLowerCase(java.util.Locale.ROOT)) {
            case "start" -> start(operator, params, nowMs);
            case "stop" -> stop(operator, nowMs);
            case "status" -> status(operator, nowMs);
            case "readiness" -> readiness(operator);
            default -> usage();
        };
    }

    private static List<String> start(Character operator, String[] params, long nowMs) {
        if (params.length < 3) {
            return List.of("Usage: !townlife test start <seconds> [agent-count]");
        }
        long durationMs = parseDuration(params[2]);
        int limit = params.length >= 4 ? parseLimit(params[3]) : DEFAULT_AGENT_LIMIT;
        AgentTownLifeProfile profile = validatedProfile(operator.getMapId());
        List<AgentRuntimeEntry> candidates = localEntries(operator).stream()
                .limit(limit)
                .toList();
        if (candidates.isEmpty()) {
            return List.of("No registered Agents belonging to you are present in "
                    + profile.profileId() + ". Spawn/place Agents in this town first.");
        }

        String callerId = callerId(operator);
        int started = 0;
        List<String> lines = new ArrayList<>();
        for (AgentRuntimeEntry entry : candidates) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            String requestId = "gm-townlife-test:" + operator.getId() + ':'
                    + agent.getId() + ':' + Long.toUnsignedString(nowMs, 36);
            AgentTownLifeVisitRequest visit = new AgentTownLifeVisitRequest(
                    operator.getMapId(), AgentTownLifeVisitRequest.Purpose.SYSTEM,
                    "bounded GM TownLife test", 0L);
            AgentTownLifeVisitLeaseRequest lease = new AgentTownLifeVisitLeaseRequest(
                    AgentTownLifeEntryRequest.external(requestId, callerId, visit),
                    AgentTownLifeAdmissionMode.MANUAL_ONLY,
                    nowMs + durationMs, DEFAULT_GRACEFUL_TIMEOUT_MS,
                    "GM TownLife test duration elapsed");
            AgentTownLifeSessionResult result = AgentTownLifeVisitLeaseRuntime.start(
                    entry, agent, lease, nowMs, agent.getId());
            if (result.started()) {
                started++;
            }
            lines.add(agent.getName() + ": " + result.status()
                    + (result.reason().isBlank() ? "" : " (" + result.reason() + ')'));
        }
        lines.add(0, "TownLife test map=" + profile.profileId() + " requested="
                + candidates.size() + " accepted=" + started + " duration="
                + durationMs / 1_000L + "s");
        return List.copyOf(lines);
    }

    private static List<String> stop(Character operator, long nowMs) {
        String callerId = callerId(operator);
        int requested = 0;
        List<String> lines = new ArrayList<>();
        for (AgentRuntimeEntry entry : localEntries(operator)) {
            Character agent = AgentRuntimeIdentityRuntime.bot(entry);
            AgentTownLifeState state = entry.capabilityStates()
                    .find(AgentTownLifeState.STATE_KEY).orElse(null);
            if (state == null || !state.enabled() || !callerId.equals(state.callerId())) {
                continue;
            }
            var handle = state.sessionHandle(agent.getId());
            var result = AgentTownLifeRuntime.requestExit(entry, agent,
                    AgentTownLifeExitRequest.graceful(
                            handle, "GM TownLife test stopped", nowMs,
                            nowMs + DEFAULT_GRACEFUL_TIMEOUT_MS));
            requested++;
            lines.add(agent.getName() + ": " + result.status());
        }
        lines.add(0, "TownLife test graceful exits requested=" + requested);
        return List.copyOf(lines);
    }

    private static List<String> status(Character operator, long nowMs) {
        String callerId = callerId(operator);
        List<String> lines = localEntries(operator).stream()
                .map(entry -> new EntryState(entry,
                        entry.capabilityStates().find(AgentTownLifeState.STATE_KEY).orElse(null)))
                .filter(value -> value.state() != null && value.state().enabled()
                        && callerId.equals(value.state().callerId()))
                .map(value -> {
                    Character agent = AgentRuntimeIdentityRuntime.bot(value.entry());
                    AgentTownLifeState state = value.state();
                    return agent.getName() + " session=" + state.sessionId()
                            + " stage=" + state.stage() + " activity=" + state.activity()
                            + " result=" + state.activityResult()
                            + " draining=" + state.exitRequested()
                            + " age=" + Math.max(0L, nowMs - state.sessionStartedAtMs()) / 1_000L + "s";
                })
                .toList();
        if (lines.isEmpty()) {
            return List.of("No TownLife test sessions owned by this operator on the current map.");
        }
        List<String> result = new ArrayList<>(lines.size() + 1);
        result.add("TownLife test sessions=" + lines.size() + " map=" + operator.getMapId());
        result.addAll(lines);
        return List.copyOf(result);
    }

    private static List<String> readiness(Character operator) {
        AgentTownLifeProfile profile = validatedProfile(operator.getMapId());
        var validation = AgentTownLifeProfileValidator.validate(profile);
        return List.of(
                "TownLife ready map=" + profile.profileId() + " id=" + profile.mapId()
                        + " admission=" + profile.admission().mode(),
                "venues=" + profile.venues().size() + " facilities=" + profile.facilities().size()
                        + " hotspots=" + profile.hotspots().size()
                        + " trafficZones=" + profile.trafficZones().size(),
                "extensions=" + profile.extensions().activityHandlers()
                        + " warnings=" + validation.warnings(),
                "local registered Agents=" + localEntries(operator).size()
                        + "; harness never spawns, travels, shops, or edits progression");
    }

    private static AgentTownLifeProfile validatedProfile(int mapId) {
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .find(mapId).orElseThrow(() ->
                        new IllegalArgumentException("current map has no TownLife profile"));
        AgentTownLifeProfileValidator.requireValid(profile);
        return profile;
    }

    private static List<AgentRuntimeEntry> localEntries(Character operator) {
        if (operator.getMap() == null) {
            return List.of();
        }
        return AgentRuntimeRegistry.agentEntriesForLeader(operator.getId()).stream()
                .filter(entry -> {
                    Character agent = AgentRuntimeIdentityRuntime.bot(entry);
                    return agent != null && agent.getMap() == operator.getMap();
                })
                .sorted(Comparator.comparingInt(AgentRuntimeIdentityRuntime::botId))
                .toList();
    }

    private static long parseDuration(String value) {
        long durationMs;
        try {
            durationMs = Math.multiplyExact(Long.parseLong(value), 1_000L);
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException("duration must be a whole number of seconds");
        }
        if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS) {
            throw new IllegalArgumentException("duration must be between "
                    + MIN_DURATION_MS / 1_000L + " and " + MAX_DURATION_MS / 1_000L
                    + " seconds");
        }
        return durationMs;
    }

    private static int parseLimit(String value) {
        int limit;
        try {
            limit = Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("agent-count must be a whole number");
        }
        if (limit < 1 || limit > MAX_AGENT_LIMIT) {
            throw new IllegalArgumentException("agent-count must be between 1 and " + MAX_AGENT_LIMIT);
        }
        return limit;
    }

    private static String callerId(Character operator) {
        return "gm-townlife-test:" + operator.getId();
    }

    private static List<String> usage() {
        return List.of("Usage: !townlife test start <seconds> [agent-count]"
                + "|stop|status|readiness");
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }

    private static long tuningLong(String name) {
        return config.AgentTuning.longValue(TUNING_PREFIX + name);
    }

    private record EntryState(AgentRuntimeEntry entry, AgentTownLifeState state) {
    }
}
