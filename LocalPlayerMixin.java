package dev.nova;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Nova implements ClientModInitializer {
    @Override public void onInitializeClient() {
        Modules.init();
        Config.load();

        KeyMapping openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.nova.gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC));

        // One unbound keybind per module: set them in Options > Controls > Key Binds
        Map<Module, KeyMapping> binds = new LinkedHashMap<>();
        for (Module m : Modules.ALL) {
            if (m.hidden) continue;
            binds.put(m, KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.nova.toggle." + m.name.toLowerCase(), InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC)));
        }

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.consumeClick()) mc.setScreen(new ClickGui());
            binds.forEach((m, k) -> { while (k.consumeClick()) m.toggle(); });
            if (mc.player == null || mc.level == null) {
                // left the world: stop one-shot bots/camera so they can't carry over
                NewModules.Freecam.active = false;
                for (Module m : Modules.ALL)
                    if (!m.persistEnabled && m.enabled) { m.enabled = false; try { m.onDisable(); } catch (Throwable ignored) {} }
                return;
            }
            for (Module m : Modules.ALL) if (m.enabled) safeTick(m);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        HudRenderCallback.EVENT.register((g, tracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.options.hideGui) return;
            float pt = tracker.getGameTimeDeltaPartialTick(false);

            for (Module m : Modules.ALL) if (m.enabled) safeRender(m, g, pt);

            try {
            ClientSettings cs = Modules.CLIENT;
            int accent = Theme.accent(), accent2 = Theme.accent2();

            if (cs.watermark.on()) {
                int w = mc.font.width("KRYPTON") + 8 + 8;
                Ui.round(g, 3, 3, 3 + w + 22, 15, 3, 0xAA101015);
                Ui.round(g, 3, 3, 5, 15, 1, accent);
                Ui.gradientText(g, mc.font, "KRYPTON", 9, 5, 0xFF12A04A, 0xFF0B7A36, 1f, 1);
                g.drawString(mc.font, "1.3", 9 + w - 6, 5, 0xFF8A8A96);
            }

            if (cs.arrayOn.on()) drawModuleList(g, mc, cs, accent, accent2);
            Notifications.render(g, mc);
            } catch (Throwable t) { System.err.println("[Krypton] HUD error: " + t); }
        });
    }

    // A bug in one module must never take the whole game down: turn that module off and keep going.
    private static void safeTick(Module m) {
        try { m.onTick(); } catch (Throwable t) { fail(m, "tick", t); }
    }

    private static void safeRender(Module m, GuiGraphics g, float pt) {
        try { m.onRender(g, pt); } catch (Throwable t) { fail(m, "render", t); }
    }

    private static void fail(Module m, String where, Throwable t) {
        m.enabled = false;
        try { m.onDisable(); } catch (Throwable ignored) {}
        System.err.println("[Krypton] " + m.name + " crashed in " + where + ": " + t);
        t.printStackTrace();
        Notifications.push(m.name + " turned off (error)", 0xFFFF5555);
        Modules.chat("§c" + m.name + " hit an error in " + where + " and was turned off (" + t.getClass().getSimpleName() + ")");
    }

    private static void drawModuleList(GuiGraphics g, Minecraft mc, ClientSettings cs, int accent, int accent2) {
        List<Module> on = new ArrayList<>();
        for (Module m : Modules.ALL) if (m.enabled && !m.hidden && !m.category.equals("HUD")) on.add(m);
        on.sort(Comparator.comparingInt((Module m) -> mc.font.width(m.name)).reversed());

        boolean right = cs.arraySide.mode() == 0;
        int y = right ? 4 : g.guiHeight() / 3;
        int i = 0;
        for (Module m : on) {
            int tw = mc.font.width(m.name);
            int x = right ? g.guiWidth() - tw - 10 : 6;
            float t = on.size() <= 1 ? 0f : i / (float) (on.size() - 1);
            int color = switch (cs.arrayColor.mode()) {
                case 1 -> Theme.rainbow(i * 0.04f);
                case 2 -> 0xFFFFFFFF;
                default -> Ui.lerp(accent, accent2, t);
            };
            if (cs.arrayBg.on()) Ui.round(g, x - 3, y - 1, x + tw + 3, y + 10, 2, 0x99101015);
            if (right) Ui.round(g, x + tw + 3, y - 1, x + tw + 5, y + 10, 1, color);
            else Ui.round(g, x - 5, y - 1, x - 3, y + 10, 1, color);
            g.drawString(mc.font, m.name, x, y, color);
            y += 12;
            i++;
        }
    }
}
