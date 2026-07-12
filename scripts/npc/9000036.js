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
/* NPC: Agent E (9000036)
	Victoria Road : Henesys
*/

var status = -1;
var flow = "main";

function start() {
    if (!cm.isAdventurerPartnerProgramEnabled()) {
        cm.sendDefault();
        cm.dispose();
        return;
    }
    status = 0;
    cm.sendSimple(cm.adventurerPartnerMainMenu());
}

function action(mode, type, selection) {
    if (mode !== 1) {
        cm.dispose();
        return;
    }

    if (flow === "roster") {
        cm.sendOk(cm.adventurerPartnerRegister(selection));
        cm.dispose();
        return;
    }

    if (flow === "mode") {
        cm.sendOk(cm.adventurerPartnerChangeMode(selection));
        cm.dispose();
        return;
    }

    switch (selection) {
        case 0:
            flow = "roster";
            cm.sendSimple(cm.adventurerPartnerRosterMenu());
            break;
        case 1:
            cm.sendOk(cm.adventurerPartnerView());
            cm.dispose();
            break;
        case 2:
            cm.sendOk(cm.adventurerPartnerInvite());
            cm.dispose();
            break;
        case 3:
            cm.sendOk(cm.adventurerPartnerEnterSoloTag());
            cm.dispose();
            break;
        case 4:
            flow = "mode";
            cm.sendSimple("Choose the preferred mode. This can only change while the pair is inactive and canonical.\r\n\r\n"
                    + "#L0#Solo Tag Mode#l\r\n#L1#Double Partner Mode#l");
            break;
        case 5:
            cm.sendOk(cm.adventurerPartnerRelease());
            cm.dispose();
            break;
        case 6:
            cm.sendOk(cm.adventurerPartnerUnregister());
            cm.dispose();
            break;
        case 7:
            cm.sendOk(cm.adventurerPartnerExplanation());
            cm.dispose();
            break;
        case 8:
            cm.sendDefault();
            cm.dispose();
            break;
        default:
            cm.dispose();
            break;
    }
}
