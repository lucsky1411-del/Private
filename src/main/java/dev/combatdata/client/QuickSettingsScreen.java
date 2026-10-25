package dev.combatdata.client;

import dev.combatdata.CombatDataClient;
import dev.combatdata.core.Lang;
import dev.combatdata.core.Settings;
import dev.combatdata.ui.MenuScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.combatdata.core.Lang.t;

/**
 * The most used switches on one plain screen, opened from Mod Menu. Built from vanilla buttons only, so it looks like
 * every other mod's config screen. Changes apply at once and are saved on Done; "Full menu" opens the mod's own menu.
 */
public final class QuickSettingsScreen extends Screen {
    private final Screen parent;

    private record Row(Supplier<String> label, Supplier<String> hint, Runnable click) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();

    public QuickSettingsScreen(Screen parent) {
        super(Component.literal("Combat Data"));
        this.parent = parent;
        CombatDataClient.applyLanguage(net.minecraft.client.Minecraft.getInstance());
    }

    private static Settings s() {
        return CombatDataClient.settings();
    }

    private static String onOff(boolean v) {
        return v ? t("вкл", "on", "an") : t("выкл", "off", "aus");
    }

    private static String languageName(int code) {
        return switch (code) {
            case Lang.EN -> "English";
            case Lang.DE -> "Deutsch";
            case Lang.RU -> "Русский";
            default -> t("как в Minecraft", "as in Minecraft", "wie in Minecraft");
        };
    }

    private void buildRows() {
        rows.clear();
        rows.add(new Row(() -> t("Язык", "Language", "Sprache") + ": " + languageName(s().language),
                () -> t("Язык меню мода", "Language of the mod's menu", "Sprache des Mod-Menüs"), () -> {
            int[] order = {Lang.AUTO, Lang.EN, Lang.DE, Lang.RU};
            int i = 0;
            while (i < order.length && order[i] != s().language) i++;
            s().language = order[(i + 1) % order.length];
            CombatDataClient.applyLanguage(minecraft);
            rebuildWidgets();
        }));
        rows.add(new Row(() -> t("Вид статистики", "Stats view", "Statistik-Ansicht") + ": "
                + (s().expertMode ? t("эксперт", "expert", "Experte") : t("простой", "simple", "einfach")),
                () -> t("Простой: только главное и советы. Эксперт: все цифры и графики.",
                        "Simple: only the key numbers and tips. Expert: every number and chart.",
                        "Einfach: nur das Wichtigste und Tipps. Experte: alle Zahlen und Diagramme."),
                () -> s().expertMode = !s().expertMode));
        rows.add(new Row(() -> t("Индикаторы на экране", "On-screen indicators", "Anzeigen im Spiel") + ": " + onOff(s().indicators.enabled),
                () -> t("HP, дальность, комбо и др. во время игры", "HP, reach, combo and more while playing", "HP, Reichweite, Combo und mehr beim Spielen"),
                () -> s().indicators.enabled = !s().indicators.enabled));
        rows.add(new Row(() -> t("Надпись REC", "REC label", "REC-Anzeige") + ": " + onOff(s().hudVisible),
                () -> t("Показывает, что идёт запись", "Shows that recording is running", "Zeigt, dass die Aufnahme läuft"),
                () -> s().hudVisible = !s().hudVisible));
        rows.add(new Row(() -> t("Итог боя", "Fight summary", "Kampfzusammenfassung") + ": " + onOff(s().fightSummary),
                () -> t("Короткий итог на несколько секунд после боя", "A short summary for a few seconds after a fight", "Kurze Zusammenfassung für ein paar Sekunden nach einem Kampf"),
                () -> s().fightSummary = !s().fightSummary));
        rows.add(new Row(() -> t("Автосохранение боёв", "Autosave fights", "Kämpfe automatisch speichern") + ": " + onOff(s().autoSave),
                () -> t("Каждый бой сохраняется сам, без F9", "Every fight is saved by itself, no F9 needed", "Jeder Kampf wird von selbst gespeichert, ohne F9"),
                () -> s().autoSave = !s().autoSave));
        rows.add(new Row(() -> t("Звук при сохранении", "Sound on save", "Sound beim Speichern") + ": " + onOff(s().soundOnSave),
                () -> t("Короткий звук, когда запись сохранена", "A short sound when a recording is saved", "Ein kurzer Ton, wenn eine Aufnahme gespeichert wurde"),
                () -> s().soundOnSave = !s().soundOnSave));
    }

    @Override
    protected void init() {
        buildRows();
        buttons.clear();
        int bw = Math.min(310, width - 20), x = (width - bw) / 2;
        int top = Math.max(36, height / 2 - (rows.size() * 24 + 40) / 2);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            Button b = Button.builder(Component.literal(r.label().get()), btn -> {
                r.click().run();
                btn.setMessage(Component.literal(r.label().get()));
            }).bounds(x, top + i * 24, bw, 20).build();
            b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(r.hint().get())));
            buttons.add(b);
            addRenderableWidget(b);
        }
        int by = top + rows.size() * 24 + 12, half = (bw - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal(t("Полное меню…", "Full menu…", "Ganzes Menü…")), b -> {
            CombatDataClient.saveSettings();
            minecraft.setScreen(new MenuScreen(this, MenuScreen.TabId.SETTINGS));
        }).bounds(x, by, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal(t("Готово", "Done", "Fertig")), b -> onClose())
                .bounds(x + half + 8, by, half, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, Component.literal("Combat Data"), width / 2, 14, 0xFFFFFFFF);
        String sub = t("Быстрые настройки · всё остальное в полном меню (Right Shift)",
                "Quick settings · everything else is in the full menu (Right Shift)",
                "Schnelleinstellungen · alles andere im ganzen Menü (Rechte Shift)");
        g.drawCenteredString(font, Component.literal(sub), width / 2, 24, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        CombatDataClient.saveSettings();
        minecraft.setScreen(parent);
    }
}
