package dev.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Screen-space ESP helpers: projects world points to the HUD layer (works best in first person). */
public final class Draw {
    private Draw() {}

    public static double[] project(Vec3 p, GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.player.getEyePosition(pt);
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double pitch = Math.toRadians(mc.player.getViewXRot(pt));
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);

        double dx = p.x - cam.x, dy = p.y - cam.y, dz = p.z - cam.z;
        double fwd = dx * (-sy * cp) + dy * (-sp) + dz * (cy * cp);
        if (fwd < 0.05) return null;
        double right = dx * (-cy) + dz * (-sy);
        double up = dx * (-sy * sp) + dy * cp + dz * (cy * sp);

        double fov = Math.toRadians(mc.options.fov().get());
        double scale = (g.guiHeight() / 2.0) / Math.tan(fov / 2.0);
        return new double[]{g.guiWidth() / 2.0 + right / fwd * scale, g.guiHeight() / 2.0 - up / fwd * scale};
    }

    /** 2D bounding rectangle of a projected AABB, or null if any corner is behind the camera. */
    public static double[] rect(AABB b, GuiGraphics g, float pt) {
        double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
        for (int i = 0; i < 8; i++) {
            Vec3 c = new Vec3((i & 1) == 0 ? b.minX : b.maxX, (i & 2) == 0 ? b.minY : b.maxY, (i & 4) == 0 ? b.minZ : b.maxZ);
            double[] s = project(c, g, pt);
            if (s == null) return null;
            minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
            minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    public static void outline(GuiGraphics g, double[] r, int color) {
        int x1 = (int) r[0], y1 = (int) r[1], x2 = (int) r[2], y2 = (int) r[3];
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    public static void line(GuiGraphics g, double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        int steps = (int) Math.min(2000, Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = (int) (x1 + dx * i / steps), y = (int) (y1 + dy * i / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
