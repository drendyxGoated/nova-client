package dev.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Vape-style toasts when modules are toggled. */
public final class Notifications {
    private Notifications() {}
    private record N(String text, int color, long at) {}
    private static final List<N> LIST = new ArrayList<>();

    public static void push(String text, int color) {
        LIST.add(new N(text, color, System.currentTimeMillis()));
        if (LIST.size() > 6) LIST.remove(0);
    }

    public static void module(Module m) {
        if (m.hidden || Modules.CLIENT == null || !Modules.CLIENT.notifs.on()) return;
        push(m.name + (m.enabled ? " enabled" : " disabled"), m.enabled ? Theme.accent() : 0xFF888894);
    }

    public static void render(GuiGraphics g, Minecraft mc) {
        long now = System.currentTimeMillis();
        LIST.removeIf(n -> now - n.at > 2600);
        int w = g.guiWidth(), h = g.guiHeight();
        int y = h - 46;
        for (int i = LIST.size() - 1; i >= 0; i--) {
            N n = LIST.get(i);
            long age = now - n.at;
            float in = Math.min(1f, age / 220f);
            float out = age > 2300 ? 1f - (age - 2300) / 300f : 1f;
            float a = Math.max(0f, Math.min(in, out));
            int tw = mc.font.width(n.text);
            int bw = tw + 24;
            int x = (int) (w - bw - 8 + (1f - in) * (bw + 8));
            Ui.round(g, x, y - 20, x + bw, y, 3, Ui.fade(0xE0121216, a));
            Ui.round(g, x, y - 20, x + 3, y, 1, Ui.fade(n.color, a));
            g.drawString(mc.font, n.text, x + 12, y - 14, Ui.fade(0xFFFFFFFF, a));
            y -= 24;
        }
    }
}
