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
package client.command.commands.gm2;

import client.Character;
import client.Client;
import client.command.Command;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.Pet;
import config.YamlConfig;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;
import tools.Pair;

import java.util.ArrayList;

import static java.util.concurrent.TimeUnit.DAYS;

public class ItemDropCommand extends Command {
    {
        setDescription("Spawn an item onto the ground.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();

        if (params.length < 1) {
            player.yellowMessage("Syntax: !drop <itemid> <quantity>");
            return;
        }

        int itemId;
        short quantity = 1;

        try {
            itemId = Integer.parseInt(params[0]);
            if (params.length >= 2) {
                quantity = Short.parseShort(params[1]);
            }
        } catch (Exception e) {
            int size = params.length;

            String lastParam = params[params.length - 1];
            if (isNumber(lastParam)) {
                size--;
                quantity = Short.parseShort(lastParam);
            }

            StringBuilder query = new StringBuilder();
            for (int i = 0; i < size; i++) {
                query.append(params[i]);
                if (i < size - 1) {
                    query.append(" ");
                }
            }
            ArrayList<Pair<Integer, String>> searchResult = ItemInformationProvider.getInstance().getItemDataByName(query.toString());
            if (searchResult == null || searchResult.isEmpty() || searchResult.getFirst() == null) {
                player.yellowMessage("Item '" + query + "' does not exist.");
                return;
            }
            itemId = searchResult.getFirst().getLeft();
        }

        ItemInformationProvider ii = ItemInformationProvider.getInstance();

        if (ii.getName(itemId) == null || !ii.hasData(itemId)) {
            player.yellowMessage("Item id '" + params[0] + "' does not exist.");
            return;
        }

        if (YamlConfig.config.server.BLOCK_GENERATE_CASH_ITEM && ii.isCash(itemId)) {
            player.yellowMessage("You cannot create a cash item with this command.");
            return;
        }

        if (ItemConstants.isPet(itemId)) {
            if (params.length >= 2) {   // thanks to istreety & TacoBell
                quantity = 1;
                long days = Math.max(1, Integer.parseInt(params[1]));
                long expiration = System.currentTimeMillis() + DAYS.toMillis(days);
                int petid = Pet.createPet(itemId);

                Item toDrop = new Item(itemId, (short) 0, quantity, petid);
                toDrop.setExpiration(expiration);

                toDrop.setOwner("");
                if (player.gmLevel() < 3) {
                    short f = toDrop.getFlag();
                    f |= ItemConstants.ACCOUNT_SHARING;
                    f |= ItemConstants.UNTRADEABLE;
                    f |= ItemConstants.SANDBOX;

                    toDrop.setFlag(f);
                    toDrop.setOwner("TRIAL-MODE");
                }

                c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, c.getPlayer().getPosition(), true, true);

                return;
            } else {
                player.yellowMessage("Pet Syntax: !drop <itemid> <expiration>");
                return;
            }
        }

        Item toDrop;
        if (ItemConstants.getInventoryType(itemId) == InventoryType.EQUIP) {
            for (int i = 0; i < quantity; i++) {
                toDrop = ii.randomizeStats((Equip) ii.getEquipById(itemId));
                c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, c.getPlayer().getPosition(), true, true);
            }
            return;
        } else {
            toDrop = new Item(itemId, (short) 0, quantity);
        }

//        toDrop.setOwner(player.getName());
        if (player.gmLevel() < 3) {
            short f = toDrop.getFlag();
            f |= ItemConstants.ACCOUNT_SHARING;
            f |= ItemConstants.UNTRADEABLE;
            f |= ItemConstants.SANDBOX;

            toDrop.setFlag(f);
            toDrop.setOwner("TRIAL-MODE");
        }

        c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, c.getPlayer().getPosition(), true, true);
    }

    private static boolean isNumber(String string) {
        return string != null && string.matches("-?\\d+(\\.\\d+)?");
    }
}
