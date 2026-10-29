package dev.nova;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {
    private Config() {}

    private static Path file(String name) {
        String safe = name.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        String f = safe.isEmpty() ? "nova.properties" : "krypton-" + safe + ".properties";
        return FabricLoader.getInstance().getConfigDir().resolve(f);
    }

    public static void save() { saveAs(""); }
    public static void load() { loadAs(""); }

    /** Named profiles: /krypton config save <name>. Empty name = the main config. */
    public static boolean saveAs(String name) {
        Properties p = new Properties();
        for (Module m : Modules.ALL) {
            if (m.persistEnabled && !m.hidden) p.setProperty(m.name + ".enabled", String.valueOf(m.enabled));
            for (Setting s : m.settings) if (!s.isButton()) p.setProperty(m.name + "." + s.name, String.valueOf(s.value));
        }
        try (OutputStream out = Files.newOutputStream(file(name))) { p.store(out, "Krypton config"); return true; }
        catch (Exception e) { System.err.println("[Krypton] save failed: " + e); return false; }
    }

    public static boolean loadAs(String name) {
        if (!Files.exists(file(name))) return false;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file(name))) { p.load(in); }
        catch (Exception e) { System.err.println("[Krypton] load failed: " + e); return false; }
        for (Module m : Modules.ALL) {
            if (m.persistEnabled && !m.hidden)
                m.enabled = Boolean.parseBoolean(p.getProperty(m.name + ".enabled", String.valueOf(m.enabled)));
            for (Setting s : m.settings) {
                if (s.isButton()) continue;
                try { s.value = Double.parseDouble(p.getProperty(m.name + "." + s.name, String.valueOf(s.value))); }
                catch (NumberFormatException ignored) {}
            }
        }
        return true;
    }
}
