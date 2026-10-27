package dev.combatdata.ui;

import dev.combatdata.CombatDataClient;
import dev.combatdata.client.HudPositionScreen;
import dev.combatdata.client.Recordings;
import dev.combatdata.core.Calibration;
import dev.combatdata.core.Cdrec;
import dev.combatdata.core.JumpResetEvaluator;
import dev.combatdata.core.Retention;
import dev.combatdata.core.Settings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import static dev.combatdata.core.Lang.t;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Changes go to a draft and apply on "Apply"; HUD position changes apply immediately. */
final class SettingsTab implements Tab {
    private static String[] sections() {
        if (dev.combatdata.core.Lang.german()) return new String[]{"Allgemein", "HUD", "Statistikfilter", "Aussehen", "Erweitert"};
        return dev.combatdata.core.Lang.english()
                ? new String[]{"General", "HUD", "Stat filters", "Appearance", "Advanced"}
                : new String[]{"Основные", "HUD", "Фильтры статистики", "Оформление", "Дополнительно"};
    }

    private static String[] sectionsShort() {
        if (dev.combatdata.core.Lang.german()) return new String[]{"Allgemein", "HUD", "Filter", "Aussehen", "Erweitert"};
        return dev.combatdata.core.Lang.english()
                ? new String[]{"General", "HUD", "Filters", "Look", "Advanced"}
                : new String[]{"Основные", "HUD", "Фильтры", "Вид", "Дополн."};
    }
    private static final int[] PRESETS = {5, 10, 20, 30, 60};
    private static final Settings DEF = new Settings();
    private final MenuScreen menu;
    private Settings draft;
    private int section;
    private double scroll;
    private int maxScroll;
    private int panelX, panelY, panelW, panelH;

    SettingsTab(MenuScreen menu) {
        this.menu = menu;
    }

    private static Settings live() {
        return CombatDataClient.settings();
    }

    @Override
    public void onShow() {
        if (draft == null) draft = live().copy();
    }

    boolean dirty() {
        return draft != null && draft.diffCount(live()) > 0;
    }

    void apply() {
        if (draft == null) return;
        boolean limits = draft.storageLimitMb != live().storageLimitMb || draft.autoDeleteDays != live().autoDeleteDays;
        live().copyFrom(draft);
        CombatDataClient.saveSettings();
        CombatDataClient.applyLanguage(Minecraft.getInstance());
        if (limits && (draft.storageLimitMb > 0 || draft.autoDeleteDays > 0)) {
            dev.combatdata.client.Recorder.cleanupNow(Minecraft.getInstance());
            storageReloadAt = System.currentTimeMillis() + 1500;
        }
        draft = live().copy();
    }

    void cancel() {
        draft = live().copy();
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        if (draft == null) draft = live().copy();
        draft.hudX = live().hudX;
        draft.hudY = live().hudY;
        draft.fsX = live().fsX;
        draft.fsY = live().fsY;
        ui.segmented(x, y, ui.segmentedWidth(sections()) <= w ? sections() : sectionsShort(), section, i -> {
            section = i;
            storageList = null;
            scroll = 0;
        });
        int footerY = y + h - 16;
        panelX = x;
        panelY = y + 24;
        panelW = w;
        panelH = footerY - 8 - panelY;
        Draw.roundBordered(ui.g, panelX, panelY, panelW, panelH, Theme.R_PANEL, Theme.BORDER, Theme.PANEL);
        int ix = panelX + 1, iy = panelY + 1, iw = panelW - 2, ih = panelH - 2;
        ui.g.enableScissor(ix, iy, ix + iw, iy + ih);
        ui.clip(ix, iy, iw, ih);
        int top = iy - (int) scroll;
        if (ui.find != null) ui.find.at(MenuScreen.TabId.SETTINGS, section);
        int bottom = content(ui, ix, top, iw, section);
        int content = bottom - top;
        maxScroll = Math.max(0, content - ih);
        if (ui.find != null) scroll += ui.find.scrollDelta(iy, ih);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ui.unclip();
        ui.g.disableScissor();
        ui.scrollbar(panelX + panelW - 5, iy + 2, ih - 4, scroll, ih, content);

        int pending = draft.diffCount(live());
        if (pending > 0) {
            Draw.circle(ui.g, x, footerY + 5, 6, Theme.AMBER);
            ui.text(pending + (dev.combatdata.core.Lang.german() ? (pending == 1 ? " Änderung nicht übernommen" : " Änderungen nicht übernommen")
                    : dev.combatdata.core.Lang.english() ? (pending == 1 ? " change not applied" : " changes not applied")
                    : " " + dev.combatdata.core.Lang.plural(pending, "изменение", "изменения", "изменений") + " не применено"), x + 10,
                    Ui.textY(footerY, 16), Theme.MUTED);
        }
        int bx = x + w, bw = ui.buttonWidth(t("Применить", "Apply", "Übernehmen"));
        bx -= bw;
        ui.button(bx, footerY, bw, 16, t("Применить", "Apply", "Übernehmen"), Ui.Kind.PRIMARY, pending > 0, this::apply);
        bw = ui.buttonWidth(t("Отмена", "Cancel", "Abbrechen"));
        bx -= bw + 4;
        ui.button(bx, footerY, bw, 16, t("Отмена", "Cancel", "Abbrechen"), Ui.Kind.SECONDARY, pending > 0, this::cancel);
    }

    private int content(Ui ui, int x, int top, int w, int s) {
        return switch (s) {
            case 0 -> keys(ui, x, heading(ui, x, whatToRecord(ui, x, recording(ui, x, presets(ui, x, top, w), w), w), w, t("Язык и клавиши", "Language and keys", "Sprache und Tasten")), w);
            case 1 -> hud(ui, x, top, w);
            case 2 -> filters(ui, x, top, w);
            case 3 -> appearance(ui, x, top, w);
            default -> advanced(ui, x, top, w);
        };
    }

    /** Lays out every section for the search index. */
    void index(Ui ui, int w) {
        if (draft == null) draft = live().copy();
        for (int s = 0; s < sections().length; s++) {
            ui.find.at(MenuScreen.TabId.SETTINGS, s);
            content(ui, 0, 0, w, s);
        }
    }

    /** Opens a section for a search result; the search then scrolls to the row. */
    void reveal(int s) {
        if (s != section) {
            section = s;
            storageList = null;
            scroll = 0;
        }
    }

    static String sectionName(int s) {
        String[] n = sections();
        return s >= 0 && s < n.length ? n[s] : "";
    }

    @Override
    public boolean scrolled(double mx, double my, double dy) {
        if (mx < panelX || mx >= panelX + panelW || my < panelY || my >= panelY + panelH) return false;
        // Clamp now, otherwise one frame is drawn past the edge and the panel flickers.
        scroll = Math.max(0, Math.min(scroll - dy * 20, maxScroll));
        return true;
    }

    private static void labelInfo(Ui ui, int x, int y, int h, String label, boolean hint, String text) {
        int ly = hint ? y + h / 2 - 9 : Ui.textY(y, h);
        ui.info(x + 10 + ui.font.width(label) + 14, ly - 1, text);
    }

    private int row(Ui ui, int x, int y, int w, int h, String label, String hint, boolean changed) {
        ui.mark(label, hint, x + 4, y, w - 8, h);
        int ly = hint == null ? Ui.textY(y, h) : y + h / 2 - 9;
        ui.text(label, x + 10, ly, Theme.TEXT);
        if (changed) Draw.circle(ui.g, x + 10 + ui.font.width(label) + 5, ly + 1, 5, Theme.AMBER);
        if (hint != null) ui.text(Draw.ellipsize(ui.font, hint, w / 2), x + 10, y + h / 2 + 1, Theme.MUTED);
        ui.g.fill(x + 8, y + h - 1, x + w - 8, y + h, Theme.BORDER);
        return y + h;
    }

    private void reset(Ui ui, int right, int y, boolean differs, String def, Runnable action) {
        if (!differs) return;
        int s = 14, bx = right - s;
        boolean hov = ui.hover(bx, y, s, s);
        if (hov) {
            Draw.round(ui.g, bx, y, s, s, Theme.R_CTRL, Theme.RAISED_HOVER);
            String tip = t("По умолчанию: ", "Default: ", "Standard: ") + def;
            ui.textRight(tip, bx - 4, Ui.textY(y, s), Theme.MUTED);
        }
        ui.textCenter("↺", bx + s / 2 + 1, Ui.textY(y, s), hov ? Theme.TEXT_STRONG : Theme.MUTED);
        ui.click(bx, y, s, s, action);
    }

    /** Three starting points; each sets only the switches that decide how much the mod shows. */
    private int presets(Ui ui, int x, int y, int w) {
        y = heading(ui, x, y, w, t("Быстрый старт", "Quick start", "Schnellstart"));
        String[][] p = {
                {t("Просто", "Simple", "Einfach"), t("Главное и советы, без индикаторов", "Key numbers and tips, no indicators", "Das Wichtigste und Tipps, ohne Anzeigen")},
                {t("Тренировка", "Training", "Training"), t("Все цифры, индикаторы, автосохранение", "All numbers, indicators, autosave", "Alle Zahlen, Anzeigen, automatisch speichern")},
                {t("Только запись", "Record only", "Nur aufnehmen"), t("Ничего на экране, бои сохраняются сами", "Nothing on screen, fights save themselves", "Nichts im Bild, Kämpfe speichern sich selbst")}};
        int gap = 6, cols = w >= 300 ? 3 : 1, bw = (w - 20 - gap * (cols - 1)) / cols, bh = 30;
        for (int i = 0; i < p.length; i++) {
            int bx = x + 10 + (i % cols) * (bw + gap), by = y + 6 + (i / cols) * (bh + gap);
            boolean hov = ui.hover(bx, by, bw, bh);
            ui.mark(p[i][0], p[i][1], bx, by, bw, bh);
            Draw.roundBordered(ui.g, bx, by, bw, bh, Theme.R_CTRL, hov ? Theme.ACCENT : Theme.BORDER, hov ? Theme.ROW_HOVER : Theme.WINDOW);
            ui.text(Draw.ellipsize(ui.font, p[i][0], bw - 10), bx + 6, by + 4, Theme.TEXT_STRONG);
            ui.text(Draw.ellipsize(ui.font, p[i][1], bw - 10), bx + 6, by + 16, Theme.MUTED);
            int preset = i;
            ui.click(bx, by, bw, bh, () -> {
                draft.applyPreset(preset);
                live().indicators.enabled = draft.indicators.enabled;
            });
        }
        int rowsN = (p.length + cols - 1) / cols;
        y += 6 + rowsN * (bh + gap);
        ui.text(Draw.ellipsize(ui.font, t("Затем «Применить». Всё можно поменять ниже.", "Then press Apply. You can change anything below.",
                "Danach „Übernehmen“. Alles lässt sich unten noch ändern."), w - 20), x + 10, y, Theme.DIM);
        return y + 14;
    }

    private int recording(Ui ui, int x, int y, int w) {
        Settings l = live();
        int rh = 38, right = x + w - 10;
        row(ui, x, y, w, rh, t("Длина буфера", "Buffer length", "Pufferlänge"), t("Хранятся последние N минут, от 1 до 120", "Keeps the last N minutes, 1 to 120", "Behält die letzten N Minuten, 1 bis 120"), draft.bufferMinutes != l.bufferMinutes);
        int sw = 72;
        ui.stepper(right - sw, y + 4, sw, draft.bufferMinutes + t(" мин", " min", " Min."), () -> stepBuffer(-1), () -> stepBuffer(1));
        reset(ui, right - sw - 4, y + 5, draft.bufferMinutes != DEF.bufferMinutes, DEF.bufferMinutes + t(" мин", " min", " Min."),
                () -> draft.bufferMinutes = DEF.bufferMinutes);
        int cw = 22, cx = right - (cw + 3) * PRESETS.length + 3;
        for (int m : PRESETS) {
            ui.chip(cx, y + 23, cw, String.valueOf(m), draft.bufferMinutes == m, () -> {
                draft.bufferMinutes = m;
                draft.clamp();
            });
            cx += cw + 3;
        }
        y += rh;
        row(ui, x, y, w, 26, t("Очищать буфер при смене сервера", "Clear buffer on server change", "Puffer bei Serverwechsel leeren"), t("Кадры с прошлого сервера не смешиваются с новыми", "Fights from the last server do not mix with the new one", "Kämpfe vom letzten Server vermischen sich nicht mit dem neuen"), draft.clearOnServerChange != l.clearOnServerChange);
        ui.toggle(right - 20, y + 7, draft.clearOnServerChange, v -> draft.clearOnServerChange = v);
        reset(ui, right - 26, y + 6, draft.clearOnServerChange != DEF.clearOnServerChange, onOff(DEF.clearOnServerChange),
                () -> draft.clearOnServerChange = DEF.clearOnServerChange);
        y += 26;
        y = flag(ui, x, y, w, t("Автосохранение боёв", "Autosave fights", "Kämpfe automatisch speichern"),
                t("Через " + draft.fsDelaySec + " с после боя, от " + draft.statMinFightHits + " ударов",
                        draft.fsDelaySec + " s after a fight, from " + draft.statMinFightHits + " hits", draft.fsDelaySec + " s nach einem Kampf, ab " + draft.statMinFightHits + " Treffer"),
                draft.autoSave, l.autoSave, DEF.autoSave, v -> draft.autoSave = v);
        y = flag(ui, x, y, w, t("Звук при сохранении", "Sound on save", "Sound beim Speichern"), t("Высокий звук: сохранено, низкий: не удалось", "High: saved, low: failed", "Hoher Ton: gespeichert, tiefer Ton: fehlgeschlagen"), draft.soundOnSave,
                l.soundOnSave, DEF.soundOnSave, v -> draft.soundOnSave = v);
        row(ui, x, y, w, 26, t("Громкость звука", "Sound volume", "Lautstärke"), t("Громкость звука сохранения", "Volume of the save sound", "Lautstärke des Speichertons"), draft.soundVolume != l.soundVolume);
        int tw = ui.buttonWidth(t("Проба", "Test", "Testen"));
        ui.button(right - tw, y + 5, tw, 16, t("Проба", "Test", "Testen"), Ui.Kind.GHOST, draft.soundOnSave && draft.soundVolume > 0,
                () -> dev.combatdata.client.Sounds.preview(Minecraft.getInstance(), draft));
        int vx = right - tw - 4 - sw;
        ui.stepper(vx, y + 5, sw, draft.soundVolume + "%", () -> {
            draft.soundVolume -= 10;
            draft.clamp();
        }, () -> {
            draft.soundVolume += 10;
            draft.clamp();
        });
        reset(ui, vx - 4, y + 6, draft.soundVolume != DEF.soundVolume, DEF.soundVolume + "%", () -> draft.soundVolume = DEF.soundVolume);
        return y + 26;
    }

    private static String[] recordModes() {
        return new String[]{t("Полная", "Full", "Voll"), t("Лёгкая", "Lite", "Leicht"), t("Своя", "Custom", "Eigene")};
    }

    private int whatToRecord(Ui ui, int x, int y, int w) {
        Settings l = live();
        y = heading(ui, x, y, w, t("Что записывать", "What to record", "Was aufnehmen"));
        String label = t("Режим записи", "Recording mode", "Aufnahmemodus");
        y = choice(ui, x, y, w, label, recordModes(), draft.recordMode(), l.recordMode(), DEF.recordMode(), draft::setRecordMode);
        labelInfo(ui, x, y - 26, 26, label, false, Help.recordMode());
        y = flag(ui, x, y, w, t("Позиции во время боя", "Positions during fights", "Positionen im Kampf"), t("Бои → Дистанция и повтор; больше всего места", "Fights → Distance and replay; the largest part", "Kämpfe → Distanz und Wiederholung; der größte Teil"),
                draft.recordPositions, l.recordPositions, DEF.recordPositions, v -> draft.recordPositions = v);
        y = flag(ui, x, y, w, t("Нажатия W, S и спринта", "W, S and sprint presses", "W-, S- und Sprint-Tasten"), t("Атака → W-tap", "Attack → W-tap", "Angriff → W-Tap"),
                draft.recordInput, l.recordInput, DEF.recordInput, v -> draft.recordInput = v);
        y = flag(ui, x, y, w, t("Отлёт после удара", "Knockback after a hit", "Rückstoß nach einem Treffer"), t("Защита → Отлёт и подбор окна jump reset", "Defense → Knockback and jump reset window fitting", "Verteidigung → Rückstoß und Anpassung des Jump-Reset-Fensters"),
                draft.recordKnockback, l.recordKnockback, DEF.recordKnockback, v -> draft.recordKnockback = v);
        y = flag(ui, x, y, w, t("Дистанция ударов по вам", "Distance of hits on you", "Distanz der Treffer auf dich"), t("Защита → Удары по мне, 3 block hit против вас", "Defense → Hits on me, 3 block hits against you", "Verteidigung → Treffer auf mich, 3 Block Hits gegen dich"),
                draft.recordTaken, l.recordTaken, DEF.recordTaken, v -> draft.recordTaken = v);
        ui.text(Draw.ellipsize(ui.font, t("Выключенное не пишется в новые записи, в Просмотре там «не записывалось».", "What is off is not saved in new recordings; View shows \"not recorded\" there.", "Was aus ist, wird in neuen Aufnahmen nicht gespeichert; die Ansicht zeigt dort „nicht aufgenommen“."), w - 20),
                x + 10, y + 8, Theme.DIM);
        return y + 20;
    }

    static int heading(Ui ui, int x, int y, int w, String text) {
        if (ui.find != null) ui.find.group(text);
        ui.mark(text, null, x + 4, y + 3, w - 8, 18);
        ui.text(Theme.head(text), x + 10, y + 10, Theme.ACCENT);
        ui.g.fill(x + 8, y + 21, x + w - 8, y + 22, Theme.BORDER);
        return y + 22;
    }

    private int filters(Ui ui, int x, int y, int w) {
        Settings l = live();
        y = heading(ui, x, y, w, t("Удары", "Hits", "Treffer"));
        y = flag(ui, x, y, w, t("Только удары мечом", "Sword hits only", "Nur Schwerttreffer"), t("Рука и другое оружие не входят в атаку, комбо и 3 block hit", "Hand and other weapons are left out of attack, combos and 3 block hit", "Hand und andere Waffen zählen nicht für Angriff, Combos und 3 Block Hit"),
                draft.statSwordOnly, l.statSwordOnly, DEF.statSwordOnly, v -> draft.statSwordOnly = v);
        y = flag(ui, x, y, w, t("Удар рукой не начинает размен", "Hand hit does not start a trade", "Ein Handschlag startet keinen Trade"), t("Тычок рукой перед боем не первый удар (для разменов)", "A poke before a fight is not the first hit (for trades)", "Ein Antippen vor dem Kampf zählt nicht als erster Treffer (für Trades)"),
                draft.statHandNoTrade, l.statHandNoTrade, DEF.statHandNoTrade, v -> draft.statHandNoTrade = v);
        y = heading(ui, x, y, w, t("Игроки", "Players", "Spieler"));
        y = flag(ui, x, y, w, t("Не учитывать тиммейтов", "Ignore teammates", "Teammitglieder ignorieren"), t("Общая команда scoreboard без урона по своим", "Same scoreboard team with friendly fire off", "Gleiches Scoreboard-Team mit ausgeschaltetem Friendly Fire"),
                draft.statNoTeammates, l.statNoTeammates, DEF.statNoTeammates, v -> draft.statNoTeammates = v);
        y = flag(ui, x, y, w, t("Не учитывать NPC и ботов", "Ignore NPCs and bots", "NPCs und Bots ignorieren"), t("Игроки с UUID версии 2", "Players with a version 2 UUID", "Spieler mit einer UUID der Version 2"),
                draft.statNoNpc, l.statNoNpc, DEF.statNoNpc, v -> draft.statNoNpc = v);
        y = heading(ui, x, y, w, t("Пинг и бои", "Ping and fights", "Ping und Kämpfe"));
        y = flag(ui, x, y, w, t("Не учитывать высокий пинг", "Ignore high ping", "Hohen Ping ignorieren"), t("Удары, когда ваш пинг выше порога", "Hits while your ping is above the limit", "Treffer, während dein Ping über dem Limit lag"),
                draft.statHighPing, l.statHighPing, DEF.statHighPing, v -> draft.statHighPing = v);
        int right = x + w - 10, sw = 72;
        row(ui, x, y, w, 26, t("Порог пинга", "Ping limit", "Ping-Limit"), t("Удары при пинге выше не идут в статистику", "Hits above this ping are left out of the stats", "Treffer über diesem Ping zählen nicht für die Statistik"), draft.statPingLimitMs != l.statPingLimitMs);
        ui.stepper(right - sw, y + 5, sw, draft.statPingLimitMs + t(" мс", " ms"), () -> {
            draft.statPingLimitMs -= 25;
            draft.clamp();
        }, () -> {
            draft.statPingLimitMs += 25;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 6, draft.statPingLimitMs != DEF.statPingLimitMs, DEF.statPingLimitMs + t(" мс", " ms"),
                () -> draft.statPingLimitMs = DEF.statPingLimitMs);
        y += 26;
        row(ui, x, y, w, 30, t("Минимальный размер боя", "Minimum fight size", "Mindestgröße eines Kampfs"), t("Подтверждённых ударов в сумме, иначе бой не в списке", "Confirmed hits in total, smaller fights are not listed", "Bestätigte Treffer insgesamt, kleinere Kämpfe werden nicht gelistet"),
                draft.statMinFightHits != l.statMinFightHits);
        ui.stepper(right - sw, y + 7, sw, String.valueOf(draft.statMinFightHits), () -> {
            draft.statMinFightHits--;
            draft.clamp();
        }, () -> {
            draft.statMinFightHits++;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 8, draft.statMinFightHits != DEF.statMinFightHits, String.valueOf(DEF.statMinFightHits),
                () -> draft.statMinFightHits = DEF.statMinFightHits);
        y += 30;
        ui.text(Draw.ellipsize(ui.font, t("Применяются при просмотре ко всем записям, в файле сохраняется всё.", "Applied when viewing, to all recordings; files keep everything.", "Wird beim Ansehen auf alle Aufnahmen angewendet; die Dateien behalten alles."), w - 20), x + 10, y + 8, Theme.DIM);
        ui.text(Draw.ellipsize(ui.font, t("В Просмотре чип «Оружие» меняет выбор только для этой записи.", "In View, the Weapon chip changes it for that recording only.", "In der Ansicht ändert der Waffen-Chip das nur für diese Aufnahme."), w - 20), x + 10, y + 20, Theme.DIM);
        return y + 36;
    }

    private int advanced(Ui ui, int x, int y, int w) {
        Settings l = live();
        y = heading(ui, x, y, w, "Jump reset");
        y = jumpReset(ui, x, y, w);
        y = heading(ui, x, y, w, "3 block hit");
        y = statistics(ui, x, y, w);
        y = heading(ui, x, y, w, t("Размены", "Trades"));
        int right = x + w - 10, sw = 72;
        row(ui, x, y, w, 30, t("Пауза между разменами", "Pause between trades", "Pause zwischen Trades"), t("Без ударов дольше: начинается новый размен", "No hits for longer: a new trade starts", "Länger keine Treffer: ein neuer Trade beginnt"),
                draft.tradeGapMs != l.tradeGapMs);
        ui.stepper(right - sw, y + 7, sw, Fmt.num(draft.tradeGapMs / 1000.0, 2) + t(" с", " s"), () -> {
            draft.tradeGapMs -= 250;
            draft.clamp();
        }, () -> {
            draft.tradeGapMs += 250;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 8, draft.tradeGapMs != DEF.tradeGapMs, Fmt.num(DEF.tradeGapMs / 1000.0, 2) + t(" с", " s"),
                () -> draft.tradeGapMs = DEF.tradeGapMs);
        y += 30;
        y = choice(ui, x, y, w, t("Победитель размена", "Trade winner", "Trade-Sieger"), new String[]{t("По ударам", "By hits", "Nach Treffern"), t("По весу", "By weight", "Nach Gewicht")},
                draft.tradeByWeight ? 1 : 0, l.tradeByWeight ? 1 : 0, DEF.tradeByWeight ? 1 : 0, v -> draft.tradeByWeight = v == 1);
        y = heading(ui, x, y, w, t("Хранилище", "Storage", "Speicher"));
        y = storage(ui, x, y, w);
        y = heading(ui, x, y, w, t("Отладка", "Debugging", "Fehlersuche"));
        return flag(ui, x, y, w, t("Отладочный лог", "Debug log", "Debug-Log"), t("Строки [CD] в latest.log", "[CD] lines in latest.log", "[CD]-Zeilen in latest.log"),
                draft.debugLog, l.debugLog, DEF.debugLog, v -> draft.debugLog = v);
    }

    private static String onOff(boolean v) {
        return v ? t("вкл.", "on", "an") : t("выкл.", "off", "aus");
    }

    private void stepBuffer(int dir) {
        int m = draft.bufferMinutes;
        if (dir > 0) m = m < 10 ? m + 1 : m + 5;
        else m = m <= 10 ? m - 1 : m - 5;
        draft.bufferMinutes = m;
        draft.clamp();
    }

    private static String[] formats() {
        return new String[]{t("Полный", "Full", "Voll"), t("Время", "Time", "Zeit"), t("Кружок", "Dot", "Punkt")};
    }

    private static String[] textColors() {
        return new String[]{t("Состояние", "State", "Zustand"), t("Белый", "White", "Weiß"), t("Серый", "Grey", "Grau")};
    }

    private int flag(Ui ui, int x, int y, int w, String label, String hint, boolean value, boolean liveV, boolean def,
                     java.util.function.Consumer<Boolean> set) {
        int h = hint == null ? 26 : 30, right = x + w - 10;
        row(ui, x, y, w, h, label, hint, value != liveV);
        ui.toggle(right - 20, y + (h - 11) / 2, value, set);
        reset(ui, right - 26, y + (h - 14) / 2, value != def, onOff(def), () -> set.accept(def));
        return y + h;
    }

    private int choice(Ui ui, int x, int y, int w, String label, String[] items, int value, int liveV, int def,
                       java.util.function.Consumer<Integer> set) {
        int right = x + w - 10, sw = ui.segmentedWidth(items);
        row(ui, x, y, w, 26, label, null, value != liveV);
        ui.segmented(right - sw, y + 4, items, value, set);
        reset(ui, right - sw - 4, y + 6, value != def, items[def], () -> set.accept(def));
        return y + 26;
    }

    private int hud(Ui ui, int x, int y, int w) {
        Settings l = live();
        int right = x + w - 10;
        row(ui, x, y, w, 30, t("Предпросмотр", "Preview", "Vorschau"), t("Так надпись будет выглядеть после «Применить»", "How the label will look after Apply", "So sieht die Anzeige nach „Übernehmen“ aus"), false);
        int pw = 150, ph = 22, pxl = right - pw, pyl = y + 4;
        ui.g.fill(pxl, pyl, pxl + pw, pyl + ph / 2, 0xFF7EA6D6);
        ui.g.fill(pxl, pyl + ph / 2, pxl + pw, pyl + ph, 0xFF5D8C3A);
        dev.combatdata.client.RecHud.preview(ui.g, ui.font, pxl + 6, pyl + (ph - 13) / 2, draft);
        y += 30;
        row(ui, x, y, w, 26, t("Показывать надпись записи", "Show recording label", "Aufnahme-Anzeige zeigen"), t("Надпись REC в углу экрана", "The REC label in the corner of the screen", "Die REC-Anzeige in der Bildschirmecke"), draft.hudVisible != l.hudVisible);
        ui.toggle(right - 20, y + 7, draft.hudVisible, v -> draft.hudVisible = v);
        reset(ui, right - 26, y + 6, draft.hudVisible != DEF.hudVisible, onOff(DEF.hudVisible),
                () -> draft.hudVisible = DEF.hudVisible);
        y += 26;
        row(ui, x, y, w, 26, t("Масштаб надписи", "Label scale", "Größe der Anzeige"), t("Размер надписи REC", "Size of the REC label", "Größe der REC-Anzeige"), draft.hudScale != l.hudScale);
        ui.stepper(right - 72, y + 5, 72, String.format(Locale.ROOT, "%.2fx", draft.hudScale), () -> {
            draft.hudScale -= 0.25;
            draft.clamp();
        }, () -> {
            draft.hudScale += 0.25;
            draft.clamp();
        });
        reset(ui, right - 76, y + 6, draft.hudScale != DEF.hudScale, String.format(Locale.ROOT, "%.2fx", DEF.hudScale),
                () -> draft.hudScale = DEF.hudScale);
        y += 26;
        row(ui, x, y, w, 26, t("Непрозрачность фона", "Background opacity", "Hintergrund-Deckkraft"), t("0%: без фона, 100%: сплошной", "0%: no background, 100%: solid", "0 %: kein Hintergrund, 100 %: deckend"), draft.hudBgOpacity != l.hudBgOpacity);
        ui.stepper(right - 72, y + 5, 72, draft.hudBgOpacity + "%", () -> {
            draft.hudBgOpacity -= 10;
            draft.clamp();
        }, () -> {
            draft.hudBgOpacity += 10;
            draft.clamp();
        });
        reset(ui, right - 76, y + 6, draft.hudBgOpacity != DEF.hudBgOpacity, DEF.hudBgOpacity + "%",
                () -> draft.hudBgOpacity = DEF.hudBgOpacity);
        y += 26;
        y = choice(ui, x, y, w, t("Формат", "Format"), formats(), draft.hudFormat, l.hudFormat, DEF.hudFormat, v -> draft.hudFormat = v);
        y = choice(ui, x, y, w, t("Цвет текста", "Text color", "Textfarbe"), textColors(), draft.hudTextColor, l.hudTextColor, DEF.hudTextColor,
                v -> draft.hudTextColor = v);
        y = flag(ui, x, y, w, t("Кружок", "Dot", "Punkt"), t("Цветной кружок перед REC", "Colored dot before REC", "Farbiger Punkt vor REC"), draft.hudShowDot, l.hudShowDot, DEF.hudShowDot, v -> draft.hudShowDot = v);
        y = flag(ui, x, y, w, t("Мигание кружка", "Blinking dot", "Blinkender Punkt"), t("Кружок мигает, пока идёт запись", "The dot blinks while recording", "Der Punkt blinkt während der Aufnahme"), draft.hudBlink, l.hudBlink, DEF.hudBlink, v -> draft.hudBlink = v);
        y = flag(ui, x, y, w, t("Тень у текста", "Text shadow", "Textschatten"), t("Легче читать на светлом фоне", "Easier to read on bright backgrounds", "Besser lesbar auf hellem Hintergrund"), draft.hudShadow, l.hudShadow, DEF.hudShadow, v -> draft.hudShadow = v);
        y = flag(ui, x, y, w, t("Скрывать на паузе", "Hide when paused", "Ausblenden, wenn pausiert"), t("Вне мира и в паузе одиночной игры", "Outside a world and in singleplayer pause", "Außerhalb einer Welt und in der Einzelspieler-Pause"), draft.hudHideWhenPaused,
                l.hudHideWhenPaused, DEF.hudHideWhenPaused, v -> draft.hudHideWhenPaused = v);
        row(ui, x, y, w, 30, t("Положение надписи", "Label position", "Position der Anzeige"), t("Меняется сразу, без «Применить»", "Changes immediately, no Apply needed", "Wirkt sofort, kein „Übernehmen“ nötig"), false);
        int bw = ui.buttonWidth(t("Переместить", "Move", "Verschieben"));
        ui.button(right - bw, y + 7, bw, 16, t("Переместить", "Move", "Verschieben"), Ui.Kind.SECONDARY, true,
                () -> Minecraft.getInstance().setScreen(new HudPositionScreen(menu)));
        // Position bypasses the draft, so resetting it saves immediately.
        reset(ui, right - bw - 4, y + 8, l.hudX != DEF.hudX || l.hudY != DEF.hudY, t("левый верхний угол", "top left corner", "obere linke Ecke"), () -> {
            l.hudX = DEF.hudX;
            l.hudY = DEF.hudY;
            CombatDataClient.saveSettings();
        });
        y += 30;
        return fightSummary(ui, x, y, w);
    }

    private static String[] fsLayouts() {
        return new String[]{t("Две строки", "Two lines", "Zwei Zeilen"), t("Одна строка", "One line", "Eine Zeile"), t("Столбиком", "Column", "Spalte")};
    }

    private int fightSummary(Ui ui, int x, int y, int w) {
        Settings l = live();
        int right = x + w - 10, sw = 72;
        y = heading(ui, x, y, w, t("Итог боя", "Fight summary", "Kampfzusammenfassung"));
        String label = t("Показывать итог боя", "Show fight summary", "Kampfzusammenfassung anzeigen");
        y = flag(ui, x, y, w, label, t("После боя на несколько секунд", "For a few seconds after a fight", "Für ein paar Sekunden nach einem Kampf"),
                draft.fightSummary, l.fightSummary, DEF.fightSummary, v -> draft.fightSummary = v);
        labelInfo(ui, x, y - 30, 30, label, true, Help.fightSummary());
        int fw = dev.combatdata.client.FightHud.previewWidth(ui.font, draft) + 12;
        int fh = dev.combatdata.client.FightHud.previewHeight(ui.font, draft) + 8;
        boolean wide = fw <= w - 80;
        int rh = wide ? Math.max(26, fh + 8) : fh + 22, fx = wide ? right - fw : x + 10, fy = wide ? y + (rh - fh) / 2 : y + 18;
        if (wide) row(ui, x, y, w, rh, t("Пример", "Example", "Beispiel"), null, false);
        else {
            ui.text(t("Пример", "Example", "Beispiel"), x + 10, y + 5, Theme.TEXT);
            ui.g.fill(x + 8, y + rh - 1, x + w - 8, y + rh, Theme.BORDER);
        }
        ui.g.enableScissor(Math.max(fx, x + 1), fy, Math.min(fx + fw, x + w - 1), fy + fh);
        ui.g.fill(fx, fy, fx + fw, fy + fh / 2, 0xFF7EA6D6);
        ui.g.fill(fx, fy + fh / 2, fx + fw, fy + fh, 0xFF5D8C3A);
        dev.combatdata.client.FightHud.preview(ui.g, ui.font, fx + 6, fy + 4, draft);
        ui.g.disableScissor();
        y += rh;

        boolean partsChanged = draft.fsTitle != l.fsTitle || draft.fsHits != l.fsHits || draft.fsJr != l.fsJr || draft.fsCombo != l.fsCombo
                || draft.fsTrades != l.fsTrades || draft.fsReach != l.fsReach || draft.fsThreeBlock != l.fsThreeBlock || draft.fsTaken != l.fsTaken;
        String pl = t("Что показывать", "What to show", "Was anzeigen");
        ui.text(pl, x + 10, y + 8, Theme.TEXT);
        if (partsChanged) Draw.circle(ui.g, x + 10 + ui.font.width(pl) + 5, y + 9, 5, Theme.AMBER);
        Flow fl = new Flow(x + 10, y + 22, w - 20);
        fsChip(ui, fl, t("Ник и время", "Name and time", "Name und Zeit"), draft.fsTitle, v -> draft.fsTitle = v);
        fsChip(ui, fl, t("Попадания", "Hits", "Treffer"), draft.fsHits, v -> draft.fsHits = v);
        fsChip(ui, fl, "Jump reset", draft.fsJr, v -> draft.fsJr = v);
        fsChip(ui, fl, t("Комбо", "Combo"), draft.fsCombo, v -> draft.fsCombo = v);
        fsChip(ui, fl, t("Размены", "Trades"), draft.fsTrades, v -> draft.fsTrades = v);
        fsChip(ui, fl, t("Дистанция", "Reach", "Reichweite"), draft.fsReach, v -> draft.fsReach = v);
        fsChip(ui, fl, "3 block hit", draft.fsThreeBlock, v -> draft.fsThreeBlock = v);
        fsChip(ui, fl, t("Получено ударов", "Hits taken", "Eingesteckte Treffer"), draft.fsTaken, v -> draft.fsTaken = v);
        y = fl.bottom() + 2;
        ui.g.fill(x + 8, y - 1, x + w - 8, y, Theme.BORDER);

        y = choice(ui, x, y, w, t("Вид", "Layout"), fsLayouts(), draft.fsLayout, l.fsLayout, DEF.fsLayout, v -> draft.fsLayout = v);
        y = flag(ui, x, y, w, t("Цветные цифры", "Colored numbers", "Farbige Zahlen"), t("Зелёный хорошо, жёлтый средне, красный плохо", "Green good, yellow fair, red bad", "Grün gut, gelb okay, rot schlecht"),
                draft.fsColors, l.fsColors, DEF.fsColors, v -> draft.fsColors = v);
        row(ui, x, y, w, 26, t("Непрозрачность фона", "Background opacity", "Hintergrund-Deckkraft"), t("0%: без фона, 100%: сплошной", "0%: no background, 100%: solid", "0 %: kein Hintergrund, 100 %: deckend"), draft.fsBgOpacity != l.fsBgOpacity);
        ui.stepper(right - sw, y + 5, sw, draft.fsBgOpacity + "%", () -> {
            draft.fsBgOpacity -= 10;
            draft.clamp();
        }, () -> {
            draft.fsBgOpacity += 10;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 6, draft.fsBgOpacity != DEF.fsBgOpacity, DEF.fsBgOpacity + "%", () -> draft.fsBgOpacity = DEF.fsBgOpacity);
        y += 26;
        row(ui, x, y, w, 26, t("Масштаб", "Scale", "Größe"), t("Размер итога боя", "Size of the fight summary", "Größe der Kampfzusammenfassung"), draft.fsScale != l.fsScale);
        ui.stepper(right - sw, y + 5, sw, String.format(Locale.ROOT, "%.2fx", draft.fsScale), () -> {
            draft.fsScale -= 0.25;
            draft.clamp();
        }, () -> {
            draft.fsScale += 0.25;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 6, draft.fsScale != DEF.fsScale, String.format(Locale.ROOT, "%.2fx", DEF.fsScale), () -> draft.fsScale = DEF.fsScale);
        y += 26;
        row(ui, x, y, w, 30, t("Бой окончен через", "Fight ends after", "Kampf endet nach"), t("Без ударов; для итога и автосохранения", "No hits; for summary and autosave", "Keine Treffer; für Zusammenfassung und automatisches Speichern"), draft.fsDelaySec != l.fsDelaySec);
        ui.stepper(right - sw, y + 7, sw, draft.fsDelaySec + t(" с", " s"), () -> {
            draft.fsDelaySec -= draft.fsDelaySec > 10 ? 5 : 1;
            draft.clamp();
        }, () -> {
            draft.fsDelaySec += draft.fsDelaySec >= 10 ? 5 : 1;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 8, draft.fsDelaySec != DEF.fsDelaySec, DEF.fsDelaySec + t(" с", " s"), () -> draft.fsDelaySec = DEF.fsDelaySec);
        y += 30;
        row(ui, x, y, w, 26, t("Показывается", "Shown for", "Angezeigt für"), t("Сколько секунд виден итог", "How many seconds the summary stays", "Wie viele Sekunden die Zusammenfassung bleibt"), draft.fsShowSec != l.fsShowSec);
        ui.stepper(right - sw, y + 5, sw, draft.fsShowSec + t(" с", " s"), () -> {
            draft.fsShowSec--;
            draft.clamp();
        }, () -> {
            draft.fsShowSec++;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 6, draft.fsShowSec != DEF.fsShowSec, DEF.fsShowSec + t(" с", " s"), () -> draft.fsShowSec = DEF.fsShowSec);
        y += 26;
        y = choice(ui, x, y, w, t("Положение", "Position"), new String[]{t("Рядом с REC", "Next to REC", "Neben REC"), t("Своё", "Custom", "Eigene")},
                draft.fsNearRec ? 0 : 1, l.fsNearRec ? 0 : 1, DEF.fsNearRec ? 0 : 1, v -> draft.fsNearRec = v == 0);
        if (!draft.fsNearRec) {
            row(ui, x, y, w, 30, t("Своё место", "Custom place", "Eigene Position"), t("Меняется сразу, без «Применить»", "Changes immediately, no Apply needed", "Wirkt sofort, kein „Übernehmen“ nötig"), false);
            int bw = ui.buttonWidth(t("Переместить", "Move", "Verschieben"));
            ui.button(right - bw, y + 7, bw, 16, t("Переместить", "Move", "Verschieben"), Ui.Kind.SECONDARY, true,
                    () -> Minecraft.getInstance().setScreen(new HudPositionScreen(menu, true, draft)));
            y += 30;
        }
        return y;
    }

    private static void fsChip(Ui ui, Flow fl, String label, boolean on, java.util.function.Consumer<Boolean> set) {
        int cw = ui.font.width(label) + 12;
        int[] p = fl.place(cw, false);
        ui.mark(label, null, p[0] - 3, p[1], cw + 6, 18);
        ui.chip(p[0], p[1] + 3, cw, label, on, () -> set.accept(!on));
    }


    private static final int CARD_W = 104, CARD_H = 44;
    /** HSL of the open color is kept separately so hue isn't lost on greys. */
    private int editRole = -1;
    private double[] editHsl;
    private final List<int[]> sliders = new ArrayList<>();
    private int dragSlider = -1;
    private String themeStatus;
    private int themeStatusColor;
    private long themeStatusUntil;

    dev.combatdata.core.ThemeSpec previewSpec() {
        return (draft != null ? draft : live()).themeSpec();
    }

    Settings previewSettings() {
        return draft != null ? draft : live();
    }

    private static String[] roleNames() {
        return new String[]{t("Фон окна", "Window background", "Fensterhintergrund"), t("Панели и карточки", "Panels and cards", "Flächen und Karten"),
                t("Поля и графики", "Fields and charts", "Felder und Diagramme"), t("Рамки и линии", "Borders and lines", "Rahmen und Linien"), t("Кнопки", "Buttons", "Schaltflächen"),
                t("Текст", "Text"), t("Подписи", "Captions", "Beschriftungen"), t("Акцент", "Accent", "Akzent"), t("Хорошо: попадание, успех", "Good: hit, success", "Gut: Treffer, Erfolg"),
                t("Плохо: промах, ошибка", "Bad: miss, mistake", "Schlecht: Fehlschlag, Fehler"), t("Внимание: 3 block hit", "Warning: 3 block hit", "Warnung: 3 Block Hit"),
                t("Fly out и пометки", "Fly out and notes", "Fly out und Hinweise")};
    }

    /** The custom design: every shape setting with a live preview (the whole menu is the preview). */
    private int designEditor(Ui ui, int x, int y, int w) {
        Settings l = live();
        y = heading(ui, x, y, w, t("Свой дизайн", "Custom design", "Eigenes Design"));
        int sw = 72, right = x + w - 10;
        y = stepRow(ui, x, y, w, t("Скругление углов", "Corner rounding", "Ecken abrunden"), t("0: квадратные", "0: square", "0: eckig"),
                String.valueOf(draft.dsRadius), draft.dsRadius != l.dsRadius, () -> draft.dsRadius--, () -> draft.dsRadius++);
        y = stepRow(ui, x, y, w, t("Непрозрачность окна", "Window opacity", "Deckkraft des Fensters"), t("Ниже: видно игру за меню", "Lower: the game shows through", "Weniger: das Spiel scheint durch"),
                draft.dsWindowAlpha + "%", draft.dsWindowAlpha != l.dsWindowAlpha, () -> draft.dsWindowAlpha -= 10, () -> draft.dsWindowAlpha += 10);
        y = stepRow(ui, x, y, w, t("Непрозрачность панелей", "Panel opacity", "Deckkraft der Flächen"), t("Карточки, графики, поля", "Cards, charts, fields", "Karten, Diagramme, Felder"),
                draft.dsPanelAlpha + "%", draft.dsPanelAlpha != l.dsPanelAlpha, () -> draft.dsPanelAlpha -= 10, () -> draft.dsPanelAlpha += 10);
        y = choice(ui, x, y, w, t("Кнопки", "Buttons", "Knöpfe"), new String[]{t("Классика", "Classic", "Klassisch"), "Java", t("Плоские", "Flat", "Flach")},
                draft.dsWidgets, l.dsWidgets, DEF.dsWidgets, v -> draft.dsWidgets = v);
        y = flag(ui, x, y, w, t("Тень у текста", "Text shadow", "Textschatten"), t("Как в Minecraft", "Like Minecraft", "Wie in Minecraft"),
                draft.dsShadow, l.dsShadow, DEF.dsShadow, v -> draft.dsShadow = v);
        y = flag(ui, x, y, w, t("Заголовки заглавными", "Upper-case headings", "Überschriften in Großbuchstaben"), null,
                draft.dsCaps, l.dsCaps, DEF.dsCaps, v -> draft.dsCaps = v);
        y = flag(ui, x, y, w, t("Рамки у карточек", "Outlined cards", "Rahmen um Karten"), t("Иначе мягкие блоки", "Otherwise soft blocks", "Sonst weiche Blöcke"),
                draft.dsBorders, l.dsBorders, DEF.dsBorders, v -> draft.dsBorders = v);
        y = flag(ui, x, y, w, t("Названия в боковой панели", "Names in the sidebar", "Namen in der Seitenleiste"), t("Иначе только значки", "Otherwise icons only", "Sonst nur Symbole"),
                draft.dsLabels, l.dsLabels, DEF.dsLabels, v -> draft.dsLabels = v);
        Flow fl = new Flow(x + 10, y + 6, w - 20);
        flowButton(ui, fl, t("Как Java", "Like Java", "Wie Java"), Ui.Kind.SECONDARY, true, () -> draft.designFrom(dev.combatdata.core.Themes.STYLE_JAVA));
        flowButton(ui, fl, t("Как NoRisk", "Like NoRisk", "Wie NoRisk"), Ui.Kind.SECONDARY, true, () -> draft.designFrom(dev.combatdata.core.Themes.STYLE_NORISK));
        flowButton(ui, fl, t("Как классика", "Like Classic", "Wie Klassisch"), Ui.Kind.SECONDARY, true, () -> draft.designFrom(dev.combatdata.core.Themes.STYLE_CLASSIC));
        flowButton(ui, fl, t("Копировать код дизайна", "Copy design code", "Design-Code kopieren"), Ui.Kind.GHOST, true, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(draft.designCode());
            designStatus = t("Код дизайна скопирован: формы и цвета.", "Design code copied: shapes and colors.", "Design-Code kopiert: Formen und Farben.");
        });
        flowButton(ui, fl, t("Вставить код дизайна", "Paste design code", "Design-Code einfügen"), Ui.Kind.GHOST, true, () -> {
            dev.combatdata.core.ThemeSpec ts = draft.applyDesignCode(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (ts == null) {
                designStatus = t("В буфере нет кода дизайна (начинается с cddesign1;).", "No design code on the clipboard (it starts with cddesign1;).",
                        "Kein Design-Code in der Zwischenablage (er beginnt mit cddesign1;).");
                return;
            }
            if (draft.customThemes.size() < dev.combatdata.core.Themes.MAX_CUSTOM) {
                dev.combatdata.core.ThemeSpec n = new dev.combatdata.core.ThemeSpec(dev.combatdata.core.Themes.newId(draft.customThemes),
                        dev.combatdata.core.Themes.uniqueName(ts.name(), draft.customThemes, null), ts.colors());
                draft.customThemes.add(n);
                draft.theme = n.id();
            }
            designStatus = t("Дизайн вставлен. Нажмите «Применить».", "Design pasted. Press Apply.", "Design eingefügt. Drück „Übernehmen“.");
        });
        y = fl.bottom() + 2;
        if (designStatus != null) {
            ui.text(Draw.ellipsize(ui.font, designStatus, w - 20), x + 10, y, Theme.GREEN);
            y += 12;
        }
        ui.text(Draw.ellipsize(ui.font, t("Всё меню — это предпросмотр. Сохраняется по «Применить».", "The whole menu is the preview. Saved on Apply.",
                "Das ganze Menü ist die Vorschau. Gespeichert wird mit „Übernehmen“."), w - 20), x + 10, y + 2, Theme.DIM);
        return y + 16;
    }

    private String designStatus;

    private int stepRow(Ui ui, int x, int y, int w, String label, String hint, String value, boolean changed, Runnable minus, Runnable plus) {
        int h = hint == null ? 26 : 30, sw = 72, right = x + w - 10;
        row(ui, x, y, w, h, label, hint, changed);
        ui.stepper(right - sw, y + (h - Ui.CTRL_H) / 2, sw, value, () -> {
            minus.run();
            draft.clamp();
        }, () -> {
            plus.run();
            draft.clamp();
        });
        return y + h;
    }

    private int appearance(Ui ui, int x, int y, int w) {
        sliders.clear();
        y = heading(ui, x, y, w, t("Стиль меню", "Menu style", "Menü-Stil"));
        String[] styles = new String[dev.combatdata.core.Themes.STYLE_COUNT];
        for (int i = 0; i < styles.length; i++) styles[i] = dev.combatdata.core.Themes.styleName(i);
        y = choice(ui, x, y, w, t("Стиль", "Style", "Stil"), styles, draft.uiStyle, live().uiStyle, DEF.uiStyle, v -> {
            if (v == draft.uiStyle) return;
            int prev = draft.uiStyle;
            draft.uiStyle = v;
            // Custom starts from the look you had; the other styles start with their own colors.
            if (v == dev.combatdata.core.Themes.STYLE_CUSTOM) draft.designFrom(prev);
            else draft.theme = dev.combatdata.core.Themes.themeForStyle(v);
        });
        ui.text(Draw.ellipsize(ui.font, t("Стиль задаёт формы и кнопки, цвета задаёт тема ниже: её можно менять в любом стиле.",
                "The style sets shapes and buttons, the theme below sets the colors: change it in any style.",
                "Der Stil bestimmt Formen und Knöpfe, das Theme unten die Farben: du kannst es in jedem Stil ändern."), w - 20),
                x + 10, y + 4, Theme.DIM);
        y += 16;
        if (draft.uiStyle == dev.combatdata.core.Themes.STYLE_CUSTOM) y = designEditor(ui, x, y, w);
        String head = t("Тема", "Theme");
        y = heading(ui, x, y, w, head);
        ui.info(x + 10 + ui.font.width(head) + 6, y - 13, Help.themes());
        List<dev.combatdata.core.ThemeSpec> all = new ArrayList<>(dev.combatdata.core.Themes.BUILTIN);
        all.addAll(draft.customThemes);
        int inner = w - 20, gap = 6, cols = Math.max(1, (inner + gap) / (CARD_W + gap));
        int cw = (inner - gap * (cols - 1)) / cols, top = y + 8;
        for (int i = 0; i < all.size(); i++) {
            themeCard(ui, x + 10 + (i % cols) * (cw + gap), top + (i / cols) * (CARD_H + gap), cw, all.get(i));
        }
        y = top + ((all.size() + cols - 1) / cols) * (CARD_H + gap) + 2;

        dev.combatdata.core.ThemeSpec sel = draft.themeSpec();
        int index = draft.customThemes.indexOf(sel);
        boolean full = draft.customThemes.size() >= dev.combatdata.core.Themes.MAX_CUSTOM;
        Flow fl = new Flow(x + 10, y, w - 20);
        flowButton(ui, fl, t("Создать копию", "Make a copy", "Kopie erstellen"), Ui.Kind.SECONDARY, !full, () -> {
            dev.combatdata.core.ThemeSpec n = dev.combatdata.core.Themes.copyOf(sel, draft.customThemes);
            draft.customThemes.add(n);
            draft.theme = n.id();
            editRole = -1;
        });
        flowButton(ui, fl, t("Копировать код", "Copy code", "Code kopieren"), Ui.Kind.GHOST, true, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(dev.combatdata.core.Themes.toCode(sel));
            status(t("Код темы скопирован в буфер обмена.", "Theme code copied to the clipboard.", "Theme-Code in die Zwischenablage kopiert."), Theme.GREEN);
        });
        flowButton(ui, fl, t("Вставить код", "Paste code", "Code einfügen"), Ui.Kind.GHOST, !full, this::pasteTheme);
        if (index >= 0) {
            flowButton(ui, fl, t("Переименовать", "Rename", "Umbenennen"), Ui.Kind.GHOST, true, () -> askRename(index));
            flowButton(ui, fl, t("Удалить", "Delete", "Löschen"), Ui.Kind.DANGER, true, () -> askDelete(index));
        }
        y = fl.bottom() + 4;
        if (full) {
            ui.text(Draw.ellipsize(ui.font, t("Своих тем не больше ", "No more than ", "Nicht mehr als ") + dev.combatdata.core.Themes.MAX_CUSTOM
                    + t(": удалите ненужную.", " custom themes: delete one.", " eigene Themes: lösch eins."), w - 20), x + 10, y, Theme.DIM);
            y += 12;
        }
        if (themeStatus != null && System.currentTimeMillis() < themeStatusUntil) {
            ui.text(Draw.ellipsize(ui.font, themeStatus, w - 20), x + 10, y, themeStatusColor);
            y += 12;
        }
        if (index < 0) {
            ui.text(Draw.ellipsize(ui.font, t("Встроенная тема не меняется: «Создать копию» сделает свою,", "A built-in theme does not change: \"Make a copy\" creates your own", "Ein eingebautes Theme ändert sich nicht: „Kopie erstellen“ legt dein eigenes an"), w - 20),
                    x + 10, y + 4, Theme.DIM);
            ui.text(Draw.ellipsize(ui.font, t("в ней можно настроить каждый цвет.", "where every color can be adjusted.", "bei dem du jede Farbe anpassen kannst."), w - 20), x + 10, y + 16, Theme.DIM);
            return y + 32;
        }
        y = heading(ui, x, y, w, Draw.ellipsize(ui.font, t("Цвета: ", "Colors: ", "Farben: ") + sel.name(), w - 20));
        y = colorRows(ui, x, y + 4, w, sel, index);
        ui.text(Draw.ellipsize(ui.font, t("Клик по цвету: ползунки, по коду: ввести hex. Сохраняется по «Применить».", "Click a color for sliders, its code to type hex. Saved on Apply.", "Klick auf eine Farbe für Regler, auf den Code zum Hex-Eingeben. Gespeichert mit „Übernehmen“."), w - 20),
                x + 10, y + 6, Theme.DIM);
        return y + 22;
    }

    private static void flowButton(Ui ui, Flow fl, String label, Ui.Kind kind, boolean enabled, Runnable action) {
        int bw = ui.buttonWidth(label);
        int[] p = fl.place(bw, false);
        ui.button(p[0], p[1], bw, 16, label, kind, enabled, action);
    }

    private void status(String text, int color) {
        themeStatus = text;
        themeStatusColor = color;
        themeStatusUntil = System.currentTimeMillis() + 4000;
    }

    private void themeCard(Ui ui, int x, int y, int w, dev.combatdata.core.ThemeSpec s) {
        ui.mark(dev.combatdata.core.Themes.displayName(s), null, x - 3, y - 3, w + 6, CARD_H + 6);
        boolean sel = s.id().equals(draft.theme), hov = ui.hover(x, y, w, CARD_H);
        Draw.roundBordered(ui.g, x, y, w, CARD_H, Theme.R_PANEL, sel ? Theme.ACCENT : hov ? Theme.CTRL_HOVER : Theme.BORDER,
                s.color(dev.combatdata.core.ThemeSpec.WINDOW));
        if (sel) Draw.roundBordered(ui.g, x + 1, y + 1, w - 2, CARD_H - 2, Theme.R_PANEL - 1, Theme.ACCENT, s.color(dev.combatdata.core.ThemeSpec.WINDOW));
        ui.text(Draw.ellipsize(ui.font, dev.combatdata.core.Themes.displayName(s), w - 10), x + 6, y + 5, s.color(dev.combatdata.core.ThemeSpec.TEXT));
        int px = x + 5, py = y + 16, pw = w - 10, ph = CARD_H - 21;
        Draw.roundBordered(ui.g, px, py, pw, ph, 2, s.color(dev.combatdata.core.ThemeSpec.BORDER), s.color(dev.combatdata.core.ThemeSpec.PANEL));
        Draw.round(ui.g, px + 4, py + 4, 16, 7, 2, s.color(dev.combatdata.core.ThemeSpec.ACCENT));
        int[] dots = {dev.combatdata.core.ThemeSpec.GREEN, dev.combatdata.core.ThemeSpec.RED, dev.combatdata.core.ThemeSpec.AMBER, dev.combatdata.core.ThemeSpec.BLUE};
        for (int k = 0; k < dots.length; k++) Draw.circle(ui.g, px + 26 + k * 8, py + 5, 5, s.color(dots[k]));
        ui.g.fill(px + 4, py + ph - 7, px + Math.max(8, pw / 2), py + ph - 6, s.color(dev.combatdata.core.ThemeSpec.MUTED));
        ui.click(x, y, w, CARD_H, () -> {
            draft.theme = s.id();
            editRole = -1;
        });
    }

    private int colorRows(Ui ui, int x, int y, int w, dev.combatdata.core.ThemeSpec s, int index) {
        String[] names = roleNames();
        int right = x + w - 10, h = 20;
        for (int r = 0; r < dev.combatdata.core.ThemeSpec.COUNT; r++) {
            String hx = dev.combatdata.core.Themes.hex(s.color(r));
            int hw = ui.font.width(hx) + 10, role = r;
            boolean open = r == editRole, hov = ui.hover(x + 8, y, w - 16 - hw - 4, h), hh = ui.hover(right - hw, y + 2, hw, h - 4);
            if (open || hov) Draw.round(ui.g, x + 8, y + 1, w - 16, h - 2, Theme.R_CTRL, open ? Theme.RAISED : Theme.ROW_HOVER);
            Draw.roundBordered(ui.g, x + 12, y + 4, 12, 12, 2, Theme.CTRL_HOVER, s.color(r));
            ui.text(Draw.ellipsize(ui.font, names[r], w - hw - 50), x + 30, Ui.textY(y, h), Theme.TEXT);
            Draw.roundBordered(ui.g, right - hw, y + 2, hw, h - 4, Theme.R_CTRL, hh ? Theme.CTRL_HOVER : Theme.BORDER, Theme.INSET);
            ui.textCenter(hx, right - hw / 2, Ui.textY(y + 2, h - 4), Theme.TEXT_STRONG);
            ui.click(x + 8, y, w - 16 - hw - 4, h, () -> {
                editRole = editRole == role ? -1 : role;
                editHsl = null;
            });
            ui.click(right - hw, y + 2, hw, h - 4, () -> askHex(index, role));
            y += h;
            if (open) y = hslSliders(ui, x, y, w, index, r);
        }
        return y;
    }

    private int hslSliders(Ui ui, int x, int y, int w, int index, int role) {
        int c = draft.customThemes.get(index).color(role) & 0xFFFFFF;
        if (editHsl == null || dev.combatdata.core.Themes.fromHsl(editHsl[0], editHsl[1], editHsl[2]) != c)
            editHsl = dev.combatdata.core.Themes.toHsl(c);
        String[] labels = {t("Оттенок", "Hue", "Farbton"), t("Насыщенность", "Saturation", "Sättigung"), t("Светлота", "Lightness", "Helligkeit")};
        int lw = 0;
        for (String s : labels) lw = Math.max(lw, ui.font.width(s));
        int bx = x + 30 + lw + 8, bw = Math.max(40, x + w - 10 - 34 - bx), bh = 9;
        for (int ch = 0; ch < 3; ch++) {
            ui.text(labels[ch], x + 30, y + 3, Theme.MUTED);
            int steps = Math.max(1, bw / 2);
            for (int i = 0; i < steps; i++) {
                double f = (i + 0.5) / steps;
                double[] v = editHsl.clone();
                v[ch] = ch == 0 ? f * 360 : f;
                int sx = bx + i * bw / steps, ex = bx + (i + 1) * bw / steps;
                ui.g.fill(sx, y + 3, ex, y + 3 + bh, 0xFF000000 | dev.combatdata.core.Themes.fromHsl(v[0], v[1], v[2]));
            }
            ui.g.renderOutline(bx - 1, y + 2, bw + 2, bh + 2, Theme.BORDER);
            double val = ch == 0 ? editHsl[0] / 360 : editHsl[ch];
            int kx = bx + (int) Math.round(val * (bw - 1));
            ui.g.fill(kx - 1, y + 1, kx + 2, y + bh + 5, Theme.TEXT_STRONG);
            ui.g.fill(kx, y + 2, kx + 1, y + bh + 4, Theme.WINDOW);
            ui.textRight(ch == 0 ? Math.round(editHsl[0]) + "°" : Math.round(editHsl[ch] * 100) + "%", x + w - 10, y + 3, Theme.TEXT);
            sliders.add(new int[]{bx, y, bw, bh + 6, ch, index, role});
            y += 16;
        }
        return y + 4;
    }

    @Override
    public boolean pressed(double mx, double my) {
        if (section != 3 || mx < panelX || mx >= panelX + panelW || my < panelY || my >= panelY + panelH) return false;
        for (int i = 0; i < sliders.size(); i++) {
            int[] s = sliders.get(i);
            if (mx >= s[0] - 3 && mx < s[0] + s[2] + 3 && my >= s[1] && my < s[1] + s[3]) {
                dragSlider = i;
                slide(mx);
                return true;
            }
        }
        return false;
    }

    @Override
    public void dragged(double mx, double my, double dx) {
        slide(mx);
    }

    @Override
    public void released(double mx, double my) {
        dragSlider = -1;
    }

    private void slide(double mx) {
        if (dragSlider < 0 || dragSlider >= sliders.size() || editHsl == null) return;
        int[] s = sliders.get(dragSlider);
        int ch = s[4], index = s[5], role = s[6];
        if (index >= draft.customThemes.size()) return;
        double f = Math.max(0, Math.min(1, (mx - s[0]) / (double) Math.max(1, s[2] - 1)));
        editHsl[ch] = ch == 0 ? Math.min(359.9, f * 360) : f;
        dev.combatdata.core.ThemeSpec t = draft.customThemes.get(index);
        draft.customThemes.set(index, t.withColor(role, dev.combatdata.core.Themes.fromHsl(editHsl[0], editHsl[1], editHsl[2])));
    }

    private void askHex(int index, int role) {
        Modal[] self = new Modal[1];
        self[0] = new Modal(roleNames()[role], List.of(t("Цвет в виде #RRGGBB, например #D97757.", "Color as #RRGGBB, for example #D97757.", "Farbe als #RRGGBB, zum Beispiel #D97757.")),
                dev.combatdata.core.Themes.hex(draft.customThemes.get(index).color(role)), List.of(
                new Modal.Btn(t("Отмена", "Cancel", "Abbrechen"), Ui.Kind.SECONDARY, v -> menu.closeModal()),
                new Modal.Btn("OK", Ui.Kind.PRIMARY, v -> {
                    int c = dev.combatdata.core.Themes.parseHex(v);
                    if (c < 0) {
                        self[0].error = t("Не похоже на цвет: нужно 6 знаков 0–9 и A–F.", "Not a color: needs 6 characters 0–9 and A–F.", "Keine Farbe: braucht 6 Zeichen 0–9 und A–F.");
                        return;
                    }
                    if (index < draft.customThemes.size())
                        draft.customThemes.set(index, draft.customThemes.get(index).withColor(role, c));
                    editHsl = null;
                    menu.closeModal();
                })));
        menu.openModal(self[0]);
    }

    private void askRename(int index) {
        dev.combatdata.core.ThemeSpec s = draft.customThemes.get(index);
        Modal[] self = new Modal[1];
        self[0] = new Modal(t("Название темы", "Theme name", "Theme-Name"), List.of(t("До 24 символов.", "Up to 24 characters.", "Bis zu 24 Zeichen.")), s.name(), List.of(
                new Modal.Btn(t("Отмена", "Cancel", "Abbrechen"), Ui.Kind.SECONDARY, v -> menu.closeModal()),
                new Modal.Btn("OK", Ui.Kind.PRIMARY, v -> {
                    String n = dev.combatdata.core.Themes.cleanName(v);
                    if (n.isEmpty()) {
                        self[0].error = t("Название не может быть пустым.", "The name cannot be empty.", "Der Name darf nicht leer sein.");
                        return;
                    }
                    if (index < draft.customThemes.size())
                        draft.customThemes.set(index, s.withName(dev.combatdata.core.Themes.uniqueName(n, draft.customThemes, s.id())));
                    menu.closeModal();
                })));
        menu.openModal(self[0]);
    }

    private void askDelete(int index) {
        dev.combatdata.core.ThemeSpec s = draft.customThemes.get(index);
        menu.openModal(new Modal(t("Удалить тему?", "Delete the theme?", "Theme löschen?"), List.of(
                "«" + s.name() + "»" + t(" удалится после «Применить».", " is removed after Apply.", " wird nach „Übernehmen“ entfernt.")), null, List.of(
                new Modal.Btn(t("Отмена", "Cancel", "Abbrechen"), Ui.Kind.SECONDARY, v -> menu.closeModal()),
                new Modal.Btn(t("Удалить", "Delete", "Löschen"), Ui.Kind.DANGER, v -> {
                    draft.customThemes.remove(s);
                    if (draft.theme.equals(s.id())) draft.theme = dev.combatdata.core.Themes.DEFAULT_ID;
                    editRole = -1;
                    menu.closeModal();
                }))));
    }

    private void pasteTheme() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        dev.combatdata.core.ThemeSpec p = dev.combatdata.core.Themes.fromCode(clip);
        if (p == null) {
            status(t("В буфере обмена нет кода темы (он начинается с cdtheme1;).", "No theme code on the clipboard (it starts with cdtheme1;).", "Kein Theme-Code in der Zwischenablage (er beginnt mit cdtheme1;)."), Theme.RED);
            return;
        }
        List<dev.combatdata.core.ThemeSpec> list = draft.customThemes;
        dev.combatdata.core.ThemeSpec n = new dev.combatdata.core.ThemeSpec(dev.combatdata.core.Themes.newId(list),
                dev.combatdata.core.Themes.uniqueName(p.name(), list, null), p.colors());
        list.add(n);
        draft.theme = n.id();
        editRole = -1;
        status(t("Тема «", "Theme \"", "Theme „") + n.name() + t("» добавлена.", "\" added.", "“ hinzugefügt."), Theme.GREEN);
    }

    private int jumpReset(Ui ui, int x, int y, int w) {
        Settings l = live();
        int right = x + w - 10, sw = 72;
        row(ui, x, y, w, 30, t("Окно успеха: от", "Success window: from", "Erfolgsfenster: von"), t("Мс после удара", "Ms after the hit", "ms nach dem Treffer"), draft.jrLowerMs != l.jrLowerMs);
        ui.stepper(right - sw, y + 7, sw, draft.jrLowerMs + t(" мс", " ms"), () -> jr(0, -10), () -> jr(0, 10));
        reset(ui, right - sw - 4, y + 8, draft.jrLowerMs != DEF.jrLowerMs, DEF.jrLowerMs + t(" мс", " ms"), () -> {
            draft.jrLowerMs = DEF.jrLowerMs;
            draft.clamp();
        });
        y += 30;
        row(ui, x, y, w, 30, t("Окно успеха: до", "Success window: to", "Erfolgsfenster: bis"), t("Мс после удара", "Ms after the hit", "ms nach dem Treffer"), draft.jrUpperMs != l.jrUpperMs);
        ui.stepper(right - sw, y + 7, sw, draft.jrUpperMs + t(" мс", " ms"), () -> jr(1, -10), () -> jr(1, 10));
        reset(ui, right - sw - 4, y + 8, draft.jrUpperMs != DEF.jrUpperMs, DEF.jrUpperMs + t(" мс", " ms"), () -> {
            draft.jrUpperMs = DEF.jrUpperMs;
            draft.clamp();
        });
        y += 30;
        row(ui, x, y, w, 30, t("Прыжки до удара", "Jumps before the hit", "Sprünge vor dem Treffer"), t("Насколько ранний прыжок ещё считается попыткой", "How early a jump still counts as an attempt", "Wie früh ein Sprung noch als Versuch zählt"), draft.jrPreMs != l.jrPreMs);
        ui.stepper(right - sw, y + 7, sw, draft.jrPreMs + t(" мс", " ms"), () -> jr(2, -25), () -> jr(2, 25));
        reset(ui, right - sw - 4, y + 8, draft.jrPreMs != DEF.jrPreMs, DEF.jrPreMs + t(" мс", " ms"), () -> draft.jrPreMs = DEF.jrPreMs);
        y += 30;
        row(ui, x, y, w, 30, t("Прыжки после удара", "Jumps after the hit", "Sprünge nach dem Treffer"), t("Насколько поздний прыжок ещё считается попыткой", "How late a jump still counts as an attempt", "Wie spät ein Sprung noch als Versuch zählt"), draft.jrPostMs != l.jrPostMs);
        ui.stepper(right - sw, y + 7, sw, draft.jrPostMs + t(" мс", " ms"), () -> jr(3, -25), () -> jr(3, 25));
        reset(ui, right - sw - 4, y + 8, draft.jrPostMs != DEF.jrPostMs, DEF.jrPostMs + t(" мс", " ms"), () -> draft.jrPostMs = DEF.jrPostMs);
        y += 30;
        row(ui, x, y, w, 30, t("Не учитывать зажатый прыжок", "Ignore held jump", "Gehaltenen Sprung ignorieren"), t("Прыжок, зажатый задолго до удара", "Jump held long before the hit", "Sprung lange vor dem Treffer gehalten"), draft.jrExcludeHeld != l.jrExcludeHeld);
        ui.toggle(right - 20, y + 9, draft.jrExcludeHeld, v -> draft.jrExcludeHeld = v);
        reset(ui, right - 26, y + 8, draft.jrExcludeHeld != DEF.jrExcludeHeld, onOff(DEF.jrExcludeHeld),
                () -> draft.jrExcludeHeld = DEF.jrExcludeHeld);
        y += 30;
        y = flag(ui, x, y, w, t("Считать fly out отдельно", "Count fly out separately", "Fly out getrennt zählen"),
                t("Прыжок с S без W не попытка jump reset", "A jump with S and no W is not a jump reset attempt", "Ein Sprung mit S und ohne W ist kein Jump-Reset-Versuch"),
                draft.jrSeparateFlyOut, l.jrSeparateFlyOut, DEF.jrSeparateFlyOut, v -> draft.jrSeparateFlyOut = v);
        y = calibrationRow(ui, x, y, w);
        ui.text(t("Параметры применяются и к старым записям.", "Settings also apply to old recordings.", "Einstellungen gelten auch für alte Aufnahmen."), x + 10, y + 8, Theme.DIM);
        return y + 24;
    }

    private volatile Calibration.Result calibAll;
    private volatile String calibStatus;
    private volatile boolean calibRunning;

    private int calibrationRow(Ui ui, int x, int y, int w) {
        int h = 42, right = x + w - 10;
        row(ui, x, y, w, h, t("Подобрать окно по записям", "Fit window from recordings", "Fenster aus Aufnahmen anpassen"), t("По отлёту после удара во всех сохранённых записях", "By knockback distance in all saved recordings", "Nach Rückstoßweite in allen gespeicherten Aufnahmen"), false);
        labelInfo(ui, x, y, h, t("Подобрать окно по записям", "Fit window from recordings", "Fenster aus Aufnahmen anpassen"), true, Help.calibrate());
        String bl = calibRunning ? t("Считаю…", "Working…", "Arbeite…") : t("Подобрать", "Fit", "Anpassen");
        int bw = ui.buttonWidth(bl);
        ui.button(right - bw, y + 6, bw, 16, bl, Ui.Kind.SECONDARY, !calibRunning, this::runCalibration);
        Calibration.Result c = calibAll;
        String status = calibStatus;
        if (c != null && c.hasRecommendation()) {
            String text = c.recLower() + "…" + c.recUpper() + t(" мс: отлёт ", " ms: knockback ", " ms: Rückstoß ") + Fmt.num(c.recKb(), 2) + t(" бл.", " bl.", " Bl.")
                    + (Double.isFinite(c.baselineKb()) ? t(" против ", " vs ", " vs. ") + Fmt.num(c.baselineKb(), 2) + t(" без прыжка", " without jump", " ohne Sprung") : "")
                    + " · " + c.samples() + t(" попыток, ", " attempts, ", " Versuche, ") + status;
            String tb = t("В черновик", "To draft", "Zum Entwurf");
            int tw = ui.buttonWidth(tb);
            ui.text(Draw.ellipsize(ui.font, text, w - tw - 30), x + 10, y + 28, Theme.AMBER);
            ui.button(right - tw, y + 24, tw, 14, tb, Ui.Kind.GHOST, true, () -> {
                draft.jrLowerMs = (int) c.recLower();
                draft.jrUpperMs = (int) c.recUpper();
                draft.clamp();
            });
        } else if (c != null) {
            ui.text(Draw.ellipsize(ui.font, t("Мало данных: ", "Not enough data: ", "Nicht genug Daten: ") + c.samples() + t(" попыток с прыжком из ", " jump attempts of ", " Sprungversuche von ") + Calibration.MIN_SAMPLES
                    + t(" нужных (", " needed (", " nötig (") + status + ")", w - 20), x + 10, y + 28, Theme.DIM);
        } else if (status != null) {
            ui.text(Draw.ellipsize(ui.font, status, w - 20), x + 10, y + 28, Theme.DIM);
        }
        return y + h;
    }

    private void runCalibration() {
        if (calibRunning) return;
        calibRunning = true;
        calibAll = null;
        calibStatus = t("Считаю…", "Working…", "Arbeite…");
        JumpResetEvaluator.Params params = draft.jumpParams();
        dev.combatdata.core.StatFilter sf = draft.statFilter();
        Thread t = new Thread(() -> {
            try {
                List<JumpResetEvaluator.Attempt> all = new ArrayList<>();
                int files = 0;
                for (Recordings.Entry e : Recordings.list()) {
                    if (e.header() == null) continue;
                    try {
                        all.addAll(JumpResetEvaluator.evaluate(sf.apply(Cdrec.read(e.file()).events()).kept(), params));
                        files++;
                    } catch (Exception ex) {
                        CombatDataClient.LOG.warn("[CD] калибровка: не читается {}", e.file().getFileName());
                    }
                }
                calibStatus = files + t(" записей", " recordings", " Aufnahmen");
                calibAll = Calibration.analyze(all, params);
            } catch (Exception ex) {
                calibStatus = t("Ошибка: ", "Error: ", "Fehler: ") + ex.getMessage();
            } finally {
                calibRunning = false;
            }
        }, "combatdata-calibration");
        t.setDaemon(true);
        t.start();
    }

    void adoptJumpWindow(int lower, int upper) {
        Settings l = live();
        l.jrLowerMs = lower;
        l.jrUpperMs = upper;
        l.clamp();
        CombatDataClient.saveSettings();
        if (draft != null) {
            draft.jrLowerMs = l.jrLowerMs;
            draft.jrUpperMs = l.jrUpperMs;
        }
    }

    private void jr(int field, int delta) {
        switch (field) {
            case 0 -> draft.jrLowerMs += delta;
            case 1 -> draft.jrUpperMs += delta;
            case 2 -> draft.jrPreMs += delta;
            default -> draft.jrPostMs += delta;
        }
        draft.clamp();
    }

    private int statistics(Ui ui, int x, int y, int w) {
        int right = x + w - 10, sw = 72;
        row(ui, x, y, w, 30, t("Интерполяция у противника", "Opponent interpolation", "Interpolation des Gegners"), t("Для 3 block hit: насколько позже он видит ваше положение", "For 3 block hit: how late they see your position", "Für 3 Block Hit: wie verspätet er deine Position sieht"),
                draft.interpMs != live().interpMs);
        labelInfo(ui, x, y, 30, t("Интерполяция у противника", "Opponent interpolation", "Interpolation des Gegners"), true, Help.interp());
        ui.stepper(right - sw, y + 7, sw, draft.interpMs + t(" мс", " ms"), () -> {
            draft.interpMs -= 10;
            draft.clamp();
        }, () -> {
            draft.interpMs += 10;
            draft.clamp();
        });
        reset(ui, right - sw - 4, y + 8, draft.interpMs != DEF.interpMs, DEF.interpMs + t(" мс", " ms"), () -> draft.interpMs = DEF.interpMs);
        y += 30;
        ui.text(t("Задержка = (ваш пинг + пинг противника) / 2 + интерполяция.", "Delay = (your ping + opponent ping) / 2 + interpolation.", "Verzögerung = (dein Ping + Gegner-Ping) / 2 + Interpolation."), x + 10, y + 8, Theme.DIM);
        ui.text(t("Применяется и к старым записям.", "Also applies to old recordings.", "Gilt auch für alte Aufnahmen."), x + 10, y + 20, Theme.DIM);
        return y + 36;
    }

    private static final int[] LIMITS_MB = {0, 100, 250, 500, 1000, 2000, 5000, 10000};
    private static final int[] AGES_DAYS = {0, 7, 14, 30, 60, 90, 180, 365};
    private List<Recordings.Entry> storageList;
    private long storageReloadAt;
    private boolean storageLoading;

    private int storage(Ui ui, int x, int y, int w) {
        Settings l = live();
        long now = System.currentTimeMillis();
        if (!storageLoading && (storageList == null || (storageReloadAt > 0 && now >= storageReloadAt))) {
            storageLoading = true;
            storageReloadAt = 0;
            Recordings.listAsync(list -> {
                storageList = list;
                storageLoading = false;
            });
        }
        List<Recordings.Entry> files = storageList == null ? List.of() : storageList;
        int right = x + w - 10, sw = 90;
        row(ui, x, y, w, 30, t("Папка записей", "Recordings folder", "Aufnahmeordner"), Recordings.dir().toString(), false);
        int bw = ui.buttonWidth(t("Открыть", "Open", "Öffnen"));
        ui.button(right - bw, y + 7, bw, 16, t("Открыть", "Open", "Öffnen"), Ui.Kind.SECONDARY, true, () -> {
            try {
                java.nio.file.Files.createDirectories(Recordings.dir());
                net.minecraft.util.Util.getPlatform().openUri(Recordings.dir().toUri());
            } catch (Exception ex) {
                CombatDataClient.LOG.warn("[CD] не удалось открыть папку: {}", ex.toString());
            }
        });
        y += 30;
        int favs = 0;
        List<Retention.FileInfo> infos = new ArrayList<>();
        for (Recordings.Entry e : files) {
            boolean fav = Recordings.favorite(e);
            if (fav) favs++;
            infos.add(new Retention.FileInfo(e.stem(), e.size(), e.time(), fav));
        }
        row(ui, x, y, w, 26, t("Занято", "Used", "Belegt"), null, false);
        ui.textRight(files.size() + t(" записей · ", " recordings · ", " Aufnahmen · ") + Fmt.size(Retention.totalBytes(infos)) + t(" · избранных ", " · favorites ", " · Favoriten ") + favs,
                right, Ui.textY(y, 26), Theme.TEXT_STRONG);
        y += 26;
        row(ui, x, y, w, 30, t("Лимит места", "Space limit", "Speicherlimit"), t("Старые записи удаляются, пока папка больше лимита", "Oldest recordings are deleted while the folder is over the limit", "Die ältesten Aufnahmen werden gelöscht, solange der Ordner über dem Limit liegt"), draft.storageLimitMb != l.storageLimitMb);
        ui.stepper(right - sw, y + 7, sw, draft.storageLimitMb == 0 ? t("без лимита", "no limit", "kein Limit") : draft.storageLimitMb + t(" МБ", " MB"),
                () -> draft.storageLimitMb = step(LIMITS_MB, draft.storageLimitMb, -1),
                () -> draft.storageLimitMb = step(LIMITS_MB, draft.storageLimitMb, 1));
        reset(ui, right - sw - 4, y + 8, draft.storageLimitMb != DEF.storageLimitMb, t("без лимита", "no limit", "kein Limit"),
                () -> draft.storageLimitMb = DEF.storageLimitMb);
        y += 30;
        row(ui, x, y, w, 30, t("Удалять старше", "Delete older than", "Löschen älter als"), t("По дате записи", "By recording date", "Nach Aufnahmedatum"), draft.autoDeleteDays != l.autoDeleteDays);
        ui.stepper(right - sw, y + 7, sw, draft.autoDeleteDays == 0 ? t("никогда", "never", "nie") : draft.autoDeleteDays + t(" дн.", " days", " Tage"),
                () -> draft.autoDeleteDays = step(AGES_DAYS, draft.autoDeleteDays, -1),
                () -> draft.autoDeleteDays = step(AGES_DAYS, draft.autoDeleteDays, 1));
        reset(ui, right - sw - 4, y + 8, draft.autoDeleteDays != DEF.autoDeleteDays, t("никогда", "never", "nie"),
                () -> draft.autoDeleteDays = DEF.autoDeleteDays);
        y += 30;
        List<String> gone = Retention.toDelete(infos, draft.storageLimitMb * 1024L * 1024L, draft.autoDeleteDays, now);
        if (!gone.isEmpty()) {
            long bytes = 0;
            for (Retention.FileInfo f : infos) if (gone.contains(f.id())) bytes += f.sizeBytes();
            ui.text(Draw.ellipsize(ui.font, t("При этих лимитах будет удалено записей: ", "These limits will delete recordings: ", "Diese Limits löschen Aufnahmen: ") + gone.size() + " (" + Fmt.size(bytes) + ")",
                    w - 20), x + 10, y + 8, Theme.AMBER);
            y += 12;
        }
        ui.text(t("Избранные (★) и самая новая запись не удаляются.", "Favorites (★) and the newest recording are never deleted.", "Favoriten (★) und die neueste Aufnahme werden nie gelöscht."), x + 10, y + 8, Theme.DIM);
        ui.text(t("Проверка после каждого сохранения и по «Применить».", "Checked after every save and on Apply.", "Geprüft nach jedem Speichern und bei „Übernehmen“."), x + 10, y + 20, Theme.DIM);
        return y + 36;
    }

    private static int step(int[] values, int cur, int dir) {
        if (dir > 0) {
            for (int v : values) if (v > cur) return v;
            return values[values.length - 1];
        }
        for (int i = values.length - 1; i >= 0; i--) if (values[i] < cur) return values[i];
        return values[0];
    }

    private int language(Ui ui, int x, int y, int w) {
        Settings l = live();
        String mc = Minecraft.getInstance().options != null ? Minecraft.getInstance().options.languageCode : null;
        String[] items = {"English", "Deutsch", "Русский"};
        int[] codes = {dev.combatdata.core.Lang.EN, dev.combatdata.core.Lang.DE, dev.combatdata.core.Lang.RU};
        int res = dev.combatdata.core.Lang.resolve(draft.language, mc);
        int shown = res == dev.combatdata.core.Lang.DE ? 1 : res == dev.combatdata.core.Lang.RU ? 2 : 0;
        int right = x + w - 10, sw = ui.segmentedWidth(items), h = 30;
        row(ui, x, y, w, h, t("Язык", "Language", "Sprache"), draft.language == dev.combatdata.core.Lang.AUTO
                ? t("Как в Minecraft (немецкий → немецкий, русский → русский, другие → английский)", "As in Minecraft (German → German, Russian → Russian, others → English)", "Wie in Minecraft (Deutsch → Deutsch, Russisch → Russisch, sonst Englisch)")
                : t("Выбран вручную", "Chosen manually", "Manuell gewählt"), draft.language != l.language);
        ui.segmented(right - sw, y + 5, items, shown, v -> draft.language = codes[v]);
        reset(ui, right - sw - 4, y + 8, draft.language != DEF.language, t("как в Minecraft", "as in Minecraft", "wie in Minecraft"),
                () -> draft.language = DEF.language);
        return y + h;
    }

    private int keys(Ui ui, int x, int y, int w) {
        y = language(ui, x, y, w);
        y = key(ui, x, y, w, t("Открыть меню", "Open menu", "Menü öffnen"), CombatDataClient.MENU);
        y = key(ui, x, y, w, t("Сохранить буфер", "Save buffer", "Puffer speichern"), CombatDataClient.SAVE);
        y = key(ui, x, y, w, t("Поставить метку", "Add marker", "Markierung setzen"), CombatDataClient.MARKER);
        y = key(ui, x, y, w, t("Сбросить буфер (дважды)", "Reset buffer (twice)", "Puffer leeren (zweimal)"), CombatDataClient.RESET);
        y = key(ui, x, y, w, t("Показать или скрыть индикаторы", "Show or hide indicators", "Indikatoren ein-/ausblenden"), CombatDataClient.INDICATORS);
        ui.text(Draw.ellipsize(ui.font, t("Сброс также: Shift + клавиша сохранения дважды, /combatdata reset confirm", "Reset also: Shift + save key twice, /combatdata reset confirm", "Leeren auch: zweimal Shift + Speichertaste, /combatdata reset confirm"), w - 20), x + 10, y + 8, Theme.DIM);
        y += 12;
        ui.text(t("Изменить: Настройки → Управление → Combat Data", "Change in: Options → Controls → Combat Data", "Ändern unter: Optionen → Steuerung → Combat Data"), x + 10, y + 8, Theme.DIM);
        return y + 24;
    }

    private int key(Ui ui, int x, int y, int w, String label, KeyMapping km) {
        row(ui, x, y, w, 26, label, null, false);
        keyChip(ui, x + w - 10, y + 5, km);
        return y + 26;
    }

    /** The bound key as a raised chip, right-aligned to right. Shared with the Indicators tab. */
    static void keyChip(Ui ui, int right, int y, KeyMapping km) {
        String k = km.isUnbound() ? t("не назначена", "not bound", "nicht belegt") : km.getTranslatedKeyMessage().getString();
        int kw = ui.font.width(k) + 14, kx = right - kw;
        Draw.roundBordered(ui.g, kx, y, kw, 16, Theme.R_CTRL, Theme.BORDER, Theme.RAISED);
        ui.textCenter(k, kx + kw / 2, Ui.textY(y, 16), km.isUnbound() ? Theme.MUTED : Theme.TEXT_STRONG);
    }
}
