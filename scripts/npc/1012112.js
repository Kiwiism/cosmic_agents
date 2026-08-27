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
/**
 * @author BubblesDev
 * @author Ronan
 * @NPC Tory
 */

var status = 0;
var em = null;
const AgentHpqSessionRegistry = Java.type('server.agents.capabilities.partyquest.hpq.AgentHpqSessionRegistry');

function isLivePartyMember(eim, player) {
    var eventLeader = eim == null ? null : eim.getLeader();
    return eventLeader != null && player != null && eventLeader.getPartyId() > 0
        && player.getPartyId() == eventLeader.getPartyId();
}

function isGmObserver(eim, player) {
    return eim != null && player != null && player.gmLevel() >= 6
        && !isLivePartyMember(eim, player);
}

function isRewardObserver(eim, player) {
    if (AgentHpqSessionRegistry.isManagedEvent(player)) {
        return !AgentHpqSessionRegistry.isRegisteredParticipant(player);
    }
    return isGmObserver(eim, player);
}

function giveRewardAndWarp(eim, destinationMap) {
    var player = cm.getPlayer();
    var managed = AgentHpqSessionRegistry.isManagedEvent(player);
    if (managed && !AgentHpqSessionRegistry.beginRewardClaim(player)) {
        cm.sendOk("This HPQ reward is only available once to registered members of this run.");
        return;
    }
    if (eim != null && eim.giveEventReward(player)) {
        if (managed) {
            AgentHpqSessionRegistry.completeRewardClaim(player);
        }
        cm.warp(destinationMap);
    } else {
        if (managed) {
            AgentHpqSessionRegistry.cancelRewardClaim(player);
        }
        cm.sendOk("It seems you are short on space in one of your inventories. Please check that first to get rewarded properly.");
    }
}

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
        if (mode == 0 && status == 0) {
            cm.dispose();
            return;
        }
        if (mode == 1) {
            status++;
        } else {
            status--;
        }

        if (cm.getMapId() == 100000200) {
            if (status == 0) {
                em = cm.getEventManager("HenesysPQ");
                if (em == null) {
                    cm.sendOk("The Henesys PQ has encountered an error.");
                    cm.dispose();
                    return;
                } else if (cm.isUsingOldPqNpcStyle()) {
                    action(1, 0, 0);
                    return;
                }

                var menu = "#e#b<Party Quest: Primrose Hill>\r\n#k#n" + em.getProperty("party") + "\r\n\r\nI'm Tory. Inside here is a beautiful hill where the primrose blooms. There's a tiger that lives in the hill, Growlie, and he seems to be looking for something to eat. Would you like to head over to the hill of primrose and join forces with your party members to help Growlie out?#b\r\n#L0#I want to participate in the party quest.\r\n#L1#I would like to " + (cm.getPlayer().isRecvPartySearchInviteEnabled() ? "disable" : "enable") + " Party Search.\r\n#L2#I would like to hear more details.\r\n#L3#I would like to redeem an instance hat.";
                if (cm.getPlayer().gmLevel() >= 6) {
                    menu += "\r\n#L4#[GM6] Warp to current HPQ leader.";
                }
                cm.sendSimple(menu);
            } else if (status == 1) {
                if (selection == 0) {
                    if (cm.getParty() == null) {
                        cm.sendOk("Hi there! I'm Tory. This place is covered with mysterious aura of the full moon, and no one person can enter here by him/herself.");
                        cm.dispose();
                    } else if (!cm.isLeader()) {
                        cm.sendOk("If you'd like to enter here, the leader of your party will have to talk to me. Talk to your party leader about this.");
                        cm.dispose();
                    } else {
                        var eli = em.getEligibleParty(cm.getParty());
                        if (eli.size() > 0) {
                            if (!em.startInstance(cm.getParty(), cm.getPlayer().getMap(), 1)) {
                                cm.sendOk("Someone is already attempting the PQ. Please wait for them to finish, or find another channel.");
                            }
                        } else {
                            cm.sendOk("You cannot start this party quest yet, because either your party is not in the range size, some of your party members are not eligible to attempt it or they are not in this map. If you're having trouble finding party members, try Party Search.");
                        }

                        cm.dispose();
                    }
                } else if (selection == 1) {
                    var psState = cm.getPlayer().toggleRecvPartySearchInvite();
                    cm.sendOk("Your Party Search status is now: #b" + (psState ? "enabled" : "disabled") + "#k. Talk to me whenever you want to change it back.");
                    cm.dispose();
                } else if (selection == 2) {
                    cm.sendOk("#e#b<Party Quest: Primrose Hill>#k#n\r\nCollect primrose seeds from the flowers at the bottom part of the map and drop them by the platforms above the stage. Primrose seed color must match to grow the seeds, so test until you find the correct combination. When all the seeds have been planted, that is, starting second part of the mission, scout the Moon Bunny while it prepares Rice Cakes for the hungry Growlie. Once Growlie becomes satisfied, your mission is complete.");
                    cm.dispose();
                } else if (selection == 3) {
                    cm.sendYesNo("So you want to exchange #b20 #b#t4001158##k for the instance-designed hat?");
                } else if (selection == 4 && cm.getPlayer().gmLevel() >= 6) {
                    warpToCurrentHpqLeader();
                } else {
                    cm.dispose();
                }
            } else {
                if (cm.hasItem(4001158, 20)) {
                    if (cm.canHold(1002798)) {
                        cm.gainItem(4001158, -20);
                        cm.gainItem(1002798, 20);
                        cm.sendNext("Here it is. Enjoy!");
                    }
                } else {
                    cm.sendNext("You don't have enough #t4001158# to buy it yet!");
                }

                cm.dispose();
            }
        } else if (cm.getMapId() == 910010100) {
            if (status == 0) {
                if (isRewardObserver(cm.getEventInstance(), cm.getPlayer())) {
                    cm.sendYesNo("Would you like to leave this HPQ observation without receiving a party quest reward?");
                } else {
                    cm.sendYesNo("Thank you for aiding in the effort of feeding the Growlie. As a matter of fact, your team has already been rewarded for reaching this far. With this problem now solved, there is another issue happening right now, if you are interessed check #bTommy#k there for the info. So, are you returning straight to Henesys now?");
                }
            } else if (status == 1) {
                if (isRewardObserver(cm.getEventInstance(), cm.getPlayer())) {
                    cm.warp(100000200);
                } else {
                    giveRewardAndWarp(cm.getEventInstance(), 100000200);
                }
                cm.dispose();
            }
        } else if (cm.getMapId() == 910010400) {
            if (status == 0) {
                if (isRewardObserver(cm.getEventInstance(), cm.getPlayer())) {
                    cm.sendYesNo("Would you like to leave this HPQ observation without receiving a party quest reward?");
                } else {
                    cm.sendYesNo("So, are you returning to Henesys now?");
                }
            } else if (status == 1) {
                if (isRewardObserver(cm.getEventInstance(), cm.getPlayer())) {
                    cm.warp(100000200);
                } else if (cm.getEventInstance() == null) {
                    cm.warp(100000200);
                } else {
                    giveRewardAndWarp(cm.getEventInstance(), 100000200);
                }
                cm.dispose();
            }
        }
    }
}

function warpToCurrentHpqLeader() {
    var player = cm.getPlayer();
    var sessions = AgentHpqSessionRegistry.sessions();
    for (var i = 0; i < sessions.size(); i++) {
        var session = sessions.get(i);
        if (session == null || session.eventLeaderId() <= 0) {
            continue;
        }
        var leader = cm.getClient().getChannelServer().getPlayerStorage().getCharacterById(session.eventLeaderId());
        if (leader == null) {
            leader = cm.getClient().getWorldServer().getPlayerStorage().getCharacterById(session.eventLeaderId());
        }
        if (leader == null || leader.getClient() == null || leader.getClient().getChannel() != player.getClient().getChannel()) {
            continue;
        }
        if (leader.getEventInstance() == null) {
            continue;
        }
        player.forceChangeMap(leader.getMap(), leader.getMap().findClosestPortal(leader.getPosition()));
        cm.sendOk("Warping to HPQ leader " + leader.getName() + ".");
        cm.dispose();
        return;
    }
    cm.sendOk("No active HPQ leader is currently available on this channel.");
    cm.dispose();
}
