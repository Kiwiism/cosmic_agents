package server.partner;

public enum ProfileOrientation {
    CANONICAL,
    SWAPPED;

    public ProfileOrientation reversed() {
        return this == CANONICAL ? SWAPPED : CANONICAL;
    }
}
