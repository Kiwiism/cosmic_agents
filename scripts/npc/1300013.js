/*
	NPC: Blocked Entrance (portal?)
	MAP: Mushroom Castle - East Castle Tower (106021400)
*/

var status;

function startKingPepeInstance() {
    var pepe = cm.getEventManager("KingPepeAndYetis");
    pepe.setProperty("player", cm.getPlayer().getName());
    var requestedVariant = cm.getPlayer().consumeAgentMushroomYetiPityVariant();
    var difficulty = requestedVariant >= 3300005 && requestedVariant <= 3300007
            ? requestedVariant : 1;
    var party = cm.getPlayer().getParty();
    if (party == null) {
        return pepe.startInstance(-1, cm.getPlayer(), cm.getPlayer(), difficulty);
    }
    var eligible = pepe.getEligibleParty(party);
    return eligible.size() > 0 && pepe.startInstance(party, cm.getMap(), difficulty);
}

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
        return;
    } else if (mode == 0 && status == 0) {
        cm.dispose();
        return;
    } else if (mode == 0) {
        status--;
    } else {
        status++;
    }


    if (cm.getMapId() == 106021402) {
        if (!(cm.isQuestCompleted(2331))) {
            cm.dispose();
            return;
        }

        if (status == 0) {
            cm.sendSimple("#L0#Enter to fight #bKing Pepe#k and #bYeti Brothers#k.#l\r\n#L1#Enter to fight #bPrime Minister#k.#l");
        } else if (status == 1) {
            if (selection == 0) {
                if (!startKingPepeInstance()) {
                    cm.sendOk("Your party must have 1 to 3 members here with identical Yeti quest progress.");
                }
                cm.dispose();

            } else if (selection == 1) {
                var em = cm.getEventManager("MK_PrimeMinister2");

                var party = cm.getPlayer().getParty();
                if (party != null) {
                    if (!em.startInstance(party, cm.getMap(), 1)) {
                        cm.sendOk("Another party is already challenging the boss in this channel.");
                    }
                } else {
                    if (!em.startInstance(cm.getPlayer())) {
                        cm.sendOk("Another party is already challenging the boss in this channel.");
                    }
                }

                cm.dispose();

            }
        }
    } else {
        var questProgress = cm.getQuestProgressInt(2330, 3300005) + cm.getQuestProgressInt(2330, 3300006) + cm.getQuestProgressInt(2330, 3300007); //3 Yetis
        var storyReady = cm.isQuestStarted(2330) && questProgress < 3;
        var farmReady = cm.isQuestCompleted(2336);
        if (!(storyReady || farmReady)) {  // story progression or an explicit post-story rematch
            cm.dispose();
            return;
        }

        if (status == 0) {
            cm.sendSimple("#L1#Enter to fight #bKing Pepe#k and #bYeti Brothers#k.#l");
        } else if (status == 1) {
            if (selection == 1) {
                if (!startKingPepeInstance()) {
                    cm.sendOk("Your party must have 1 to 3 members here in the same story or post-story farming mode.");
                }
                cm.dispose();

            }
        }
    }
}
