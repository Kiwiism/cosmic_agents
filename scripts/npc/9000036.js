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
        if (flow === "completeDoubleInvite") {
            cm.adventurerPartnerCompleteDoubleInvite();
        }
        cm.dispose();
        return;
    }

    if (flow === "completeDoubleInvite") {
        cm.adventurerPartnerCompleteDoubleInvite();
        cm.dispose();
        return;
    }

    if (flow === "roster") {
        cm.sendOk(cm.adventurerPartnerRegister(selection));
        cm.dispose();
        return;
    }

    if (flow === "unregister") {
        cm.sendOk(cm.adventurerPartnerUnregister());
        cm.dispose();
        return;
    }

    if (flow === "inviteAfterModeChange") {
        flow = "completeDoubleInvite";
        cm.sendOk(cm.adventurerPartnerInvite());
        return;
    }

    if (flow === "confirmSoloChange") {
        cm.sendOk(cm.adventurerPartnerChangeToSoloTag());
        cm.dispose();
        return;
    }

    if (flow === "confirmRelease") {
        cm.sendOk(cm.adventurerPartnerRelease());
        cm.dispose();
        return;
    }

    if (flow === "confirmBuffSharingPurchase") {
        cm.sendOk(cm.adventurerPartnerPurchaseBuffSharingItem());
        cm.dispose();
        return;
    }

    switch (selection) {
        case 0:
            flow = "roster";
            cm.sendSimple(cm.adventurerPartnerRosterMenu());
            break;
        case 1:
            flow = "unregister";
            cm.sendYesNo("End this partnership permanently? Any active Partner session will be released safely first. Both characters keep their canonical progress.");
            break;
        case 2:
            flow = "completeDoubleInvite";
            cm.sendOk(cm.adventurerPartnerInvite());
            break;
        case 3:
            cm.sendOk(cm.adventurerPartnerPrepareSoloTag());
            cm.dispose();
            break;
        case 4:
            if (cm.adventurerPartnerSoloChangeRequiresConfirmation()) {
                flow = "confirmSoloChange";
                cm.sendYesNo(cm.adventurerPartnerSoloChangeConfirmation());
            } else {
                cm.sendOk(cm.adventurerPartnerChangeToSoloTag());
                cm.dispose();
            }
            break;
        case 5:
            var changeResult = cm.adventurerPartnerChangeToDoublePartner();
            if (cm.isAdventurerPartnerDoubleModeSelected()) {
                flow = "inviteAfterModeChange";
                cm.sendYesNo(changeResult + "\r\n\r\nWould you like to invite your Partner now?");
            } else {
                cm.sendOk(changeResult);
                cm.dispose();
            }
            break;
        case 6:
            if (cm.adventurerPartnerReleaseRequiresConfirmation()) {
                flow = "confirmRelease";
                cm.sendYesNo(cm.adventurerPartnerReleaseConfirmation());
            } else {
                cm.sendOk(cm.adventurerPartnerRelease());
                cm.dispose();
            }
            break;
        case 7:
            cm.sendOk(cm.adventurerPartnerExplanation());
            cm.dispose();
            break;
        case 8:
            cm.sendDefault();
            cm.dispose();
            break;
        case 10:
            flow = "confirmBuffSharingPurchase";
            cm.sendYesNo(cm.adventurerPartnerBuffSharingPurchaseConfirmation());
            break;
        default:
            cm.dispose();
            break;
    }
}
