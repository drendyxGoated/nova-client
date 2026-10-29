package dev.nova;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Players on this list are never attacked/aimed at and show up cyan. /krypton friend add <name> */
public final class Friends {
    private Friends() {}
    static final Set<String> SET = new TreeSet<>();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("krypton-friends.txt"); }

    public static boolean is(Entity e) {
        return e instanceof Player p && SET.contains(p.getName().getString().toLowerCase());
    }

    static void load() {
        try {
            if (Files.exists(file())) for (String l : Files.readAllLines(file())) if (!l.isBlank()) SET.add(l.trim().toLowerCase());
        } catch (Exception ignored) {}
    }

    static void save() {
        try { Files.write(file(), SET); } catch (Exception ignored) {}
    }
}
