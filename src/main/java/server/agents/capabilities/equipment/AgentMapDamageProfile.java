package server.agents.capabilities.equipment;

import client.Character;
import server.agents.integration.AgentLifeGatewayRuntime;
import server.agents.integration.LifeGateway;
import server.life.Monster;
import server.life.MonsterStats;
import server.life.SpawnPoint;
import server.maps.MapleMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Stats of the selected non-friendly mob on the agent's map. Includes currently
 * alive mobs and normal spawn templates, so an equip pass that runs while the
 * room is briefly clear still benchmarks against the map's mobs.
 */
public record AgentMapDamageProfile(int mobWdef, int mobAvoid, int mobLevel) {
    public static AgentMapDamageProfile snapshot(Character agent) {
        return snapshot(agent, AgentLifeGatewayRuntime.life());
    }

    public static AgentMapDamageProfile snapshotByAvoid(Character agent) {
        return snapshotByAvoid(agent, AgentLifeGatewayRuntime.life());
    }

    static AgentMapDamageProfile snapshot(Character agent, LifeGateway life) {
        return fromStats(collectCandidates(agent, life));
    }

    static AgentMapDamageProfile snapshotByAvoid(Character agent, LifeGateway life) {
        return fromStatsByAvoid(collectCandidates(agent, life));
    }

    private static List<MonsterStats> collectCandidates(Character agent, LifeGateway life) {
        if (agent == null) {
            return null;
        }
        MapleMap map;
        try {
            map = agent.getMap();
        } catch (Throwable t) {
            return null;
        }
        if (map == null) {
            return null;
        }
        List<MonsterStats> candidates = new ArrayList<>();
        List<Monster> mobs;
        try {
            mobs = map.getAllMonsters();
        } catch (Throwable t) {
            return null;
        }
        if (mobs != null) {
            for (Monster monster : mobs) {
                if (monster == null || !monster.isAlive()) {
                    continue;
                }
                MonsterStats stats = monster.getStats();
                if (stats != null) {
                    candidates.add(stats);
                }
            }
        }
        try {
            for (SpawnPoint spawn : map.getMonsterSpawn()) {
                if (spawn == null || spawn.getDenySpawn() || spawn.getMobTime() < 0) {
                    continue;
                }
                Monster template = life.getMonster(spawn.getMonsterId());
                if (template != null && template.getStats() != null) {
                    candidates.add(template.getStats());
                }
            }
        } catch (Throwable ignored) {
            // Live mobs are enough; spawn templates are only a fallback/stabilizer.
        }
        return candidates;
    }

    public static AgentMapDamageProfile fromStats(List<MonsterStats> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        MonsterStats picked = null;
        for (MonsterStats stats : candidates) {
            if (stats == null || stats.isFriendly()) {
                continue;
            }
            if (picked == null
                    || stats.getLevel() > picked.getLevel()
                    || (stats.getLevel() == picked.getLevel() && stats.getAvoidability() > picked.getAvoidability())
                    || (stats.getLevel() == picked.getLevel()
                    && stats.getAvoidability() == picked.getAvoidability()
                    && stats.getPDDamage() > picked.getPDDamage())) {
                picked = stats;
            }
        }
        if (picked == null) {
            return null;
        }
        return new AgentMapDamageProfile(picked.getPDDamage(), picked.getAvoidability(), picked.getLevel());
    }

    public static AgentMapDamageProfile fromStatsByAvoid(List<MonsterStats> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        MonsterStats picked = null;
        for (MonsterStats stats : candidates) {
            if (stats == null || stats.isFriendly()) {
                continue;
            }
            if (picked == null
                    || stats.getAvoidability() > picked.getAvoidability()
                    || (stats.getAvoidability() == picked.getAvoidability()
                    && stats.getLevel() > picked.getLevel())) {
                picked = stats;
            }
        }
        if (picked == null) {
            return null;
        }
        return new AgentMapDamageProfile(picked.getPDDamage(), picked.getAvoidability(), picked.getLevel());
    }
}
