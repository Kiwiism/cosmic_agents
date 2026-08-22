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
import constants.game.GameConstants;
import constants.id.NpcId;
import server.maps.FieldLimit;
import server.maps.MapFactory;
import server.maps.MapleMap;
import server.maps.MiniDungeonInfo;
import server.maps.Portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GotoCommand extends Command {

    {
        setDescription("Warp to a predefined town.");

        List<Entry<String, Integer>> towns = new ArrayList<>(GameConstants.GOTO_TOWNS.entrySet());
        sortGotoEntries(towns);

        try {
            // thanks shavit for noticing goto areas getting loaded from wz needlessly only for the name retrieval

            for (Map.Entry<String, Integer> e : towns) {
                GOTO_TOWNS_INFO += ("'" + e.getKey() + "' - #b" + (MapFactory.loadPlaceName(e.getValue())) + "#k\r\n");
            }

        } catch (Exception e) {
            monitoring.RuntimeFailureLogger.log(e);

            GOTO_TOWNS_INFO = "(none)";
        }

    }

    public static String GOTO_TOWNS_INFO = "";

    private static void sortGotoEntries(List<Entry<String, Integer>> listEntries) {
        listEntries.sort((e1, e2) -> e1.getValue().compareTo(e2.getValue()));
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            String sendStr = "Syntax: #b@goto <town name>#k. Available towns:\r\n\r\n" + GOTO_TOWNS_INFO;

            player.getAbstractPlayerInteraction().npcTalk(NpcId.SPINEL, sendStr);
            return;
        }

        if (!player.isAlive()) {
            player.dropMessage(1, "This command cannot be used when you're dead.");
            return;
        }

        if (player.getEventInstance() != null || MiniDungeonInfo.isDungeonMap(player.getMapId()) || FieldLimit.CANNOTMIGRATE.check(player.getMap().getFieldLimit())) {
            player.dropMessage(1, "This command can not be used in this map.");
            return;
        }

        Map<String, Integer> gotomaps = GameConstants.GOTO_TOWNS;

        if (gotomaps.containsKey(params[0])) {
            MapleMap target = c.getChannelServer().getMapFactory().getMap(gotomaps.get(params[0]));

            // expedition issue with this command detected thanks to Masterrulax
            warp(player, target);
        } else {
            // detailed info on goto available areas suggested thanks to Vcoc
            String sendStr = "Town '#r" + params[0] + "#k' is not available. Available towns:\r\n\r\n" + GOTO_TOWNS_INFO;

            player.getAbstractPlayerInteraction().npcTalk(NpcId.SPINEL, sendStr);
        }
    }

    private static void warp(Character player, MapleMap target) {
        Portal targetPortal = target.getRandomPlayerSpawnpoint();
        player.saveLocationOnWarp();
        player.changeMap(target, targetPortal);
    }
}
