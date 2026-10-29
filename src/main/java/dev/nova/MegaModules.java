package dev.nova;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Waypoints, custom block search, block-entity ESP, chunk borders, nearby list, auto-XP, vehicle fly, nuker, macro. */
public final class MegaModules {
    private MegaModules() {}

    public static void register() {
        add(new Waypoints()); add(new Search()); add(new BlockEntityESP()); add(new ChunkBorders());
        add(new Nearby()); add(new AutoMend()); add(new VehicleFly()); add(new Nuker()); add(new Macro());
        loadWaypoints(); loadSearch();
    }
    private static void add(Module m) { Modules.ALL.add(m); }

    static Path cfg(String f) { return FabricLoader.getInstance().getConfigDir().resolve(f); }

    // ======================= WAYPOINTS =======================
    public record WP(String name, String dim, int x, int y, int z) {}
    public static final List<WP> WPS = new ArrayList<>();

    static void loadWaypoints() {
        try {
            if (!Files.exists(cfg("krypton-waypoints.txt"))) return;
            for (String l : Files.readAllLines(cfg("krypton-waypoints.txt"))) {
                String[] a = l.split("\\|");
                if (a.length == 5) WPS.add(new WP(a[0], a[1], Integer.parseInt(a[2]), Integer.parseInt(a[3]), Integer.parseInt(a[4])));
            }
        } catch (Exception ignored) {}
    }

    static void saveWaypoints() {
        List<String> out = new ArrayList<>();
        for (WP w : WPS) out.add(w.name() + "|" + w.dim() + "|" + w.x() + "|" + w.y() + "|" + w.z());
        try { Files.write(cfg("krypton-waypoints.txt"), out); } catch (Exception ignored) {}
    }

    public static class Waypoints extends Module {
        final Setting tracers = bool("Tracers", false);
        final Setting range = num("Hide beyond (blocks)", 5000, 100, 100000);
        Waypoints() { super("Waypoints", "Render"); }

        @Override public void onRender(GuiGraphics g, float pt) {
            String dim = Modules.dim();
            double cx = g.guiWidth() / 2.0, cy = g.guiHeight() / 2.0;
            int accent = Theme.accent();
            for (WP w : WPS) {
                if (!w.dim().equals(dim)) continue;
                double d = Math.sqrt(mc.player.distanceToSqr(w.x() + 0.5, w.y(), w.z() + 0.5));
                if (d > range.num()) continue;
                double[] p = Draw.project(new Vec3(w.x() + 0.5, w.y() + 1.0, w.z() + 0.5), g, pt);
                if (p == null) continue;
                String t = w.name() + " " + (int) d + "m";
                g.fill((int) p[0] - 2, (int) p[1] - 2, (int) p[0] + 2, (int) p[1] + 2, accent);
                g.drawString(mc.font, t, (int) p[0] - mc.font.width(t) / 2, (int) p[1] - 12, accent);
                if (tracers.on()) Draw.line(g, cx, cy, p[0], p[1], Ui.withAlpha(accent, 170));
            }
        }
    }

    // ======================= SEARCH (any block) =======================
    public static final Set<Block> SEARCH = new LinkedHashSet<>();

    static String idOf(Block b) { return BuiltInRegistries.BLOCK.getKey(b).getPath(); }

    static Block parseBlock(String in) {
        String id = in.toLowerCase().replace("minecraft:", "");
        for (Block b : BuiltInRegistries.BLOCK) if (idOf(b).equals(id)) return b;
        return null;
    }

    static void loadSearch() {
        try {
            if (!Files.exists(cfg("krypton-search.txt"))) return;
            for (String l : Files.readAllLines(cfg("krypton-search.txt"))) { Block b = parseBlock(l.trim()); if (b != null) SEARCH.add(b); }
        } catch (Exception ignored) {}
    }

    static void saveSearch() {
        List<String> out = new ArrayList<>();
        for (Block b : SEARCH) out.add(idOf(b));
        try { Files.write(cfg("krypton-search.txt"), out); } catch (Exception ignored) {}
    }

    /** Highlights any blocks you choose: /krypton esp add <block> (e.g. diamond_block, obsidian, lava). */
    public static class Search extends Module {
        record Hit(BlockPos pos, int color) {}
        final Setting radius = num("Radius", 16, 4, 32);
        List<Hit> found = new ArrayList<>();
        List<Hit> building = new ArrayList<>();
        int sx = Integer.MIN_VALUE, rr, t;
        BlockPos origin;

        Search() { super("Search", "World"); }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || SEARCH.isEmpty()) { found = new ArrayList<>(); return; }
            if (sx == Integer.MIN_VALUE) {
                if (t++ % 30 != 0) return;
                origin = mc.player.blockPosition();
                rr = (int) radius.num();
                sx = -rr;
                building = new ArrayList<>();
            }
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = -rr; y <= rr; y++) for (int z = -rr; z <= rr; z++) {
                p.set(origin.getX() + sx, origin.getY() + y, origin.getZ() + z);
                Block b = mc.level.getBlockState(p).getBlock();
                if (SEARCH.contains(b)) building.add(new Hit(p.immutable(), Theme.hueColor(Math.abs(idOf(b).hashCode()) % 360, 0.7f, 1f)));
            }
            if (++sx > rr) {
                final BlockPos o = origin;
                building.sort(Comparator.comparingDouble(h -> h.pos().distSqr(o)));
                int cap = Modules.cap();
                found = building.size() > cap ? new ArrayList<>(building.subList(0, cap)) : building;
                sx = Integer.MIN_VALUE;
            }
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            for (Hit h : found) {
                double[] r = Draw.rect(new AABB(h.pos()), g, pt);
                if (r != null) Draw.outline(g, r, h.color());
            }
        }
    }

    // ======================= BLOCK ENTITY ESP =======================
    public static class BlockEntityESP extends Module {
        record Item(BlockPos pos, int color, String label) {}
        final Setting range = num("Range", 128, 16, 256);
        final Setting beds = bool("Beds", true), signs = bool("Signs", false), furnaces = bool("Furnaces", false),
                dispensers = bool("Dispensers/Droppers", false), beacons = bool("Beacons", true),
                tables = bool("Enchanting tables", true), brewing = bool("Brewing stands", false);
        List<Item> cache = new ArrayList<>();
        int t;

        BlockEntityESP() { super("BlockEntityESP", "Render"); }

        Item classify(BlockEntity be) {
            BlockPos p = be.getBlockPos();
            if (beds.on() && be instanceof BedBlockEntity) return new Item(p, 0xFFFF5555, "Bed");
            if (signs.on() && be instanceof SignBlockEntity) return new Item(p, 0xFFDDDDDD, "Sign");
            if (furnaces.on() && be instanceof AbstractFurnaceBlockEntity) return new Item(p, 0xFFAAAAAA, "Furnace");
            if (dispensers.on() && be instanceof DispenserBlockEntity) return new Item(p, 0xFF888888, "Dispenser");
            if (beacons.on() && be instanceof BeaconBlockEntity) return new Item(p, 0xFF55FFFF, "Beacon");
            if (tables.on() && be instanceof EnchantingTableBlockEntity) return new Item(p, 0xFFAA55FF, "Enchanting");
            if (brewing.on() && be instanceof BrewingStandBlockEntity) return new Item(p, 0xFFFFAA55, "Brewing");
            return null;
        }

        @Override public void onTick() {
            if (mc.player == null || t++ % 20 != 0) return;
            final List<Item> list = new ArrayList<>();
            Modules.forEachChunk((cx, cz, bes) -> {
                for (BlockEntity be : bes) { Item it = classify(be); if (it != null) list.add(it); }
            });
            cache = list;
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            double max = range.num() * range.num();
            int drawn = 0;
            for (Item it : cache) {
                if (drawn >= Modules.cap()) break;
                if (it.pos().distSqr(mc.player.blockPosition()) > max) continue;
                double[] r = Draw.rect(new AABB(it.pos()), g, pt);
                if (r == null) continue;
                drawn++;
                Draw.outline(g, r, it.color());
                g.drawString(mc.font, it.label(), (int) r[0], (int) r[1] - 10, it.color());
            }
        }
    }

    // ======================= CHUNK BORDERS =======================
    public static class ChunkBorders extends Module {
        final Setting height = num("Height", 16, 4, 64);
        ChunkBorders() { super("ChunkBorders", "Render"); }

        @Override public void onRender(GuiGraphics g, float pt) {
            int x0 = (mc.player.getBlockX() >> 4) * 16, z0 = (mc.player.getBlockZ() >> 4) * 16;
            double y = Math.floor(mc.player.getY());
            double h = height.num();
            int c = 0xCCFFDD33;
            double[][] corner = {{x0, z0}, {x0 + 16, z0}, {x0 + 16, z0 + 16}, {x0, z0 + 16}};
            for (int i = 0; i < 4; i++) {
                double[] a = Draw.project(new Vec3(corner[i][0], y - h, corner[i][1]), g, pt);
                double[] b = Draw.project(new Vec3(corner[i][0], y + h, corner[i][1]), g, pt);
                if (a != null && b != null) Draw.line(g, a[0], a[1], b[0], b[1], c);
                double[] n1 = Draw.project(new Vec3(corner[i][0], y, corner[i][1]), g, pt);
                double[] n2 = Draw.project(new Vec3(corner[(i + 1) % 4][0], y, corner[(i + 1) % 4][1]), g, pt);
                if (n1 != null && n2 != null) Draw.line(g, n1[0], n1[1], n2[0], n2[1], c);
            }
        }
    }

    // ======================= NEARBY (HUD) =======================
    public static class Nearby extends Module {
        final Setting range = num("Range", 128, 16, 512);
        Nearby() { super("Nearby", "HUD"); }

        @Override public void onRender(GuiGraphics g, float pt) {
            List<Player> list = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering())
                if (e instanceof Player p && p != mc.player && mc.player.distanceTo(p) <= range.num()) list.add(p);
            list.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));
            int y = g.guiHeight() / 2 - 80;
            int x = g.guiWidth() - 8;
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                Player p = list.get(i);
                String t = p.getName().getString() + "  " + (int) p.getHealth() + "hp  " + (int) mc.player.distanceTo(p) + "m";
                int col = Friends.is(p) ? 0xFF55FFFF : 0xFFFF6666;
                g.drawString(mc.font, t, x - mc.font.width(t), y, col);
                y += 10;
            }
        }
    }

    // ======================= AUTO MEND (xp bottles) =======================
    public static class AutoMend extends Module {
        final Setting below = num("Armor below %", 90, 10, 100);
        int cd;
        AutoMend() { super("AutoMend", "Player"); }

        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null || mc.screen != null || cd-- > 0) return;
            boolean need = false;
            for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = mc.player.getItemBySlot(es);
                if (!s.isEmpty() && s.isDamageableItem() && s.getDamageValue() > 0
                        && 100 - s.getDamageValue() * 100 / Math.max(1, s.getMaxDamage()) < below.num()) need = true;
            }
            int slot = Place.hotbar(s -> s.is(Items.EXPERIENCE_BOTTLE));
            if (!need || slot < 0) return;
            float rem = Humanizer.aim(new float[]{mc.player.getYRot(), 90f}, 0.7);
            if (Humanizer.on() && rem > 8) return;
            Place.withSlot(slot, () -> { mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND); return true; });
            cd = Humanizer.jitter(3);
        }
    }

    // ======================= VEHICLE FLY =======================
    /** Boats/horses/pigs/minecarts: faster and (optionally) flying. Jump = up, Sprint = down (Shift would dismount). */
    public static class VehicleFly extends Module {
        final Setting speed = num("Speed", 1.2, 0.2, 5.0);
        final Setting fly = bool("Fly", true);
        VehicleFly() { super("VehicleFly", "Movement"); }

        @Override public void onTick() {
            if (mc.player == null) return;
            Entity v = mc.player.getVehicle();
            if (v == null) return;
            Vec3 h = MoreModules.moveVec(speed.num());
            double y = v.getDeltaMovement().y;
            if (fly.on()) y = mc.options.keyJump.isDown() ? speed.num() * 0.5 : mc.options.keySprint.isDown() ? -speed.num() * 0.5 : 0;
            v.setDeltaMovement(h.x, y, h.z);
            v.setYRot(mc.player.getYRot());
        }
    }

    // ======================= NUKER =======================
    /** Mines the nearest matching block in reach, one at a time, with eased aim. Keeps the floor under you by default. */
    public static class Nuker extends Module {
        final Setting range = num("Range", 4, 2, 5);
        final Setting what = mode("Blocks", 0, "Everything", "Ores only", "Leaves/plants");
        final Setting keepFloor = bool("Keep floor", true);
        BlockPos target;
        Nuker() { super("Nuker", "World"); }

        boolean match(BlockState st) {
            String id = idOf(st.getBlock());
            return switch (what.mode()) {
                case 1 -> id.endsWith("_ore") || id.equals("ancient_debris");
                case 2 -> id.contains("leaves") || id.contains("vine") || id.contains("grass") || id.contains("fern")
                        || id.contains("sapling") || id.contains("flower");
                default -> true;
            };
        }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            BlockPos feet = mc.player.blockPosition();
            Vec3 eye = mc.player.getEyePosition();
            int r = (int) range.num();
            BlockPos best = null; double bd = 1e9; BlockState bst = null;
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                BlockPos p = feet.offset(x, y, z);
                if (keepFloor.on() && p.getY() < feet.getY()) continue;
                BlockState st = mc.level.getBlockState(p);
                if (st.isAir() || !st.getFluidState().isEmpty() || st.getDestroySpeed(mc.level, p) < 0) continue;
                double d = eye.distanceTo(Vec3.atCenterOf(p));
                if (d > 4.5 || d >= bd || !match(st)) continue;
                best = p; bd = d; bst = st;
            }
            if (best == null) { target = null; return; }

            Vec3 center = Vec3.atCenterOf(best);
            float rem = Humanizer.aim(Targets.rotations(eye, center), 0.5);
            if (Humanizer.on() && rem > 12) return;

            var inv = mc.player.getInventory();
            int bestSlot = inv.getSelectedSlot();
            float bs = inv.getItem(bestSlot).getDestroySpeed(bst);
            for (int i = 0; i < 9; i++) {
                float s = inv.getItem(i).getDestroySpeed(bst);
                if (s > bs) { bs = s; bestSlot = i; }
            }
            if (bestSlot != inv.getSelectedSlot()) inv.setSelectedSlot(bestSlot);

            Vec3 d = eye.subtract(center);
            Direction face = Direction.getApproximateNearest(d.x, d.y, d.z);
            if (!best.equals(target)) { mc.gameMode.startDestroyBlock(best, face); target = best; }
            else mc.gameMode.continueDestroyBlock(best, face);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    // ======================= MACRO =======================
    /** Sends any chat message or /command on a timer: /krypton macro <seconds> <text...> */
    public static class Macro extends Module {
        public static String text = "";
        final Setting interval = num("Interval (sec)", 60, 1, 3600);
        int t, nextAt;
        Macro() { super("Macro", "Misc"); persistEnabled = false; }

        @Override public void onEnable() {
            if (text.isEmpty()) {
                Modules.chat("Set one first: /krypton macro <seconds> <text or /command>");
                enabled = false;
                return;
            }
            nextAt = (int) (interval.num() * 20);
            t = nextAt - 20;
        }

        @Override public void onTick() {
            if (mc.player == null || mc.player.connection == null || text.isEmpty() || ++t < nextAt) return;
            t = 0;
            nextAt = (int) (interval.num() * 20 * (Humanizer.on() ? 0.9 + Humanizer.R.nextDouble() * 0.2 : 1));
            if (text.startsWith("/")) mc.player.connection.sendCommand(text.substring(1));
            else mc.player.connection.sendChat(text);
        }
    }
}
