package dev.nova;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * /krypton ...  : control everything from chat so you never need an update just to tweak a value.
 *   help | list | toggle <module> | settings <module> | set <module> <setting> <value>
 *   mine <x> <y> <z> | mine <x> <z> | mine stop
 *   friend add|remove|list      waypoint add|del|list      esp add|remove|list|clear <block>
 *   config save|load <name>     macro <seconds> <text...> | macro stop
 */
public final class Commands {
    private Commands() {}

    private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String n) { return ClientCommandManager.literal(n); }
    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> arg(String n, ArgumentType<T> t) { return ClientCommandManager.argument(n, t); }

    private static final SuggestionProvider<FabricClientCommandSource> MODS = (ctx, b) -> {
        for (Module m : Modules.ALL) if (!m.hidden) b.suggest(m.name);
        return b.buildFuture();
    };

    static Module find(String n) {
        for (Module m : Modules.ALL) if (m.name.equalsIgnoreCase(n) && !m.hidden) return m;
        return null;
    }

    static String norm(String s) { return s.toLowerCase().replaceAll("[^a-z0-9]", ""); }

    static Setting findSetting(Module m, String n) {
        for (Setting s : m.settings) if (norm(s.name).equals(norm(n))) return s;
        return null;
    }

    static String show(Setting s) {
        if (s.isButton()) return "[button]";
        if (s.isBool) return s.on() ? "on" : "off";
        if (s.isMode()) return s.modeName();
        return Math.abs(s.value - Math.rint(s.value)) < 1e-6 ? String.valueOf((long) Math.rint(s.value)) : String.format("%.2f", s.value);
    }

    static String apply(Setting s, String v) {
        v = v.trim();
        if (s.isButton()) { s.action.run(); return "ran " + s.name; }
        if (s.isBool) {
            s.value = (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes")) ? 1 : 0;
            return s.name + " = " + show(s);
        }
        if (s.isMode()) {
            for (int i = 0; i < s.modes.length; i++) if (s.modes[i].equalsIgnoreCase(v) || norm(s.modes[i]).equals(norm(v))) { s.value = i; return s.name + " = " + show(s); }
            try { int i = Integer.parseInt(v); if (i >= 0 && i < s.modes.length) { s.value = i; return s.name + " = " + show(s); } } catch (NumberFormatException ignored) {}
            return "options: " + String.join(", ", s.modes);
        }
        try {
            s.value = Math.max(s.min, Math.min(s.max, Double.parseDouble(v)));
            return s.name + " = " + show(s);
        } catch (NumberFormatException e) { return "that's not a number"; }
    }

    private static NewModules.AutoMiner miner() {
        for (Module m : Modules.ALL) if (m instanceof NewModules.AutoMiner am) return am;
        return null;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            var root = lit("krypton");

            root.then(lit("help").executes(ctx -> {
                Modules.chat("§7/krypton list | toggle <mod> | settings <mod> | set <mod> <setting> <value>");
                Modules.chat("§7mine <x> <y> <z> | friend add/remove/list | waypoint add/del/list | esp add/remove/list/clear <block>");
                Modules.chat("§7config save/load <name> | macro <seconds> <text> | macro stop");
                return 1;
            }));

            root.then(lit("list").executes(ctx -> {
                StringBuilder on = new StringBuilder(), off = new StringBuilder();
                for (Module m : Modules.ALL) if (!m.hidden) (m.enabled ? on : off).append(m.name).append(", ");
                Modules.chat("§aON:§r " + (on.length() == 0 ? "none" : on.substring(0, on.length() - 2)));
                Modules.chat("§7OFF:§r " + (off.length() == 0 ? "none" : off.substring(0, off.length() - 2)));
                return 1;
            }));

            root.then(lit("toggle").then(arg("module", StringArgumentType.word()).suggests(MODS).executes(ctx -> {
                Module m = find(StringArgumentType.getString(ctx, "module"));
                if (m == null) { Modules.chat("§cNo such module. Try /krypton list"); return 0; }
                m.toggle();
                return 1;
            })));

            root.then(lit("settings").then(arg("module", StringArgumentType.word()).suggests(MODS).executes(ctx -> {
                Module m = find(StringArgumentType.getString(ctx, "module"));
                if (m == null) { Modules.chat("§cNo such module."); return 0; }
                Modules.chat("§e" + m.name + "§r settings:");
                for (Setting s : m.settings) Modules.chat("  " + norm(s.name) + " = " + show(s));
                return 1;
            })));

            root.then(lit("set").then(arg("module", StringArgumentType.word()).suggests(MODS)
                .then(arg("setting", StringArgumentType.word()).suggests((ctx, b) -> {
                    Module m = find(StringArgumentType.getString(ctx, "module"));
                    if (m != null) for (Setting s : m.settings) b.suggest(norm(s.name));
                    return b.buildFuture();
                }).then(arg("value", StringArgumentType.greedyString()).executes(ctx -> {
                    Module m = find(StringArgumentType.getString(ctx, "module"));
                    if (m == null) { Modules.chat("§cNo such module."); return 0; }
                    Setting s = findSetting(m, StringArgumentType.getString(ctx, "setting"));
                    if (s == null) { Modules.chat("§cNo such setting. /krypton settings " + m.name); return 0; }
                    Modules.chat(apply(s, StringArgumentType.getString(ctx, "value")));
                    return 1;
                })))));

            // ---- mine ----
            root.then(lit("mine")
                .then(lit("stop").executes(ctx -> {
                    NewModules.AutoMiner am = miner();
                    if (am != null && am.enabled) am.toggle();
                    return 1;
                }))
                .then(arg("x", IntegerArgumentType.integer())
                    .then(arg("a", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            NewModules.AutoMiner am = miner();
                            if (am == null || Minecraft.getInstance().player == null) return 0;
                            am.goTo(IntegerArgumentType.getInteger(ctx, "x"), Minecraft.getInstance().player.getBlockY(), IntegerArgumentType.getInteger(ctx, "a"));
                            return 1;
                        })
                        .then(arg("b", IntegerArgumentType.integer()).executes(ctx -> {
                            NewModules.AutoMiner am = miner();
                            if (am == null) return 0;
                            am.goTo(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "b"));
                            return 1;
                        })))));

            // ---- friends ----
            root.then(lit("friend")
                .then(lit("add").then(arg("name", StringArgumentType.word()).executes(ctx -> {
                    Friends.SET.add(StringArgumentType.getString(ctx, "name").toLowerCase());
                    Friends.save();
                    Modules.chat("Added friend: " + StringArgumentType.getString(ctx, "name"));
                    return 1;
                })))
                .then(lit("remove").then(arg("name", StringArgumentType.word()).executes(ctx -> {
                    Friends.SET.remove(StringArgumentType.getString(ctx, "name").toLowerCase());
                    Friends.save();
                    Modules.chat("Removed friend: " + StringArgumentType.getString(ctx, "name"));
                    return 1;
                })))
                .then(lit("list").executes(ctx -> {
                    Modules.chat("Friends: " + (Friends.SET.isEmpty() ? "none" : String.join(", ", Friends.SET)));
                    return 1;
                })));

            // ---- waypoints ----
            root.then(lit("waypoint")
                .then(lit("add").then(arg("name", StringArgumentType.word())
                    .executes(ctx -> {
                        var p = Minecraft.getInstance().player;
                        if (p == null) return 0;
                        addWp(StringArgumentType.getString(ctx, "name"), p.getBlockX(), p.getBlockY(), p.getBlockZ());
                        return 1;
                    })
                    .then(arg("x", IntegerArgumentType.integer()).then(arg("y", IntegerArgumentType.integer()).then(arg("z", IntegerArgumentType.integer())
                        .executes(ctx -> {
                            addWp(StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "x"),
                                    IntegerArgumentType.getInteger(ctx, "y"), IntegerArgumentType.getInteger(ctx, "z"));
                            return 1;
                        }))))))
                .then(lit("del").then(arg("name", StringArgumentType.word()).executes(ctx -> {
                    String n = StringArgumentType.getString(ctx, "name");
                    boolean removed = MegaModules.WPS.removeIf(w -> w.name().equalsIgnoreCase(n));
                    MegaModules.saveWaypoints();
                    Modules.chat(removed ? "Deleted waypoint " + n : "No waypoint named " + n);
                    return 1;
                })))
                .then(lit("list").executes(ctx -> {
                    if (MegaModules.WPS.isEmpty()) Modules.chat("No waypoints yet.");
                    for (MegaModules.WP w : MegaModules.WPS) Modules.chat(w.name() + ": " + w.x() + " " + w.y() + " " + w.z() + " [" + w.dim() + "]");
                    return 1;
                })));

            // ---- block search ----
            root.then(lit("esp")
                .then(lit("add").then(arg("block", StringArgumentType.word()).executes(ctx -> {
                    Block b = MegaModules.parseBlock(StringArgumentType.getString(ctx, "block"));
                    if (b == null) { Modules.chat("§cUnknown block id (try e.g. diamond_block, obsidian, lava)"); return 0; }
                    MegaModules.SEARCH.add(b);
                    MegaModules.saveSearch();
                    Module s = find("Search");
                    if (s != null && !s.enabled) s.toggle();
                    Modules.chat("Searching for " + MegaModules.idOf(b));
                    return 1;
                })))
                .then(lit("remove").then(arg("block", StringArgumentType.word()).executes(ctx -> {
                    Block b = MegaModules.parseBlock(StringArgumentType.getString(ctx, "block"));
                    if (b != null) MegaModules.SEARCH.remove(b);
                    MegaModules.saveSearch();
                    return 1;
                })))
                .then(lit("clear").executes(ctx -> { MegaModules.SEARCH.clear(); MegaModules.saveSearch(); return 1; }))
                .then(lit("list").executes(ctx -> {
                    List<String> ids = new ArrayList<>();
                    for (Block b : MegaModules.SEARCH) ids.add(MegaModules.idOf(b));
                    Modules.chat("Search list: " + (ids.isEmpty() ? "empty" : String.join(", ", ids)));
                    return 1;
                })));

            // ---- config profiles ----
            root.then(lit("config")
                .then(lit("save").then(arg("name", StringArgumentType.word()).executes(ctx -> {
                    boolean ok = Config.saveAs(StringArgumentType.getString(ctx, "name"));
                    Modules.chat(ok ? "Saved profile " + StringArgumentType.getString(ctx, "name") : "§cCouldn't save");
                    return 1;
                })))
                .then(lit("load").then(arg("name", StringArgumentType.word()).executes(ctx -> {
                    boolean[] was = new boolean[Modules.ALL.size()];
                    for (int i = 0; i < was.length; i++) was[i] = Modules.ALL.get(i).enabled;
                    boolean ok = Config.loadAs(StringArgumentType.getString(ctx, "name"));
                    if (!ok) { Modules.chat("§cNo such profile"); return 0; }
                    for (int i = 0; i < was.length; i++) {
                        Module m = Modules.ALL.get(i);
                        try {
                            if (m.enabled && !was[i]) m.onEnable();
                            else if (!m.enabled && was[i]) m.onDisable();
                        } catch (Throwable ignored) {}
                    }
                    Modules.chat("Loaded profile " + StringArgumentType.getString(ctx, "name"));
                    return 1;
                }))));

            // ---- macro ----
            root.then(lit("macro")
                .then(lit("stop").executes(ctx -> {
                    Module m = find("Macro");
                    if (m != null && m.enabled) m.toggle();
                    return 1;
                }))
                .then(arg("seconds", IntegerArgumentType.integer(1, 3600)).then(arg("text", StringArgumentType.greedyString()).executes(ctx -> {
                    MegaModules.Macro.text = StringArgumentType.getString(ctx, "text");
                    Module m = find("Macro");
                    if (m == null) return 0;
                    for (Setting s : m.settings) if (s.name.startsWith("Interval")) s.value = IntegerArgumentType.getInteger(ctx, "seconds");
                    if (m.enabled) m.toggle();
                    m.toggle();
                    Modules.chat("Macro running every " + IntegerArgumentType.getInteger(ctx, "seconds") + "s: " + MegaModules.Macro.text);
                    return 1;
                }))));

            dispatcher.register(root);
        });
    }

    private static void addWp(String name, int x, int y, int z) {
        String dim = Minecraft.getInstance().level == null ? "Overworld" : Modules.dim();
        MegaModules.WPS.removeIf(w -> w.name().equalsIgnoreCase(name));
        MegaModules.WPS.add(new MegaModules.WP(name.replace("|", ""), dim, x, y, z));
        MegaModules.saveWaypoints();
        Module m = find("Waypoints");
        if (m != null && !m.enabled) m.toggle();
        Modules.chat("Waypoint " + name + " set at " + x + " " + y + " " + z);
    }
}
