package server.agents.progression;

import client.Character;
import client.Job;
import client.QuestStatus;
import client.SkillFactory;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatSkillConstraintState;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.Set;

/** Resumable, live-state-reconciled Explorer second-job advancement. */
public final class AgentSecondJobAdvancementRuntime {
    private static final int INTERACTION_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.progression.AgentSecondJobAdvancementRuntime.INTERACTION_DISTANCE_PX");
    private static final long TRIAL_REBALANCE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentSecondJobAdvancementRuntime.TRIAL_REBALANCE_MS");
    private static final long TRIAL_TIMEOUT_MS = 20 * 60_000L;

    private AgentSecondJobAdvancementRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentSecondJobAdvancementState state = entry.capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        if (state.branchId().isBlank()) return false;
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(state.branchId());
        if (state.phase() == AgentSecondJobAdvancementState.Phase.BLOCKED) return false;
        AgentSecondJobAdvancementState.Phase previousPhase = state.phase();
        AgentSecondJobAdvancementState.Phase phase = reconcile(agent, branch, gateway);
        if (phase == AgentSecondJobAdvancementState.Phase.EXAMINER && previousPhase != phase) {
            // End the trial combat once, then preserve the examiner approach route across ticks.
            // Repeated stop calls used to reset navigation continuously on tall trial maps.
            gateway.stop(entry);
        }
        state.phase(phase, phaseReason(phase, branch, agent, gateway), nowMs);

        if (phase == AgentSecondJobAdvancementState.Phase.COMPLETE) {
            AgentSpBuildProfileService.select(entry, branch.spProfileId());
            clearTrial(entry, agent, branch);
            return false;
        }
        if (nowMs - state.phaseSinceMs() >= 120_000
                && gateway.stuckDurationMs(entry) >= 120_000) {
            block(entry, agent, branch, state,
                    "navigation made no physical progress for 120 seconds", nowMs, gateway);
            return false;
        }
        String readiness = readinessFailure(agent, branch, gateway);
        if (readiness != null && phase != AgentSecondJobAdvancementState.Phase.VERIFY) {
            gateway.stop(entry);
            state.phase(AgentSecondJobAdvancementState.Phase.READY, readiness, nowMs);
            return false; // yield to supply/survival maintenance
        }
        if ((phase == AgentSecondJobAdvancementState.Phase.TRIAL
                || phase == AgentSecondJobAdvancementState.Phase.EXAMINER)
                && nowMs - state.phaseSinceMs() > TRIAL_TIMEOUT_MS) {
            block(entry, agent, branch, state, "trial made no terminal progress within 20 minutes", nowMs, gateway);
            return false;
        }

        return switch (phase) {
            case LEADER -> leader(entry, agent, branch, gateway);
            case INSTRUCTOR -> instructor(entry, agent, branch, gateway);
            case TRIAL -> trial(entry, agent, branch, state, nowMs, gateway);
            case EXAMINER -> examiner(entry, agent, branch, gateway);
            case RETURN_TO_LEADER -> finalLeader(entry, agent, branch, state, nowMs, gateway);
            case VERIFY -> verify(entry, agent, branch, state, nowMs, gateway);
            case READY -> false;
            case COMPLETE, BLOCKED -> false;
        };
    }

    public static void cancel(AgentRuntimeEntry entry, Character agent) {
        AgentSecondJobAdvancementState state = entry.capabilityStates()
                .find(AgentSecondJobAdvancementState.STATE_KEY).orElse(null);
        if (state == null || state.branchId().isBlank()) return;
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(state.branchId());
        clearTrial(entry, agent, branch);
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
    }

    private static AgentSecondJobAdvancementState.Phase reconcile(
            Character agent, AgentSecondJobCatalog.Branch branch, PrimitiveCapabilityGateway gateway) {
        int job = gateway.characterState(agent).jobId();
        if (job == branch.targetJobId()) return AgentSecondJobAdvancementState.Phase.COMPLETE;
        if (gateway.mapId(agent) == branch.trialMapId()) {
            return gateway.itemCount(agent, branch.collectionItemId()) >= branch.requiredCount()
                    ? AgentSecondJobAdvancementState.Phase.EXAMINER
                    : AgentSecondJobAdvancementState.Phase.TRIAL;
        }
        if (branch.pirate()) {
            return gateway.questStatus(agent, branch.collectQuestId())
                    == QuestStatus.Status.COMPLETED.getId()
                    ? AgentSecondJobAdvancementState.Phase.RETURN_TO_LEADER
                    : AgentSecondJobAdvancementState.Phase.LEADER;
        }
        if (gateway.itemCount(agent, 4031012) > 0
                || gateway.questStatus(agent, branch.finalQuestId())
                == QuestStatus.Status.STARTED.getId()) {
            return AgentSecondJobAdvancementState.Phase.RETURN_TO_LEADER;
        }
        if (gateway.itemCount(agent, branch.letterItemId()) > 0
                || gateway.questStatus(agent, branch.startQuestId())
                == QuestStatus.Status.COMPLETED.getId()
                || gateway.questStatus(agent, branch.collectQuestId())
                == QuestStatus.Status.STARTED.getId()) {
            return AgentSecondJobAdvancementState.Phase.INSTRUCTOR;
        }
        return AgentSecondJobAdvancementState.Phase.LEADER;
    }

    private static boolean leader(AgentRuntimeEntry entry, Character agent,
                                  AgentSecondJobCatalog.Branch branch,
                                  PrimitiveCapabilityGateway gateway) {
        if (!travel(entry, agent, branch.leaderMapId(), gateway)) return true;
        if (!nearNpc(entry, agent, branch.leaderNpcId(), gateway)) return true;
        if (branch.pirate()
                && gateway.questStatus(agent, branch.startQuestId())
                == QuestStatus.Status.NOT_STARTED.getId()
                && gateway.canStartQuest(agent, branch.startQuestId(), branch.leaderNpcId())) {
            gateway.startQuest(agent, branch.startQuestId(), branch.leaderNpcId());
            return true;
        }
        if (!claimTrial(agent, branch, gateway)) return false;
        gateway.stop(entry);
        gateway.runNpcScript(agent, branch.leaderNpcId());
        return true;
    }

    private static boolean instructor(AgentRuntimeEntry entry, Character agent,
                                      AgentSecondJobCatalog.Branch branch,
                                      PrimitiveCapabilityGateway gateway) {
        if (!travel(entry, agent, branch.instructorMapId(), gateway)) return true;
        if (!nearNpc(entry, agent, branch.instructorNpcId(), gateway)) return true;
        if (!claimTrial(agent, branch, gateway)) return false;
        gateway.stop(entry);
        gateway.runNpcScript(agent, branch.instructorNpcId());
        return true;
    }

    private static boolean trial(AgentRuntimeEntry entry, Character agent,
                                 AgentSecondJobCatalog.Branch branch,
                                 AgentSecondJobAdvancementState state, long nowMs,
                                 PrimitiveCapabilityGateway gateway) {
        if (!AgentSecondJobTrialRegistry.claim(branch.trialMapId(), agent.getId())) return false;
        if (branch.requiredSkillId() > 0) {
            entry.capabilityStates().require(AgentCombatSkillConstraintState.STATE_KEY)
                    .require(branch.requiredSkillId());
        }
        int itemCount = gateway.itemCount(agent, branch.collectionItemId());
        if (state.trialRebalanceDue(itemCount, nowMs, TRIAL_REBALANCE_MS)) {
            // Preserve collected items and the advancement phase, but discard stale target,
            // lease, platform-batch, and navigation state before selecting the remaining mobs.
            gateway.stop(entry);
        }
        gateway.lootNearby(agent, Set.of(branch.collectionItemId()));
        gateway.grind(entry, branch.trialMobIds(), Set.of());
        return true;
    }

    private static boolean examiner(AgentRuntimeEntry entry, Character agent,
                                    AgentSecondJobCatalog.Branch branch,
                                    PrimitiveCapabilityGateway gateway) {
        entry.capabilityStates().require(AgentCombatSkillConstraintState.STATE_KEY).clear();
        if (!nearNpc(entry, agent, branch.examinerNpcId(), gateway)) return true;
        gateway.runNpcScript(agent, branch.examinerNpcId());
        AgentSecondJobTrialRegistry.release(branch.trialMapId(), agent.getId());
        return true;
    }

    private static boolean finalLeader(AgentRuntimeEntry entry, Character agent,
                                       AgentSecondJobCatalog.Branch branch,
                                       AgentSecondJobAdvancementState state, long nowMs,
                                       PrimitiveCapabilityGateway gateway) {
        if (!travel(entry, agent, branch.leaderMapId(), gateway)) return true;
        if (!nearNpc(entry, agent, branch.leaderNpcId(), gateway)) return true;
        Job oldJob = agent.getJob();
        gateway.stop(entry);
        gateway.runNpcScript(agent, branch.leaderNpcId(), 0, 3, branch.leaderSelection());
        if (agent.getJob().getId() != branch.targetJobId()) {
            state.phase(AgentSecondJobAdvancementState.Phase.VERIFY,
                    "leader dialogue did not commit expected job " + branch.targetJobId(), nowMs);
            return true;
        }
        AgentSpBuildProfileService.select(entry, branch.spProfileId());
        AgentBuildService.handleJobAdvance(entry, agent, oldJob, agent.getJob());
        state.phase(AgentSecondJobAdvancementState.Phase.COMPLETE,
                "advanced to job " + branch.targetJobId(), nowMs);
        return true;
    }

    private static boolean verify(AgentRuntimeEntry entry, Character agent,
                                  AgentSecondJobCatalog.Branch branch,
                                  AgentSecondJobAdvancementState state, long nowMs,
                                  PrimitiveCapabilityGateway gateway) {
        if (agent.getJob().getId() == branch.targetJobId()) {
            state.phase(AgentSecondJobAdvancementState.Phase.COMPLETE,
                    "live job verified", nowMs);
            return true;
        }
        block(entry, agent, branch, state,
                "final job verification expected " + branch.targetJobId()
                        + " but found " + agent.getJob().getId(), nowMs, gateway);
        return false;
    }

    private static String readinessFailure(Character agent, AgentSecondJobCatalog.Branch branch,
                                           PrimitiveCapabilityGateway gateway) {
        var snapshot = gateway.characterState(agent);
        if (!snapshot.alive()) return "waiting for survival recovery";
        if (snapshot.level() < 30) return "level 30 is required";
        if (snapshot.jobId() != branch.firstJobId() && snapshot.jobId() != branch.targetJobId()) {
            return "expected first job " + branch.firstJobId() + " but found " + snapshot.jobId();
        }
        if (gateway.freeSlots(agent, branch.collectionItemId()) < 1
                && gateway.itemCount(agent, branch.collectionItemId()) < branch.requiredCount()) {
            return "one free ETC slot is required for trial drops";
        }
        if (snapshot.hp() * 4 < snapshot.maxHp()) return "HP is below the safe trial threshold";
        if (branch.requiredSkillId() > 0
                && agent.getSkillLevel(SkillFactory.getSkill(branch.requiredSkillId())) < 1) {
            return "required Pirate trial skill " + branch.requiredSkillId() + " is not learned";
        }
        if (branch.targetJobId() == 520) {
            var weapon = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
            if (AgentCombatAmmoCounter.countAmmo(agent, weapon) < 1) {
                return "Gunslinger trial requires bullets";
            }
        }
        return null;
    }

    private static boolean travel(AgentRuntimeEntry entry, Character agent, int mapId,
                                  PrimitiveCapabilityGateway gateway) {
        AgentSecondJobAdvancementState state = entry.capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        if (gateway.mapId(agent) == mapId) {
            state.capabilityProgress();
            return true;
        }
        AgentRouteOutcome outcome = gateway.travelTo(entry, agent, mapId, System.currentTimeMillis());
        if (outcome.status() == AgentRouteStatus.MOVING) {
            state.capabilityProgress();
        } else if (outcome.status() == AgentRouteStatus.NO_ROUTE
                || outcome.status() == AgentRouteStatus.PORTAL_UNAVAILABLE) {
            gateway.refreshNavigation(entry, agent);
            if (state.capabilityFailure() >= 8) {
                state.phase(AgentSecondJobAdvancementState.Phase.BLOCKED,
                        "navigation could not route from " + gateway.mapId(agent)
                                + " to second-job map " + mapId + ": " + outcome.status(),
                        System.currentTimeMillis());
            }
        }
        return outcome.status() == AgentRouteStatus.ARRIVED;
    }

    private static boolean nearNpc(AgentRuntimeEntry entry, Character agent, int npcId,
                                   PrimitiveCapabilityGateway gateway) {
        Point npc = gateway.npcPosition(agent, npcId);
        AgentSecondJobAdvancementState state = entry.capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        if (npc == null) {
            if (state.capabilityFailure() >= 8) {
                state.phase(AgentSecondJobAdvancementState.Phase.BLOCKED,
                        "second-job NPC " + npcId + " is absent from map " + gateway.mapId(agent),
                        System.currentTimeMillis());
            }
            return false;
        }
        state.capabilityProgress();
        if (!gateway.grounded(agent)
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, npc, INTERACTION_DISTANCE_PX)) {
            gateway.navigate(entry, npc, true);
            return false;
        }
        gateway.facePosition(agent, npc);
        return true;
    }

    private static boolean claimTrial(Character agent, AgentSecondJobCatalog.Branch branch,
                                      PrimitiveCapabilityGateway gateway) {
        return AgentSecondJobTrialRegistry.claim(branch.trialMapId(), agent.getId())
                && gateway.characterCount(agent, branch.trialMapId()) == 0;
    }

    private static void clearTrial(AgentRuntimeEntry entry, Character agent,
                                   AgentSecondJobCatalog.Branch branch) {
        entry.capabilityStates().require(AgentCombatSkillConstraintState.STATE_KEY).clear();
        AgentSecondJobTrialRegistry.release(branch.trialMapId(), agent.getId());
    }

    private static void block(AgentRuntimeEntry entry, Character agent,
                              AgentSecondJobCatalog.Branch branch,
                              AgentSecondJobAdvancementState state, String reason, long nowMs,
                              PrimitiveCapabilityGateway gateway) {
        clearTrial(entry, agent, branch);
        gateway.stop(entry);
        state.phase(AgentSecondJobAdvancementState.Phase.BLOCKED, reason, nowMs);
    }

    private static String phaseReason(AgentSecondJobAdvancementState.Phase phase,
                                      AgentSecondJobCatalog.Branch branch, Character agent,
                                      PrimitiveCapabilityGateway gateway) {
        return switch (phase) {
            case LEADER -> "travelling to job leader to start or resume the advancement";
            case INSTRUCTOR -> "taking the letter to the field instructor";
            case TRIAL -> "collecting " + gateway.itemCount(agent, branch.collectionItemId())
                    + '/' + branch.requiredCount() + " trial items";
            case EXAMINER -> "trial items complete; approaching examiner";
            case RETURN_TO_LEADER -> "returning to the job leader for committed branch " + branch.id();
            case VERIFY -> "verifying irreversible job change";
            case COMPLETE -> "second-job advancement complete";
            case READY -> "checking advancement readiness";
            case BLOCKED -> "advancement blocked";
        };
    }
}
