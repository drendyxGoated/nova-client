package dev.nova;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public final class Modules {
    private Modules() {}

    public static final List<Module> ALL = new ArrayList<>();
    public static ClientSettings CLIENT;

    public static void init() {
        ALL.add(new KillAura());
        ALL.add(new PlayerESP());
        ALL.add(new EntityESP());
        ALL.add(new StorageESP());
        ALL.add(new Tracers());
        ALL.add(new StashFinder());
        ALL.add(new Fullbright());
        ALL.add(new AutoSprint());
        MoreModules.register();
        ExtraModules.register();
        NewModules.register();
        BuildModules.register();
        MegaModules.register();
        CLIENT = new ClientSettings();
        ALL.add(CLIENT);
    }

    public static List<String> categories() {
        return List.of("Combat", "Movement", "Player", "Render", "World", "Misc", "HUD", "Settings");
    }

    // ---------- helpers ----------
    static void chat(String msg) {
        if (Module.mc.player != null)
            Module.mc.player.displayClientMessage(Component.literal("§2[Krypton]§r " + msg), false);
    }

    static int cap() { return CLIENT == null ? 150 : (int) CLIENT.maxShapes.num(); }

    static String dim() {
        String d = Module.mc.level.dimension().toString();
        return d.contains("nether") ? "Nether" : d.contains("the_end") ? "End" : "Overworld";
    }

    static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity
                || be instanceof EnderChestBlockEntity || be instanceof HopperBlockEntity;
    }

    static int storageColor(BlockEntity be) {
        if (be instanceof ShulkerBoxBlockEntity) return 0xFFFF55FF;
        if (be instanceof EnderChestBlockEntity) return 0xFFAA00FF;
        if (be instanceof BarrelBlockEntity) return 0xFFC08040;
        if (be instanceof HopperBlockEntity) return 0xFF888888;
        return 0xFFFFAA00;
    }

    /** Loop over every loaded chunk's block entities within render distance. */
    interface ChunkVisitor { void visit(int cx, int cz, Collection<BlockEntity> bes); }

    static void forEachChunk(ChunkVisitor v) {
        var mc = Module.mc;
        if (mc.level == null || mc.player == null) return;
        int r = mc.options.getEffectiveRenderDistance();
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                ChunkAccess ca = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (ca instanceof LevelChunk chunk) v.visit(cx, cz, chunk.getBlockEntities().values());
            }
        }
    }

    static Map<Long, List<BlockEntity>> storageByChunk() {
        Map<Long, List<BlockEntity>> out = new HashMap<>();
        forEachChunk((cx, cz, bes) -> {
            for (BlockEntity be : bes)
                if (isStorage(be)) out.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(be);
        });
        return out;
    }

    // ----- stash scoring -----
    static class ChunkInfo {
        int chests, shulkers, barrels, hoppers, dispensers, furnaces, brewing, beds, signs;
        double score, sx, sy, sz; int n;

        int misc() { return hoppers + dispensers + furnaces + brewing + beds + signs; }

        String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(chests).append(" chests, ").append(shulkers).append(" shulkers, ").append(barrels).append(" barrels");
            if (misc() > 0) sb.append(", ").append(misc()).append(" misc");
            return sb.toString();
        }
    }

    /** Ender chests are worth 0 (everyone has one). Beds/signs only count if includeMisc. */
    static double weight(BlockEntity be, boolean includeMisc) {
        if (be instanceof ShulkerBoxBlockEntity) return 2;
        if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) return 1;
        if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity
                || be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity) return 0.5;
        if (includeMisc && be instanceof BedBlockEntity) return 1;
        if (includeMisc && be instanceof SignBlockEntity) return 0.25;
        return 0;
    }

    static Map<Long, ChunkInfo> scanChunks(boolean includeMisc) {
        Map<Long, ChunkInfo> out = new HashMap<>();
        forEachChunk((cx, cz, bes) -> {
            for (BlockEntity be : bes) {
                double w = weight(be, includeMisc);
                if (w <= 0) continue;
                ChunkInfo ci = out.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ChunkInfo());
                if (be instanceof ShulkerBoxBlockEntity) ci.shulkers++;
                else if (be instanceof ChestBlockEntity) ci.chests++;
                else if (be instanceof BarrelBlockEntity) ci.barrels++;
                else if (be instanceof HopperBlockEntity) ci.hoppers++;
                else if (be instanceof DispenserBlockEntity) ci.dispensers++;
                else if (be instanceof AbstractFurnaceBlockEntity) ci.furnaces++;
                else if (be instanceof BrewingStandBlockEntity) ci.brewing++;
                else if (be instanceof BedBlockEntity) ci.beds++;
                else if (be instanceof SignBlockEntity) ci.signs++;
                ci.score += w;
                ci.sx += be.getBlockPos().getX(); ci.sy += be.getBlockPos().getY(); ci.sz += be.getBlockPos().getZ();
                ci.n++;
            }
        });
        return out;
    }

    // ================= COMBAT =================
    public static class KillAura extends Module {
        final Setting range = num("Range", 3.0, 1.0, 6.0);
        final Setting lowestHp = bool("Lowest HP first", false);
        final Setting walls = bool("Through walls", false);
        final Setting rotate = bool("Rotate", true);
        final Setting aimSpeed = num("Aim speed", 0.45, 0.1, 1.0);
        final Setting miss = num("Miss chance %", 4, 0, 30);
        final Targets.Filter filter;
        int hold;

        KillAura() {
            super("KillAura", "Combat");
            filter = new Targets.Filter(this, true, true, true, false, false, false);
        }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (mc.player.getAttackStrengthScale(0f) < 0.95f) return;

            Entity best = null; double bestScore = Double.MAX_VALUE;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || !filter.accepts(e) || Friends.is(e)) continue;
                double d = mc.player.distanceTo(e);
                if (d > range.num()) continue;
                if (!walls.on() && !mc.player.hasLineOfSight(e)) continue;
                double score = lowestHp.on() ? le.getHealth() : d;
                if (score < bestScore) { best = e; bestScore = score; }
            }
            if (best == null) return;

            if (rotate.on()) {
                float rem = Humanizer.aim(Targets.rotations(mc.player.getEyePosition(), best.getBoundingBox().getCenter()), aimSpeed.num());
                if (Humanizer.on() && rem > 14) return;
            }
            if (hold > 0) { hold--; return; }
            if (Humanizer.on() && Humanizer.chance(miss.num() / 100.0)) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                hold = 1 + Humanizer.R.nextInt(3);
                return;
            }
            mc.gameMode.attack(mc.player, best);
            mc.player.swing(InteractionHand.MAIN_HAND);
            hold = Humanizer.on() ? Humanizer.R.nextInt(3) : 0;
        }
    }

    // ================= RENDER =================
    public static class PlayerESP extends Module {
        final Setting range = num("Range", 256, 32, 512);
        final Setting names = bool("Names", true);
        final Setting health = bool("Health bar", true);

        PlayerESP() { super("PlayerESP", "Render"); }

        @Override public void onRender(GuiGraphics g, float pt) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof Player p) || p == mc.player || mc.player.distanceTo(p) > range.num()) continue;
                double[] r = Draw.rect(p.getBoundingBox(), g, pt);
                if (r == null) continue;
                Draw.outline(g, r, Friends.is(p) ? 0xFF55FFFF : 0xFFFF3030);
                if (health.on()) {
                    float hp = Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth()));
                    int bx = (int) r[0] - 4, bh = (int) (r[3] - r[1]);
                    g.fill(bx, (int) r[1], bx + 2, (int) r[3], 0xFF300000);
                    g.fill(bx, (int) (r[3] - bh * hp), bx + 2, (int) r[3], 0xFF30FF30);
                }
                if (names.on()) {
                    String label = p.getName().getString() + " " + (int) mc.player.distanceTo(p) + "m";
                    g.drawString(mc.font, label, (int) r[0], (int) r[1] - 10, 0xFFFFFFFF);
                }
            }
        }
    }

    /** ESP for everything that isn't a player: hostiles, animals (cows etc), villagers, water mobs, items, crystals, boats. */
    public static class EntityESP extends Module {
        final Setting range = num("Range", 128, 16, 256);
        final Setting labels = bool("Labels", true);
        final Targets.Filter filter;

        EntityESP() {
            super("EntityESP", "Render");
            filter = new Targets.Filter(this, false, true, true, true, false, false);
            filter.extras(this, false, true, true);
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            int drawn = 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !filter.accepts(e)) continue;
                double dist = mc.player.distanceTo(e);
                if (dist > range.num()) continue;
                if (drawn >= cap()) break;
                double[] r = Draw.rect(e.getBoundingBox(), g, pt);
                if (r == null) continue;
                drawn++;
                int color = Targets.colorOf(e);
                Draw.outline(g, r, color);
                if (labels.on())
                    g.drawString(mc.font, e.getName().getString() + " " + (int) dist + "m", (int) r[0], (int) r[1] - 10, color);
            }
        }
    }

    public static class StorageESP extends Module {
        final Setting range = num("Range", 128, 16, 256);
        List<BlockEntity> cache = new ArrayList<>();
        int tick;

        StorageESP() { super("StorageESP", "Render"); }

        @Override public void onTick() {
            if (mc.player == null || tick++ % 20 != 0) return;
            List<BlockEntity> list = new ArrayList<>();
            for (List<BlockEntity> l : storageByChunk().values()) list.addAll(l);
            cache = list;
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            double max = range.num() * range.num();
            int drawn = 0;
            for (BlockEntity be : cache) {
                if (drawn >= cap()) break;
                if (be.isRemoved() || be.getBlockPos().distSqr(mc.player.blockPosition()) > max) continue;
                drawn++;
                double[] r = Draw.rect(new AABB(be.getBlockPos()), g, pt);
                if (r != null) Draw.outline(g, r, storageColor(be));
            }
        }
    }

    public static class Tracers extends Module {
        final Targets.Filter filter;
        Tracers() {
            super("Tracers", "Render");
            filter = new Targets.Filter(this, true, false, false, false, false, false);
            filter.extras(this, false, false, false);
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
            int drawn = 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !filter.accepts(e)) continue;
                if (drawn++ >= 40) break;
                double[] s = Draw.project(e.getBoundingBox().getCenter(), g, pt);
                if (s == null) continue;
                int c = (Targets.colorOf(e) & 0x00FFFFFF) | 0xCC000000;
                Draw.line(g, cx, cy, s[0], s[1], c);
            }
        }
    }

    // ================= WORLD =================
    /**
     * Scores every loaded chunk by what's in it (shulkers worth 2, chests/barrels 1, hoppers/furnaces 0.5,
     * ender chests 0, optionally beds/signs) and flags the ones over the threshold.
     * Alerts in chat with a breakdown + coords, draws labels in the world, logs to config/nova-stashes.txt,
     * and re-alerts if a chunk's score grows by 50%.
     */
    public static class StashFinder extends Module {
        record Stash(double score, String desc, double x, double y, double z, String dim) {}

        final Setting minScore = num("Min score", 8, 2, 100);
        final Setting misc = bool("Count beds/signs", false);
        final Setting chat = bool("Chat alerts", true);
        final Setting labels = bool("World labels", true);
        final Setting tracers = bool("Tracers", false);
        final Setting file = bool("Save to file", true);

        final Map<Long, Stash> found = new LinkedHashMap<>();
        final Map<Long, Double> reported = new HashMap<>();
        int tick;

        StashFinder() { super("StashFinder", "World"); }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || tick++ % 40 != 0) return;
            String dim = dim();
            for (var en : scanChunks(misc.on()).entrySet()) {
                long key = en.getKey();
                ChunkInfo ci = en.getValue();
                if (ci.score < minScore.num()) { found.remove(key); reported.remove(key); continue; }
                Stash s = new Stash(ci.score, ci.describe(), ci.sx / ci.n, ci.sy / ci.n, ci.sz / ci.n, dim);
                found.put(key, s);
                Double prev = reported.get(key);
                if (prev == null || ci.score >= prev * 1.5) {
                    reported.put(key, ci.score);
                    String where = (int) s.x + ", " + (int) s.y + ", " + (int) s.z;
                    if (chat.on()) Modules.chat("§aStash§r score " + (int) ci.score + " (" + s.desc + ") at " + where
                            + " [" + dim + ", " + (int) Math.sqrt(mc.player.distanceToSqr(s.x, s.y, s.z)) + "m away]");
                    if (file.on()) save(java.time.LocalDateTime.now() + " | " + dim + " | " + where + " | score " + (int) ci.score + " | " + s.desc);
                }
            }
        }

        void save(String line) {
            try {
                Path p = FabricLoader.getInstance().getConfigDir().resolve("nova-stashes.txt");
                Files.writeString(p, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            if (!labels.on() && !tracers.on()) return;
            String dim = dim();
            double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
            for (Stash s : found.values()) {
                if (!s.dim.equals(dim)) continue;
                double[] p = Draw.project(new Vec3(s.x, s.y + 1, s.z), g, pt);
                if (p == null) continue;
                int dist = (int) Math.sqrt(mc.player.distanceToSqr(s.x, s.y, s.z));
                if (labels.on()) {
                    String t = "STASH " + (int) s.score + " | " + dist + "m";
                    g.fill((int) p[0] - 2, (int) p[1] - 2, (int) p[0] + 2, (int) p[1] + 2, 0xFFFFD700);
                    g.drawString(mc.font, t, (int) p[0] - mc.font.width(t) / 2, (int) p[1] - 12, 0xFFFFD700);
                }
                if (tracers.on()) Draw.line(g, cx, cy, p[0], p[1], 0xAAFFD700);
            }
        }
    }

    public static class Fullbright extends Module {
        Fullbright() { super("Fullbright", "Render"); }
        @Override public void onTick() {
            if (mc.player != null) mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false));
        }
        @Override public void onDisable() { if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION); }
    }

    // ================= MOVEMENT =================
    public static class AutoSprint extends Module {
        AutoSprint() { super("AutoSprint", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            if (mc.options.keyUp.isDown() && !mc.player.horizontalCollision && mc.player.getFoodData().getFoodLevel() > 6)
                mc.player.setSprinting(true);
        }
    }
}
