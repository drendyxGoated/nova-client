package dev.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * Makes bots/aura behave less like a script: eased rotations with noise, mouse-sensitivity (GCD)
 * snapping so look deltas look like real mouse movement, randomized delays and occasional misses.
 * This lowers how robotic things look. It does NOT make anything undetectable.
 */
public final class Humanizer {
    private Humanizer() {}
    public static final Random R = new Random();

    public static boolean on() { return Modules.CLIENT == null || Modules.CLIENT.humanize.on(); }

    public static int jitter(int base) {
        if (!on()) return Math.max(1, base);
        return Math.max(1, (int) Math.round(base * (0.8 + R.nextDouble() * 0.4)));
    }

    public static boolean chance(double p) { return R.nextDouble() < p; }

    /** Snap an angle delta to what a real mouse at your sensitivity could produce. */
    static float quant(float d) {
        double sens = Minecraft.getInstance().options.sensitivity().get();
        double e = sens * 0.6 + 0.2;
        double step = e * e * e * 8.0 * 0.15;
        if (step <= 0) return d;
        return (float) (Math.round(d / step) * step);
    }

    /** Turn toward {yaw,pitch}. Returns the remaining angle (degrees). */
    public static float aim(float[] t, double speed) {
        var p = Minecraft.getInstance().player;
        float cy = p.getYRot(), cp = p.getXRot();
        float dy = Mth.wrapDegrees(t[0] - cy), dp = t[1] - cp;
        if (!on()) {
            p.setYRot(t[0]); p.setXRot(Mth.clamp(t[1], -90f, 90f));
            return 0f;
        }
        double k = speed * (0.75 + R.nextDouble() * 0.5);
        float sy = quant((float) (dy * k + R.nextGaussian() * 0.12));
        float sp = quant((float) (dp * k + R.nextGaussian() * 0.08));
        p.setYRot(cy + sy);
        p.setXRot(Mth.clamp(cp + sp, -90f, 90f));
        return (float) Math.hypot(Mth.wrapDegrees(t[0] - p.getYRot()), t[1] - p.getXRot());
    }
}
