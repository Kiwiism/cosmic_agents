package server.agents.capabilities.expedition.balrog;

import client.Character;
import client.BuffStat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.combat.AgentCombatBuffRuntime;
import server.agents.capabilities.expedition.AgentExpeditionPreparedMember;
import server.agents.capabilities.expedition.AgentExpeditionScenario;
import server.agents.capabilities.expedition.AgentExpeditionSpec;
import server.agents.field.AgentBalrogTestFixtureService;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.expeditions.ExpeditionType;
import server.life.Monster;

import java.util.List;
import java.util.Set;

/** Easy Balrog's build pool, claw/body phase policy, and battle status. */
public final class AgentEasyBalrogScenario implements AgentExpeditionScenario {
    private static final Logger log = LoggerFactory.getLogger(AgentEasyBalrogScenario.class);
    private static final int POWER_ELIXIR_ITEM_ID = 2_000_005;
    private static final int RECOVERY_THRESHOLD_PERCENT = config.AgentTuning.intValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogScenario.RECOVERY_THRESHOLD_PERCENT");
    private static final long VITALS_LOG_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.expedition.balrog.AgentEasyBalrogScenario.VITALS_LOG_INTERVAL_MS");
    private static final List<String> MEMBER_NAMES = List.of(
            "Balrog01", "Balrog02", "Balrog03", "Balrog04", "Balrog05", "Balrog06",
            "Balrog07", "Balrog08", "Balrog09", "Balrog10", "Balrog11", "Balrog12");

    private final List<AgentBalrogTestFixtureService.Build> roster;
    private final AgentExpeditionSpec spec;
    private CombatPhase combatPhase;
    private long nextVitalsLogAtMs;

    public AgentEasyBalrogScenario(long seed) {
        roster = AgentBalrogTestFixtureService.selectRoster(seed);
        spec = new AgentExpeditionSpec(
                "easy-balrog-level-60",
                "Easy Balrog",
                ExpeditionType.BALROG_EASY,
                AgentBalrogDefinition.RECRUIT_MAP,
                AgentBalrogDefinition.BATTLE_MAP,
                AgentBalrogDefinition.RECRUIT_MAP,
                AgentBalrogDefinition.ENTRY_NPC,
                AgentBalrogDefinition.PARTY_CAPACITY,
                5_000L,
                MEMBER_NAMES,
                List.of(1, 1),
                List.of(1),
                List.of(1, 2, 0));
    }

    @Override
    public AgentExpeditionSpec spec() {
        return spec;
    }

    @Override
    public AgentExpeditionPreparedMember prepareMember(
            AgentRuntimeEntry entry, int ordinal, long memberSeed, long nowMs) throws Exception {
        AgentBalrogTestFixtureService.Build build = roster.get(ordinal);
        AgentBalrogTestFixtureService.PreparationResult prepared =
                AgentBalrogTestFixtureService.prepare(entry, build, memberSeed, nowMs);
        return new AgentExpeditionPreparedMember(
                prepared.job().name(),
                prepared.buildId(),
                prepared.minimumHitChance(),
                prepared.weaponItemId(),
                prepared.weaponAttack());
    }

    @Override
    public void tickCombat(List<Character> members, EventInstanceManager event, long nowMs) {
        if (members.isEmpty() || members.getFirst().getMap() == null) return;
        List<Monster> monsters = server.agents.perception.AgentMapPerception
                .monsters(members.getFirst().getMap());
        boolean liveClaw = monsters.stream().anyMatch(mob -> mob.isAlive()
                && AgentBalrogDefinition.CLAW_MOBS.contains(mob.getId()));
        boolean realBody = monsters.stream().anyMatch(mob -> mob.isAlive()
                && mob.getId() == AgentBalrogDefinition.BODY_MOB && !mob.isFake());
        boolean seal = monsters.stream().anyMatch(mob -> mob.isAlive()
                && mob.getId() == AgentBalrogDefinition.RELEASE_SEAL_MOB);
        CombatPhase nextPhase = liveClaw
                ? (seal ? CombatPhase.SEALED_CLAW : CombatPhase.CLAW)
                : (realBody ? CombatPhase.BODY : CombatPhase.TRANSITION);
        if (nextPhase != combatPhase) {
            combatPhase = nextPhase;
            log.info("Easy Balrog combat phase={} members={} mobs={}",
                    combatPhase, members.size(), battleStatus(members.getFirst()));
        }
        for (Character member : members) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            if (entry == null) continue;
            maintainBattleResources(entry, member);
            if (liveClaw) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(
                        entry, AgentBalrogDefinition.CLAW_MOBS);
            } else if (realBody) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(
                        entry, Set.of(AgentBalrogDefinition.BODY_MOB));
            } else {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
            }
        }
        if (nowMs >= nextVitalsLogAtMs) {
            nextVitalsLogAtMs = nowMs + VITALS_LOG_INTERVAL_MS;
            log.info("Easy Balrog party vitals phase={} {}", combatPhase,
                    members.stream().map(AgentEasyBalrogScenario::vitals).toList());
        }
    }

    private static void maintainBattleResources(AgentRuntimeEntry entry, Character member) {
        AgentCombatBuffRuntime.tryCastCriticalSurvivalBuff(entry, member);
        if (needsExpeditionRecovery(member.getHp(), member.getCurrentMaxHp())
                || needsExpeditionRecovery(member.getMp(), member.getCurrentMaxMp())) {
            AgentPrimitiveCapabilityGatewayRuntime.gateway().useItem(member, POWER_ELIXIR_ITEM_ID);
        }
    }

    static boolean needsExpeditionRecovery(int current, int maximum) {
        return maximum > 0 && (long) current * 100L <= (long) maximum * RECOVERY_THRESHOLD_PERCENT;
    }

    private static String vitals(Character member) {
        return member.getName() + "=" + member.getHp() + '/' + member.getCurrentMaxHp()
                + "hp," + member.getMp() + '/' + member.getCurrentMaxMp() + "mp"
                + (member.getBuffedValue(BuffStat.MAGIC_GUARD) == null ? "" : ",MG")
                + "@(" + member.getPosition().x + ',' + member.getPosition().y + ')';
    }

    @Override
    public List<String> battleStatus(Character leader) {
        if (leader == null || leader.getMap() == null) return List.of();
        List<String> mobs = server.agents.perception.AgentMapPerception.monsters(leader.getMap()).stream()
                .filter(Monster::isAlive)
                .filter(mob -> mob.getId() >= 8830007 && mob.getId() <= 8830013)
                .map(mob -> mob.getId() + (mob.isFake() ? "(fake)" : "")
                        + "=" + mob.getHp() + '/' + mob.getMaxHp())
                .toList();
        return mobs.isEmpty() ? List.of() : List.of("Easy Balrog mobs: " + mobs);
    }

    public List<AgentBalrogTestFixtureService.Build> roster() {
        return roster;
    }

    enum CombatPhase {
        SEALED_CLAW,
        CLAW,
        TRANSITION,
        BODY
    }

    @Override
    public List<String> rosterSummary() {
        return List.of(
                "Selected level-60 weapon builds: "
                        + roster.stream().map(AgentBalrogTestFixtureService.Build::buildId).toList(),
                "Per Agent: 2,000 Power Elixirs, 500 All Cures, 100 Sniper Pills, "
                        + "and 30,000 weapon projectiles when required.");
    }
}
