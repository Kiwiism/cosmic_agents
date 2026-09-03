function act() {
    rm.sprayItems(true, 1, 500, 1000, 15);
    var eim = rm.getEventInstance();
    if (eim != null) {
        var System = Java.type('java.lang.System');
        eim.setProperty("balrogRewardOpenedAt", "" + System.currentTimeMillis());
    }
}
