package dev.nova;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Config {
    private Config() {}
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nova.properties"); }

    public static void save() {
        Properties p = new Properties();
        for (Module m : Modules.ALL) {
            p.setProperty(m.name + ".enabled", String.valueOf(m.enabled));
            for (Setting s : m.settings) p.setProperty(m.name + "." + s.name, String.valueOf(s.value));
        }
        try (OutputStream out = Files.newOutputStream(file())) { p.store(out, "Nova config"); }
        catch (Exception e) { System.err.println("[Nova] save failed: " + e); }
    }

    public static void load() {
        if (!Files.exists(file())) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file())) { p.load(in); }
        catch (Exception e) { System.err.println("[Nova] load failed: " + e); return; }
        for (Module m : Modules.ALL) {
            m.enabled = Boolean.parseBoolean(p.getProperty(m.name + ".enabled", "false"));
            for (Setting s : m.settings) {
                try { s.value = Double.parseDouble(p.getProperty(m.name + "." + s.name, String.valueOf(s.value))); }
                catch (NumberFormatException ignored) {}
            }
        }
    }
}
