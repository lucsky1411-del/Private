package dev.combatdata.client;

import dev.combatdata.CombatDataClient;
import dev.combatdata.core.JrTraining;
import dev.combatdata.core.LiveCombat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import static dev.combatdata.core.Lang.t;

/** The jump reset training panel at the top of the screen: streak, best streak, hits saved and the last jump. */
public final class TrainingHud {
    private TrainingHud() {
    }

    public static void extract(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!CombatDataClient.settings().jrTraining || mc.options.hideGui || mc.player == null) return;
        JrTraining tr = Indicators.TRAINING;
        var font = mc.font;
        String title = t("ТРЕНИРОВКА JUMP RESET", "JUMP RESET TRAINING", "JUMP-RESET-TRAINING");
        String rate = Double.isFinite(tr.ratePct()) ? Math.round(tr.ratePct()) + "%" : "—";
        String stats = t("серия ", "streak ", "Serie ") + tr.streak() + t(" · лучшая ", " · best ", " · beste ") + tr.best() + " · "
                + tr.success() + "/" + tr.hits() + " (" + rate + ")";
        String last = tr.lastText();
        boolean fresh = tr.lastAt() > 0 && System.currentTimeMillis() - tr.lastAt() < 1500;
        int lastColor = tr.last() == null ? 0xFFA8A69D : tr.last() == LiveCombat.Jr.SUCCESS ? 0xFF7FD68A : 0xFFE5736B;
        if (!fresh && tr.last() != null) lastColor = (lastColor & 0x00FFFFFF) | 0xB0000000;
        int w = Math.max(font.width(title), Math.max(font.width(stats), font.width(last))) + 12, h = 36;
        int x = (g.guiWidth() - w) / 2, y = 6;
        RecHud.roundRect(g, x, y, w, h, 0x90000000);
        g.drawString(font, title, x + (w - font.width(title)) / 2, y + 4, 0xFFE3B15C, true);
        g.drawString(font, stats, x + (w - font.width(stats)) / 2, y + 14, 0xFFF4F2EA, true);
        g.drawString(font, last, x + (w - font.width(last)) / 2, y + 24, lastColor, true);
    }
}
