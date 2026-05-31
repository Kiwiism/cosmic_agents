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
import constants.inventory.EquipStats;
import constants.inventory.EquipType;
import provider.DressingRoom;
import server.ItemInformationProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DressingRoomCashCommand extends Command {

    public static final int PAGE_SIZE = 100;

    {
        setDescription("Find cash equipments");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length < 1) {
            player.dropMessage(5, "Please do !dresscash <type> <page>");
            return;
        }

        EquipType equipType = EquipType.getEquipTypeByString(params[0]);
        if (equipType == EquipType.UNDEFINED) {
            player.dropMessage(5, "Unknown EquipType");
            player.dropMessage(5, "Possible EquipTypes are:");
            List<String> unusedTypes = Arrays.asList("UNDEFINED", "FACE", "HAIR");

            for (EquipType type : EquipType.values()) {
                if (type.name().startsWith("PET") || unusedTypes.contains(type.name())) {
                    continue;
                }
                player.dropMessage(5, type.name());
            }
            return;
        }

        int page = 1;
        if (params.length >= 2) {
            try {
                page = Integer.parseInt(params[1]);
            } catch (Exception ignored) {
                player.dropMessage(5, "Invalid page, showing page 1");
            }
        }

        if (!c.tryacquireClient()) {
            player.dropMessage(5, "Please wait a while for your request to be processed.");
            return;
        }
        try {
            String output = "";
            int count = 0;
            List<EquipStats> equips = DressingRoom.getEquipsByType(equipType);
            ItemInformationProvider ii = ItemInformationProvider.getInstance();

            List<EquipStats> result = new ArrayList<>();
            for (EquipStats equip : equips) {
                if (!ii.isCash(equip.getItemId())) {
                    continue;
                }
                result.add(equip);
            }
            int totalFound = result.size();
            int totalPage = (int) Math.ceil((double) totalFound / PAGE_SIZE);

            result = result.subList(Math.clamp((long) (page - 1) * PAGE_SIZE, 0, totalFound), Math.clamp((long) page * PAGE_SIZE, 0, totalFound));

            for (EquipStats equip : result) {
                if (count >= PAGE_SIZE) { // limit to reduce spam
                    break;
                }
                int itemId = equip.getItemId();

                output += "#L" + itemId + "#"; // Dialog Selector
                output += "#v" + itemId + "#"; // Item Icon
                output += "#z" + itemId + "#"; // Item Name + Stats
                output += " - #b" + itemId + "\r\n"; // Item ID
                count++;
            }
            if (count <= 0) {
                player.dropMessage(5, "The item you searched for doesn't exist.");
                return;
            }

            output = "\r\nShowing " + count + " results from page " + page + " / " + totalPage + ":\r\n\r\n" + output + "\r\n.";

            c.getAbstractPlayerInteraction().npcTalkDressingRoom(NpcId.MAPLE_ADMINISTRATOR, output);
        } finally {
            c.releaseClient();
        }
    }
}
