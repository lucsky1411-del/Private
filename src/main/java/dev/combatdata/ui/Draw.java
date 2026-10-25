package dev.combatdata.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Fill-only primitives (no textures, shaders or OpenGL) to stay compatible with VulkanMod. */
public final class Draw {
    private Draw() {
    }

    public static void round(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(Theme.radius(r), Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    private static final java.util.Map<String, net.minecraft.resources.Identifier> SPRITES = new java.util.HashMap<>();

    /**
     * A sprite from the game's own GUI atlas (widget/button, widget/checkbox, ...), nine-sliced to any size. These are
     * vanilla textures drawn with the vanilla GUI pipeline, so they look exactly like Java menus and stay compatible
     * with renderers such as VulkanMod.
     */
    public static void sprite(GuiGraphics g, String name, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        net.minecraft.resources.Identifier id = SPRITES.computeIfAbsent(name, net.minecraft.resources.Identifier::withDefaultNamespace);
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, w, h);
    }

    /** A Java button: normal, highlighted (hover or selected) or disabled. */
    public static void javaButton(GuiGraphics g, int x, int y, int w, int h, boolean highlight, boolean enabled) {
        sprite(g, !enabled ? "widget/button_disabled" : highlight ? "widget/button_highlighted" : "widget/button", x, y, w, h);
    }

    /** NoRisk look: flat translucent fill with a 1px outline; highlight brightens the outline. */
    public static void flat(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        if (w > 2 && h > 2) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void roundBordered(GuiGraphics g, int x, int y, int w, int h, int r, int border, int fill) {
        // Square styles draw the outline as four edges, so a see-through fill does not show the border color behind it.
        if (Theme.radius(r) == 0) {
            flat(g, x, y, w, h, fill, border);
            return;
        }
        round(g, x, y, w, h, r, border);
        round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    public static void circle(GuiGraphics g, int x, int y, int d, int color) {
        double r = d / 2.0;
        for (int row = 0; row < d; row++) {
            double cy = row + 0.5 - r;
            double hw = Math.sqrt(Math.max(0, r * r - cy * cy));
            int x1 = (int) Math.round(r - hw), x2 = (int) Math.round(r + hw);
            if (x2 > x1) g.fill(x + x1, y + row, x + x2, y + row + 1, color);
        }
    }

    public static void dashH(GuiGraphics g, int x1, int x2, int y, int dash, int gap, int color) {
        for (int x = x1; x < x2; x += dash + gap) g.fill(x, y, Math.min(x + dash, x2), y + 1, color);
    }

    /** 1px line from fills: a vertical segment per x column so steep parts don't break up. */
    public static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (x2 < x1) {
            int tx = x1, ty = y1;
            x1 = x2;
            y1 = y2;
            x2 = tx;
            y2 = ty;
        }
        if (x1 == x2) {
            g.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
            return;
        }
        int prev = y1;
        for (int x = x1; x <= x2; x++) {
            int y = (int) Math.round(y1 + (double) (y2 - y1) * (x - x1) / (x2 - x1));
            g.fill(x, Math.min(prev, y), x + 1, Math.max(prev, y) + 1, color);
            prev = y;
        }
    }

    public static void arrow(GuiGraphics g, int cx, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int ry = up ? y + i : y - i;
            g.fill(cx - i, ry, cx + i + 1, ry + 1, color);
        }
    }

    public static String ellipsize(Font font, String s, int maxW) {
        if (s == null) return "";
        if (font.width(s) <= maxW) return s;
        String dots = "...";
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end) + dots) > maxW) end--;
        return s.substring(0, end) + dots;
    }
}
