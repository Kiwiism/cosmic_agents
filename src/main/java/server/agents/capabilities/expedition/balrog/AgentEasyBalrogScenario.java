package server.agents.capabilities.expedition.balrog;

import client.Character;
import scripting.event.EventInstanceManager;
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
    private static final List<String> MEMBER_NAMES = List.of(
            "Balrog01", "Balrog02", "Balrog03", "Balrog04", "Balrog05", "Balrog06");

    private final List<AgentBalrogTestFixtureService.Build> roster;
    private final AgentExpeditionSpec spec;

    public AgentEasyBalrogScenario(long seed) {
        roster = AgentBalrogTestFixtureService.selectRoster(seed);
        spec = new AgentExpeditionSpec(
                "easy-balrog-level-60",
                "Easy Balrog",
                ExpeditionType.BALROG_EASY,
                AgentBalrogDefinition.RECRUIT_MAP,
                AgentBalrogDefinition.BATTLE_MAP,
                AgentBalrogDefinition.ENTRY_NPC,
                6,
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
        for (Character member : members) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            if (entry == null) continue;
            if (liveClaw) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(
                        entry, AgentBalrogDefinition.CLAW_MOBS, Set.of(AgentBalrogDefinition.BODY_MOB));
            } else if (realBody) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().grind(
                        entry, Set.of(AgentBalrogDefinition.BODY_MOB));
            } else {
                AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
            }
        }
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

    @Override
    public List<String> rosterSummary() {
        return List.of(
                "Selected level-60 weapon builds: "
                        + roster.stream().map(AgentBalrogTestFixtureService.Build::buildId).toList(),
                "Per Agent: 2,000 Power Elixirs, 500 All Cures, 100 Sniper Pills, "
                        + "and 30,000 weapon projectiles when required.");
    }
}
