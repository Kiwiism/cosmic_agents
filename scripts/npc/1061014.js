/*
 * Mu Young: Easy and Normal Balrog expedition lobby.
 * @author Ronan
 */

var status = 0;
var expedition;
var expedMembers;
var player;
var em;
var exped;
var expedDifficulty;
var expedName = "Balrog";
var expedBoss = "Balrog";
var expedMap = "Balrog's Tomb";

const ExpeditionType = Java.type('server.expeditions.ExpeditionType');

var list = "What would you like to do?#b\r\n\r\n#L1#View current Expedition members#l\r\n#L2#Start the fight!#l\r\n#L3#Stop the expedition.#l";

function start() {
    action(1, 0, 0);
}

function selectExpedition(type) {
    exped = type;
    if (type == ExpeditionType.BALROG_EASY) {
        expedDifficulty = "Easy";
        em = cm.getEventManager("BalrogBattle_Easy");
    } else {
        expedDifficulty = "Normal";
        em = cm.getEventManager("BalrogBattle");
    }
    expedition = cm.getExpedition(exped);
}

function openExpedition() {
    if (player.getLevel() < exped.getMinLevel() || player.getLevel() > exped.getMaxLevel()) {
        cm.sendOk("You do not meet the criteria to battle " + expedBoss + "!");
        cm.dispose();
    } else if (expedition == null) {
        cm.sendSimple("#e#b<Expedition: " + expedDifficulty + " " + expedName + ">\r\n#k#n" + em.getProperty("party") + "\r\n\r\nWould you like to assemble a team to take on #r" + expedBoss + "#k?\r\n#b#L1#Lets get this going!#l\r\n#L2#No, I think I'll wait a bit...#l\r\n#L3#I would like to see info about this expedition...#l");
        status = 1;
    } else if (expedition.isLeader(player)) {
        if (expedition.isInProgress()) {
            cm.sendOk("Your expedition is already in progress, for those who remain battling lets pray for those brave souls.");
            cm.dispose();
        } else {
            cm.sendSimple(list);
            status = 2;
        }
    } else if (expedition.isRegistering()) {
        if (expedition.contains(player)) {
            cm.sendOk("You have already registered for the expedition. Please wait for #r" + expedition.getLeader().getName() + "#k to begin it.");
            cm.dispose();
        } else {
            cm.sendOk(expedition.addMember(player));
            cm.dispose();
        }
    } else if (expedition.isInProgress()) {
        if (expedition.contains(player)) {
            var eim = em.getInstance(expedName + player.getClient().getChannel());
            if (eim != null && eim.getIntProperty("canJoin") == 1) {
                eim.registerPlayer(player);
            } else {
                cm.sendOk("Your expedition already started the battle against " + expedBoss + ". Lets pray for those brave souls.");
            }
            cm.dispose();
        } else {
            cm.sendOk("Another expedition has taken the initiative to challenge " + expedBoss + ", lets pray for those brave souls.");
            cm.dispose();
        }
    }
}

function action(mode, type, selection) {
    player = cm.getPlayer();
    if (mode == -1 || mode == 0) {
        cm.dispose();
        return;
    }

    if (status == 0) {
        cm.sendSimple("#e#b<Balrog Expedition>#k#n\r\n\r\nChoose a difficulty:\r\n#b#L1#Easy (3 or more players)#l\r\n#L2#Normal (6 or more players)#l");
        status = 20;
    } else if (status == 20) {
        selectExpedition(selection == 1 ? ExpeditionType.BALROG_EASY : ExpeditionType.BALROG_NORMAL);
        openExpedition();
    } else if (status == 1) {
        if (selection == 1) {
            expedition = cm.getExpedition(exped);
            if (expedition != null) {
                cm.sendOk("Someone already took the initiative to lead this expedition. Try joining them!");
                cm.dispose();
                return;
            }
            var result = cm.createExpedition(exped);
            if (result == 0) {
                cm.sendOk("The #r" + expedDifficulty + " " + expedBoss + " Expedition#k has been created.\r\n\r\nTalk to me again to view the current team or start the fight!");
            } else if (result > 0) {
                cm.sendOk("Sorry, you've already reached the quota of attempts for this expedition! Try again another day...");
            } else {
                cm.sendOk("An unexpected error occurred while starting the expedition. Please try again later.");
            }
            cm.dispose();
        } else if (selection == 2) {
            cm.sendOk("Sure, not everyone's up to challenging " + expedBoss + ".");
            cm.dispose();
        } else {
            cm.sendSimple("Hi there. I am #b#nMu Young#n#k, the temple Keeper. This temple is under siege by the Balrog troops. The #e#bOrder of the Altair#n#k has been sending mercenaries, but they were eliminated every time.\r\n#L1#What is the #eOrder of the Altair?#l");
            status = 10;
        }
    } else if (status == 2) {
        if (selection == 1) {
            if (expedition == null) {
                cm.sendOk("The expedition could not be loaded.");
                cm.dispose();
                return;
            }
            expedMembers = expedition.getMemberList();
            var size = expedMembers.size();
            if (size == 1) {
                cm.sendOk("You are the only member of the expedition.");
                cm.dispose();
                return;
            }
            var text = "The following members make up your expedition (Click on them to expel them):\r\n";
            text += "\r\n\t\t1." + expedition.getLeader().getName();
            for (var i = 1; i < size; i++) {
                text += "\r\n#b#L" + (i + 1) + "#" + (i + 1) + ". " + expedMembers.get(i).getValue() + "#l\n";
            }
            cm.sendSimple(text);
            status = 6;
        } else if (selection == 2) {
            var min = exped.getMinSize();
            var size = expedition.getMemberList().size();
            if (size < min) {
                cm.sendOk("You need at least " + min + " players registered in your expedition.");
                cm.dispose();
                return;
            }
            cm.sendOk("The expedition will begin and you will now be escorted to the #b" + expedMap + "#k.");
            status = 4;
        } else if (selection == 3) {
            const PacketCreator = Java.type('tools.PacketCreator');
            player.getMap().broadcastMessage(PacketCreator.serverNotice(6, expedition.getLeader().getName() + " has ended the expedition."));
            cm.endExpedition(expedition);
            cm.sendOk("The expedition has now ended. Sometimes the best strategy is to run away.");
            cm.dispose();
        }
    } else if (status == 4) {
        if (em == null) {
            cm.sendOk("The event could not be initialized. Please report this problem.");
            cm.dispose();
            return;
        }
        em.setProperty("leader", player.getName());
        em.setProperty("channel", player.getClient().getChannel());
        if (!em.startInstance(expedition)) {
            cm.sendOk("Another expedition has taken the initiative to challenge " + expedBoss + ".");
            cm.dispose();
            return;
        }
        cm.dispose();
    } else if (status == 6) {
        if (selection > 0) {
            var banned = expedMembers.get(selection - 1);
            expedition.ban(banned);
            cm.sendOk("You have banned " + banned.getValue() + " from the expedition.");
            cm.dispose();
        } else {
            cm.sendSimple(list);
            status = 2;
        }
    } else if (status == 10) {
        cm.sendOk("The Order of the Altair is a group of elite mercenaries that oversees the world's economy and battle operations. It was founded 40 years after the Black Mage was defeated to foresee the next possible attack.");
        cm.dispose();
    }
}
