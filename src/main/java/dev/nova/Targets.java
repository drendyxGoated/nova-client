package dev.nova;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Classifies entities so every module can filter the same way (players, hostiles, animals, ...). */
public final class Targets {
    private Targets() {}

    public enum Kind { PLAYER, HOSTILE, ANIMAL, VILLAGER, WATER, AMBIENT, ITEM, CRYSTAL, VEHICLE, PROJECTILE, OTHER }

    public static Kind kind(Entity e) {
        if (e instanceof Player) return Kind.PLAYER;
        if (e instanceof ItemEntity) return Kind.ITEM;
        String id = e.getType().toShortString();
        if (id.equals("end_crystal")) return Kind.CRYSTAL;
        if (id.contains("boat") || id.contains("raft") || id.contains("minecart")) return Kind.VEHICLE;
        if (e instanceof Projectile) return Kind.PROJECTILE;
        if (id.equals("villager") || id.equals("wandering_trader")) return Kind.VILLAGER;
        if (e instanceof Enemy) return Kind.HOSTILE;
        if (e instanceof LivingEntity) {
            String cat = e.getType().getCategory().getName();
            if (cat.equals("monster")) return Kind.HOSTILE;
            if (cat.equals("ambient")) return Kind.AMBIENT;
            if (cat.contains("water") || cat.equals("axolotls")) return Kind.WATER;
            if (cat.equals("creature")) return Kind.ANIMAL;
        }
        return Kind.OTHER;
    }

    public static int color(Kind k) {
        return switch (k) {
            case PLAYER -> 0xFFFF3030;
            case HOSTILE -> 0xFFFF8800;
            case ANIMAL -> 0xFF55FF55;
            case VILLAGER -> 0xFFAA66FF;
            case WATER -> 0xFF3399FF;
            case AMBIENT -> 0xFFAAAAAA;
            case ITEM -> 0xFFFFFF55;
            case CRYSTAL -> 0xFFFF55FF;
            case VEHICLE -> 0xFF999999;
            case PROJECTILE -> 0xFFFFFFFF;
            default -> 0xFFCCCCCC;
        };
    }

    /** Friends are cyan everywhere. */
    public static int colorOf(Entity e) { return Friends.is(e) ? 0xFF55FFFF : color(kind(e)); }

    /** {yaw, pitch} needed to look from eye at target. */
    public static float[] rotations(Vec3 eye, Vec3 t) {
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new float[]{yaw, pitch};
    }

    /** A bundle of on/off settings (Players, Hostiles, Animals, ...) a module can add to itself. */
    public static class Filter {
        final Setting players, hostiles, animals, villagers, water, ambient;
        Setting items, crystals, vehicles;

        Filter(Module m, boolean pl, boolean ho, boolean an, boolean vi, boolean wa, boolean am) {
            players = m.bool("Players", pl);
            hostiles = m.bool("Hostiles", ho);
            animals = m.bool("Animals", an);
            villagers = m.bool("Villagers", vi);
            water = m.bool("Water mobs", wa);
            ambient = m.bool("Ambient (bats)", am);
        }

        void extras(Module m, boolean it, boolean cr, boolean ve) {
            items = m.bool("Items", it);
            crystals = m.bool("Crystals", cr);
            vehicles = m.bool("Boats/Carts", ve);
        }

        boolean accepts(Entity e) {
            return switch (kind(e)) {
                case PLAYER -> players.on();
                case HOSTILE -> hostiles.on();
                case ANIMAL -> animals.on();
                case VILLAGER -> villagers.on();
                case WATER -> water.on();
                case AMBIENT -> ambient.on();
                case ITEM -> items != null && items.on();
                case CRYSTAL -> crystals != null && crystals.on();
                case VEHICLE -> vehicles != null && vehicles.on();
                default -> false;
            };
        }
    }
}
