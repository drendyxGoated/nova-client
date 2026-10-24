package dev.nova;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class BuildModules {
    private BuildModules() {}

    public static void register() {
        add(new BaseBuilder()); add(new Surround()); add(new Scaffold());
        add(new Eagle()); add(new Parkour());
        add(new AutoReplenish()); add(new AutoArmor()); add(new AutoDrop());
        add(new HoleESP()); add(new AutoCommand()); add(new AutoFarm());
    }
    private static void add(Module m) { Modules.ALL.add(m); }

    static final net.minecraft.core.Direction[] HORIZ = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /** True if stepping by d would put you over open air. */
    static boolean edge(Vec3 d) {
        return Module.mc.player.onGround()
                && Module.mc.level.noCollision(Module.mc.player, Module.mc.player.getBoundingBox().move(d.x, -0.5, d.z));
    }

    // ======================= BASE BUILDER =======================
    /** Auto-builds a built-in blueprint around you. (.litematic import comes later.) */
    public static class BaseBuilder extends Module {
        final Setting plan = mode("Blueprint", 0, "Bunker 5x5", "Panic room 3x3", "Platform 7x7", "Wall 9x4");
        final Setting material = mode("Block", 0, "Obsidian", "Cobblestone", "Any block");
        final Setting delay = num("Delay (ticks)", 2, 1, 10);
        final Setting look = bool("Look at blocks", true);
        List<BlockPos> targets = new ArrayList<>();
        int cd, stuck;

        BaseBuilder() { super("BaseBuilder", "World"); persistEnabled = false; }

        void halt(String why) {
            enabled = false;
            Modules.chat("BaseBuilder: " + why);
            Notifications.push("BaseBuilder: " + why, 0xFF9AE66E);
        }

        @Override public void onEnable() {
            if (mc.player == null) { enabled = false; return; }
            BlockPos o = mc.player.blockPosition();
            Direction f = mc.player.getDirection(), r = f.getClockWise();
            List<BlockPos> list = new ArrayList<>();
            switch (plan.mode()) {
                case 0 -> { for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -1; y <= 3; y++)
                    if (y == -1 || y == 3 || Math.abs(x) == 2 || Math.abs(z) == 2) list.add(o.offset(x, y, z)); }
                case 1 -> { for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 2; y++)
                    if (y == -1 || y == 2 || Math.abs(x) == 1 || Math.abs(z) == 1) if (!(x == 0 && z == 0 && y >= 0 && y <= 1)) list.add(o.offset(x, y, z)); }
                case 2 -> { for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) list.add(o.offset(x, -1, z)); }
                default -> { for (int w = -4; w <= 4; w++) for (int y = 0; y <= 3; y++) list.add(o.relative(f, 2).relative(r, w).above(y)); }
            }
            list.sort(Comparator.comparingInt(BlockPos::getY));
            targets = list; cd = 0; stuck = 0;
            Modules.chat("BaseBuilder: " + list.size() + " blocks queued. Hold the block in your hotbar.");
        }

        int slot() {
            return switch (material.mode()) {
                case 0 -> Place.hotbar(s -> s.is(Items.OBSIDIAN));
                case 1 -> Place.hotbar(s -> s.is(Items.COBBLESTONE));
                default -> Place.hotbar(Place::buildBlock);
            };
        }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;
            if (cd-- > 0) return;
            targets.removeIf(p -> !mc.level.getBlockState(p).canBeReplaced());
            if (targets.isEmpty()) { halt("done!"); return; }
            int slot = slot();
            if (slot < 0) { halt("no " + material.modeName().toLowerCase() + " in hotbar"); return; }

            Vec3 eye = mc.player.getEyePosition();
            for (BlockPos p : targets) {
                if (eye.distanceTo(Vec3.atCenterOf(p)) > 4.4 || !Place.canSupport(p)) continue;
                if (look.on()) {
                    float rem = Humanizer.aim(Targets.rotations(eye, Vec3.atCenterOf(p)), 0.6);
                    if (Humanizer.on() && rem > 25) return;
                }
                if (Place.withSlot(slot, () -> Place.block(p))) { cd = Humanizer.jitter((int) delay.num()) - 1; stuck = 0; return; }
            }
            if (++stuck > 60) halt("can't reach the rest, move closer or give it support");
        }
    }

    // ======================= PVP BLOCKS =======================
    public static class Surround extends Module {
        int cd;
        Surround() { super("Surround", "Combat"); }
        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            if (cd-- > 0) return;
            int slot = Place.hotbar(s -> s.is(Items.OBSIDIAN));
            if (slot < 0) return;
            BlockPos f = mc.player.blockPosition();
            for (Direction d : HORIZ) {
                BlockPos p = f.relative(d);
                if (mc.level.getBlockState(p).canBeReplaced() && Place.withSlot(slot, () -> Place.block(p))) {
                    cd = Humanizer.jitter(1);
                    return;
                }
            }
        }
    }

    public static class Scaffold extends Module {
        final Setting tower = bool("Tower (hold jump)", false);
        int cd;
        Scaffold() { super("Scaffold", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            BlockPos below = mc.player.blockPosition().below();
            if (mc.level.getBlockState(below).canBeReplaced()) {
                int slot = Place.hotbar(Place::buildBlock);
                if (slot >= 0) Place.withSlot(slot, () -> Place.block(below));
            }
            if (tower.on() && mc.options.keyJump.isDown() && !mc.player.onGround() && mc.player.getDeltaMovement().y < 0.1) {
                Vec3 c = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(c.x, 0.42, c.z);
            }
        }
    }

    public static class Eagle extends Module {
        Eagle() { super("Eagle", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            Vec3 d = MoreModules.moveVec(0.3);
            mc.options.keyShift.setDown(d != Vec3.ZERO && edge(d));
        }
        @Override public void onDisable() { mc.options.keyShift.setDown(false); }
    }

    public static class Parkour extends Module {
        Parkour() { super("Parkour", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            Vec3 d = MoreModules.moveVec(0.3);
            if (d != Vec3.ZERO && edge(d)) mc.player.jumpFromGround();
        }
    }

    // ======================= INVENTORY =======================
    public static class AutoReplenish extends Module {
        final Setting below = num("Refill below", 16, 2, 60);
        int cd;
        AutoReplenish() { super("AutoReplenish", "Player"); }
        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null || mc.screen != null || cd-- > 0) return;
            var inv = mc.player.getInventory();
            int cid = mc.player.inventoryMenu.containerId;
            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !s.isStackable() || s.getCount() > below.num() || s.getCount() >= s.getMaxStackSize()) continue;
                for (int j = 9; j < 36; j++) {
                    ItemStack o = inv.getItem(j);
                    if (o.isEmpty() || !ItemStack.isSameItemSameComponents(s, o)) continue;
                    mc.gameMode.handleInventoryMouseClick(cid, j, 0, ClickType.PICKUP, mc.player);
                    mc.gameMode.handleInventoryMouseClick(cid, 36 + i, 0, ClickType.PICKUP, mc.player);
                    mc.gameMode.handleInventoryMouseClick(cid, j, 0, ClickType.PICKUP, mc.player);
                    cd = Humanizer.jitter(6);
                    return;
                }
            }
        }
    }

    /** Equips armor from your inventory into empty armor slots. */
    public static class AutoArmor extends Module {
        int cd;
        AutoArmor() { super("AutoArmor", "Player"); }
        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null || mc.screen != null || cd-- > 0) return;
            var inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;
                var eq = s.get(DataComponents.EQUIPPABLE);
                if (eq == null) continue;
                EquipmentSlot es = eq.slot();
                if (es != EquipmentSlot.HEAD && es != EquipmentSlot.CHEST && es != EquipmentSlot.LEGS && es != EquipmentSlot.FEET) continue;
                if (!mc.player.getItemBySlot(es).isEmpty()) continue;
                int slot = i < 9 ? 36 + i : i;
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, 0, ClickType.QUICK_MOVE, mc.player);
                cd = Humanizer.jitter(5);
                return;
            }
        }
    }

    public static class AutoDrop extends Module {
        final Setting rotten = bool("Rotten food", true);
        final Setting stone = bool("Cobble / stone types", false);
        final Setting dirt = bool("Dirt / gravel / sand", false);
        int cd;
        AutoDrop() { super("AutoDrop", "Player"); }

        boolean junk(ItemStack s) {
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (rotten.on() && (id.equals("rotten_flesh") || id.equals("spider_eye") || id.equals("poisonous_potato"))) return true;
            if (stone.on() && (id.equals("cobblestone") || id.equals("andesite") || id.equals("diorite") || id.equals("granite") || id.equals("cobbled_deepslate"))) return true;
            return dirt.on() && (id.equals("dirt") || id.equals("gravel") || id.equals("sand") || id.equals("red_sand"));
        }

        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null || mc.screen != null || cd-- > 0) return;
            var inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty() || !junk(s)) continue;
                int slot = i < 9 ? 36 + i : i;
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, 1, ClickType.THROW, mc.player);
                cd = Humanizer.jitter(4);
                return;
            }
        }
    }

    // ======================= WORLD =======================
    /** Safe PvP holes: open spot with bedrock/obsidian on all four sides. Green = all bedrock. */
    public static class HoleESP extends Module {
        record Hole(BlockPos pos, boolean bedrock) {}
        final Setting radius = num("Radius", 8, 3, 16);
        List<Hole> holes = new ArrayList<>();
        int t;
        HoleESP() { super("HoleESP", "Render"); }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || t++ % 20 != 0) return;
            List<Hole> out = new ArrayList<>();
            int r = (int) radius.num();
            BlockPos o = mc.player.blockPosition();
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int y = -3; y <= 3; y++) {
                BlockPos p = o.offset(x, y, z);
                if (!mc.level.getBlockState(p).isAir() || !mc.level.getBlockState(p.above()).isAir() || mc.level.getBlockState(p.below()).isAir()) continue;
                boolean ok = true, allBedrock = true;
                for (Direction d : HORIZ) {
                    Block b = mc.level.getBlockState(p.relative(d)).getBlock();
                    if (b == Blocks.BEDROCK) continue;
                    if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) { allBedrock = false; continue; }
                    ok = false; break;
                }
                if (ok) out.add(new Hole(p, allBedrock));
            }
            holes = out;
        }

        @Override public void onRender(GuiGraphics g, float pt) {
            for (Hole h : holes) {
                BlockPos p = h.pos();
                double[] r = Draw.rect(new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 0.1, p.getZ() + 1), g, pt);
                if (r != null) Draw.outline(g, r, h.bedrock() ? 0xFF55FF55 : 0xFFFFDD33);
            }
        }
    }

    public static class AutoCommand extends Module {
        final Setting cmd = mode("Command", 0, "/rtp", "/home", "/spawn", "/afk");
        final Setting interval = num("Interval (sec)", 120, 5, 3600);
        int t, nextAt;
        AutoCommand() { super("AutoCommand", "Misc"); persistEnabled = false; }
        @Override public void onEnable() { nextAt = (int) (interval.num() * 20); t = nextAt - 20; }
        @Override public void onTick() {
            if (mc.player == null || mc.player.connection == null || ++t < nextAt) return;
            t = 0;
            nextAt = (int) (interval.num() * 20 * (Humanizer.on() ? 0.85 + Humanizer.R.nextDouble() * 0.3 : 1));
            mc.player.connection.sendCommand(cmd.modeName().substring(1));
        }
    }

    /** Stand in a crop field: harvests fully grown crops in reach and replants from your hotbar. */
    public static class AutoFarm extends Module {
        final Setting range = num("Range", 4, 2, 5);
        final Setting replant = bool("Replant", true);
        int cd;
        AutoFarm() { super("AutoFarm", "World"); }

        @Override public void onTick() {
            if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null || cd-- > 0) return;
            BlockPos o = mc.player.blockPosition();
            Vec3 eye = mc.player.getEyePosition();
            int r = (int) range.num();
            BlockPos harvest = null, plantAt = null;
            for (int x = -r; x <= r && harvest == null; x++) for (int z = -r; z <= r && harvest == null; z++) for (int y = -1; y <= 1; y++) {
                BlockPos p = o.offset(x, y, z);
                if (eye.distanceTo(Vec3.atCenterOf(p)) > 4.5) continue;
                BlockState st = mc.level.getBlockState(p);
                if (st.getBlock() instanceof CropBlock cb && cb.isMaxAge(st)) { harvest = p; break; }
                if (replant.on() && plantAt == null && st.isAir() && mc.level.getBlockState(p.below()).is(Blocks.FARMLAND)) plantAt = p;
            }
            if (harvest != null) {
                float rem = Humanizer.aim(Targets.rotations(eye, Vec3.atCenterOf(harvest)), 0.6);
                if (Humanizer.on() && rem > 25) return;
                mc.gameMode.startDestroyBlock(harvest, Direction.UP);
                mc.player.swing(InteractionHand.MAIN_HAND);
                cd = Humanizer.jitter(3);
                return;
            }
            if (plantAt != null) {
                int slot = Place.hotbar(s -> s.is(Items.WHEAT_SEEDS) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT_SEEDS));
                if (slot < 0) return;
                final BlockPos support = plantAt.below();
                float rem = Humanizer.aim(Targets.rotations(eye, Vec3.atCenterOf(plantAt)), 0.6);
                if (Humanizer.on() && rem > 25) return;
                Place.withSlot(slot, () -> Place.click(support, Direction.UP));
                cd = Humanizer.jitter(3);
            }
        }
    }
}
