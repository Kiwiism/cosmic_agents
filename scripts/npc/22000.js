/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

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
/* Author: Xterminator
	NPC Name: 		Shanks
	Map(s): 		Maple Road : Southperry (60000)
	Description: 		Brings you to Victoria Island
*/
var status = 0;
var adminMenu = false;

function start() {
    const AgentAuthorityService = Java.type('server.agents.auth.AgentAuthorityService');
    if (AgentAuthorityService.mayOperate(cm.getPlayer())) {
        adminMenu = true;
        cm.sendSimple("Take this ship and you'll head off to a bigger continent. What would you like to do?"
            + "\r\n\r\n#L0##bTravel to Victoria Island.#l"
            + "\r\n#L1##r[Agent administration] Send every Agent on Maple Island to Lith Harbor "
            + "after its current plan is complete, then begin Lith Harbor TownLife.#l");
        return;
    }
    sendTravelPrompt();
}

function action(mode, type, selection) {
    if (adminMenu) {
        if (mode != 1) {
            cm.dispose();
            return;
        }
        adminMenu = false;
        if (selection == 1) {
            const HandoffRuntime = Java.type(
                'server.agents.plans.mapleisland.AgentMapleIslandLithHandoffRuntime');
            const System = Java.type('java.lang.System');
            var result = HandoffRuntime.requestAll(cm.getPlayer(), System.currentTimeMillis());
            if (!result.authorized()) {
                cm.sendOk("You are not authorized to assign Agent plans.");
            } else {
                cm.sendOk("The Lith Harbor handoff was assigned to #b" + result.assigned()
                    + "#k Maple Island Agents on this channel.\r\n\r\n"
                    + "#b" + result.startedNow() + "#k can begin the Southperry transfer now.\r\n"
                    + "#b" + result.waitingForCurrentPlan()
                    + "#k will finish their current Maple Island plan first.\r\n"
                    + "#b" + result.alreadyQueued() + "#k were already queued.\r\n"
                    + "#b" + result.alreadyInTownLife()
                    + "#k were already running TownLife.");
            }
            cm.dispose();
            return;
        }
        status = 0;
        sendTravelPrompt();
        return;
    }
    status++;
    if (mode != 1) {
        if (mode == 0 && type != 1) {
            status -= 2;
        } else if (type == 1 || (mode == -1 && type != 1)) {
            if (mode == 0) {
                cm.sendOk("Hmm... I guess you still have things to do here?");
            }
            cm.dispose();
            return;
        }
    }
    if (status == 1) {
        if (cm.haveItem(4031801)) {
            cm.sendNext("Okay, now give me 150 mesos... Hey, what's that? Is that the recommendation letter from Lucas, the chief of Amherst? Hey, you should have told me you had this. I, Shanks, recognize greatness when I see one, and since you have been recommended by Lucas, I see that you have a great, great potential as an adventurer. No way would I charge you for this trip!");
        } else {
            cm.sendNext("Bored of this place? Here... Give me #e150 mesos#n first...");
        }
    } else if (status == 2) {
        if (cm.haveItem(4031801)) {
            cm.sendNextPrev("Since you have the recommendation letter, I won't charge you for this. Alright, buckle up, because we're going to head to Victoria Island right now, and it might get a bit turbulent!!");
        } else if (cm.getLevel() > 6) {
            if (cm.getMeso() < 150) {
                cm.sendOk("What? You're telling me you wanted to go without any money? You're one weirdo...");
                cm.dispose();
            } else {
                cm.sendNext("Awesome! #e150#n mesos accepted! Alright, off to Victoria Island!");
            }
        } else {
            cm.sendOk("Let's see... I don't think you are strong enough. You'll have to be at least Level 7 to go to Victoria Island.");
            cm.dispose();
        }
    } else if (status == 3) {
        if (cm.haveItem(4031801)) {
            cm.gainItem(4031801, -1);
        } else {
            cm.gainMeso(-150);
        }
        cm.warp(104000000, 0);
        cm.dispose();
    }
}

function sendTravelPrompt() {
    cm.sendYesNo("Take this ship and you'll head off to a bigger continent. For #e150 mesos#n, I'll take you to #bVictoria Island#k. The thing is, once you leave this place, you can't ever come back. What do you think? Do you want to go to Victoria Island?");
}
