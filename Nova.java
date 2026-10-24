package dev.nova;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Small drawing toolkit: rounded rectangles, color math, gradient text. */
public final class Ui {
    private Ui() {}

    public static void round(GuiGraphics g, int x1, int y1, int x2, int y2, int r, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) { g.fill(x1, y1, x2, y2, color); return; }
        g.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - (i + 0.5);
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    public static int withAlpha(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF); }

    /** Multiplies a color's alpha by f (0..1). */
    public static int fade(int c, float f) {
        int a = (int) (((c >>> 24) & 255) * Math.max(0f, Math.min(1f, f)));
        return (a << 24) | (c & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int ar = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int ag = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int ab = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void gradientText(GuiGraphics g, Font font, String s, int x, int y, int c1, int c2, float fade, int spacing) {
        int cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int col = lerp(c1, c2, s.length() <= 1 ? 0 : i / (float) (s.length() - 1));
            g.drawString(font, ch, cx, y, fade(col, fade));
            cx += font.width(ch) + spacing;
        }
    }
}
