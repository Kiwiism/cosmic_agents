/* Mushroom Kingdom quest 2314: inspect the first forest barrier. */
function enter(pi) {
    if (pi.isQuestStarted(2314)) {
        pi.setQuestProgress(2314, 2314, "1");
        pi.message("You inspected the powerful barrier. Report back to the Secretary of Domestic Affairs.");
    }
    return false;
}
