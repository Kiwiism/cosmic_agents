package server.agents.field;

import client.Character;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.perception.AgentMapPerception;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Thin GM harness adapter; field policy remains independent of command parsing. */
public final class AgentFieldCommandService {
    private AgentFieldCommandService() {
    }

    public static List<String> execute(Character operator, String[] params, long nowMs) {
        if (operator == null || operator.getMap() == null) {
            return List.of("A live operator map is required.");
        }
        if (params == null || params.length == 0 || "help".equals(params[0])) {
            return help();
        }
        return switch (params[0]) {
            case "start" -> start(operator, params, nowMs);
            case "ladder" -> ladder(operator, params, nowMs);
            case "observe" -> observe(operator, params, nowMs);
            case "prepare" -> prepare(operator, params, nowMs);
            case "add" -> add(operator, params, nowMs);
            case "remove" -> remove(operator, params, nowMs);
            case "status" -> status(operator, nowMs);
            case "stop" -> stop(operator, nowMs);
            default -> help();
        };
    }

    private static List<String> observe(Character operator, String[] params, long nowMs) {
        if (params.length < 2) {
            return observeHelp();
        }
        return switch (params[1]) {
            case "start" -> observeStart(operator, params, nowMs);
            case "status" -> observeStatus(operator);
            case "rotate" -> AgentFieldObservationRuntime.rotateNow(operator)
                    ? List.of("Rotated every ready observation map to its next authored population window.")
                    : List.of("No active observation deployment is ready to rotate.");
            case "stop" -> {
                AgentFieldObservationRuntime.StopResult stopped =
                        AgentFieldObservationRuntime.stop(operator, nowMs);
                yield List.of(stopped.message() + " Agents stopped: " + stopped.stoppedAgents() + '.');
            }
            case "catalog" -> observeCatalog();
            default -> observeHelp();
        };
    }

    private static List<String> observeStart(Character operator, String[] params, long nowMs) {
        if (params.length > 4) {
            return observeHelp();
        }
        String group = "all";
        Integer mapId = null;
        if (params.length >= 3) {
            String selector = params[2].toLowerCase();
            if (Set.of("all", "recommended", "exploratory").contains(selector)) {
                group = selector;
            } else {
                try {
                    mapId = Integer.parseInt(params[2]);
                    group = null;
                } catch (NumberFormatException invalid) {
                    return List.of("Observation selector must be all, recommended, exploratory, or a catalog map ID.");
                }
            }
        }
        long seed = nowMs;
        if (params.length == 4) {
            try {
                seed = Long.parseLong(params[3]);
            } catch (NumberFormatException invalid) {
                return List.of("Observation seed must be a whole number.");
            }
        }
        AgentFieldObservationRuntime.StartResult result =
                AgentFieldObservationRuntime.start(operator, group, mapId, seed, nowMs);
        if (!result.success()) {
            return List.of(result.message());
        }
        return List.of(result.message(), "Session " + result.sessionId() + " | maps=" + result.maps()
                + " | pooled Agents=" + result.agents() + " | seed=" + seed + '.',
                "Agents launch in 250ms waves; each ready map rotates every 120 seconds.");
    }

    private static List<String> observeStatus(Character operator) {
        AgentFieldObservationRuntime.Status status = AgentFieldObservationRuntime.status(operator);
        if (status == null) {
            return List.of("No observation deployment is active in this world/channel.");
        }
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Observation " + status.sessionId() + " | seed=" + status.seed()
                + " | roster=" + status.totalAgents() + " | supplies="
                + (status.supplyDurationMs() / 3_600_000L) + "h | active=" + status.active() + '.');
        for (AgentFieldObservationRuntime.MapStatus map : status.maps()) {
            lines.add(map.mapId() + " " + map.mapName() + " | ready=" + map.readyAgents() + '/'
                    + map.expectedAgents() + " | phase=" + map.phase() + " | grinding="
                    + map.activeAgents() + " | rotation=" + map.activeCounts()
                    + (map.failures().isEmpty() ? "" : " | failures=" + map.failures().size()));
        }
        return List.copyOf(lines);
    }

    private static List<String> observeCatalog() {
        ArrayList<String> lines = new ArrayList<>();
        AgentFieldObservationCatalogRepository.defaultRepository().maps().forEach(map -> lines.add(
                map.mapId() + " " + map.mapName() + " | L" + map.level() + " | roster="
                        + map.maximumAgents() + " | " + map.group() + " | active=" + map.activeCounts() + " | parties="
                        + map.partySizes()));
        return List.copyOf(lines);
    }

    private static List<String> observeHelp() {
        return List.of(
                "!agentfield observe start <all|recommended|exploratory|map-id> [seed]",
                "!agentfield observe status | rotate | stop | catalog",
                "This test-only harness reuses pooled Agents and does not raise the normal six-Agent field cap.");
    }

    private static List<String> ladder(Character operator, String[] params, long nowMs) {
        List<String> names = params.length <= 1
                ? List.of()
                : Arrays.asList(params).subList(1, params.length);
        Selection selection = select(operator, 5, names);
        if (!selection.failure().isBlank()) {
            return List.of(selection.failure());
        }
        AgentFieldLadderRuntime.StartResult result =
                AgentFieldLadderRuntime.start(operator, selection.entries(), nowMs);
        if (!result.success()) {
            return List.of(result.message());
        }
        return List.of(result.message() + " Run " + result.runId() + '.',
                "Boundaries are server-timed every 120 seconds; diagnostics remain on /api/agentfield.");
    }

    private static List<String> prepare(Character operator, String[] params, long nowMs) {
        if (params.length != 3) {
            return List.of("Usage: !agentfield prepare <agent-name> <career>");
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(operator.getId(), params[1]);
        if (entry == null || AgentRuntimeIdentityRuntime.bot(entry) == null) {
            return List.of("That Agent is not active in your cohort.");
        }
        try {
            AgentFieldTestFixtureService.Prepared prepared =
                    AgentFieldTestFixtureService.prepare(operator, entry, params[2], nowMs);
            return List.of("Prepared " + prepared.name() + " as level " + prepared.level()
                    + " job " + prepared.jobId() + " (" + prepared.bundleId() + ") in map "
                    + prepared.mapId() + "; EXP reset to " + prepared.exp() + '.');
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return List.of("Could not prepare field fixture: " + failure.getMessage());
        } catch (java.io.IOException failure) {
            return List.of("Could not persist field fixture: " + failure.getMessage());
        }
    }

    private static List<String> start(Character operator, String[] params, long nowMs) {
        if (params.length < 4) {
            return help();
        }
        AgentFieldMode mode;
        try {
            mode = AgentFieldMode.valueOf(params[1].toUpperCase());
        } catch (IllegalArgumentException invalid) {
            return List.of("Mode must be solo or party.");
        }
        boolean objective = switch (params[2]) {
            case "free" -> false;
            case "objective" -> true;
            default -> false;
        };
        if (!"free".equals(params[2]) && !"objective".equals(params[2])) {
            return List.of("Target mode must be free or objective.");
        }
        int count;
        try {
            count = Integer.parseInt(params[3]);
        } catch (NumberFormatException invalid) {
            return List.of("Agent count must be a number from 1 to "
                    + AgentFieldPolicyConfig.maximumParticipants() + '.');
        }
        if (count < 1 || count > AgentFieldPolicyConfig.maximumParticipants()) {
            return List.of("Agent count must be from 1 to "
                    + AgentFieldPolicyConfig.maximumParticipants() + '.');
        }
        List<String> requestedNames = params.length <= 4
                ? List.of()
                : Arrays.asList(params).subList(4, params.length);
        Selection selection = select(operator, count, requestedNames);
        if (!selection.failure().isBlank()) {
            return List.of(selection.failure());
        }
        Set<Integer> mobIds = objective ? liveMobIds(operator) : Set.of();
        if (objective && mobIds.isEmpty()) {
            return List.of("No live mob species are available for an automatic objective.");
        }
        AgentFieldRuntime.StartResult result = AgentFieldRuntime.start(
                operator, selection.entries(), mode, mobIds,
                AgentFieldPolicyConfig.testObjectiveKillsPerMob(), true, nowMs);
        if (!result.success()) {
            return List.of(result.message());
        }
        selection.entries().forEach(AgentMovementCommandRuntime::grind);
        ArrayList<String> lines = new ArrayList<>();
        lines.add(result.message() + " Session " + result.sessionId() + '.');
        lines.add("Agents: " + selection.entries().stream()
                .map(AgentRuntimeIdentityRuntime::botName).toList());
        if (objective) {
            lines.add("Equal objective: " + AgentFieldPolicyConfig.testObjectiveKillsPerMob()
                    + " kill(s) each of mob IDs " + mobIds + '.');
        }
        lines.add("Use !agentfield status for territories and !agentfield stop to restore directives.");
        return List.copyOf(lines);
    }

    private static List<String> add(Character operator, String[] params, long nowMs) {
        if (params.length != 2) {
            return List.of("Usage: !agentfield add <agent-name>");
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(operator.getId(), params[1]);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null || agent.getMap() != operator.getMap()) {
            return List.of("That Agent is not active in your cohort and map instance.");
        }
        if (AgentUniversalPlanRuntime.active(entry)) {
            return List.of(agent.getName() + " has an active universal plan; suspend or stop it first.");
        }
        AgentFieldSnapshot snapshot = AgentFieldRuntime.snapshot(operator, nowMs);
        if (snapshot == null) {
            return List.of("No field session is active in this map instance.");
        }
        Map<Integer, Integer> remaining = new java.util.LinkedHashMap<>();
        snapshot.requiredKills().forEach((mobId, required) -> {
            int count = required - snapshot.completedKills().getOrDefault(mobId, 0);
            if (count > 0) {
                remaining.put(mobId, count);
            }
        });
        AgentFieldIntent intent = remaining.isEmpty()
                ? AgentFieldIntent.freeGrind(snapshot.sessionId())
                : AgentFieldIntent.partyCoverage(snapshot.sessionId(), remaining.keySet(), remaining);
        if (!AgentFieldRuntime.add(operator, entry, intent, nowMs)) {
            return List.of("The Agent could not be added; check map instance and session capacity.");
        }
        AgentMovementCommandRuntime.grind(entry);
        return List.of("Added " + agent.getName() + "; coverage will rebalance on the next observation.");
    }

    private static List<String> remove(Character operator, String[] params, long nowMs) {
        if (params.length != 2) {
            return List.of("Usage: !agentfield remove <agent-name>");
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(operator.getId(), params[1]);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            return List.of("That Agent is not active in your cohort.");
        }
        if (!AgentFieldRuntime.remove(operator, agent.getId(), nowMs)) {
            return List.of(agent.getName() + " is not in this field session.");
        }
        AgentMovementCommandRuntime.stop(entry);
        return List.of("Removed " + agent.getName()
                + ", stopped its grind loop, and released its territory lease.");
    }

    private static List<String> stop(Character operator, long nowMs) {
        AgentFieldSnapshot snapshot = AgentFieldRuntime.snapshot(operator, nowMs);
        if (snapshot == null) {
            return List.of("No field session is active in this map instance.");
        }
        List<AgentRuntimeEntry> participants = snapshot.participants().stream()
                .map(participant -> AgentRuntimeRegistry.findByAgentCharacterId(participant.agentId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        AgentFieldRuntime.stop(operator, nowMs);
        participants.forEach(AgentMovementCommandRuntime::stop);
        return List.of("Stopped the field session, its participant grind loops, and combat leases.");
    }

    private static List<String> status(Character operator, long nowMs) {
        AgentFieldSnapshot snapshot = AgentFieldRuntime.snapshot(operator, nowMs);
        if (snapshot == null) {
            return List.of("No field session is active in this map instance.");
        }
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Field " + snapshot.sessionId() + " | " + snapshot.mode()
                + " | revision " + snapshot.revision() + " | mobs " + snapshot.liveMobs()
                + " | real players " + snapshot.realPlayers() + '.');
        if (!snapshot.requiredKills().isEmpty()) {
            lines.add("Objective " + snapshot.completedKills() + " / " + snapshot.requiredKills()
                    + (snapshot.objectiveComplete() ? " COMPLETE" : ""));
        }
        for (AgentFieldSnapshot.Participant participant : snapshot.participants()) {
            lines.add(participant.name() + " [" + participant.intent() + "] cells="
                    + participant.cellIds() + " regions=" + participant.regionIds()
                    + " anchor=(" + participant.anchorX() + ',' + participant.anchorY() + ") lease="
                    + participant.leaseRemainingMs() + "ms pos=(" + participant.positionX() + ','
                    + participant.positionY() + ") kills=" + participant.kills() + " exp="
                    + participant.exp() + "; " + participant.reason());
        }
        lines.add("JSON diagnostics: http://127.0.0.1:8790/api/agentfield?id=" + snapshot.mapId());
        return List.copyOf(lines);
    }

    private static Selection select(Character operator, int count, List<String> names) {
        List<AgentRuntimeEntry> candidates;
        if (names.isEmpty()) {
            candidates = AgentRuntimeRegistry.entriesForLeader(operator.getId()).stream()
                    .filter(entry -> AgentRuntimeIdentityRuntime.botMap(entry) == operator.getMap())
                    .sorted(java.util.Comparator.comparing(AgentRuntimeIdentityRuntime::botName))
                    .limit(count)
                    .toList();
        } else {
            if (names.size() != count) {
                return new Selection(List.of(), "Provide exactly " + count + " Agent name(s), or omit all names.");
            }
            ArrayList<AgentRuntimeEntry> resolved = new ArrayList<>();
            for (String name : names) {
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(operator.getId(), name);
                if (entry == null || AgentRuntimeIdentityRuntime.botMap(entry) != operator.getMap()) {
                    return new Selection(List.of(), name + " is not active in your cohort and map instance.");
                }
                resolved.add(entry);
            }
            candidates = resolved.stream().distinct().toList();
        }
        if (candidates.size() != count) {
            return new Selection(List.of(), "Only " + candidates.size()
                    + " eligible Agent(s) are active in your cohort and map; requested " + count + '.');
        }
        List<String> planned = candidates.stream().filter(AgentUniversalPlanRuntime::active)
                .map(AgentRuntimeIdentityRuntime::botName).toList();
        if (!planned.isEmpty()) {
            return new Selection(List.of(), "These Agents have active universal plans: " + planned
                    + ". Suspend or stop those plans before a field exercise.");
        }
        return new Selection(List.copyOf(candidates), "");
    }

    private static Set<Integer> liveMobIds(Character operator) {
        return AgentMapPerception.monsters(operator.getMap()).stream()
                .filter(mob -> mob.isAlive())
                .map(mob -> mob.getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<String> help() {
        return List.of(
                "!agentfield prepare <agent-name> <warrior|bowman|magician|thief-dagger|pirate-gun>",
                "!agentfield start <solo|party> <free|objective> <1-6> [agent names...]",
                "!agentfield ladder <warrior> <bowman> <magician> <thief> <pirate>",
                "!agentfield observe start <all|recommended|exploratory|map-id> [seed] | status | rotate | stop | catalog",
                "!agentfield add <agent-name> | remove <agent-name> | status | stop",
                "Objective mode assigns the same configurable kill count to every live mob species.");
    }

    private record Selection(List<AgentRuntimeEntry> entries, String failure) {
    }
}
