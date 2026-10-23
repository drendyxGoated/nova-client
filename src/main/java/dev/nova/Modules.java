package dev.nova;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class Modules {
    private Modules() {}

    public static final List<Module> ALL = new ArrayList<>();

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
    }

    public static List<String> categories() {
        return List.of("Combat", "Movement", "Player", "Render", "World");
    }

    // ---------- helpers ----------
    static void chat(String msg) {
        if (Module.mc.player != null)
            Module.mc.player.displayClientMessage(Component.literal("§b[Nova]§r " + msg), false);
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

    /** Every loaded storage block entity within render distance, grouped by chunk. */
    static Map<Long, List<BlockEntity>> storageByChunk() {
        Map<Long, List<BlockEntity>> out = new HashMap<>();
        var mc = Module.mc;
        if (mc.level == null || mc.player == null) return out;
        int r = mc.options.getEffectiveRenderDistance();
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                ChunkAccess ca = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(ca instanceof LevelChunk chunk)) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (isStorage(be)) out.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(be);
                }
            }
        }
        return out;
    }

    // ---------- COMBAT ----------
    public static class KillAura extends Module {
        final Setting range = num("Range", 3.0, 1.0, 6.0);
        final Setting players = bool("Players", true);
        final Setting hostiles = bool("Hostiles", true);
        final Setting passives = bool("Passives", false);
        final Setting rotate = bool("Rotate", true);

        KillAura() { super("KillAura", "Combat"); }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (mc.player.getAttackStrengthScale(0f) < 0.95f) return;

            Entity best = null; double bestDist = Double.MAX_VALUE;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive()) continue;
                boolean ok = (e instanceof Player && players.on()) || (e instanceof Enemy && hostiles.on())
                        || (!(e instanceof Player) && !(e instanceof Enemy) && passives.on());
                if (!ok) continue;
                double d = mc.player.distanceTo(e);
                if (d <= range.num() && d < bestDist) { best = e; bestDist = d; }
            }
            if (best == null) return;

            if (rotate.on()) {
                Vec3 eye = mc.player.getEyePosition();
                Vec3 t = best.getBoundingBox().getCenter();
                double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                mc.player.setYRot(yaw);
                mc.player.setXRot(pitch);
            }
            mc.gameMode.attack(mc.player, best);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    // ---------- RENDER ----------
    public static class PlayerESP extends Module {
        final Setting names = bool("Names", true);
        PlayerESP() { super("PlayerESP", "Render"); }

        @Override public void onRender(GuiGraphics g, float pt) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof Player p) || p == mc.player) continue;
                double[] r = Draw.rect(p.getBoundingBox(), g, pt);
                if (r == null) continue;
                Draw.outline(g, r, 0xFFFF3030);
                float hp = Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth()));
                int bx = (int) r[0] - 4, bh = (int) (r[3] - r[1]);
                g.fill(bx, (int) r[1], bx + 2, (int) r[3], 0xFF300000);
                g.fill(bx, (int) (r[3] - bh * hp), bx + 2, (int) r[3], 0xFF30FF30);
                if (names.on()) {
                    String label = p.getName().getString() + " " + (int) mc.player.distanceTo(p) + "m";
                    g.drawString(mc.font, label, (int) r[0], (int) r[1] - 10, 0xFFFFFFFF);
                }
            }
        }
    }

    public static class EntityESP extends Module {
        final Setting hostiles = bool("Hostiles", true);
        final Setting passives = bool("Passives", false);
        EntityESP() { super("EntityESP", "Render"); }

        @Override public void onRender(GuiGraphics g, float pt) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof LivingEntity) || e instanceof Player) continue;
                boolean h = e instanceof Enemy;
                if ((h && !hostiles.on()) || (!h && !passives.on())) continue;
                double[] r = Draw.rect(e.getBoundingBox(), g, pt);
                if (r != null) Draw.outline(g, r, h ? 0xFFFF8800 : 0xFF55FF55);
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
            for (BlockEntity be : cache) {
                if (be.isRemoved() || be.getBlockPos().distSqr(mc.player.blockPosition()) > max) continue;
                double[] r = Draw.rect(new AABB(be.getBlockPos()), g, pt);
                if (r != null) Draw.outline(g, r, storageColor(be));
            }
        }
    }

    public static class Tracers extends Module {
        final Setting players = bool("Players", true);
        final Setting hostiles = bool("Hostiles", false);
        Tracers() { super("Tracers", "Render"); }

        @Override public void onRender(GuiGraphics g, float pt) {
            double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !(e instanceof LivingEntity)) continue;
                boolean p = e instanceof Player, h = e instanceof Enemy;
                if (!((p && players.on()) || (h && hostiles.on()))) continue;
                double[] s = Draw.project(e.getBoundingBox().getCenter(), g, pt);
                if (s != null) Draw.line(g, cx, cy, s[0], s[1], p ? 0xCCFF3030 : 0xCCFF8800);
            }
        }
    }

    // ---------- WORLD ----------
    /** Flags chunks that contain a lot of storage blocks, which is the usual sign of a stash or base. */
    public static class StashFinder extends Module {
        final Setting threshold = num("Min Storage", 8, 2, 64);
        final Set<Long> reported = new HashSet<>();
        int tick;

        StashFinder() { super("StashFinder", "World"); }

        @Override public void onEnable() { reported.clear(); }

        @Override public void onTick() {
            if (mc.player == null || tick++ % 40 != 0) return;
            for (var en : storageByChunk().entrySet()) {
                if (en.getValue().size() < threshold.num() || !reported.add(en.getKey())) continue;
                BlockEntity first = en.getValue().get(0);
                chat("Possible base: " + en.getValue().size() + " storage blocks near "
                        + first.getBlockPos().getX() + ", " + first.getBlockPos().getY() + ", " + first.getBlockPos().getZ());
            }
        }
    }

    public static class Fullbright extends Module {
        Fullbright() { super("Fullbright", "World"); }
        @Override public void onTick() {
            if (mc.player != null) mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1_000_000, 0, false, false));
        }
        @Override public void onDisable() { if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION); }
    }

    // ---------- MOVEMENT ----------
    public static class AutoSprint extends Module {
        AutoSprint() { super("AutoSprint", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            if (mc.options.keyUp.isDown() && !mc.player.horizontalCollision && mc.player.getFoodData().getFoodLevel() > 6)
                mc.player.setSprinting(true);
        }
    }
}
