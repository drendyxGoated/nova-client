package dev.nova;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ClickGui extends Screen {
    private static final int W = 110, ROW = 14, ACCENT = 0xFF5B8CFF;

    private record Hit(int x, int y, int w, int h, Module m, Setting s) {}
    private final List<Hit> hits = new ArrayList<>();

    public ClickGui() { super(Component.literal("Nova")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() { Config.save(); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x66000000);
        hits.clear();
        int px = 20;
        for (String cat : Modules.categories()) {
            int y = 20;
            g.fill(px, y, px + W, y + ROW + 2, ACCENT);
            g.drawString(font, cat, px + 5, y + 4, 0xFFFFFFFF);
            y += ROW + 2;
            for (Module m : Modules.ALL) {
                if (!m.category.equals(cat)) continue;
                boolean hover = mouseX >= px && mouseX < px + W && mouseY >= y && mouseY < y + ROW;
                g.fill(px, y, px + W, y + ROW, m.enabled ? 0xDD2E5BD8 : hover ? 0xDD333333 : 0xDD1A1A1A);
                g.drawString(font, m.name, px + 5, y + 3, m.enabled ? 0xFFFFFFFF : 0xFFAAAAAA);
                if (!m.settings.isEmpty()) g.drawString(font, m.expanded ? "-" : "+", px + W - 10, y + 3, 0xFFAAAAAA);
                hits.add(new Hit(px, y, W, ROW, m, null));
                y += ROW;
                if (m.expanded) {
                    for (Setting s : m.settings) {
                        g.fill(px, y, px + W, y + ROW, 0xDD101010);
                        if (s.isBool) {
                            g.fill(px + 5, y + 3, px + 13, y + 11, s.on() ? ACCENT : 0xFF444444);
                            g.drawString(font, s.name, px + 18, y + 3, 0xFFCCCCCC);
                        } else {
                            double f = (s.value - s.min) / (s.max - s.min);
                            g.fill(px, y, px + (int) (W * f), y + ROW, 0xAA2E5BD8);
                            g.drawString(font, s.name + ": " + String.format("%.1f", s.value), px + 5, y + 3, 0xFFCCCCCC);
                        }
                        hits.add(new Hit(px, y, W, ROW, m, s));
                        y += ROW;
                    }
                }
            }
            px += W + 10;
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        for (Hit h : hits) {
            if (mx < h.x || mx >= h.x + h.w || my < h.y || my >= h.y + h.h) continue;
            if (h.s == null) {
                if (button == 0) h.m.toggle();
                else if (button == 1 && !h.m.settings.isEmpty()) h.m.expanded = !h.m.expanded;
            } else if (h.s.isBool) {
                h.s.flip();
            } else {
                double f = (mx - h.x) / h.w;
                h.s.value = Math.round((h.s.min + f * (h.s.max - h.s.min)) * 10.0) / 10.0;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
