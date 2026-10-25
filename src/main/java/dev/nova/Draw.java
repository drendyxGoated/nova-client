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
        int w = g.guiWidth(), h = g.guiHeight();
        if (r[2] < 0 || r[0] > w || r[3] < 0 || r[1] > h) return;           // fully offscreen
        if (r[2] - r[0] > 3000 || r[3] - r[1] > 3000) return;                // absurdly big (camera inside it)
        int x1 = (int) Math.max(-4, r[0]), y1 = (int) Math.max(-4, r[1]);
        int x2 = (int) Math.min(w + 4, r[2]), y2 = (int) Math.min(h + 4, r[3]);
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    /** Line clipped to the screen (Liang-Barsky), drawn with a 2px stride and a hard cap on fills. */
    public static void line(GuiGraphics g, double x1, double y1, double x2, double y2, int color) {
        int w = g.guiWidth(), h = g.guiHeight();
        double dx = x2 - x1, dy = y2 - y1, t0 = 0, t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x1, w - x1, y1, h - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) { if (q[i] < 0) return; }
            else {
                double r = q[i] / p[i];
                if (p[i] < 0) { if (r > t1) return; if (r > t0) t0 = r; }
                else { if (r < t0) return; if (r < t1) t1 = r; }
            }
        }
        double ax = x1 + t0 * dx, ay = y1 + t0 * dy, bx = x1 + t1 * dx, by = y1 + t1 * dy;
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 1) return;
        int steps = (int) Math.min(len / 2, 400);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int x = (int) (ax + (bx - ax) * t), y = (int) (ay + (by - ay) * t);
            g.fill(x, y, x + 2, y + 2, color);
        }
    }
}
