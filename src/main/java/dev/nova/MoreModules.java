package dev.nova;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class MoreModules {
    private MoreModules() {}

    public static void register() {
        Modules.ALL.add(new TriggerBot());
        Modules.ALL.add(new AutoTotem());
        Modules.ALL.add(new AutoEat());
        Modules.ALL.add(new Reach());
        Modules.ALL.add(new Flight());
        Modules.ALL.add(new Speed());
        Modules.ALL.add(new Jesus());
        Modules.ALL.add(new Spider());
        Modules.ALL.add(new NoFall());
        Modules.ALL.add(new Step());
        Modules.ALL.add(new AutoWalk());
        Modules.ALL.add(new AntiAFK());
        Modules.ALL.add(new AutoTool());
        Modules.ALL.add(new Zoom());
    }

    static Vec3 moveVec(double speed) {
        var o = Module.mc.options;
        double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        if (f == 0 && s == 0) return Vec3.ZERO;
        double len = Math.sqrt(f * f + s * s); f /= len; s /= len;
        double y = Math.toRadians(Module.mc.player.getYRot());
        return new Vec3((-Math.sin(y) * f + Math.cos(y) * s) * speed, 0, (Math.cos(y) * f + Math.sin(y) * s) * speed);
    }

    // ================= COMBAT =================
    public static class TriggerBot extends Module {
        final Setting players = bool("Players", true);
        final Setting hostiles = bool("Hostiles", true);
        TriggerBot() { super("TriggerBot", "Combat"); }
        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null || mc.screen != null) return;
            if (!(mc.hitResult instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity le) || !le.isAlive()) return;
            boolean ok = (le instanceof Player && players.on()) || (le instanceof Enemy && hostiles.on());
            if (!ok || Friends.is(le) || mc.player.getAttackStrengthScale(0f) < 0.95f) return;
            mc.gameMode.attack(mc.player, le);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static class AutoTotem extends Module {
        final Setting delay = num("Delay (ticks)", 2, 0, 10);
        int cd;
        AutoTotem() { super("AutoTotem", "Combat"); }
        @Override public void onTick() {
            if (mc.player == null || mc.gameMode == null) return;
            if (cd > 0) { cd--; return; }
            if (mc.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
            var inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                    int slot = i < 9 ? 36 + i : i; // inventory menu slot ids
                    mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, 40, ClickType.SWAP, mc.player);
                    cd = (int) delay.num() + (Humanizer.on() ? Humanizer.R.nextInt(3) : 0);
                    return;
                }
            }
        }
    }

    public static class AutoEat extends Module {
        final Setting threshold = num("Eat below", 14, 1, 19);
        int prev = -1; boolean eating;
        AutoEat() { super("AutoEat", "Combat"); }

        int findFood() {
            var inv = mc.player.getInventory();
            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.has(DataComponents.FOOD)) continue;
                if (s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO)
                        || s.is(Items.PUFFERFISH) || s.is(Items.CHORUS_FRUIT)) continue;
                return i;
            }
            return -1;
        }
        void stop() {
            mc.options.keyUse.setDown(false);
            if (prev >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prev);
            prev = -1; eating = false;
        }
        @Override public void onDisable() { if (eating) stop(); }
        @Override public void onTick() {
            if (mc.player == null || mc.screen != null) return;
            int food = mc.player.getFoodData().getFoodLevel();
            if (food <= threshold.num()) {
                if (!eating) {
                    int slot = findFood();
                    if (slot < 0) return;
                    prev = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(slot);
                    eating = true;
                }
                mc.options.keyUse.setDown(true);
            } else if (eating) stop();
        }
    }

    public static class Reach extends Module {
        final Setting entity = num("Entity reach", 3.5, 3.0, 6.0);
        final Setting block = num("Block reach", 5.0, 4.5, 8.0);
        Reach() { super("Reach", "Combat"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            mc.player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(entity.num());
            mc.player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(block.num());
        }
        @Override public void onDisable() {
            if (mc.player == null) return;
            mc.player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(3.0);
            mc.player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(4.5);
        }
    }

    // ================= MOVEMENT =================
    public static class Flight extends Module {
        final Setting speed = num("Speed", 0.6, 0.1, 3.0);
        final Setting antiKick = bool("AntiKick", true);
        int t;
        Flight() { super("Flight", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            Vec3 h = moveVec(speed.num());
            double y = 0;
            if (mc.options.keyJump.isDown()) y = speed.num() * 0.5;
            else if (mc.options.keyShift.isDown()) y = -speed.num() * 0.5;
            if (antiKick.on() && y == 0 && t++ % 20 == 0) y = -0.04;
            mc.player.setDeltaMovement(h.x, y, h.z);
            mc.player.fallDistance = 0;
        }
    }

    public static class Speed extends Module {
        final Setting speed = num("Speed", 0.35, 0.1, 1.5);
        Speed() { super("Speed", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            Vec3 h = moveVec(speed.num());
            if (h == Vec3.ZERO) return;
            Vec3 c = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(h.x, c.y, h.z);
        }
    }

    public static class Jesus extends Module {
        Jesus() { super("Jesus", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null || mc.player.isShiftKeyDown()) return;
            if (mc.player.isInWater()) {
                Vec3 c = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(c.x, 0.1, c.z);
            }
        }
    }

    public static class Spider extends Module {
        Spider() { super("Spider", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null) return;
            if (mc.player.horizontalCollision) {
                Vec3 c = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(c.x, 0.2, c.z);
            }
        }
    }

    public static class NoFall extends Module {
        NoFall() { super("NoFall", "Movement"); }
        @Override public void onTick() {
            if (mc.player == null || mc.player.connection == null) return;
            if (mc.player.fallDistance > 2 && !mc.player.getAbilities().flying)
                mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
        }
    }

    public static class Step extends Module {
        final Setting height = num("Height", 1.0, 0.6, 2.5);
        Step() { super("Step", "Movement"); }
        @Override public void onTick() {
            if (mc.player != null) mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(height.num());
        }
        @Override public void onDisable() {
            if (mc.player != null) mc.player.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6);
        }
    }

    public static class AutoWalk extends Module {
        AutoWalk() { super("AutoWalk", "Movement"); }
        @Override public void onTick() { mc.options.keyUp.setDown(true); }
        @Override public void onDisable() { mc.options.keyUp.setDown(false); }
    }

    // ================= PLAYER =================
    /** Handy for the 2b2t queue / staying online. */
    public static class AntiAFK extends Module {
        final Setting interval = num("Interval (s)", 15, 5, 60);
        int t;
        AntiAFK() { super("AntiAFK", "Player"); }
        @Override public void onTick() {
            if (mc.player == null || ++t < interval.num() * 20) return;
            t = -Humanizer.R.nextInt(200);
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.player.setYRot(mc.player.getYRot() + (new Random().nextBoolean() ? 5 : -5));
            if (mc.player.onGround()) mc.player.jumpFromGround();
        }
    }

    public static class AutoTool extends Module {
        AutoTool() { super("AutoTool", "Player"); }
        @Override public void onTick() {
            if (mc.player == null || mc.level == null || !mc.options.keyAttack.isDown()) return;
            if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
            BlockState st = mc.level.getBlockState(bhr.getBlockPos());
            var inv = mc.player.getInventory();
            int best = inv.getSelectedSlot();
            float bestSpeed = inv.getItem(best).getDestroySpeed(st);
            for (int i = 0; i < 9; i++) {
                float s = inv.getItem(i).getDestroySpeed(st);
                if (s > bestSpeed) { bestSpeed = s; best = i; }
            }
            if (best != inv.getSelectedSlot()) inv.setSelectedSlot(best);
        }
    }

    // ================= RENDER =================
    public static class Zoom extends Module {
        final Setting fov = num("FOV", 30, 10, 60);
        int old = -1;
        Zoom() { super("Zoom", "Render"); }
        @Override public void onTick() {
            if (old < 0) old = mc.options.fov().get();
            mc.options.fov().set((int) fov.num());
        }
        @Override public void onDisable() {
            if (old > 0) mc.options.fov().set(old);
            old = -1;
        }
    }
}
