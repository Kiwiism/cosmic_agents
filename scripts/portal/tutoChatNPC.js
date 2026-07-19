function enter(pi) {
    // Consume the one-time tutorial trigger without opening the level-30
    // account shortcut dialog; repeated test warps should enter unobstructed.
    pi.blockPortal();
    return true;
}
