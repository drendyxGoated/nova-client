package dev.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    public final String name;
    public final String category;
    public final List<Setting> settings = new ArrayList<>();
    public boolean enabled;
    public boolean expanded;
    public boolean hidden;                 // settings-only entries (no on/off switch)
    public boolean persistEnabled = true;  // false = never auto-start on game launch
    public float anim;                     // 0..1 toggle animation for the GUI

    protected Module(String name, String category) { this.name = name; this.category = category; }

    protected Setting bool(String n, boolean d) { Setting s = new Setting(n, d); settings.add(s); return s; }
    protected Setting num(String n, double d, double min, double max) { Setting s = new Setting(n, d, min, max); settings.add(s); return s; }
    protected Setting mode(String n, int d, String... modes) { Setting s = new Setting(n, d, modes); settings.add(s); return s; }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable(); else onDisable();
        Notifications.module(this);
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender(GuiGraphics g, float partial) {}
}
