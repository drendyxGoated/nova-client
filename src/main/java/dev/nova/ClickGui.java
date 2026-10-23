package dev.nova;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Sidebar + card layout: categories on the left, modules with toggle switches, sliders and mode pills on the right. */
public class ClickGui extends Screen {
    private static final int W = 580, H = 360, SB = 124;
    private static final int PANEL = 0xFF15151B, SIDE = 0xFF0E0E13, CARD = 0xFF1D1D25, CARD_HOVER = 0xFF262631, SUB = 0xFF181820;

    private record Hit(int x, int y, int w, int h, int type, Module m, Setting s, int idx) {}
    // types: 0 module row, 1 bool, 2 slider, 3 mode, 4 category tab

    private final List<Hit> hits = new ArrayList<>();
    private final long openAt = System.currentTimeMillis();
    private int tab = 0;
    private double scroll = 0;
    private int contentH = 0, viewY1 = 0, viewY2 = 0;
    private Hit dragging;

    public ClickGui() { super(Component.literal("Nova")); }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { Config.save(); }

    private float fade() {
        if (!Theme.anim()) return 1f;
        float t = Math.min(1f, (System.currentTimeMillis() - openAt) / 220f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static String fmt(Setting s) {
        if (Math.abs(s.value - Math.rint(s.value)) < 1e-6) return String.valueOf((long) Math.rint(s.value));
        return String.format("%.2f", s.value);
    }

    private List<Module> modulesOf(String cat) {
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.ALL) if (m.category.equals(cat)) out.add(m);
        return out;
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        ClientSettings cs = Modules.CLIENT;
        float f = fade();
        int rad = (int) cs.radius.num();
        float op = (float) (cs.opacity.num() / 100.0) * f;
        int accent = Theme.accent(), accent2 = Theme.accent2();
        int rowH = new int[]{18, 22, 26}[cs.rowSize.mode()];
        List<String> cats = Modules.categories();
        if (tab >= cats.size()) tab = 0;

        if (cs.dim.on()) g.fill(0, 0, width, height, Ui.fade(0x99000000, f));

        int x0 = (width - W) / 2;
        int y0 = (height - H) / 2 + (int) ((1f - f) * 14);
        hits.clear();

        // window + glow border
        Ui.round(g, x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, rad + 1, Ui.fade(Ui.withAlpha(accent, 80), f));
        Ui.round(g, x0, y0, x0 + W, y0 + H, rad, Ui.fade(PANEL, op));

        // sidebar
        Ui.round(g, x0, y0, x0 + SB, y0 + H, rad, Ui.fade(SIDE, op));
        g.fill(x0 + SB - rad - 1, y0, x0 + SB, y0 + H, Ui.fade(SIDE, op));
        g.fill(x0 + SB, y0, x0 + SB + 1, y0 + H, Ui.fade(0xFF23232C, f));

        Ui.gradientText(g, font, "NOVA", x0 + 18, y0 + 14, accent, accent2, f, 2);
        g.drawString(font, "client", x0 + 19, y0 + 26, Ui.fade(0xFF70707C, f));

        int cy = y0 + 52;
        for (int i = 0; i < cats.size(); i++) {
            String c = cats.get(i);
            boolean sel = i == tab;
            boolean hover = Ui.in(mx, my, x0 + 8, cy, SB - 16, 22);
            if (sel) Ui.round(g, x0 + 8, cy, x0 + SB - 8, cy + 22, rad, Ui.fade(Ui.withAlpha(accent, 45), f));
            else if (hover) Ui.round(g, x0 + 8, cy, x0 + SB - 8, cy + 22, rad, Ui.fade(0xFF1A1A22, f));
            if (sel) Ui.round(g, x0 + 8, cy + 4, x0 + 11, cy + 18, 1, Ui.fade(accent, f));
            g.drawString(font, c, x0 + 20, cy + 7, Ui.fade(sel ? 0xFFFFFFFF : 0xFF9A9AA8, f));
            if (!c.equals("Settings")) {
                int on = 0;
                for (Module m : modulesOf(c)) if (m.enabled) on++;
                String cnt = String.valueOf(on);
                g.drawString(font, cnt, x0 + SB - 16 - font.width(cnt), cy + 7, Ui.fade(on > 0 ? accent : 0xFF55555F, f));
            }
            hits.add(new Hit(x0 + 8, cy, SB - 16, 22, 4, null, null, i));
            cy += 24;
        }
        g.drawString(font, "ESC to close", x0 + 18, y0 + H - 16, Ui.fade(0xFF55555F, f));

        // content header
        String cat = cats.get(tab);
        int cx1 = x0 + SB + 16, cx2 = x0 + W - 16;
        g.drawString(font, cat, cx1, y0 + 14, Ui.fade(0xFFFFFFFF, f));
        g.fill(cx1, y0 + 30, cx2, y0 + 31, Ui.fade(Ui.withAlpha(accent, 90), f));

        int vx1 = x0 + SB + 12, vx2 = x0 + W - 12;
        viewY1 = y0 + 38; viewY2 = y0 + H - 10;
        int cw = vx2 - vx1;
        double maxScroll = Math.max(0, contentH - (viewY2 - viewY1));
        scroll = Mth.clamp(scroll, -maxScroll, 0);

        g.enableScissor(vx1, viewY1, vx2, viewY2);
        int y = viewY1 + (int) scroll;
        for (Module m : modulesOf(cat)) {
            if (!m.hidden) {
                boolean hover = Ui.in(mx, my, vx1, y, cw, rowH) && my >= viewY1 && my < viewY2;
                float target = m.enabled ? 1f : 0f;
                m.anim = Theme.anim() ? m.anim + (target - m.anim) * 0.3f : target;
                Ui.round(g, vx1, y, vx2, y + rowH, rad, Ui.fade(hover ? CARD_HOVER : CARD, op));
                if (m.anim > 0.03f) Ui.round(g, vx1, y + 3, vx1 + 3, y + rowH - 3, 1, Ui.fade(accent, f * m.anim));
                g.drawString(font, m.name, vx1 + 12, y + (rowH - 8) / 2,
                        Ui.fade(Ui.lerp(0xFF9A9AA8, 0xFFFFFFFF, m.anim), f));

                int sw = 28, sh = 12, sx = vx2 - sw - 10, sy = y + (rowH - sh) / 2;
                Ui.round(g, sx, sy, sx + sw, sy + sh, sh / 2, Ui.fade(Ui.lerp(0xFF3A3A46, accent, m.anim), f));
                int kx = sx + 2 + (int) ((sw - sh) * m.anim);
                Ui.round(g, kx, sy + 2, kx + sh - 4, sy + sh - 2, (sh - 4) / 2, Ui.fade(0xFFFFFFFF, f));
                if (!m.settings.isEmpty())
                    g.drawString(font, m.expanded ? "v" : ">", sx - 14, y + (rowH - 8) / 2, Ui.fade(0xFF70707C, f));

                hits.add(new Hit(vx1, y, cw, rowH, 0, m, null, 0));
                y += rowH + 3;
            }
            if (m.expanded || m.hidden) {
                for (Setting s : m.settings) {
                    int sx1 = vx1 + 8, sx2 = vx2 - 8, sw2 = sx2 - sx1;
                    if (s.isMode()) {
                        int hh = 20;
                        Ui.round(g, sx1, y, sx2, y + hh, rad, Ui.fade(SUB, op));
                        g.drawString(font, s.name, sx1 + 8, y + 6, Ui.fade(0xFFC8C8D2, f));
                        String mn = s.modeName();
                        int pw = font.width(mn) + 14;
                        Ui.round(g, sx2 - pw - 6, y + 3, sx2 - 6, y + hh - 3, rad, Ui.fade(Ui.withAlpha(accent, 70), f));
                        g.drawString(font, mn, sx2 - pw + 1, y + 6, Ui.fade(0xFFFFFFFF, f));
                        hits.add(new Hit(sx1, y, sw2, hh, 3, m, s, 0));
                        y += hh + 2;
                    } else if (s.isBool) {
                        int hh = 20;
                        Ui.round(g, sx1, y, sx2, y + hh, rad, Ui.fade(SUB, op));
                        g.drawString(font, s.name, sx1 + 8, y + 6, Ui.fade(0xFFC8C8D2, f));
                        int bx = sx2 - 22;
                        Ui.round(g, bx, y + 5, bx + 16, y + 15, 5, Ui.fade(s.on() ? accent : 0xFF3A3A46, f));
                        int kx = s.on() ? bx + 9 : bx + 2;
                        Ui.round(g, kx, y + 7, kx + 5, y + 13, 2, Ui.fade(0xFFFFFFFF, f));
                        hits.add(new Hit(sx1, y, sw2, hh, 1, m, s, 0));
                        y += hh + 2;
                    } else {
                        int hh = 28;
                        Ui.round(g, sx1, y, sx2, y + hh, rad, Ui.fade(SUB, op));
                        g.drawString(font, s.name, sx1 + 8, y + 5, Ui.fade(0xFFC8C8D2, f));
                        String v = fmt(s);
                        g.drawString(font, v, sx2 - 8 - font.width(v), y + 5, Ui.fade(accent, f));
                        int tx = sx1 + 8, tw = sw2 - 16, ty = y + 19;
                        Ui.round(g, tx, ty, tx + tw, ty + 4, 2, Ui.fade(0xFF2E2E3A, f));
                        double fr = (s.value - s.min) / Math.max(1e-9, (s.max - s.min));
                        int fw = (int) (tw * fr);
                        Ui.round(g, tx, ty, tx + Math.max(2, fw), ty + 4, 2, Ui.fade(accent, f));
                        Ui.round(g, tx + fw - 3, ty - 2, tx + fw + 3, ty + 6, 3, Ui.fade(0xFFFFFFFF, f));
                        hits.add(new Hit(tx, y, tw, hh, 2, m, s, 0));
                        y += hh + 2;
                    }
                }
                y += 3;
            }
        }
        g.disableScissor();
        contentH = y - (viewY1 + (int) scroll) + 4;
    }

    private void setSlider(Hit h, double mx) {
        double fr = Mth.clamp((mx - h.x) / (double) h.w, 0, 1);
        double v = h.s.min + fr * (h.s.max - h.s.min);
        double st = Math.max(1e-9, h.s.step);
        v = h.s.min + Math.round((v - h.s.min) / st) * st;
        h.s.value = Mth.clamp(v, h.s.min, h.s.max);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int button = event.button();
        for (Hit h : hits) {
            if (!Ui.in(mx, my, h.x, h.y, h.w, h.h)) continue;
            if (h.type != 4 && (my < viewY1 || my >= viewY2)) continue;
            switch (h.type) {
                case 4 -> { tab = h.idx; scroll = 0; }
                case 0 -> {
                    if (button == 0) h.m.toggle();
                    else if (button == 1 && !h.m.settings.isEmpty()) h.m.expanded = !h.m.expanded;
                }
                case 1 -> h.s.flip();
                case 3 -> h.s.cycle();
                case 2 -> { dragging = h; setSlider(h, mx); }
                default -> {}
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // Drag/release/scroll: no @Override so a signature mismatch can never break the build.
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) { setSlider(dragging, event.x()); return true; }
        return false;
    }
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { setSlider(dragging, mx); return true; }
        return false;
    }
    public boolean mouseReleased(MouseButtonEvent event) { dragging = null; return false; }
    public boolean mouseReleased(double mx, double my, int button) { dragging = null; return false; }

    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        for (Hit h : hits) {
            if (h.type == 2 && Ui.in(mx, my, h.x, h.y, h.w, h.h) && my >= viewY1 && my < viewY2) {
                h.s.value = Mth.clamp(h.s.value + Math.signum(scrollY) * h.s.step, h.s.min, h.s.max);
                return true;
            }
        }
        scroll += scrollY * 22;
        return true;
    }
}
