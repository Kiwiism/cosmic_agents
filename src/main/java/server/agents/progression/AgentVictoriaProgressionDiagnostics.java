package server.agents.progression;

import client.Character;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.supplies.AgentSupplyProcurementState;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;

/** Per-level evidence for the reference run; best-effort and never blocks Agent progression. */
public final class AgentVictoriaProgressionDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(AgentVictoriaProgressionDiagnostics.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path ROOT = Path.of(".runtime", "agents", "progression", "evidence");
    private static final Path MILESTONE_ROOT = Path.of(".runtime", "agents", "progression", "milestones");
    private static final Set<Integer> DURABLE_LEVEL_MILESTONES = Set.of(20, 25, 30);

    private AgentVictoriaProgressionDiagnostics() {
    }

    public static Path capture(AgentRuntimeEntry entry, Character agent, long nowMs) throws IOException {
        AgentVictoriaProgressionEvidence evidence = snapshot(entry, agent, nowMs);
        Path directory = ROOT.resolve(Integer.toString(agent.getId()));
        Path destination = directory.resolve("level-" + agent.getLevel() + ".json");
        writeAtomically(destination, evidence);
        return destination;
    }

    public static Path captureMilestone(AgentRuntimeEntry entry,
                                        Character agent,
                                        String milestoneId,
                                        long nowMs) throws IOException {
        if (milestoneId == null || !milestoneId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("a lowercase milestone id is required");
        }
        Path destination = MILESTONE_ROOT.resolve(Integer.toString(agent.getId()))
                .resolve(milestoneId + ".json");
        if (!Files.exists(destination)) {
            writeAtomically(destination, snapshot(entry, agent, nowMs));
        }
        return destination;
    }

    public static void deleteMilestones(int characterId) throws IOException {
        Path directory = MILESTONE_ROOT.resolve(Integer.toString(characterId));
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(directory);
    }

    static void captureIfLevelChanged(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || !AgentRuntimeRegistry.hasActiveAgentCharacterId(agent.getId())) {
            return;
        }
        AgentVictoriaTrainingState training = entry.capabilityStates().require(
                AgentVictoriaTrainingState.STATE_KEY);
        if (!training.markEvidenceLevelIfChanged(agent.getLevel())) {
            return;
        }
        try {
            capture(entry, agent, nowMs);
            if (DURABLE_LEVEL_MILESTONES.contains(agent.getLevel())) {
                captureMilestone(entry, agent, "victoria-level" + agent.getLevel(), nowMs);
            }
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not persist Victoria progression evidence chr={} level={}",
                    agent.getId(), agent.getLevel(), failure);
        }
    }

    private static void writeAtomically(Path destination,
                                        AgentVictoriaProgressionEvidence evidence) throws IOException {
        Path directory = destination.getParent();
        Files.createDirectories(directory);
        Path temporary = directory.resolve(destination.getFileName() + ".tmp");
        MAPPER.writeValue(temporary.toFile(), evidence);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static AgentVictoriaProgressionEvidence snapshot(AgentRuntimeEntry entry,
                                                      Character agent,
                                                      long nowMs) {
        AgentCareerProgressionState career = entry.capabilityStates().require(
                AgentCareerProgressionState.STATE_KEY);
        AgentCareerBuildBundle bundle = career.bundle();
        AgentObjectiveDefinition objective = AgentObjectiveKernel.active(entry);
        AgentObjectiveState objectives = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
        AgentVictoriaTrainingState training = entry.capabilityStates().require(
                AgentVictoriaTrainingState.STATE_KEY);
        AgentVictoriaQuestSchedulerState quests = entry.capabilityStates().require(
                AgentVictoriaQuestSchedulerState.STATE_KEY);
        AgentSupplyProcurementState supplies = entry.capabilityStates().require(
                AgentSupplyProcurementState.STATE_KEY);
        String apProfile = entry.apBuildProfileState().profile() == null ? ""
                : entry.apBuildProfileState().profile().profileId();
        String spProfile = entry.spBuildProfileState().profile() == null ? ""
                : entry.spBuildProfileState().profile().profileId();
        AgentProgressionProfile progressionProfile = AgentProgressionProfileRuntime.profile(entry);
        return new AgentVictoriaProgressionEvidence(
                2, nowMs, agent.getId(), agent.getName(), agent.getLevel(), agent.getExp(),
                agent.getJob().getId(), agent.getMapId(), agent.getMeso(),
                new AgentVictoriaProgressionEvidence.Stats(agent.getStr(), agent.getDex(),
                        agent.getInt(), agent.getLuk(), agent.getRemainingAp(),
                        Arrays.stream(agent.getRemainingSps()).boxed().toList()),
                bundle == null ? "" : bundle.bundleId(), apProfile, spProfile,
                progressionProfile.profileId(), progressionProfile.profileVersion(),
                career.stage().name(), objective == null ? "" : objective.objectiveId(),
                objective == null ? "" : objective.type(),
                objectives.suspendedSnapshot().stream()
                        .map(suspension -> suspension.objective().objectiveId()).toList(),
                training.targetLevel(), training.selectedMapId(), training.selectionReason(),
                quests.stage().name(),
                quests.questId(), supplies.phase().name(),
                supplies.category() == null ? "" : supplies.category().name());
    }
}
