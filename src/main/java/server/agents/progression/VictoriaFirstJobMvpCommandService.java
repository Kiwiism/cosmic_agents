package server.agents.progression;

import client.Character;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;

import java.io.IOException;

public final class VictoriaFirstJobMvpCommandService {
    private VictoriaFirstJobMvpCommandService() {
    }

    public static void execute(Character player, String[] params) {
        if (player == null || params == null || params.length < 2) {
            usage(player);
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentName(params[1]);
        if (entry == null) {
            player.yellowMessage("No spawned Agent named '" + params[1] + "'.");
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) {
            player.yellowMessage("No spawned Agent named '" + params[1] + "'.");
            return;
        }
        switch (params[0].toLowerCase()) {
            case "reset", "run" -> {
                if (params.length < 3) {
                    usage(player);
                    return;
                }
                String career = params[2];
                String variant = params.length >= 4 ? params[3] : "lv10";
                AgentMailboxRuntime.dispatch(entry, ignored -> {
                    try {
                        AgentVictoriaLevel15Catalog.StartVariant startVariant =
                                VictoriaFirstJobMvpTestService.resolveStartVariant(variant);
                        AgentCareerBuildBundle bundle = VictoriaFirstJobMvpTestService.resetAndStart(
                                entry, career, startVariant.variantId(), System.currentTimeMillis());
                        player.yellowMessage(agent.getName() + " reset to Lv" + startVariant.level()
                                + " Beginner at Lith Harbor with Biggs 1046 active and 1,000 mesos; "
                                + bundle.bundleId() + " / " + startVariant.variantId()
                                + " starts in 3 seconds.");
                    } catch (IOException | RuntimeException failure) {
                        player.yellowMessage("Victoria MVP reset failed: " + failure.getMessage());
                    }
                    return null;
                });
            }
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
                    if (AgentVictoriaTrainingObjectiveRuntime.start(
                            entry, agent, targetLevel, questsEnabled, System.currentTimeMillis())) {
                        player.yellowMessage(agent.getName() + " started occupancy-aware Victoria training to Lv"
                                + targetLevel + " in " + (questsEnabled ? "mixed" : "grind") + " mode.");
                    } else {
                        player.yellowMessage("Victoria training requires a Lv15+ first-job Agent below the target, "
                                + "with no other foreground objective.");
                    }
                    return null;
                });
            }
            case "stop" -> AgentMailboxRuntime.dispatch(entry, ignored -> {
                boolean cancelled = AgentVictoriaTrainingObjectiveRuntime.cancel(
                        entry, System.currentTimeMillis());
                player.yellowMessage(cancelled ? agent.getName() + " stopped Victoria training."
                        : agent.getName() + " has no active Victoria training objective.");
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

    private static void status(Character player, AgentRuntimeEntry entry, Character agent) {
        AgentCareerProgressionState state = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        AgentCareerBuildBundle bundle = state.bundle();
        player.yellowMessage("Victoria MVP: " + agent.getName() + " Lv" + agent.getLevel()
                + " job=" + agent.getJob().getId() + " map=" + agent.getMapId()
                + " mesos=" + agent.getMeso() + " variant=" + state.startVariantId()
                + " stage=" + state.stage() + ".");
        player.yellowMessage("Plan=" + AgentVictoriaLevel15PlanRepository.defaultPlan().planId()
                + " catalog=" + AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().catalogId()
                + ".");
        if (bundle != null) {
            player.yellowMessage("Bundle=" + bundle.bundleId() + " AP=" + bundle.apProfileId()
                    + " SP=" + bundle.spProfileId() + " quests=" + state.trainingQuestIndex()
                    + "/" + bundle.instructorTrainingQuestIds().size() + ".");
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
