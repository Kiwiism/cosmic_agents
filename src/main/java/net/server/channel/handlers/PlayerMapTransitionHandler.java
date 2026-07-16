/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package net.server.channel.handlers;

import client.BuffStat;
import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import net.server.services.task.channel.MobPhysicsService;
import server.agents.diagnostics.MapTransitionPacketTraceRuntime;
import server.life.Monster;
import server.maps.MapObject;
import server.maps.MapleMap;
import tools.PacketCreator;
import tools.Pair;

import java.util.Collections;
import java.util.List;

/**
 * @author Ronan
 */
public final class PlayerMapTransitionHandler extends AbstractPacketHandler {

    @Override
    public final void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();
        MapTransitionPacketTraceRuntime.mark(c, "PLAYER_MAP_TRANSFER received");
        if (!chr.isChangingMaps()) {
            MapTransitionPacketTraceRuntime.mark(c, "duplicate transition acknowledgement ignored");
            return;
        }

        int beaconid = chr.getBuffSource(BuffStat.HOMING_BEACON);
        if (beaconid != -1) {
            chr.cancelBuffStats(BuffStat.HOMING_BEACON);

            final List<Pair<BuffStat, Integer>> stat = Collections.singletonList(new Pair<>(BuffStat.HOMING_BEACON, 0));
            chr.sendPacket(PacketCreator.giveBuff(1, beaconid, stat));
        }

        MapleMap map = chr.getMap();
        // The acknowledgement means the destination field exists client-side. Keep
        // Agent attack/physics publication gated by warm-up, but allow ordinary death
        // packets from this point onward so a concurrent kill cannot leave a ghost.
        map.beginMobPhysicsObserverWarmup();
        MobPhysicsService.releaseMapInstances(
                map, MobPhysicsService.ReleaseReason.CLIENT_MAP_TRANSITION);
        chr.setMapTransitionComplete();
        MapTransitionPacketTraceRuntime.mark(c, "server physics released; normal field broadcasts enabled");

        boolean canControlMobs = !chr.isHidden() || map.shouldAllowHiddenMobSimulation(chr);
        if (canControlMobs) {  // hidden characters are allowed only by the agent-simulation server hook
            // Do not let a server-owned MOVE_MONSTER stream survive into the client's
            // acknowledgement. Placement deliberately deferred client control, so one
            // ownership handoff is sufficient here; destroying and respawning every mob
            // creates a fragile stop/kill/stop/spawn/control burst on v83.
            for (MapObject mo : map.getMonsters()) {
                Monster m = (Monster) mo;
                m.lockMonster();
                try {
                    if (!m.isAlive() || map.getMonsterByOid(m.getObjectId()) != m) {
                        continue;
                    }
                    MapTransitionPacketTraceRuntime.markMonster(c, "before control handoff", m);
                    // Atomically promote the object once for the acknowledged client.
                    // The full control packet also carries current temporary monster
                    // statuses and can materialize a controller outside placement range.
                    // Unlike destroy/respawn, this is safe for effect-spawned monsters.
                    m.aggroSwitchController(chr, false);
                    MapTransitionPacketTraceRuntime.markMonster(c, "after control handoff", m);
                } finally {
                    m.unlockMonster();
                }
            }
        }

        int removedGhosts = map.reconcileTransitionMonsterVisibility(chr);
        if (removedGhosts > 0) {
            MapTransitionPacketTraceRuntime.mark(c,
                    "removed transition monster ghosts=" + removedGhosts);
        }

        MapTransitionPacketTraceRuntime.mark(c, "post-ack physics warmupRemainingMs="
                + map.mobPhysicsObserverWarmupRemainingMs());
        MapTransitionPacketTraceRuntime.complete(c);
    }
}
