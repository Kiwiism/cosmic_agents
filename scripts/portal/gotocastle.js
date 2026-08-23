function enter(pi) {
    if (pi.isQuestStarted(2322)) {
        pi.setQuestProgress(2322, 2322, "1");
        pi.playerMessage(5, "You inspected the sealed castle entrance. Report back to the Secretary of Domestic Affairs.");
        return false;
    }
    if (pi.isQuestCompleted(2324)) {
        pi.playPortalSound();
        pi.warp(106020501, 0);
        return true;
    } else {
        pi.playerMessage(5, "The path ahead is covered with sprawling vine thorns, only a Thorn Remover to clear this out...");
        return false;
    }
}
