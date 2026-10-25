package dev.combatdata.ui;

import dev.combatdata.CombatDataClient;
import dev.combatdata.client.ProgressData;
import dev.combatdata.core.Report;
import dev.combatdata.core.Settings;
import dev.combatdata.core.SkillScores;

import java.util.List;

import static dev.combatdata.core.Lang.t;

/**
 * The overall report across sessions: a short verdict, three focus points and a table of skills with the change
 * against the block of sessions before. Sessions are play days or single recordings; a new report every N sessions.
 */
final class ReportTab implements Tab {
    private static final int LINE = 11, PAD = 8;
    private static final int[] STEPS = {5, 7, 10, 20};
    private final MenuScreen menu;
    private int window;
    private double scroll;
    private int maxScroll;
    private int px, py, pw, ph;

    ReportTab(MenuScreen menu) {
        this.menu = menu;
    }

    @Override
    public void onShow() {
        ProgressData.request(CombatDataClient.settings(), false);
        scroll = 0;
    }

    @Override
    public boolean scrolled(double mx, double my, double dy) {
        if (mx < px || mx >= px + pw || my < py || my >= py + ph) return false;
        scroll = Math.max(0, Math.min(scroll - dy * 20, maxScroll));
        return true;
    }

    @Override
    public void render(Ui ui, int x, int y, int w, int h) {
        Settings s = CombatDataClient.settings();
        ProgressData.request(s, false);
        ProgressData.Snapshot snap = ProgressData.snapshot();
        if (ui.find != null) ui.find.at(MenuScreen.TabId.REPORT, 0);
        Flow f = new Flow(x, y, w);
        List<Report.Session> sessions = snap == null ? List.of() : ProgressData.sessions(snap, s);
        int n = sessions.size();
        List<Integer> wins = Report.windows(n, s.reportStep);
        if (!wins.isEmpty() && !wins.contains(window)) window = wins.get(0);
        for (int k : wins) {
            String label = k == n && (wins.size() == 1 || k != wins.get(0))
                    ? t("Все (" + n + ")", "All (" + n + ")", "Alle (" + n + ")")
                    : t("Последние " + k, "Last " + k, "Letzte " + k);
            int cw = ui.font.width(label) + 12;
            int[] p = f.place(cw, false);
            int kk = k;
            ui.chip(p[0], p[1] + 3, cw, label, window == k, () -> {
                window = kk;
                scroll = 0;
            });
        }
        String sl = t("Сессия: ", "Session: ", "Session: ") + (s.reportByDay ? t("день", "day", "Tag") : t("запись", "recording", "Aufnahme"));
        int sw = ui.font.width(sl) + 12;
        int[] p = f.place(sw, false);
        ui.chip(p[0], p[1] + 3, sw, sl, false, () -> {
            s.reportByDay = !s.reportByDay;
            CombatDataClient.saveSettings();
            window = 0;
        });
        String el = t("Отчёт каждые: ", "Report every: ", "Bericht alle: ") + s.reportStep;
        int ew = ui.font.width(el) + 12;
        p = f.place(ew, false);
        ui.chip(p[0], p[1] + 3, ew, el, false, () -> {
            int i = 0;
            while (i < STEPS.length && STEPS[i] != s.reportStep) i++;
            s.reportStep = STEPS[(i + 1) % STEPS.length];
            CombatDataClient.saveSettings();
            window = 0;
        });
        int[] ip = f.place(Ui.INFO, false);
        ui.info(ip[0], ip[1] + 4, Help.report());
        int top = f.bottom() + 2;
        int next = s.reportStep - n % s.reportStep;
        String info = snap == null ? t("Считаю…", "Computing…", "Berechne…")
                : n + t(" сессий", " sessions", " Sessions") + " (" + (s.reportByDay ? t("дни игры", "play days", "Spieltage") : t("записи", "recordings", "Aufnahmen"))
                + ") · " + t("следующий общий отчёт через ", "next overall report in ", "nächster Gesamtbericht in ") + next;
        ui.text(Draw.ellipsize(ui.font, info, w), x, top, Theme.MUTED);
        top += 13;

        px = x;
        py = top;
        pw = w;
        ph = y + h - top;
        Draw.roundBordered(ui.g, px, py, pw, ph, Theme.R_PANEL, Theme.BORDER, Theme.PANEL);
        if (snap == null || n == 0) {
            String msg = snap == null ? t("Считаю отчёт по сохранённым записям…", "Computing the report from saved recordings…", "Berechne den Bericht aus den gespeicherten Aufnahmen…")
                    : t("Пока нет сохранённых боёв. Сохраните запись (F9) или включите автосохранение.",
                    "No saved fights yet. Save a recording (F9) or turn on autosave.",
                    "Noch keine gespeicherten Kämpfe. Speichere eine Aufnahme (F9) oder schalte automatisches Speichern ein.");
            int ty = py + ph / 2 - 10;
            for (String l : ui.wrap(msg, pw - 24)) {
                ui.textCenter(l, px + pw / 2, ty, Theme.DIM);
                ty += LINE;
            }
            return;
        }
        Report.Result r = Report.last(sessions, window);
        int ix = px + 1, iy = py + 1, iw = pw - 2, ih = ph - 2;
        ui.g.enableScissor(ix, iy, ix + iw, iy + ih);
        ui.clip(ix, iy, iw, ih);
        int cx = ix + PAD, cw = iw - PAD * 2, cy = iy + PAD - (int) scroll, start = cy;
        cy = body(ui, cx, cy, cw, r);
        int content = cy - start + PAD;
        maxScroll = Math.max(0, content - ih);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ui.unclip();
        ui.g.disableScissor();
        ui.scrollbar(px + pw - 5, iy + 2, ih - 4, scroll, ih, content);
    }

    private int body(Ui ui, int x, int y, int w, Report.Result r) {
        ui.text(Theme.head(t("Итог", "Summary", "Zusammenfassung")), x, y, Theme.ACCENT);
        String period = Fmt.date(r.fromMs()) + " – " + Fmt.date(r.toMs());
        ui.textRight(period, x + w, y, Theme.DIM);
        y += LINE + 3;
        for (String sent : r.headline(false)) {
            for (String l : ui.wrap(sent, w)) {
                ui.text(l, x, y, Theme.TEXT);
                y += LINE;
            }
        }
        y += 8;
        ui.text(Theme.head(t("Что тренировать", "What to train", "Was du trainieren solltest")), x, y, Theme.ACCENT);
        y += LINE + 3;
        if (r.focus().isEmpty()) {
            ui.text(t("Явных слабых мест нет. Так держать!", "No clear weak spots. Keep it up!", "Keine klaren Schwachstellen. Weiter so!"), x, y, Theme.GREEN);
            y += LINE;
        }
        int i = 1;
        for (Report.Focus fo : r.focus()) {
            List<String> ls = ui.wrap(fo.text(), w - 22);
            int bh = ls.size() * LINE + 6;
            box(ui, x, y, w, bh);
            ui.text(i + ".", x + 6, y + 3, Theme.ACCENT);
            int ly = y + 3;
            for (String l : ls) {
                ui.text(l, x + 18, ly, Theme.TEXT);
                ly += LINE;
            }
            y += bh + 4;
            i++;
        }
        y += 6;
        ui.text(Theme.head(t("Навыки", "Skills", "Fähigkeiten")), x, y, Theme.ACCENT);
        y += LINE + 3;
        double[] at = {0, 0.46, 0.64, 0.82};
        String prevHead = r.hasPrevious() ? t("к прошлому блоку", "vs block before", "vs. Block davor") : "";
        cols(ui, x, y, w, at, new String[]{"", t("Значение", "Value", "Wert"), t("Оценка", "Grade", "Note"), prevHead}, Theme.DIM);
        y += 12;
        for (Report.Line l : r.lines()) {
            int c = gradeColor(l.grade());
            String trend = "";
            int tc = Theme.DIM;
            double b = l.better();
            if (Double.isFinite(b)) {
                trend = (b >= 0 ? "▲ " : "▼ ") + Math.round(Math.abs(b)) + "%";
                tc = Math.abs(b) < 2 ? Theme.DIM : b > 0 ? Theme.GREEN : Theme.RED;
            }
            String v = Double.isFinite(l.value()) ? Math.round(l.value()) + "%" : "—";
            int cx0 = x + (int) (at[0] * w), cx1 = x + (int) (at[1] * w), cx2 = x + (int) (at[2] * w), cx3 = x + (int) (at[3] * w);
            ui.text(Draw.ellipsize(ui.font, l.name(), cx1 - cx0 - 4), cx0, y, Theme.MUTED);
            ui.text(v, cx1, y, c);
            ui.text(Draw.ellipsize(ui.font, SkillScores.gradeName(l.grade()), cx3 - cx2 - 4), cx2, y, c);
            ui.text(trend, cx3, y, tc);
            y += 12;
        }
        y += 4;
        for (String l : ui.wrap(t("3 block hit против вас: меньше — лучше. Остальное: больше — лучше.",
                "3 block hits against you: lower is better. Everything else: higher is better.",
                "3 Block Hits gegen dich: weniger ist besser. Alles andere: mehr ist besser."), w)) {
            ui.text(l, x, y, Theme.DIM);
            y += LINE;
        }
        return y;
    }

    private static void cols(Ui ui, int x, int y, int w, double[] at, String[] text, int color) {
        for (int i = 0; i < text.length; i++) {
            int cx = x + (int) (at[i] * w), next = i + 1 < at.length ? x + (int) (at[i + 1] * w) : x + w;
            ui.text(Draw.ellipsize(ui.font, text[i], next - cx - 4), cx, y, color);
        }
    }

    private static void box(Ui ui, int x, int y, int w, int h) {
        if (Theme.style == dev.combatdata.core.Themes.STYLE_CLASSIC) Draw.round(ui.g, x, y, w, h, Theme.R_CTRL, Theme.WINDOW);
        else Draw.roundBordered(ui.g, x, y, w, h, 0, Theme.BORDER, Theme.PANEL);
    }

    private static int gradeColor(SkillScores.Grade g) {
        return switch (g) {
            case GOOD -> Theme.GREEN;
            case FAIR -> Theme.AMBER;
            case WORK -> Theme.RED;
            case NONE -> Theme.DIM;
        };
    }
}
