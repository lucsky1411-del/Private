package dev.combatdata.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.Consumer;

/** Immediate mode: widgets draw and register click areas each frame. Layer 1 is the modal; while it is open, layer 0 ignores the mouse. */
public final class Ui {
    public enum Kind { PRIMARY, SECONDARY, GHOST, DANGER }

    public record Target(int x, int y, int w, int h, int layer, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public static final int CTRL_H = 16;

    public final GuiGraphics g;
    public final Font font;
    public final int mx, my;
    private final List<Target> targets;
    private final int activeLayer;
    public int layer;
    /** Menu search; null outside the menu. */
    Find find;
    private int cx1 = Integer.MIN_VALUE, cy1 = Integer.MIN_VALUE, cx2 = Integer.MAX_VALUE, cy2 = Integer.MAX_VALUE;

    public Ui(GuiGraphics g, Font font, int mx, int my, List<Target> targets, int activeLayer) {
        this.g = g;
        this.font = font;
        this.mx = mx;
        this.my = my;
        this.targets = targets;
        this.activeLayer = activeLayer;
    }

    /** Reports a row to the menu search: indexes it, or highlights it when a result points here. */
    public void mark(String label, String hint, int x, int y, int w, int h) {
        if (find != null) find.mark(this, label, hint, x, y, w, h);
    }

    public void clip(int x, int y, int w, int h) {
        cx1 = x;
        cy1 = y;
        cx2 = x + w;
        cy2 = y + h;
    }

    public void unclip() {
        cx1 = cy1 = Integer.MIN_VALUE;
        cx2 = cy2 = Integer.MAX_VALUE;
    }

    private boolean inClip(int px, int py) {
        return px >= cx1 && px < cx2 && py >= cy1 && py < cy2;
    }


    public static final int INFO = 9;
    private String tip;

    /** The tooltip is drawn at the end of the frame (drawTip) so cards and charts don't cover it. */
    public void info(int x, int y, String text) {
        boolean hov = hover(x - 1, y - 1, INFO + 2, INFO + 2);
        Draw.circle(g, x, y, INFO, hov ? Theme.ACCENT : Theme.RAISED_HOVER);
        drawI(x, y, hov);
        if (hov) tip = text;
    }

    /** A tooltip for any hovered area, drawn at the end of the frame like the (i) tips. */
    public void tooltip(String text) {
        tip = text;
    }

    private void drawI(int x, int y, boolean hov) {
        // "i" built from two fills: the Minecraft font renders it offset and too thin for a 9x9 circle.
        int c = hov ? Theme.ON_ACCENT : Theme.TEXT;
        g.fill(x + 4, y + 2, x + 5, y + 3, c);
        g.fill(x + 4, y + 4, x + 5, y + 7, c);
    }

    public void drawTip(int screenW, int screenH) {
        if (tip == null) return;
        g.nextStratum();
        unclip();
        int maxW = Math.min(240, screenW - 16);
        List<String> lines = wrap(tip, maxW - 12);
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        w += 12;
        int h = lines.size() * 10 + 8;
        int x = Math.min(mx + 10, screenW - w - 4), y = my + 12;
        if (y + h > screenH - 4) y = Math.max(4, my - h - 6);
        x = Math.max(4, x);
        Draw.roundBordered(g, x, y, w, h, Theme.R_PANEL, Theme.ACCENT, Theme.WINDOW);
        int ty = y + 5;
        for (String l : lines) {
            text(l, x + 6, ty, Theme.TEXT);
            ty += 10;
        }
    }

    List<String> wrap(String s, int maxW) {
        List<String> out = new java.util.ArrayList<>();
        for (String para : s.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = cur.isEmpty() ? word : cur + " " + word;
                if (font.width(next) > maxW && !cur.isEmpty()) {
                    out.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(next);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    public boolean hover(int x, int y, int w, int h) {
        return layer >= activeLayer && inClip(mx, my) && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void click(int x, int y, int w, int h, Runnable action) {
        int x1 = Math.max(x, cx1), y1 = Math.max(y, cy1), x2 = Math.min(x + w, cx2), y2 = Math.min(y + h, cy2);
        if (x2 > x1 && y2 > y1) targets.add(new Target(x1, y1, x2 - x1, y2 - y1, layer, action));
    }

    public void text(String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, Theme.shadow);
    }

    public void textRight(String s, int right, int y, int color) {
        g.drawString(font, s, right - font.width(s), y, color, Theme.shadow);
    }

    public void textCenter(String s, int cx, int y, int color) {
        g.drawString(font, s, cx - font.width(s) / 2, y, color, Theme.shadow);
    }

    public static int textY(int y, int h) {
        return y + (h - 8) / 2;
    }

    public int buttonWidth(String label) {
        return font.width(label) + 16;
    }

    public void button(int x, int y, int w, int h, String label, Kind kind, boolean enabled, Runnable onClick) {
        boolean hov = enabled && hover(x, y, w, h);
        int bg, fg;
        switch (kind) {
            case PRIMARY -> {
                bg = !enabled ? Theme.RAISED : hov ? Theme.ACCENT_HOVER : Theme.ACCENT;
                fg = enabled ? Theme.ON_ACCENT : Theme.DIM;
            }
            case DANGER -> {
                bg = hov ? Theme.RAISED_HOVER : Theme.RAISED;
                fg = enabled ? Theme.RED : Theme.DIM;
            }
            case GHOST -> {
                bg = hov ? Theme.RAISED : 0;
                fg = enabled ? (hov ? Theme.TEXT_STRONG : Theme.TEXT) : Theme.DIM;
            }
            default -> {
                bg = hov ? Theme.RAISED_HOVER : Theme.RAISED;
                fg = enabled ? Theme.TEXT : Theme.DIM;
            }
        }
        if (Theme.java()) {
            // Real Java buttons; the main action gets yellow text instead of a colored face, like vanilla.
            if (kind != Kind.GHOST || hov) Draw.javaButton(g, x, y, w, h, hov, enabled);
            fg = !enabled ? 0xFFA0A0A0 : kind == Kind.DANGER ? Theme.RED : kind == Kind.PRIMARY ? 0xFFFFFF55 : 0xFFFFFFFF;
        } else if (Theme.norisk()) {
            int fill = kind == Kind.PRIMARY && enabled ? Theme.alpha(Theme.ACCENT, hov ? 0xF0 : 0xC8) : kind == Kind.GHOST && !hov ? 0 : Theme.RAISED;
            int border = hov ? Theme.TEXT : kind == Kind.PRIMARY && enabled ? Theme.ACCENT_HOVER : Theme.BORDER;
            if (fill != 0 || hov) Draw.flat(g, x, y, w, h, fill, border);
            fg = !enabled ? Theme.DIM : kind == Kind.DANGER ? Theme.RED : 0xFFFFFFFF;
        } else if (bg != 0) Draw.round(g, x, y, w, h, Theme.R_CTRL, bg);
        textCenter(Draw.ellipsize(font, label, w - 6), x + w / 2, textY(y, h), fg);
        if (enabled) click(x, y, w, h, onClick);
    }

    public void toggle(int x, int y, boolean on, Consumer<Boolean> set) {
        int w = 20, h = 11;
        boolean hov = hover(x, y, w, h);
        if (Theme.java()) {
            // The game's checkbox, right-aligned in the switch area.
            int s = 12, cx = x + w - s, cy = y + (h - s) / 2;
            Draw.sprite(g, on ? (hov ? "widget/checkbox_selected_highlighted" : "widget/checkbox_selected")
                    : (hov ? "widget/checkbox_highlighted" : "widget/checkbox"), cx, cy, s, s);
        } else if (Theme.norisk()) {
            Draw.flat(g, x, y, w, h, on ? Theme.alpha(Theme.ACCENT, 0xD0) : Theme.INSET, hov ? Theme.TEXT : Theme.BORDER);
            int kx = on ? x + w - 9 : x + 2;
            g.fill(kx, y + 2, kx + 7, y + h - 2, on ? 0xFFFFFFFF : Theme.MUTED);
        } else {
            Draw.round(g, x, y, w, h, 5, on ? (hov ? Theme.ACCENT_HOVER : Theme.ACCENT) : (hov ? Theme.CTRL_HOVER : Theme.SWITCH_OFF));
            Draw.circle(g, on ? x + w - 9 : x + 2, y + 2, 7, Theme.KNOB);
        }
        click(x - 2, y - 2, w + 4, h + 4, () -> set.accept(!on));
    }

    public void stepper(int x, int y, int w, String value, Runnable minus, Runnable plus) {
        int h = CTRL_H, b = 14;
        boolean hm = hover(x, y, b, h), hp = hover(x + w - b, y, b, h);
        if (Theme.java()) {
            Draw.sprite(g, "widget/text_field", x + b, y, w - 2 * b, h);
            Draw.javaButton(g, x, y, b, h, hm, true);
            Draw.javaButton(g, x + w - b, y, b, h, hp, true);
        } else {
            Draw.roundBordered(g, x, y, w, h, Theme.R_CTRL, Theme.BORDER, Theme.INSET);
            if (hm) Draw.round(g, x + 1, y + 1, b - 1, h - 2, 2, Theme.RAISED);
            if (hp) Draw.round(g, x + w - b, y + 1, b - 1, h - 2, 2, Theme.RAISED);
        }
        textCenter("-", x + b / 2, textY(y, h), Theme.TEXT);
        textCenter("+", x + w - b / 2, textY(y, h), Theme.TEXT);
        textCenter(value, x + w / 2, textY(y, h), Theme.TEXT_STRONG);
        click(x, y, b, h, minus);
        click(x + w - b, y, b, h, plus);
    }

    public void chip(int x, int y, int w, String label, boolean selected, Runnable onClick) {
        int h = 12;
        boolean hov = hover(x, y, w, h);
        if (Theme.java()) {
            Draw.javaButton(g, x, y, w, h, hov || selected, true);
            textCenter(label, x + w / 2, textY(y, h), selected ? 0xFFFFFF55 : (hov ? 0xFFFFFFFF : 0xFFE0E0E0));
        } else if (Theme.norisk()) {
            Draw.flat(g, x, y, w, h, selected ? Theme.alpha(Theme.ACCENT, 0x70) : Theme.alpha(Theme.INSET, 0x80),
                    selected ? Theme.ACCENT : (hov ? Theme.TEXT : Theme.BORDER));
            textCenter(label, x + w / 2, textY(y, h), selected || hov ? 0xFFFFFFFF : Theme.MUTED);
        } else {
            Draw.roundBordered(g, x, y, w, h, Theme.R_CTRL, selected ? Theme.ACCENT : (hov ? Theme.CTRL_HOVER : Theme.BORDER), Theme.WINDOW);
            textCenter(label, x + w / 2, textY(y, h), selected ? Theme.ACCENT : (hov ? Theme.TEXT : Theme.MUTED));
        }
        click(x, y, w, h, onClick);
    }

    public int segmentedWidth(String[] items) {
        int total = 4;
        for (String it : items) total += font.width(Theme.head(it)) + 14;
        return total + 2 * (items.length - 1);
    }

    public int segmented(int x, int y, String[] items, int selected, Consumer<Integer> select) {
        int h = 14, pad = 2, total = pad;
        for (String it : items) total += font.width(Theme.head(it)) + 14;
        total += pad * (items.length - 1) + pad;
        if (!Theme.java() && !Theme.norisk()) Draw.round(g, x, y, total, h + pad * 2, Theme.R_PANEL, Theme.INSET);
        int cx = x + pad;
        for (int i = 0; i < items.length; i++) {
            String label = Theme.head(items[i]);
            int w = font.width(label) + 14;
            boolean sel = i == selected, hov = hover(cx, y + pad, w, h);
            int fg;
            if (Theme.java()) {
                // Like the game's tab bars: every item is a button, the selected one stays lit with yellow text.
                Draw.javaButton(g, cx, y + pad, w, h, sel || hov, true);
                fg = sel ? 0xFFFFFF55 : hov ? 0xFFFFFFFF : 0xFFD0D0D0;
            } else if (Theme.norisk()) {
                // Like NoRisk's top tabs: the selected one is filled with the accent and outlined.
                if (sel) Draw.flat(g, cx, y + pad, w, h, Theme.alpha(Theme.ACCENT, 0xC0), Theme.ACCENT_HOVER);
                else Draw.flat(g, cx, y + pad, w, h, hov ? Theme.RAISED : Theme.alpha(Theme.RAISED, 0x70), hov ? Theme.TEXT : Theme.BORDER);
                fg = sel || hov ? 0xFFFFFFFF : Theme.TEXT;
            } else {
                if (sel) Draw.round(g, cx, y + pad, w, h, Theme.R_CTRL, Theme.RAISED_HOVER);
                fg = sel ? Theme.TEXT_STRONG : (hov ? Theme.TEXT : Theme.MUTED);
            }
            textCenter(label, cx + w / 2, textY(y + pad, h), fg);
            int idx = i;
            click(cx, y + pad, w, h, () -> select.accept(idx));
            cx += w + pad;
        }
        return total;
    }

    public void scrollbar(int x, int y, int h, double scroll, int view, int content) {
        if (content <= view) return;
        int barH = Math.max(12, h * view / content);
        int barY = y + (int) Math.round((h - barH) * (scroll / (content - view)));
        if (Theme.java()) {
            Draw.sprite(g, "widget/scroller", x - 2, barY, 6, barH);
        } else if (Theme.norisk()) {
            g.fill(x, barY, x + 3, barY + barH, Theme.ACCENT);
        } else {
            Draw.round(g, x, barY, 3, barH, 1, Theme.RAISED_HOVER);
        }
    }
}
