package dev.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Block placement helpers shared by Scaffold, Surround, BaseBuilder, AutoFarm. */
public final class Place {
    private Place() {}
    private static Minecraft mc() { return Minecraft.getInstance(); }

    static boolean solid(BlockPos p) {
        BlockState s = mc().level.getBlockState(p);
        return !s.isAir() && s.getFluidState().isEmpty();
    }

    /** Right-click `face` of the block at `support`. */
    static boolean click(BlockPos support, Direction face) {
        var m = mc();
        Vec3 hit = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (m.player.getEyePosition().distanceTo(hit) > 4.6) return false;
        m.gameMode.useItemOn(m.player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, support, false));
        m.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** Place whatever is in hand at pos by clicking a solid neighbor. */
    static boolean block(BlockPos pos) {
        if (!mc().level.getBlockState(pos).canBeReplaced()) return false;
        for (Direction d : Direction.values()) {
            BlockPos nb = pos.relative(d);
            if (solid(nb) && click(nb, d.getOpposite())) return true;
        }
        return false;
    }

    static boolean canSupport(BlockPos pos) {
        for (Direction d : Direction.values()) if (solid(pos.relative(d))) return true;
        return false;
    }

    static int hotbar(Predicate<ItemStack> p) {
        var inv = mc().player.getInventory();
        for (int i = 0; i < 9; i++) if (!inv.getItem(i).isEmpty() && p.test(inv.getItem(i))) return i;
        return -1;
    }

    /** Select a hotbar slot, run the action, switch back. */
    static boolean withSlot(int slot, BooleanSupplier action) {
        var inv = mc().player.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        boolean r = action.getAsBoolean();
        inv.setSelectedSlot(prev);
        return r;
    }

    /** A normal full-ish building block (not torches, slabs, doors, sand...). */
    static boolean buildBlock(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem)) return false;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        String[] bad = {"torch", "sapling", "slab", "stairs", "sand", "gravel", "button", "pressure", "sign", "door", "bed",
                "tnt", "chest", "shulker", "barrel", "fence", "wall", "carpet", "rail", "lantern", "flower", "leaves", "vine", "pane", "trapdoor"};
        for (String b : bad) if (id.contains(b)) return false;
        return true;
    }
}
