/*
 	Author: Ronan
 	Map: Mushroom Castle - Deep Inside Mushroom Forest (106020300)
 	Right Portal
 */

function enter(pi) {
    // Quest 30000 is the v83-compatible persistent flag set by killarmush.js.
    if (pi.isQuestStarted(30000) || pi.isQuestCompleted(30000)) {
        pi.playPortalSound();
        pi.warp(106020400, 2);
        return true;
    } else if (pi.hasItem(4000507)) {
        pi.gainItem(4000507, -1);
        pi.message("You have used a Poison Spore to pass through the barrier.");

        pi.playPortalSound();
        pi.warp(106020400, 2);
        return true;
    }

    pi.message("The overgrown vines is blocking the way.");
    return false;
}
