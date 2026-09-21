package server.agents.capabilities.build.profiles;

import client.Character;
import client.Skill;
import constants.game.GameConstants;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.events.AgentEventPriority;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.agents.progression.events.AgentSkillLearnedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentSpBuildProfileService {
    private AgentSpBuildProfileService() {
    }

    public static AgentSpBuildProfile select(AgentRuntimeEntry entry, String profileId) {
        AgentSpBuildProfile profile = AgentSpBuildProfileRepository.defaultRepository().find(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Agent SP build profile: " + profileId));
        entry.spBuildProfileState().assign(profile);
        autoAssign(entry, entry.bot(), AgentSkillGatewayRuntime.skills());
        return profile;
    }

    public static boolean autoAssign(AgentRuntimeEntry entry, Character agent) {
        return autoAssign(entry, agent, AgentSkillGatewayRuntime.skills());
    }

    /**
     * Spends SP for a character that has no runtime session yet (shaping, provisioning): applies
     * the catalog's default profile of every job on the character's advancement chain, each capped
     * at its supported level, so a shaped level-80 Fighter ends with the first- and second-job build
     * instead of 200 unspent SP. Returns the number of skill levels learned.
     */
    public static int assignOffline(Character agent) {
        if (agent == null || agent.getJob() == null) {
            return 0;
        }
        SkillGateway skills = AgentSkillGatewayRuntime.skills();
        AgentSpBuildProfileRepository repository = AgentSpBuildProfileRepository.defaultRepository();
        int before = learnedLevels(agent);
        int jobId = agent.getJob().getId();
        List<String> profileIds = new ArrayList<>();
        profileIds.add(firstJobProfileId(jobId));
        int second = jobId / 10 * 10;
        if (second % 100 != 0) {
            profileIds.add(AgentSpBuildDefaultCatalog.profileIdFor(second));
            if (jobId != second) {
                profileIds.add(AgentSpBuildDefaultCatalog.profileIdFor(second + 1));
            }
            if (jobId == second + 2) {
                profileIds.add(AgentSpBuildDefaultCatalog.profileIdFor(jobId));
            }
        }
        for (String profileId : profileIds) {
            if (profileId == null) {
                continue;
            }
            AgentSpBuildProfile profile = repository.find(profileId).orElse(null);
            if (profile == null || !profile.supports(agent.getJob())) {
                continue;
            }
            int level = Math.min(agent.getLevel(), profile.supportedThroughLevel());
            Map<Integer, Integer> targets = cumulativeTargets(profile, level);
            applyTargets(null, agent, skills, repository, profile, targets);
            if (allCoreTargetsMet(agent, skills, targets)) {
                applyDumpSkills(null, agent, skills, repository, profile);
            }
        }
        return learnedLevels(agent) - before;
    }

    private static String firstJobProfileId(int jobId) {
        int family = jobId / 100;
        int branch = (jobId / 10) % 10;
        return switch (family) {
            case 1 -> AgentSpBuildDefaultCatalog.SOURCE_ID + "-warrior-first-job";
            case 2 -> AgentSpBuildDefaultCatalog.SOURCE_ID + "-magician-first-job";
            case 3 -> AgentSpBuildDefaultCatalog.SOURCE_ID + "-bowman-first-job";
            case 4 -> AgentSpBuildDefaultCatalog.SOURCE_ID + (branch == 2 ? "-rogue-bandit-first-job" : "-rogue-assassin-first-job");
            case 5 -> AgentSpBuildDefaultCatalog.SOURCE_ID + (branch == 2 ? "-pirate-gunslinger-first-job" : "-pirate-brawler-first-job");
            default -> null;
        };
    }

    private static Map<Integer, Integer> cumulativeTargets(AgentSpBuildProfile profile, int level) {
        Map<Integer, Integer> targets = new LinkedHashMap<>();
        if (!profile.segments().isEmpty()) {
            for (AgentSpBuildProfile.AllocationSegment segment : profile.segments()) {
                if (segment.minimumLevel() > level) {
                    break;
                }
                addTarget(profile, targets, segment.skillId(), segment.points());
            }
            return targets;
        }
        for (AgentSpBuildProfile.LevelPlan levelPlan : profile.levels()) {
            if (levelPlan.level() > level) {
                break;
            }
            for (AgentSpBuildProfile.SkillPoints allocation : levelPlan.allocations()) {
                addTarget(profile, targets, allocation.skillId(), allocation.points());
            }
        }
        return targets;
    }

    private static int learnedLevels(Character agent) {
        int total = 0;
        for (Skill skill : agent.getSkills().keySet()) {
            total += Math.max(0, agent.getSkillLevel(skill));
        }
        return total;
    }

    /** Returns true when an independent profile owns SP allocation for this Agent. */
    public static boolean autoAssign(AgentRuntimeEntry entry, Character agent, SkillGateway skills) {
        AgentSpBuildProfile profile = entry.spBuildProfileState().profile();
        if (profile == null) {
            return false;
        }
        profile = transitionOptimalProfile(entry, agent, profile);
        if (agent == null || agent.getLevel() > profile.supportedThroughLevel()
                || !profile.supports(agent.getJob())) {
            return true;
        }

        AgentSpBuildProfileRepository repository = AgentSpBuildProfileRepository.defaultRepository();
        Map<Integer, Integer> cumulativeTargets = new LinkedHashMap<>();
        if (!profile.segments().isEmpty()) {
            for (AgentSpBuildProfile.AllocationSegment segment : profile.segments()) {
                if (segment.minimumLevel() > agent.getLevel()) {
                    break;
                }
                addTarget(profile, cumulativeTargets, segment.skillId(), segment.points());
            }
            applyTargets(entry, agent, skills, repository, profile, cumulativeTargets);
            if (allCoreTargetsMet(agent, skills, cumulativeTargets)) {
                applyDumpSkills(entry, agent, skills, repository, profile);
            }
            return true;
        }
        for (AgentSpBuildProfile.LevelPlan levelPlan : profile.levels()) {
            if (levelPlan.level() > agent.getLevel()) {
                break;
            }
            for (AgentSpBuildProfile.SkillPoints allocation : levelPlan.allocations()) {
                addTarget(profile, cumulativeTargets, allocation.skillId(), allocation.points());
            }
        }
        applyTargets(entry, agent, skills, repository, profile, cumulativeTargets);
        return true;
    }

    private static void addTarget(AgentSpBuildProfile profile,
                                  Map<Integer, Integer> targets,
                                  int skillId,
                                  int points) {
        targets.compute(skillId, (ignored, current) -> current == null
                ? profile.inheritedSkillLevels().getOrDefault(skillId, 0) + points
                : current + points);
    }

    private static AgentSpBuildProfile transitionOptimalProfile(AgentRuntimeEntry entry,
                                                                 Character agent,
                                                                 AgentSpBuildProfile current) {
        if (agent == null || agent.getJob() == null || !current.isMapleRoyalsOptimal2026()
                || current.exactJobId() == agent.getJob().getId()) {
            return current;
        }
        String nextId = AgentSpBuildDefaultCatalog.nextProfileId(
                current.profileId(), agent.getJob().getId());
        if (nextId == null) {
            return current;
        }
        AgentSpBuildProfile next = AgentSpBuildProfileRepository.defaultRepository().find(nextId)
                .orElseThrow(() -> new IllegalStateException("Missing default SP profile " + nextId));
        entry.spBuildProfileState().assign(next);
        return next;
    }

    private static void applyTargets(AgentRuntimeEntry entry,
                                     Character agent,
                                     SkillGateway skills,
                                     AgentSpBuildProfileRepository repository,
                                     AgentSpBuildProfile profile,
                                     Map<Integer, Integer> targets) {
        for (Map.Entry<Integer, Integer> target : targets.entrySet()) {
            Skill skill = skills.getSkill(target.getKey());
            AgentSpBuildProfileCatalog.SkillDefinition definition = repository.skill(target.getKey());
            if (skill == null || definition == null) {
                continue;
            }
            int book = GameConstants.getSkillBook(target.getKey() / 10000);
            int targetLevel = Math.min(target.getValue(), maxAssignableLevel(agent, skill, definition));
            while (agent.getRemainingSps()[book] > 0 && agent.getSkillLevel(skill) < targetLevel) {
                if (!requirementsMet(agent, definition, skills)) {
                    return;
                }
                if (!learnOne(entry, agent, skill, target.getKey(), book, definition.maxLevel(), profile.profileId())) {
                    return;
                }
            }
        }
    }

    private static boolean allCoreTargetsMet(Character agent,
                                             SkillGateway skills,
                                             Map<Integer, Integer> targets) {
        for (Map.Entry<Integer, Integer> target : targets.entrySet()) {
            Skill skill = skills.getSkill(target.getKey());
            if (skill == null || agent.getSkillLevel(skill) < target.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void applyDumpSkills(AgentRuntimeEntry entry,
                                        Character agent,
                                        SkillGateway skills,
                                        AgentSpBuildProfileRepository repository,
                                        AgentSpBuildProfile profile) {
        for (Integer skillId : profile.dumpSkillIds()) {
            Skill skill = skills.getSkill(skillId);
            AgentSpBuildProfileCatalog.SkillDefinition definition = repository.skill(skillId);
            if (skill == null || definition == null || !requirementsMet(agent, definition, skills)) {
                continue;
            }
            int book = GameConstants.getSkillBook(skillId / 10000);
            while (agent.getRemainingSps()[book] > 0
                    && agent.getSkillLevel(skill) < maxAssignableLevel(agent, skill, definition)) {
                if (!learnOne(entry, agent, skill, skillId, book, definition.maxLevel(), profile.profileId())) {
                    break;
                }
            }
        }
    }

    /**
     * Learns one level and pays one SP only when the level change is accepted. Paying first burned
     * SP whenever {@code changeSkillLevel} refused (e.g. a retroactive passive without its
     * prerequisite), and the callers' loops then drained the whole pool one refused point at a time.
     */
    private static boolean learnOne(AgentRuntimeEntry entry,
                                    Character agent,
                                    Skill skill,
                                    int skillId,
                                    int book,
                                    int catalogMaxLevel,
                                    String profileId) {
        int currentLevel = agent.getSkillLevel(skill);
        int maxLevel = Math.min(skill.getMaxLevel(), catalogMaxLevel);
        if (currentLevel >= maxLevel) {
            return false;
        }
        if (!agent.changeSkillLevel(skill, (byte) (currentLevel + 1),
                agent.getMasterLevel(skill), agent.getSkillExpiration(skill))) {
            return false;
        }
        agent.gainSp(-1, book, false);
        if (agent.getId() > 0) {
            AgentProgressionEventPublisher.publish(entry, new AgentSkillLearnedEvent(
                            agent.getId(), System.currentTimeMillis(), agent.getLevel(),
                            skillId, currentLevel, currentLevel + 1,
                            agent.getRemainingSps()[book], profileId,
                            AgentProgressionEventPublisher.objectiveId(entry)),
                    AgentEventPriority.NORMAL);
        }
        return true;
    }

    private static int maxAssignableLevel(
            Character agent,
            Skill skill,
            AgentSpBuildProfileCatalog.SkillDefinition definition) {
        int maxLevel = Math.min(skill.getMaxLevel(), definition.maxLevel());
        return skill.isFourthJob() ? Math.min(maxLevel, agent.getMasterLevel(skill)) : maxLevel;
    }

    private static boolean requirementsMet(Character agent,
                                           AgentSpBuildProfileCatalog.SkillDefinition definition,
                                           SkillGateway skills) {
        for (AgentSpBuildProfileCatalog.Requirement requirement : definition.requirements()) {
            Skill required = skills.getSkill(requirement.skillId());
            if (required == null || agent.getSkillLevel(required) < requirement.level()) {
                return false;
            }
        }
        return true;
    }
}
