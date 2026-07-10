/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
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

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm1;

import client.Character;
import client.Client;
import client.command.Command;
import constants.id.NpcId;
import server.ItemInformationProvider;
import server.life.LifeFactory;
import server.life.MonsterDropEntry;
import server.life.MonsterInformationProvider;
import server.life.SpawnPoint;
import tools.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WhatDropsFromCommand extends Command {
    {
        setDescription("Show what items drop from a mob.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        String output = "";
        Set<Integer> uniqueMobs = new HashSet<>();
        List<Pair<Integer, String>> monsters = new ArrayList<>();
        int limit = 5;

        MonsterInformationProvider mip = MonsterInformationProvider.getInstance();
        if (params.length < 1) { // No parameters provided; get monsters from the map's spawn points.
            player.dropMessage(5, "Do @whatdropsfrom <monster name> for specific mob info, defaulting to map mobs");
            uniqueMobs = player.getMap().getMonsterSpawn()
                    .stream()
                    .map(SpawnPoint::getMonsterId)
                    .collect(Collectors.toSet());
        } else { // Find by name
            String monsterName = player.getLastCommandMessage();
            uniqueMobs = MonsterInformationProvider.getMobsIDsFromName(monsterName).stream().map(Pair::getLeft).collect(Collectors.toSet());
        }

        for (Integer mobId : uniqueMobs) {
            String mobName = mip.getMobNameFromId(mobId);
            if (mobName == null || mobName.isEmpty()) {
                mobName = "Mob#" + mobId;
            }
            mobName +=  "(" + LifeFactory.getMonsterLevel(mobId) + ")";
            monsters.add(new Pair<>(mobId, mobName));
        }

        if (monsters.isEmpty()) {
            player.dropMessage(5, "Do @whatdropsfrom <monster name> for specific mob info, defaulting to map mobs");
        } else {
            Iterator<Pair<Integer, String>> listIterator = monsters.iterator();
            for (int i = 0; i < limit && listIterator.hasNext(); i++) {
                Pair<Integer, String> data = listIterator.next();
                int mobId = data.getLeft();
                String mobName = data.getRight();
                output += mobName + " drops the following items:\r\n\r\n";
                for (MonsterDropEntry drop : mip.retrieveDrop(mobId)) {
                    try {
                        String itemName = ItemInformationProvider.getInstance().getName(drop.itemId);
                        if (itemName == null || itemName.equals("null") || drop.chance == 0) {
                            continue;
                        }
                        // Calculate the chance, factoring in whether the mob is a boss or not.
                        float chance = Math.max(1000000 / drop.chance /
                                (!mip.isBoss(mobId) ? player.getDropRate() : player.getBossDropRate()), 1);
                        output += "- #z" + drop.itemId + "# (1/" + (int) chance + ")\r\n";
                    } catch (Exception ex) {
                        monitoring.RuntimeFailureLogger.log(ex);
                    }
                }
                output += "\r\n";
            }
        }

        c.getAbstractPlayerInteraction().npcTalk(NpcId.MAPLE_ADMINISTRATOR, output);
    }
}
