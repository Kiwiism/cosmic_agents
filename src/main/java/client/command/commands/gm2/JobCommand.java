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
import client.Job;
import client.command.Command;

public class JobCommand extends Command {
    {
        setDescription("Change your job.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        if (params.length == 1) {
            int jobid = Integer.parseInt(params[0]);
            Job targetJob = Job.getById(jobid);
            if (targetJob == null) {
                player.message("Jobid " + jobid + " is not available.");
                return;
            }

            if (player.forceChangeJobForAdmin(targetJob)) {
                player.equipChanged();
                player.message("Job changed to " + targetJob + "; base HP/MP rebuilt for level "
                        + player.getLevel() + ".");
            } else {
                player.message("The requested job could not be applied.");
            }
        } else {
            player.message("Syntax: !job <job id>");
        }
    }
}
