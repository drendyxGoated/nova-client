package dev.nova;

import com.mojang.authlib.GameProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public final class NewModules {
    private NewModules() {}

    public static void register() {
        add(new Freecam());
        add(new SusChunkFinder());
        add(new SpawnerESP());
        add(new SellBot());
        add(new AutoMiner());
        add(new Keystrokes());
        add(new PotionHUD());
    }

    private static void add(Module m) { Modules.ALL.add(m); }

    static void log(String file, String line) {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve(file);
            Files.writeString(p, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    // ======================= FREECAM =======================
    /** Camera entity that reads its rotation straight from your player every frame, so turning has zero lag. */
    static class CamEntity extends RemotePlayer {
        CamEntity(ClientLevel level, GameProfile profile) { super(level, profile); }
        @Override public float getViewYRot(float pt) {
            var p = Minecraft.getInstance().player;
            return p == null ? super.getViewYRot(pt) : p.getViewYRot(pt);
        }
        @Override public float getViewXRot(float pt) {
            var p = Minecraft.getInstance().player;
            return p == null ? super.getViewXRot(pt) : p.getViewXRot(pt);
        }
    }

    /**
     * Detaches the camera; your real player is frozen in place by LocalPlayerMixin while this is on.
     * Movement has acceleration/deceleration and flies where you look. Sprint key = 2x boost.
     */
    public static class Freecam extends Module {
        public static boolean active;
        final Setting speed = num("Speed", 0.8, 0.1, 5.0);
        final Setting smooth = num("Smoothness", 0.3, 0.05, 1.0);
        final Setting lookFly = bool("Fly where you look", true);
        CamEntity cam;
        Vec3 vel = Vec3.ZERO;

        Freecam() { super("Freecam", "Render"); persistEnabled = false; }

        @Override public void onEnable() {
            if (mc.player == null || mc.level == null) { enabled = false; return; }
            cam = new CamEntity(mc.level, new GameProfile(UUID.randomUUID(), "Freecam"));
            cam.copyPosition(mc.player);
            vel = Vec3.ZERO;
            active = true;
            mc.setCameraEntity(cam);
        }

        @Override public void onTick() {
            if (cam == null || mc.player == null) return;
            cam.xo = cam.getX(); cam.yo = cam.getY(); cam.zo = cam.getZ();

            var o = mc.options;
            double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
            double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
            double v = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
            double yr = Math.toRadians(mc.player.getYRot());
            double pr = lookFly.on() ? Math.toRadians(mc.player.getXRot()) : 0;
            Vec3 fwd = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
            Vec3 left = new Vec3(Math.cos(yr), 0, Math.sin(yr));

            Vec3 target = fwd.scale(f).add(left.scale(s)).add(0, v, 0);
            if (target.lengthSqr() > 1) target = target.normalize();
            target = target.scale(speed.num() * (o.keySprint.isDown() ? 2.0 : 1.0));

            vel = vel.add(target.subtract(vel).scale(smooth.num()));
            cam.setPos(cam.getX() + vel.x, cam.getY() + vel.y, cam.getZ() + vel.z);
        }

        @Override public void onDisable() {
            active = false;
            if (mc.player != null) mc.setCameraEntity(mc.player);
            cam = null;
        }
    }

    // ======================= SUS CHUNK FINDER =======================
    /**
     * Scans loaded chunks for blocks that don't generate naturally in bulk (redstone, torches, glass,
     * crafting/enchanting tables, beacons...). Chunks with a high score are probably player-built.
     */
    public static class SusChunkFinder extends Module {
        record Sus(double score, String desc, double x, double y, double z, String dim) {}

        static final Map<Block, Double> W = new HashMap<>();
        static final Map<Block, Integer> CAT = new HashMap<>(); // 0 redstone, 1 lights, 2 utility

        static void put(int cat, double w, Block... bs) { for (Block b : bs) { W.put(b, w); CAT.put(b, cat); } }
        static {
            put(0, 1.0, Blocks.REDSTONE_WIRE, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.PISTON, Blocks.STICKY_PISTON,
                    Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.REDSTONE_LAMP, Blocks.DAYLIGHT_DETECTOR);
            put(0, 1.5, Blocks.OBSERVER, Blocks.TNT);
            put(1, 0.5, Blocks.TORCH, Blocks.WALL_TORCH, Blocks.LANTERN, Blocks.SOUL_LANTERN);
            put(2, 0.5, Blocks.GLASS, Blocks.CRAFTING_TABLE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE);
            put(2, 0.25, Blocks.GLASS_PANE);
            put(2, 1.0, Blocks.IRON_DOOR);
            put(2, 2.0, Blocks.ANVIL, Blocks.ENCHANTING_TABLE);
            put(2, 5.0, Blocks.BEACON);
        }

        final Setting minScore = num("Min score", 14, 3, 100);
        final Setting perTick = num("Chunks per tick", 1, 1, 3);
        final Setting redstone = bool("Redstone / pistons", true);
        final Setting lights = bool("Torches / lanterns", true);
        final Setting utility = bool("Glass / utility blocks", true);
        final Setting chat = bool("Chat alerts", true);
        final Setting labels = bool("World labels", true);
        final Setting tracers = bool("Tracers", false);
        final Setting file = bool("Save to file", true);

        final Map<Long, Long> scannedAt = new HashMap<>();
        final Map<Long, Sus> found = new LinkedHashMap<>();
        final Map<Long, Double> reported = new HashMap<>();

        SusChunkFinder() { super("SusChunkFinder", "World"); }

        boolean catOn(int c) { return c == 0 ? redstone.on() : c == 1 ? lights.on() : utility.on(); }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null) return;
            int r = mc.options.getEffectiveRenderDistance();
            int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
            long now = System.currentTimeMillis();
            int budget = (int) perTick.num();
            outer:
            for (int cx = pcx - r; cx <= pcx + r; cx++) {
                for (int cz = pcz - r; cz <= pcz + r; cz++) {
                    long key = ChunkPos.asLong(cx, cz);
                    Long last = scannedAt.get(key);
                    if (last != null && now - last < 90_000) continue;
                    ChunkAccess ca = mc.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (!(ca instanceof LevelChunk chunk)) continue;
                    scan(chunk, cx, cz, key);
                    scannedAt.put(key, now);
                    if (--budget <= 0) break outer;
                }
            }
        }

        void scan(LevelChunk chunk, int cx, int cz, long key) {
            double score = 0, sx = 0, sy = 0, sz = 0; int n = 0;
            int[] cnt = new int[3];
            LevelChunkSection[] secs = chunk.getSections();
            int minSec = chunk.getMinSectionY();
            for (int i = 0; i < secs.length; i++) {
                LevelChunkSection sec = secs[i];
                if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(st -> W.containsKey(st.getBlock()))) continue;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    Block b = sec.getBlockState(x, y, z).getBlock();
                    Double w = W.get(b);
                    if (w == null) continue;
                    int cat = CAT.get(b);
                    if (!catOn(cat)) continue;
                    score += w; cnt[cat]++;
                    sx += cx * 16 + x; sy += (minSec + i) * 16 + y; sz += cz * 16 + z; n++;
                }
            }
            if (n == 0 || score < minScore.num()) { found.remove(key); reported.remove(key); return; }
            String desc = "redstone " + cnt[0] + ", lights " + cnt[1] + ", utility " + cnt[2];
            Sus s = new Sus(score, desc, sx / n, sy / n, sz / n, Modules.dim());
            found.put(key, s);
            Double prev = reported.get(key);
            if (prev == null || score >= prev * 1.5) {
                reported.put(key, score);
                String where = (int) s.x + ", " + (int) s.y + ", " + (int) s.z;
                if (chat.on()) Modules.chat("§eSus chunk§r score " + (int) score + " (" + desc + ") at " + where + " [" + s.dim + "]");
                if (file.on()) log("nova-sus-chunks.txt", java.time.LocalDateTime.now() + " | " + s.dim + " | " + where + " | score " + (int) score + " | " + desc);
            }
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            if (!labels.on() && !tracers.on()) return;
            String dim = Modules.dim();
            double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
            for (Sus s : found.values()) {
                if (!s.dim.equals(dim)) continue;
                double[] p = Draw.project(new Vec3(s.x, s.y + 1, s.z), g, pt);
                if (p == null) continue;
                int dist = (int) Math.sqrt(mc.player.distanceToSqr(s.x, s.y, s.z));
                if (labels.on()) {
                    String t = "SUS " + (int) s.score + " | " + dist + "m";
                    g.fill((int) p[0] - 2, (int) p[1] - 2, (int) p[0] + 2, (int) p[1] + 2, 0xFFFFE066);
                    g.drawString(mc.font, t, (int) p[0] - mc.font.width(t) / 2, (int) p[1] - 12, 0xFFFFE066);
                }
                if (tracers.on()) Draw.line(g, cx, cy, p[0], p[1], 0xAAFFE066);
            }
        }
    }

    // ======================= SPAWNER ESP =======================
    public static class SpawnerESP extends Module {
        final Setting range = num("Range", 256, 32, 512);
        final Setting alerts = bool("Chat alerts", true);
        final Setting tracers = bool("Tracers", false);
        List<BlockPos> cache = new ArrayList<>();
        final Set<Long> alerted = new HashSet<>();
        int t;

        SpawnerESP() { super("SpawnerESP", "World"); }

        @Override public void onTick() {
            if (mc.player == null || t++ % 20 != 0) return;
            final List<BlockPos> list = new ArrayList<>();
            Modules.forEachChunk((cx, cz, bes) -> {
                for (BlockEntity be : bes) if (be instanceof SpawnerBlockEntity) list.add(be.getBlockPos());
            });
            cache = list;
            for (BlockPos p : list)
                if (alerted.add(p.asLong()) && alerts.on())
                    Modules.chat("§cSpawner§r at " + p.getX() + ", " + p.getY() + ", " + p.getZ());
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
            for (BlockPos p : cache) {
                double d = Math.sqrt(mc.player.blockPosition().distSqr(p));
                if (d > range.num()) continue;
                double[] r = Draw.rect(new AABB(p), g, pt);
                if (r == null) continue;
                Draw.outline(g, r, 0xFFFF3030);
                g.drawString(mc.font, "Spawner " + (int) d + "m", (int) r[0], (int) r[1] - 10, 0xFFFF5555);
                if (tracers.on()) Draw.line(g, cx, cy, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2, 0xAAFF3030);
            }
        }
    }

    // ======================= SELL BOT =======================
    /**
     * Runs your server's sell command on a timer. In "/ah sell" mode it can pull spawners from your
     * inventory into your hand first. Command names differ per server, so check yours.
     */
    public static class SellBot extends Module {
        final Setting cmd = mode("Command", 2, "/sell hand", "/sell all", "/ah sell <price>");
        final Setting interval = num("Interval (sec)", 30, 2, 600);
        final Setting price = num("Price", 100000, 0, 10_000_000);
        final Setting spawners = bool("Only spawners", true);
        int t, nextAt = 600;

        SellBot() {
            super("SellBot", "Player");
            persistEnabled = false;
            price.step = 1000;
        }

        @Override public void onEnable() { nextAt = (int) (interval.num() * 20); t = nextAt - 20; }

        @Override public void onTick() {
            if (mc.player == null || mc.player.connection == null || mc.gameMode == null || mc.screen != null) return;
            if (++t < nextAt) return;
            t = 0;
            nextAt = (int) (interval.num() * 20 * (Humanizer.on() ? 0.85 + Humanizer.R.nextDouble() * 0.3 : 1));
            int m = cmd.mode();
            if (m == 0) { mc.player.connection.sendCommand("sell hand"); return; }
            if (m == 1) { mc.player.connection.sendCommand("sell all"); return; }

            var inv = mc.player.getInventory();
            if (spawners.on() && !inv.getItem(inv.getSelectedSlot()).is(Items.SPAWNER)) {
                for (int i = 0; i < 36; i++) {
                    if (!inv.getItem(i).is(Items.SPAWNER)) continue;
                    if (i < 9) inv.setSelectedSlot(i);
                    else mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, i,
                            inv.getSelectedSlot(), ClickType.SWAP, mc.player);
                    t = nextAt - 6; // fire again in a few ticks once it's in hand
                    return;
                }
                return; // no spawners left
            }
            if (inv.getItem(inv.getSelectedSlot()).isEmpty()) return;
            mc.player.connection.sendCommand("ah sell " + (long) price.num());
        }
    }

    // ======================= AUTO MINER (bot) =======================
    /**
     * Digs for you. Target "Facing direction": tunnel / stairs the way you face.
     * Target "Coordinates": digs an L-shaped route to X/Y/Z (stairs down/up to match height, shaft straight down
     * when you're above the spot). Set the coords with the sliders (scroll = +-1, Shift+scroll = 100, Ctrl+scroll = 1000),
     * the "Set target to my position" button, or the chat command: /krypton mine <x> <y> <z>   (or <x> <z>, or stop).
     * Auto-picks the best tool; stops at lava/water, low health, or when another player shows up.
     */
    public static class AutoMiner extends Module {
        final Setting source = mode("Target", 0, "Facing direction", "Coordinates");
        final Setting mode = mode("Style (facing)", 0, "Tunnel forward", "Stairs down", "Stairs up");
        final Setting tx = num("Target X", 0, -30_000_000, 30_000_000);
        final Setting ty = num("Target Y", 64, -64, 320);
        final Setting tz = num("Target Z", 0, -30_000_000, 30_000_000);
        final Setting here = button("Set target to my position", () -> {
            if (mc.player != null) {
                tx.value = mc.player.getBlockX(); ty.value = mc.player.getBlockY(); tz.value = mc.player.getBlockZ();
                source.value = 1;
                Notifications.push("Target set: " + (int) tx.value + " " + (int) ty.value + " " + (int) tz.value, Theme.accent());
            }
        });
        final Setting stopFluid = bool("Stop at lava/water", true);
        final Setting stopPlayer = bool("Stop if player near", true);
        final Setting minHp = num("Stop below HP", 8, 1, 19);
        Direction dir;
        BlockPos target;
        int pause;

        AutoMiner() {
            super("AutoMiner", "Player");
            persistEnabled = false;
            tx.step = 1; tz.step = 1; ty.step = 1;
        }

        /** Used by /krypton mine. Starts (or retargets) a coordinate dig. */
        public void goTo(int x, int y, int z) {
            tx.value = x; ty.value = y; tz.value = z;
            source.value = 1;
            if (!enabled) toggle();
            else { target = null; if (mc.player != null) dir = mc.player.getDirection(); }
        }

        @Override public void onEnable() {
            if (mc.player == null) { enabled = false; return; }
            dir = mc.player.getDirection();
            target = null;
            if (source.mode() == 1)
                Modules.chat("AutoMiner heading to " + (int) tx.value + " " + (int) ty.value + " " + (int) tz.value);
            else Modules.chat("AutoMiner digging " + dir.getName());
        }
        @Override public void onDisable() { release(); }

        void release() {
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(false);
            target = null;
        }

        void halt(String why) {
            enabled = false;
            release();
            Modules.chat("AutoMiner stopped: " + why);
            Notifications.push("AutoMiner: " + why, 0xFFFF5555);
        }

        boolean fluid(BlockPos p) {
            var fs = mc.level.getFluidState(p);
            return fs.is(FluidTags.LAVA) || fs.is(FluidTags.WATER);
        }

        /** Choose a cardinal direction toward the target; stay on the current one while it still helps. */
        void pickDir(int dx, int dz) {
            boolean keep = (dir.getAxis() == Direction.Axis.X && dx != 0 && Integer.signum(dx) == dir.getStepX())
                    || (dir.getAxis() == Direction.Axis.Z && dz != 0 && Integer.signum(dz) == dir.getStepZ());
            if (keep) return;
            if (dx != 0 && (Math.abs(dx) >= Math.abs(dz) || dz == 0)) dir = dx > 0 ? Direction.EAST : Direction.WEST;
            else if (dz != 0) dir = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || mc.gameMode == null || dir == null) return;
            if (mc.screen != null) { release(); return; }
            if (mc.player.getHealth() <= minHp.num()) { halt("health low"); return; }
            if (pause > 0) { pause--; return; }
            if (Humanizer.on() && Humanizer.chance(0.008)) { release(); pause = 6 + Humanizer.R.nextInt(30); return; }
            if (stopPlayer.on()) {
                for (Entity e : mc.level.entitiesForRendering())
                    if (e instanceof Player p && p != mc.player && mc.player.distanceTo(p) < 24) { halt(p.getName().getString() + " nearby"); return; }
            }

            BlockPos feet = mc.player.blockPosition();
            int style = mode.mode();
            boolean shaft = false;
            if (source.mode() == 1) {
                int dx = (int) tx.value - feet.getX(), dz = (int) tz.value - feet.getZ(), dy = (int) ty.value - feet.getY();
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && Math.abs(dy) <= 1) { halt("arrived!"); return; }
                if (dx == 0 && dz == 0) {
                    if (dy < 0) shaft = true;
                    else { halt("target is straight above you, stand next to it first"); return; }
                } else pickDir(dx, dz);
                style = dy < 0 ? 1 : dy > 0 ? 2 : 0;
            }

            BlockPos front = feet.relative(dir);
            List<BlockPos> clear = new ArrayList<>();
            if (shaft) clear.add(feet.below());
            else switch (style) {
                case 1 -> { clear.add(front.above()); clear.add(front); clear.add(front.below()); }
                case 2 -> { clear.add(feet.above(2)); clear.add(front.above()); clear.add(front.above(2)); }
                default -> { clear.add(front); clear.add(front.above()); }
            }

            if (stopFluid.on()) {
                for (BlockPos p : clear) for (Direction d : Direction.values())
                    if (fluid(p.relative(d))) { halt("lava/water ahead"); return; }
            }

            for (BlockPos p : clear) {
                BlockState st = mc.level.getBlockState(p);
                if (st.isAir()) continue;
                if (st.getDestroySpeed(mc.level, p) < 0) { halt("unbreakable block"); return; }
                mine(p, st);
                return;
            }

            // path is clear
            target = null;
            if (shaft) { release(); return; }   // just drop down the hole
            Humanizer.aim(new float[]{dir.toYRot(), 0f}, 0.4);
            mc.options.keyUp.setDown(true);
            mc.options.keyJump.setDown(style == 2);
        }

        void mine(BlockPos p, BlockState st) {
            mc.options.keyUp.setDown(false);
            mc.options.keyJump.setDown(false);
            Vec3 center = Vec3.atCenterOf(p);
            Vec3 eye = mc.player.getEyePosition();
            float rem = Humanizer.aim(Targets.rotations(eye, center), 0.5);
            if (Humanizer.on() && rem > 12) return;

            var inv = mc.player.getInventory();
            int best = inv.getSelectedSlot();
            float bs = inv.getItem(best).getDestroySpeed(st);
            for (int i = 0; i < 9; i++) {
                float s = inv.getItem(i).getDestroySpeed(st);
                if (s > bs) { bs = s; best = i; }
            }
            if (best != inv.getSelectedSlot()) inv.setSelectedSlot(best);

            Vec3 d = eye.subtract(center);
            Direction face = Direction.getApproximateNearest(d.x, d.y, d.z);
            if (!p.equals(target)) { mc.gameMode.startDestroyBlock(p, face); target = p; }
            else mc.gameMode.continueDestroyBlock(p, face);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    // ======================= HUD EXTRAS =======================
    public static class Keystrokes extends Module {
        Keystrokes() { super("Keystrokes", "HUD"); }

        static void key(GuiGraphics g, String t, int x, int y, int w, int h, boolean down) {
            int accent = Theme.accent();
            Ui.round(g, x, y, x + w, y + h, 3, down ? Ui.withAlpha(accent, 190) : 0x88000000);
            g.drawString(mc.font, t, x + (w - mc.font.width(t)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            int bx = g.guiWidth() - 86, by = g.guiHeight() / 2 + 30, s = 24, gap = 2;
            var o = mc.options;
            key(g, "W", bx + s + gap, by, s, s, o.keyUp.isDown());
            key(g, "A", bx, by + s + gap, s, s, o.keyLeft.isDown());
            key(g, "S", bx + s + gap, by + s + gap, s, s, o.keyDown.isDown());
            key(g, "D", bx + 2 * (s + gap), by + s + gap, s, s, o.keyRight.isDown());
            key(g, "SPACE", bx, by + 2 * (s + gap), 3 * s + 2 * gap, 14, o.keyJump.isDown());
        }
    }

    public static class PotionHUD extends Module {
        PotionHUD() { super("PotionHUD", "HUD"); }
        @Override public void onRender(GuiGraphics g, float pt) {
            int y = g.guiHeight() / 2 - 20;
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                String name = e.getEffect().value().getDisplayName().getString() + " " + (e.getAmplifier() + 1);
                String time;
                if (e.isInfiniteDuration()) time = "inf";
                else { int secs = e.getDuration() / 20; time = (secs / 60) + ":" + String.format("%02d", secs % 60); }
                g.drawString(mc.font, name + "  " + time, 4, y, 0xFFFFFFFF);
                y += 10;
            }
        }
    }
}
