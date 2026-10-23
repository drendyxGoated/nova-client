package dev.nova;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class ExtraModules {
    private ExtraModules() {}

    public static void register() {
        // combat
        add(new AimAssist()); add(new AutoWeapon());
        // movement
        add(new Sneak()); add(new Bhop()); add(new AutoJump()); add(new Jetpack()); add(new HighJump());
        add(new LowGravity()); add(new Glide()); add(new ElytraFly()); add(new FastLadder());
        // player
        add(new AutoRespawn()); add(new ChestStealer()); add(new AutoMine()); add(new MiddleClickPearl());
        // render / world
        add(new ItemESP()); add(new OreESP()); add(new Trail()); add(new LowHealthWarning());
        // misc
        add(new AutoLog()); add(new PlayerAlert()); add(new DeathLogger()); add(new Panic());
        // hud
        add(new HUD()); add(new Radar()); add(new TargetHUD()); add(new ArmorHUD());
    }

    private static void add(Module m) { Modules.ALL.add(m); }

    // ======================= COMBAT =======================
    public static class AimAssist extends Module {
        final Setting range = num("Range", 5.0, 2, 8);
        final Setting smooth = num("Strength", 0.3, 0.05, 1.0);
        final Setting onlyAttack = bool("Only while attacking", true);
        final Targets.Filter filter;

        AimAssist() {
            super("AimAssist", "Combat");
            filter = new Targets.Filter(this, true, true, false, false, false, false);
        }

        @Override public void onTick() {
            if (mc.screen != null) return;
            if (onlyAttack.on() && !mc.options.keyAttack.isDown()) return;
            Entity best = null; double bd = 1e9;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive() || !filter.accepts(e)) continue;
                double d = mc.player.distanceTo(e);
                if (d > range.num() || d >= bd || !mc.player.hasLineOfSight(e)) continue;
                best = e; bd = d;
            }
            if (best == null) return;
            float[] r = Targets.rotations(mc.player.getEyePosition(), best.getBoundingBox().getCenter());
            float s = (float) smooth.num();
            mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(r[0] - mc.player.getYRot()) * s);
            mc.player.setXRot(Mth.clamp(mc.player.getXRot() + (r[1] - mc.player.getXRot()) * s, -90f, 90f));
        }
    }

    public static class AutoWeapon extends Module {
        AutoWeapon() { super("AutoWeapon", "Combat"); }
        @Override public void onTick() {
            if (mc.screen != null || !mc.options.keyAttack.isDown() || !(mc.hitResult instanceof EntityHitResult)) return;
            var inv = mc.player.getInventory();
            int best = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(ItemTags.SWORDS)) { best = i; break; }
                if (best < 0 && s.is(ItemTags.AXES)) best = i;
            }
            if (best >= 0 && best != inv.getSelectedSlot()) inv.setSelectedSlot(best);
        }
    }

    // ======================= MOVEMENT =======================
    public static class Sneak extends Module {
        Sneak() { super("Sneak", "Movement"); }
        @Override public void onTick() { mc.options.keyShift.setDown(true); }
        @Override public void onDisable() { mc.options.keyShift.setDown(false); }
    }

    public static class Bhop extends Module {
        Bhop() { super("Bhop", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null || !mc.player.onGround()) return;
            if (MoreModules.moveVec(1) != Vec3.ZERO) mc.player.jumpFromGround();
        }
    }

    public static class AutoJump extends Module {
        AutoJump() { super("AutoJump", "Movement"); }
        @Override public void onTick() {
            if (mc.player != null && mc.player.onGround() && mc.player.horizontalCollision && mc.options.keyUp.isDown())
                mc.player.jumpFromGround();
        }
    }

    public static class Jetpack extends Module {
        final Setting speed = num("Lift", 0.4, 0.1, 1.5);
        Jetpack() { super("Jetpack", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null || !mc.options.keyJump.isDown()) return;
            Vec3 c = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(c.x, speed.num(), c.z);
            mc.player.fallDistance = 0;
        }
    }

    public static class HighJump extends Module {
        final Setting power = num("Power", 0.8, 0.42, 2.0);
        HighJump() { super("HighJump", "Movement"); }
        @Override public void onTick() {
            if (mc.player != null) mc.player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(power.num());
        }
        @Override public void onDisable() {
            if (mc.player != null) mc.player.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.42);
        }
    }

    public static class LowGravity extends Module {
        final Setting gravity = num("Gravity", 0.03, 0.0, 0.08);
        LowGravity() { super("LowGravity", "Movement"); }
        @Override public void onTick() {
            if (mc.player != null) mc.player.getAttribute(Attributes.GRAVITY).setBaseValue(gravity.num());
        }
        @Override public void onDisable() {
            if (mc.player != null) mc.player.getAttribute(Attributes.GRAVITY).setBaseValue(0.08);
        }
    }

    public static class Glide extends Module {
        final Setting fall = num("Fall speed", 0.05, 0.0, 0.3);
        Glide() { super("Glide", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null || mc.player.onGround() || mc.player.isFallFlying()) return;
            Vec3 c = mc.player.getDeltaMovement();
            if (c.y < -fall.num()) mc.player.setDeltaMovement(c.x, -fall.num(), c.z);
            mc.player.fallDistance = 0;
        }
    }

    public static class ElytraFly extends Module {
        final Setting speed = num("Speed", 1.5, 0.5, 5.0);
        final Setting hover = bool("Hover when idle", true);
        ElytraFly() { super("ElytraFly", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null || !mc.player.isFallFlying()) return;
            Vec3 look = mc.player.getLookAngle();
            if (mc.options.keyUp.isDown()) mc.player.setDeltaMovement(look.scale(speed.num()));
            else if (hover.on()) mc.player.setDeltaMovement(Vec3.ZERO);
            Vec3 c = mc.player.getDeltaMovement();
            if (mc.options.keyJump.isDown()) mc.player.setDeltaMovement(c.x, c.y + 0.5, c.z);
            else if (mc.options.keyShift.isDown()) mc.player.setDeltaMovement(c.x, c.y - 0.5, c.z);
        }
    }

    public static class FastLadder extends Module {
        final Setting speed = num("Speed", 0.35, 0.1, 1.0);
        FastLadder() { super("FastLadder", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            if (mc.player.onClimbable() && mc.player.horizontalCollision && mc.options.keyUp.isDown()) {
                Vec3 c = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(c.x, speed.num(), c.z);
            }
        }
    }

    // ======================= PLAYER =======================
    public static class AutoRespawn extends Module {
        AutoRespawn() { super("AutoRespawn", "Player"); }
        @Override public void onTick() {
            if (mc.screen instanceof DeathScreen && mc.player != null) mc.player.respawn();
        }
    }

    public static class ChestStealer extends Module {
        final Setting delay = num("Delay (ticks)", 1, 0, 10);
        int cd;
        ChestStealer() { super("ChestStealer", "Player"); }
        @Override public void onTick() {
            if (mc.screen == null || mc.gameMode == null || !(mc.player.containerMenu instanceof ChestMenu cm)) return;
            if (cd-- > 0) return;
            int n = cm.getRowCount() * 9;
            for (int i = 0; i < n; i++) {
                if (!cm.getSlot(i).getItem().isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(cm.containerId, i, 0, ClickType.QUICK_MOVE, mc.player);
                    cd = (int) delay.num();
                    return;
                }
            }
        }
    }

    public static class AutoMine extends Module {
        AutoMine() { super("AutoMine", "Player"); }
        @Override public void onTick() { mc.options.keyAttack.setDown(true); }
        @Override public void onDisable() { mc.options.keyAttack.setDown(false); }
    }

    /** Middle-click throws an ender pearl from your hotbar. */
    public static class MiddleClickPearl extends Module {
        boolean last;
        MiddleClickPearl() { super("MiddleClickPearl", "Player"); }
        @Override public void onTick() {
            boolean down = mc.options.keyPickItem.isDown();
            if (down && !last && mc.screen == null && mc.gameMode != null) {
                var inv = mc.player.getInventory();
                int slot = -1;
                for (int i = 0; i < 9; i++) if (inv.getItem(i).is(Items.ENDER_PEARL)) { slot = i; break; }
                if (slot >= 0) {
                    int prev = inv.getSelectedSlot();
                    inv.setSelectedSlot(slot);
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    inv.setSelectedSlot(prev);
                }
            }
            last = down;
        }
    }

    // ======================= RENDER / WORLD =======================
    public static class ItemESP extends Module {
        final Setting range = num("Range", 64, 8, 256);
        final Setting valuables = bool("Valuables only", false);
        final Setting names = bool("Labels", true);

        ItemESP() { super("ItemESP", "Render"); }

        static boolean valuable(ItemStack s) {
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            return id.contains("diamond") || id.contains("netherite") || id.contains("shulker_box") || id.contains("totem")
                    || id.equals("elytra") || id.contains("golden_apple") || id.equals("end_crystal")
                    || id.equals("experience_bottle") || id.equals("ancient_debris") || id.equals("beacon");
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ItemEntity ie) || mc.player.distanceTo(e) > range.num()) continue;
                ItemStack st = ie.getItem();
                boolean v = valuable(st);
                if (valuables.on() && !v) continue;
                double[] r = Draw.rect(e.getBoundingBox().inflate(0.15), g, pt);
                if (r == null) continue;
                int c = v ? 0xFF55FFFF : 0xFFFFFF55;
                Draw.outline(g, r, c);
                if (names.on()) g.drawString(mc.font, st.getHoverName().getString() + " x" + st.getCount(), (int) r[0], (int) r[1] - 10, c);
            }
        }
    }

    public static class OreESP extends Module {
        record Entry(Setting s, int color) {}
        final Setting radius = num("Radius", 12, 4, 24);
        final Setting diamond = bool("Diamond", true), debris = bool("Ancient debris", true), emerald = bool("Emerald", true),
                gold = bool("Gold", true), iron = bool("Iron", false), lapis = bool("Lapis", false),
                redstone = bool("Redstone", false), coal = bool("Coal", false), copper = bool("Copper", false),
                quartz = bool("Nether quartz", false), spawners = bool("Spawners", true), portals = bool("Portals/Frames", true);
        final Map<Block, Entry> map = new HashMap<>();
        record Ore(BlockPos pos, int color) {}
        List<Ore> found = new ArrayList<>();
        int t;

        OreESP() {
            super("OreESP", "Render");
            put(diamond, 0xFF55FFFF, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
            put(debris, 0xFFAA5522, Blocks.ANCIENT_DEBRIS);
            put(emerald, 0xFF22DD44, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
            put(gold, 0xFFFFDD33, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
            put(iron, 0xFFDDBBAA, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
            put(lapis, 0xFF3355FF, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
            put(redstone, 0xFFFF2222, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
            put(coal, 0xFF444444, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
            put(copper, 0xFFDD8855, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
            put(quartz, 0xFFEEEEEE, Blocks.NETHER_QUARTZ_ORE);
            put(spawners, 0xFFFF2222, Blocks.SPAWNER);
            put(portals, 0xFFAA55FF, Blocks.NETHER_PORTAL, Blocks.END_PORTAL_FRAME);
        }

        void put(Setting s, int color, Block... blocks) { for (Block b : blocks) map.put(b, new Entry(s, color)); }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || t++ % 40 != 0) return;
            List<Ore> list = new ArrayList<>();
            int r = (int) radius.num();
            BlockPos o = mc.player.blockPosition();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                p.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                Entry en = map.get(mc.level.getBlockState(p).getBlock());
                if (en != null && en.s().on()) list.add(new Ore(p.immutable(), en.color()));
            }
            found = list;
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            for (Ore ore : found) {
                double[] r = Draw.rect(new AABB(ore.pos()), g, pt);
                if (r != null) Draw.outline(g, r, ore.color());
            }
        }
    }

    public static class Trail extends Module {
        final List<Vec3> pts = new ArrayList<>();
        int t;
        Trail() { super("Trail", "Render"); }
        @Override public void onTick() {
            if (mc.player == null || t++ % 3 != 0) return;
            pts.add(mc.player.position());
            if (pts.size() > 300) pts.remove(0);
        }
        @Override public void onDisable() { pts.clear(); }
        @Override public void onRender(GuiGraphics g, float pt) {
            for (Vec3 v : pts) {
                double[] p = Draw.project(v, g, pt);
                if (p != null) g.fill((int) p[0], (int) p[1], (int) p[0] + 2, (int) p[1] + 2, 0xCC5B8CFF);
            }
        }
    }

    public static class LowHealthWarning extends Module {
        final Setting threshold = num("Health below", 8, 2, 19);
        LowHealthWarning() { super("LowHealthWarning", "Render"); }
        @Override public void onRender(GuiGraphics g, float pt) {
            if (mc.player.getHealth() > threshold.num()) return;
            int w = g.guiWidth(), h = g.guiHeight(), t = 8, c = 0x66FF0000;
            g.fill(0, 0, w, t, c); g.fill(0, h - t, w, h, c); g.fill(0, 0, t, h, c); g.fill(w - t, 0, w, h, c);
        }
    }

    // ======================= MISC =======================
    /** Disconnects you when health is low or a player gets close. */
    public static class AutoLog extends Module {
        final Setting health = num("Health below", 6, 1, 19);
        final Setting near = num("Player within", 0, 0, 64);
        AutoLog() { super("AutoLog", "Misc"); }
        @Override public void onTick() {
            if (mc.player == null || mc.player.connection == null) return;
            String why = null;
            if (mc.player.getHealth() <= health.num()) why = "health low";
            else if (near.num() > 0) {
                for (Entity e : mc.level.entitiesForRendering())
                    if (e instanceof Player p && p != mc.player && mc.player.distanceTo(p) <= near.num()) { why = p.getName().getString() + " nearby"; break; }
            }
            if (why != null) {
                enabled = false;
                mc.player.connection.getConnection().disconnect(Component.literal("[Nova] AutoLog: " + why));
            }
        }
    }

    public static class PlayerAlert extends Module {
        final Setting leave = bool("Alert on leave", true);
        final Set<UUID> seen = new HashSet<>();
        int t;
        PlayerAlert() { super("PlayerAlert", "Misc"); }
        @Override public void onEnable() { seen.clear(); }
        @Override public void onTick() {
            if (mc.player == null || t++ % 10 != 0) return;
            Map<UUID, Player> now = new HashMap<>();
            for (Entity e : mc.level.entitiesForRendering())
                if (e instanceof Player p && p != mc.player) now.put(p.getUUID(), p);
            for (var en : now.entrySet())
                if (seen.add(en.getKey()))
                    Modules.chat("§c" + en.getValue().getName().getString() + "§r entered range (" + (int) mc.player.distanceTo(en.getValue()) + "m)");
            Iterator<UUID> it = seen.iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                if (!now.containsKey(id)) { it.remove(); if (leave.on()) Modules.chat("A player left range"); }
            }
        }
    }

    public static class DeathLogger extends Module {
        BlockPos last, deathPos;
        boolean reported;
        DeathLogger() { super("DeathLogger", "Misc"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            if (mc.player.isDeadOrDying() || mc.screen instanceof DeathScreen) {
                if (!reported && last != null) {
                    deathPos = last; reported = true;
                    Modules.chat("You died at " + last.getX() + ", " + last.getY() + ", " + last.getZ() + " [" + Modules.dim() + "]");
                }
            } else {
                last = mc.player.blockPosition();
                reported = false;
                if (deathPos != null && deathPos.distSqr(last) < 9) deathPos = null;
            }
        }
        @Override public void onRender(GuiGraphics g, float pt) {
            if (deathPos == null) return;
            double[] p = Draw.project(Vec3.atCenterOf(deathPos), g, pt);
            if (p == null) return;
            String t = "DEATH " + (int) Math.sqrt(deathPos.distSqr(mc.player.blockPosition())) + "m";
            g.drawString(mc.font, t, (int) p[0] - mc.font.width(t) / 2, (int) p[1], 0xFFFF5555);
        }
    }

    /** Turns every other module off instantly. */
    public static class Panic extends Module {
        Panic() { super("Panic", "Misc"); }
        @Override public void onEnable() {
            for (Module m : Modules.ALL) if (m != this && m.enabled) { m.enabled = false; m.onDisable(); }
            enabled = false;
        }
    }

    // ======================= HUD =======================
    public static class HUD extends Module {
        final Setting coords = bool("Coordinates", true), nether = bool("Nether coords", true), dir = bool("Direction", true),
                fps = bool("FPS", true), ping = bool("Ping", true), spd = bool("Speed (b/s)", true),
                totems = bool("Totem count", true), clock = bool("Clock", false);

        HUD() { super("HUD", "HUD"); enabled = true; }

        @Override public void onRender(GuiGraphics g, float pt) {
            List<String> lines = new ArrayList<>();
            var p = mc.player;
            if (coords.on()) lines.add(String.format("XYZ %.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()));
            if (nether.on()) {
                String d = Modules.dim();
                if (d.equals("Overworld")) lines.add(String.format("Nether %.0f %.0f", p.getX() / 8, p.getZ() / 8));
                else if (d.equals("Nether")) lines.add(String.format("Overworld %.0f %.0f", p.getX() * 8, p.getZ() * 8));
            }
            if (dir.on()) lines.add("Facing " + p.getDirection().getName());
            if (fps.on()) lines.add("FPS " + mc.getFps());
            if (ping.on()) {
                var info = p.connection.getPlayerInfo(p.getUUID());
                lines.add("Ping " + (info == null ? 0 : info.getLatency()) + "ms");
            }
            if (spd.on()) lines.add(String.format("Speed %.1f b/s", Math.hypot(p.getX() - p.xo, p.getZ() - p.zo) * 20));
            if (totems.on()) {
                int n = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? p.getOffhandItem().getCount() : 0;
                for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) n += p.getInventory().getItem(i).getCount();
                lines.add("Totems " + n);
            }
            if (clock.on()) lines.add(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            int y = 16;
            for (String l : lines) { g.drawString(mc.font, l, 4, y, 0xFFFFFFFF); y += 10; }
        }
    }

    /** 2D minimap of nearby entities. Up = the way you're facing. */
    public static class Radar extends Module {
        final Setting range = num("Range", 64, 16, 256);
        final Setting size = num("Size", 80, 40, 160);
        final Targets.Filter filter;

        Radar() {
            super("Radar", "HUD");
            filter = new Targets.Filter(this, true, true, false, false, false, false);
            filter.extras(this, false, false, false);
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            int s = (int) size.num(), x0 = 6, y0 = 100, half = s / 2, cx = x0 + half, cy = y0 + half;
            g.fill(x0, y0, x0 + s, y0 + s, 0x88000000);
            g.fill(x0, y0, x0 + s, y0 + 1, 0xFF5B8CFF); g.fill(x0, y0 + s - 1, x0 + s, y0 + s, 0xFF5B8CFF);
            g.fill(x0, y0, x0 + 1, y0 + s, 0xFF5B8CFF); g.fill(x0 + s - 1, y0, x0 + s, y0 + s, 0xFF5B8CFF);
            double yaw = Math.toRadians(mc.player.getViewYRot(pt));
            double sy = Math.sin(yaw), cyw = Math.cos(yaw);
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !filter.accepts(e)) continue;
                double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
                double xs = dx * (-cyw) + dz * (-sy);
                double ys = dx * (-sy) + dz * cyw;
                int px = cx + (int) (xs / range.num() * half), py = cy - (int) (ys / range.num() * half);
                if (px < x0 + 1 || px > x0 + s - 3 || py < y0 + 1 || py > y0 + s - 3) continue;
                g.fill(px, py, px + 3, py + 3, Targets.color(Targets.kind(e)));
            }
            g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        }
    }

    public static class TargetHUD extends Module {
        final Targets.Filter filter;
        TargetHUD() {
            super("TargetHUD", "HUD");
            filter = new Targets.Filter(this, true, true, false, false, false, false);
        }
        @Override public void onRender(GuiGraphics g, float pt) {
            LivingEntity best = null; double bd = 16;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !(e instanceof LivingEntity le) || !le.isAlive() || !filter.accepts(e)) continue;
                double d = mc.player.distanceTo(e);
                if (d < bd) { bd = d; best = le; }
            }
            if (best == null) return;
            int x = g.guiWidth() / 2 + 14, y = g.guiHeight() / 2 + 14;
            g.fill(x, y, x + 110, y + 30, 0xAA000000);
            g.drawString(mc.font, best.getName().getString(), x + 4, y + 4, 0xFFFFFFFF);
            float hp = Math.max(0f, Math.min(1f, best.getHealth() / best.getMaxHealth()));
            g.fill(x + 4, y + 16, x + 106, y + 22, 0xFF300000);
            g.fill(x + 4, y + 16, x + 4 + (int) (102 * hp), y + 22, 0xFF30FF30);
            g.drawString(mc.font, String.format("%.1f HP  %dm", best.getHealth(), (int) bd), x + 4, y + 22, 0xFFAAAAAA);
        }
    }

    public static class ArmorHUD extends Module {
        static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        ArmorHUD() { super("ArmorHUD", "HUD"); }
        @Override public void onRender(GuiGraphics g, float pt) {
            int x = g.guiWidth() / 2 - 91 - 4 * 18 - 6, y = g.guiHeight() - 20;
            for (int i = 0; i < 4; i++) {
                ItemStack s = mc.player.getItemBySlot(SLOTS[i]);
                if (s.isEmpty()) continue;
                g.renderItem(s, x + i * 18, y);
                g.renderItemDecorations(mc.font, s, x + i * 18, y);
                if (s.isDamageableItem()) {
                    int pct = 100 - s.getDamageValue() * 100 / Math.max(1, s.getMaxDamage());
                    g.drawString(mc.font, pct + "%", x + i * 18, y - 9, pct < 25 ? 0xFFFF5555 : 0xFFFFFFFF);
                }
            }
        }
    }
}
