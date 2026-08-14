package server.agents.economy.integration.cosmic;

import client.Character;
import server.maps.MapleMap;
import server.maps.Portal;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Removes offscreen workers from all FM observers and re-enters via the real entrance map. */
public final class CosmicOffscreenPresence implements CosmicEconomyWorldAdapter.OffscreenPresence {
    private final Set<Integer> detached = ConcurrentHashMap.newKeySet();

    @Override
    public void leaveVisibleFreeMarket(Character agent, Instant logicalAt) {
        if (agent.getMap() == null || agent.getMapId() < 910000000 || agent.getMapId() > 910000022)
            throw new IllegalStateException("agent is not physically in the Free Market");
        if (!detached.add(agent.getId())) throw new IllegalStateException("agent is already detached");
        try { agent.getMap().removePlayer(agent); }
        catch (RuntimeException failure) { detached.remove(agent.getId()); throw failure; }
    }

    @Override
    public void enterFreeMarketEntrance(Character agent, Instant logicalAt) {
        if (!detached.remove(agent.getId())) throw new IllegalStateException("agent was not detached");
        try {
            MapleMap entrance = agent.getClient().getChannelServer().getMapFactory().getMap(910000000);
            Portal spawn = entrance.getPortal(0);
            if (spawn == null) spawn = entrance.findClosestPlayerSpawnpoint(new java.awt.Point(0, 0));
            if (spawn == null) throw new IllegalStateException("Free Market entrance has no spawn portal");
            agent.changeMap(entrance, spawn.getPosition());
        } catch (RuntimeException failure) {
            detached.add(agent.getId());
            throw failure;
        }
    }
}
