package dev.nova;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
            binds.put(m, KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.nova.toggle." + m.name.toLowerCase(), InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.MISC)));
        }

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openGui.consumeClick()) mc.setScreen(new ClickGui());
            binds.forEach((m, k) -> { while (k.consumeClick()) m.toggle(); });
            if (mc.player == null || mc.level == null) return;
            for (Module m : Modules.ALL) if (m.enabled) m.onTick();
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        HudRenderCallback.EVENT.register((g, tracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.options.hideGui) return;
            float pt = tracker.getGameTimeDeltaPartialTick(false);

            for (Module m : Modules.ALL) if (m.enabled) m.onRender(g, pt);

            g.drawString(mc.font, "Nova", 4, 4, 0xFF5B8CFF);
            List<Module> on = new ArrayList<>();
            for (Module m : Modules.ALL) if (m.enabled) on.add(m);
            on.sort(Comparator.comparingInt((Module m) -> mc.font.width(m.name)).reversed());
            int y = 4;
            for (Module m : on) {
                g.drawString(mc.font, m.name, g.guiWidth() - mc.font.width(m.name) - 4, y, 0xFF8FB0FF);
                y += 10;
            }
        });
    }
}
