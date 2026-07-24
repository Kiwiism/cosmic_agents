package server.agents.progression;

import client.Character;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;

import java.awt.Point;
import java.io.IOException;
import java.util.Map;

public final class VictoriaFirstJobMvpCommandService {
    private VictoriaFirstJobMvpCommandService() {
    }

    public static void execute(Character player, String[] params) {
        if (player == null || params == null || params.length < 2) {
            usage(player);
            return;
        }
        String action = params[0].toLowerCase();
        if ("reset".equals(action) || "run".equals(action)) {
            run(player, params);
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(player.getId(), params[1]);
        if (entry == null) {
            player.yellowMessage("No spawned Agent named '" + params[1] + "'.");
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            player.yellowMessage("No spawned Agent named '" + params[1] + "'.");
            return;
        }
        switch (action) {
            case "train" -> {
                int targetLevel;
                try {
                    targetLevel = params.length >= 3 ? Integer.parseInt(params[2]) : 30;
                } catch (NumberFormatException invalidLevel) {
                    usage(player);
                    return;
                }
                boolean questsEnabled = params.length < 4
                        || !"grind".equalsIgnoreCase(params[3]);
                if (params.length >= 4 && !"grind".equalsIgnoreCase(params[3])
                        && !"mixed".equalsIgnoreCase(params[3])) {
                    usage(player);
                    return;
                }
                AgentMailboxRuntime.dispatch(entry, ignored -> {
                    long nowMs = System.currentTimeMillis();
                    if (AgentUniversalPlanRuntime.start(
                            entry, agent, "victoria-training",
                            new AgentPlanStartRequest(Map.of(
                                    "targetLevel", targetLevel,
                                    "questsEnabled", questsEnabled), null), nowMs)) {
                        AgentUniversalPlanRuntime.tick(entry, agent, nowMs);
                        player.yellowMessage(agent.getName() + " started occupancy-aware Victoria training to Lv"
                                + targetLevel + " in " + (questsEnabled ? "personality-weighted mixed" : "grind")
                                + " mode.");
                    } else {
                        player.yellowMessage("Victoria training requires a Lv15+ first-job Agent below the target, "
                                + "with no other foreground objective.");
                    }
                    return null;
                });
            }
            case "stop" -> AgentMailboxRuntime.dispatch(entry, ignored -> {
                boolean sessionActive = AgentUniversalPlanRuntime.active(entry)
                        || AgentVictoriaPlanSessionRuntime.active(entry);
                boolean cancelled = AgentUniversalPlanRuntime.cancel(
                        entry, agent, "stopped by Victoria command", System.currentTimeMillis());
                if (!cancelled) {
                    cancelled = AgentVictoriaTrainingObjectiveRuntime.cancel(
                            entry, System.currentTimeMillis());
                    AgentVictoriaPlanSessionRuntime.stop(entry);
                }
                player.yellowMessage(cancelled || sessionActive
                        ? agent.getName() + " stopped its Victoria progression session."
                        : agent.getName() + " has no active Victoria progression session.");
                return null;
            });
            case "snapshot" -> AgentMailboxRuntime.dispatch(entry, ignored -> {
                try {
                    var path = AgentVictoriaProgressionDiagnostics.capture(
                            entry, agent, System.currentTimeMillis());
                    player.yellowMessage("Victoria evidence written to " + path + ".");
                } catch (IOException failure) {
                    player.yellowMessage("Victoria evidence failed: " + failure.getMessage());
                }
                return null;
            });
            case "status" -> status(player, entry, agent);
            default -> usage(player);
        }
    }

    private static void run(Character player, String[] params) {
        if (params.length < 3) {
            usage(player);
            return;
        }
        String agentName = params[1];
        String career = params[2];
        String variant = params.length >= 4 ? params[3] : "lv10";
        AgentVictoriaLevel15Catalog.StartVariant startVariant;
        try {
            VictoriaFirstJobMvpTestService.resolveBundle(career);
            startVariant = VictoriaFirstJobMvpTestService.resolveStartVariant(variant);
        } catch (IllegalArgumentException failure) {
            player.yellowMessage("Victoria MVP reset failed: " + failure.getMessage());
            return;
        }

        var startMap = AgentMapGatewayRuntime.map().resolveMap(
                player.getWorld(), AgentClientGatewayRuntime.clients().channel(player),
                VictoriaFirstJobMvpTestService.LITH_HARBOR_MAP_ID);
        Point startPosition = VictoriaFirstJobMvpTestService.lithHarborArrivalPosition(
                startMap, agentName.hashCode());
        AgentLifecycleService.AgentSpawnResult spawn = AgentInteractionRuntime.spawnStationaryAgentForLeaderAt(
                player, agentName, startMap, startPosition);
        if (!spawn.success()) {
            player.yellowMessage(spawn.errorMessage());
            return;
        }
        Character agent = spawn.agent();
        AgentPartyLifecycleService.leaveAgentParty(agent);
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(player.getId(), agent.getId());
        if (entry == null) {
            player.yellowMessage("Victoria MVP could not prepare because the Agent runtime is unavailable.");
            return;
        }
        AgentMailboxRuntime.dispatch(entry, ignored -> {
            try {
                AgentCareerBuildBundle bundle = VictoriaFirstJobMvpTestService.resetAndStart(
                        entry, career, startVariant.variantId(), System.currentTimeMillis());
                player.yellowMessage(agent.getName() + " reset to Lv" + startVariant.level()
                        + " Beginner at the Lith Harbor arrival with Biggs 1046 active and 1,000 mesos; "
                        + bundle.bundleId() + " / " + startVariant.variantId()
                        + " starts in 3 seconds.");
            } catch (IOException | RuntimeException failure) {
                player.yellowMessage("Victoria MVP reset failed: " + failure.getMessage());
            }
            return null;
        });
    }

    private static void status(Character player, AgentRuntimeEntry entry, Character agent) {
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        AgentCareerBuildBundle bundle = state.bundle();
        player.yellowMessage("Victoria MVP: " + agent.getName() + " Lv" + agent.getLevel()
                + " job=" + agent.getJob().getId() + " map=" + agent.getMapId()
                + " mesos=" + agent.getMeso() + " variant=" + state.startVariantId()
                + " stage=" + state.stage()
                + " session=" + AgentVictoriaPlanSessionRuntime.plan(entry)
                + " universal=" + AgentUniversalPlanRuntime.status(entry) + ".");
        player.yellowMessage("Plan=victoria-level15-mvp stageContract="
                + AgentVictoriaLevel15StageContractRepository.defaultContract().contractId()
                + " catalog=" + AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().catalogId()
                + ".");
        if (bundle != null) {
            AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(entry);
            player.yellowMessage("Bundle=" + bundle.bundleId() + " AP=" + bundle.apProfileId()
                    + " SP=" + bundle.spProfileId() + " personality=" + profile.profileId()
                    + " instructorQuests=" + state.trainingQuestIndex()
                    + "/" + bundle.instructorTrainingQuestIds().size()
                    + " packCursor=" + state.questPackIndex() + ".");
        }
        if (!state.blockReason().isBlank()) {
            player.yellowMessage("Blocked: " + state.blockReason());
        }
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        AgentVictoriaTrainingState training = entry.capabilityStates().require(
                AgentVictoriaTrainingState.STATE_KEY);
        if (objective != null) {
            player.yellowMessage("Objective=" + objective.type() + " / " + objective.objectiveId() + ".");
        }
        if (training.active()) {
            player.yellowMessage("Training target=Lv" + training.targetLevel()
                    + " selectedMap=" + training.selectedMapId()
                    + " reason=" + training.selectionReason() + ".");
        }
    }

    private static void usage(Character player) {
        if (player == null) {
            return;
        }
        player.yellowMessage("Usage: !victoria run <AgentIGN> <warrior|bowman|magician|thief|pirate> "
                + "[lv10|lv9-olaf|lv9-grind]");
        player.yellowMessage("Reset is an alias for run. Builds: thief-dagger, pirate-knuckle. "
                + "Train: !victoria train <AgentIGN> [16-30] [mixed|grind]. "
                + "Stop: !victoria stop <AgentIGN>. "
                + "Status: !victoria status <AgentIGN>. "
                + "Evidence: !victoria snapshot <AgentIGN>");
    }
}
