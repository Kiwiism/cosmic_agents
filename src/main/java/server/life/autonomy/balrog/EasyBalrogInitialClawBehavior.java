package server.life.autonomy.balrog;

/** Easy Balrog claw active during the sealed phase (8830009). */
public final class EasyBalrogInitialClawBehavior extends EasyBalrogBehavior {
    public static final int MOB_ID = 8_830_009;

    @Override
    public int mobId() {
        return MOB_ID;
    }
}
