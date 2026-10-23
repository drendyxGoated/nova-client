package dev.nova;

import net.minecraft.util.Mth;

/** Accent colors driven by the Client settings tab. */
public final class Theme {
    private Theme() {}
    static final float[] PRESET_HUE = {215, 270, 320, 355, 25, 140, 185};

    static int hueColor(float hueDeg, float s, float v) {
        float h = (((hueDeg % 360f) + 360f) % 360f) / 360f;
        return 0xFF000000 | (Mth.hsvToRgb(h, s, v) & 0xFFFFFF);
    }

    static float baseHue() {
        ClientSettings c = Modules.CLIENT;
        if (c == null) return 215;
        int m = c.theme.mode();
        if (m < PRESET_HUE.length) return PRESET_HUE[m];
        if (m == PRESET_HUE.length) return (System.currentTimeMillis() / 25) % 360;
        return (float) c.hue.num();
    }

    public static int accent() { return hueColor(baseHue(), 0.62f, 1f); }
    public static int accent2() { return hueColor(baseHue() + 40f, 0.62f, 1f); }
    public static int rainbow(float offset) { return hueColor((System.currentTimeMillis() / 20) % 360 + offset * 360f, 0.6f, 1f); }
    public static boolean anim() { return Modules.CLIENT == null || Modules.CLIENT.anims.on(); }
}
