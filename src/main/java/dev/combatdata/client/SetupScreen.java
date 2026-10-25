package dev.combatdata.client;

import dev.combatdata.CombatDataClient;
import dev.combatdata.core.Lang;
import dev.combatdata.core.Settings;
import dev.combatdata.ui.MenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static dev.combatdata.core.Lang.t;

/**
 * Shown once, the first time the menu is opened: pick a language, then how the mod should behave (a preset).
 * Two short steps with vanilla buttons; "Skip" keeps the defaults. Everything can be changed later in Settings.
 */
public final class SetupScreen extends Screen {
    private int step;

    public SetupScreen() {
        super(Component.literal("Combat Data"));
    }

    /** The screen the menu key opens: the setup on the very first time, the menu afterwards. */
    public static Screen entry() {
        return CombatDataClient.settings().setupDone ? new MenuScreen() : new SetupScreen();
    }

    private static Settings s() {
        return CombatDataClient.settings();
    }

    @Override
    protected void init() {
        CombatDataClient.applyLanguage(Minecraft.getInstance());
        int bw = Math.min(240, width - 40), x = (width - bw) / 2, y = height / 2 - 34;
        if (step == 0) {
            int[] codes = {Lang.EN, Lang.DE, Lang.RU, Lang.AUTO};
            String[] names = {"English", "Deutsch", "Русский", t("Как в Minecraft", "As in Minecraft", "Wie in Minecraft")};
            for (int i = 0; i < codes.length; i++) {
                int code = codes[i];
                addRenderableWidget(Button.builder(Component.literal(names[i]), b -> {
                    s().language = code;
                    CombatDataClient.applyLanguage(minecraft);
                    step = 1;
                    rebuildWidgets();
                }).bounds(x, y + i * 24, bw, 20).build());
            }
        } else {
            String[][] p = {
                    {t("Просто", "Simple", "Einfach"), t("Главное и советы. Без индикаторов на экране.",
                            "The key numbers and tips. No indicators on screen.", "Das Wichtigste und Tipps. Keine Anzeigen im Bild.")},
                    {t("Тренировка", "Training", "Training"), t("Все цифры и графики, индикаторы в игре, бои сохраняются сами.",
                            "Every number and chart, indicators in game, fights save themselves.",
                            "Alle Zahlen und Diagramme, Anzeigen im Spiel, Kämpfe speichern sich selbst.")},
                    {t("Только запись", "Record only", "Nur aufnehmen"), t("Ничего на экране, бои сохраняются сами. Смотреть потом.",
                            "Nothing on screen, fights save themselves. Look at them later.",
                            "Nichts im Bild, Kämpfe speichern sich selbst. Später ansehen.")}};
            int[] presets = {Settings.PRESET_SIMPLE, Settings.PRESET_TRAINING, Settings.PRESET_RECORD_ONLY};
            for (int i = 0; i < p.length; i++) {
                int preset = presets[i];
                Button b = Button.builder(Component.literal(p[i][0]), btn -> finish(preset)).bounds(x, y + i * 24, bw, 20).build();
                b.setTooltip(Tooltip.create(Component.literal(p[i][1])));
                addRenderableWidget(b);
            }
            addRenderableWidget(Button.builder(Component.literal(t("Назад", "Back", "Zurück")), b -> {
                step = 0;
                rebuildWidgets();
            }).bounds(x, y + 3 * 24 + 8, (bw - 6) / 2, 20).build());
        }
        int half = (bw - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal(t("Пропустить", "Skip", "Überspringen")), b -> finish(-1))
                .bounds(step == 0 ? x : x + half + 6, y + (step == 0 ? 4 : 3) * 24 + 8, step == 0 ? bw : half, 20).build());
    }

    private void finish(int preset) {
        if (preset >= 0) s().applyPreset(preset);
        s().setupDone = true;
        CombatDataClient.saveSettings();
        minecraft.setScreen(new MenuScreen());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int y = height / 2 - 34;
        g.drawCenteredString(font, Component.literal("Combat Data"), width / 2, y - 44, 0xFFFFFFFF);
        String a, b;
        if (step == 0) {
            a = t("Добро пожаловать! Шаг 1 из 2", "Welcome! Step 1 of 2", "Willkommen! Schritt 1 von 2");
            b = t("Выберите язык", "Choose a language", "Wähle eine Sprache");
        } else {
            a = t("Шаг 2 из 2", "Step 2 of 2", "Schritt 2 von 2");
            b = t("Как вы хотите пользоваться модом? Всё можно поменять в настройках.",
                    "How do you want to use the mod? You can change everything in Settings later.",
                    "Wie willst du die Mod nutzen? Alles lässt sich später in den Einstellungen ändern.");
        }
        g.drawCenteredString(font, Component.literal(a), width / 2, y - 30, 0xFFA0A0A0);
        g.drawCenteredString(font, Component.literal(b), width / 2, y - 16, 0xFFE0E0E0);
        if (step == 1) {
            String hint = t("Наведите на вариант, чтобы увидеть описание", "Hover an option to see what it does",
                    "Fahr mit der Maus über eine Option, um zu sehen, was sie macht");
            g.drawCenteredString(font, Component.literal(hint), width / 2, y + 3 * 24 + 36, 0xFF808080);
        }
    }

    @Override
    public void onClose() {
        finish(-1);
    }
}
