/*
 * Dressing Room — generic selection dialog launched by the !dress / !dresscash commands.
 * The command stores the item list text via AbstractPlayerInteraction.npcTalkDressingRoom(); this
 * script retrieves it, shows it as a sendSimple list, and prompts for the chosen
 * item action.
 */

var status;
var listText;
var isSelection;
var isDressingSelection;
var selectedItemId;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode < 1) {
        cm.dispose();
        return;
    }

    status++;

    if (status === 0) {
        var AbstractPlayerInteraction = Java.type("scripting.AbstractPlayerInteraction");
        listText = AbstractPlayerInteraction.pollNpcTalkMessage(cm.getChar().getId());
        isDressingSelection = AbstractPlayerInteraction.pollDressingRoomSelection(cm.getChar().getId());
        if (listText == null) {
            cm.dispose();
            return;
        }

        isSelection = listText.indexOf("#L") >= 0;
        if (isSelection) {
            cm.sendSimple(listText);
        } else {
            cm.sendNext(listText);
        }
    } else if (status === 1) {
        if (isSelection && isDressingSelection) {
            selectedItemId = selection;
            if (selectedItemId > 0) {
                cm.sendSimple("Select an action for #v" + selectedItemId + "# #b#z" + selectedItemId + "##k:\r\n"
                    + "#L1#Spawn item (vanilla stat)#l\r\n"
                    + "#L2#Spawn item (randomized stat)#l\r\n"
                    + "#L3#Check whodrops#l");
                return;
            }

            cm.dispose();
            return;
        } else if (isSelection) {
            cm.dispose();
            return;
        }

        var NPCScriptManager = Java.type("scripting.npc.NPCScriptManager");
        var client = cm.getClient();

        NPCScriptManager.getInstance().dispose(cm);
        client.removeClickedNPC();
        NPCScriptManager.getInstance().start(client, cm.getNpc(), cm.getPlayer());
    } else if (status === 2) {
        if (!isDressingSelection || selectedItemId <= 0) {
            cm.dispose();
            return;
        }

        if (selection === 1) {
            if (cm.spawnDressingRoomItem(selectedItemId, false)) {
                cm.sendOk("Spawned #v" + selectedItemId + "# #b#z" + selectedItemId + "##k with vanilla stats.");
            } else {
                cm.dispose();
            }
        } else if (selection === 2) {
            if (cm.spawnDressingRoomItem(selectedItemId, true)) {
                cm.sendOk("Spawned #v" + selectedItemId + "# #b#z" + selectedItemId + "##k with randomized stats.");
            } else {
                cm.dispose();
            }
        } else if (selection === 3) {
            var WhoDropsCommand = Java.type("client.command.commands.gm1.WhoDropsCommand");
            cm.sendNext(WhoDropsCommand.getExactItemDropsText(selectedItemId));
        } else {
            cm.dispose();
        }
    } else {
        cm.dispose();
    }
}
