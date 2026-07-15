package client;

public final class ExperienceRateScaler {
    private ExperienceRateScaler() {
    }

    public static int scale(int gain, int rate) {
        if (gain <= 0 || rate == 1) {
            return gain;
        }

        long scaledGain = (long) gain * rate;
        return (int) Math.min(scaledGain, Integer.MAX_VALUE);
    }

    public static int scale(double gain, int rate) {
        if (gain <= 0) {
            return (int) gain;
        }

        double scaledGain = gain * rate;
        return (int) Math.min(scaledGain, Integer.MAX_VALUE);
    }
}
