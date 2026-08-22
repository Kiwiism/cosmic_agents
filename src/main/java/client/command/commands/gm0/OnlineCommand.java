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

import client.Character;
import client.Client;
import client.command.Command;
import client.command.CommandTargetPolicy;
import net.server.Server;
import net.server.channel.Channel;

import java.util.ArrayList;
import java.util.List;

public class OnlineCommand extends Command {
    {
        setDescription("Show online player and Agent totals.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        boolean listPlayers = params.length == 1 && "players".equals(params[0]);
        boolean listAgents = params.length == 1 && "agents".equals(params[0]);
        if (params.length > 0 && !listPlayers && !listAgents) {
            player.yellowMessage("Syntax: @online [players|agents]");
            return;
        }
        if ((listPlayers || listAgents) && player.gmLevel() < 2) {
            player.yellowMessage("You need GM level 2 to list character names.");
            return;
        }

        int playerCount = 0;
        int agentCount = 0;
        List<String> names = new ArrayList<>();
        for (Channel ch : Server.getInstance().getChannelsFromWorld(player.getWorld())) {
            for (Character chr : ch.getPlayerStorage().getAllCharacters()) {
                boolean agent = CommandTargetPolicy.isAgent(chr);
                if (agent) {
                    agentCount++;
                } else {
                    playerCount++;
                }
                if ((listAgents && agent) || (listPlayers && !agent)) {
                    names.add(Character.makeMapleReadable(chr.getName()));
                }
            }
        }
        player.yellowMessage("Online: " + playerCount + " players, " + agentCount + " agents.");
        if (!names.isEmpty()) {
            names.sort(String.CASE_INSENSITIVE_ORDER);
            player.message((listAgents ? "Agents: " : "Players: ") + String.join(", ", names));
        } else if (listPlayers || listAgents) {
            player.message(listAgents ? "No agents are online." : "No players are online.");
        }
    }
}
