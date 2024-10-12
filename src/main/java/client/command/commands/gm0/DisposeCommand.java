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
package client.command.commands.gm0;

import client.Client;
import client.command.Command;
import scripting.npc.NPCScriptManager;
import scripting.quest.QuestScriptManager;
import tools.PacketCreator;

public class DisposeCommand extends Command {
    {
        setDescription("Dispose to fix NPC chat.");
    }

    @Override
    public void execute(Client c, String[] params) {
        NPCScriptManager.getInstance().dispose(c);
        QuestScriptManager.getInstance().dispose(c);
        c.sendPacket(PacketCreator.enableActions());
        c.removeClickedNPC();
        if (params.length >= 1) {
            try {
                int input = Integer.parseInt(params[0]);
                float ratio = input / 100f;
                c.getPlayer().setAutopotHpAlert(ratio);
                c.getPlayer().message("HP Autopot set to " + input + "%");
            } catch (Exception ignored) {
                c.getPlayer().message("HP Autopot set error.");
            }
        }
        c.getPlayer().message("You've been disposed.");
    }
}
