package dev.combatdata.ui;

import dev.combatdata.CombatDataClient;
import dev.combatdata.client.ProgressData;
import dev.combatdata.core.Coach;
import dev.combatdata.core.Detail;
import dev.combatdata.core.Report;
import dev.combatdata.core.Settings;
import dev.combatdata.core.SkillScores;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.combatdata.core.Lang.t;

/**
 * Everything across sessions on one tab, split into pages: the overall report, training goals, opponents, records
 * and badges, a comparison of two sessions and the aim map of all swings. Sessions are play days or recordings.
 */
final class ReportTab implements Tab {
    private static final int LINE = 11, PAD = 8;
    private static final int[] STEPS = {5, 7, 10, 20};
    static final int P_REPORT = 0, P_GOALS = 1, P_RIVALS = 2, P_RECORDS = 3, P_COMPARE = 4, P_MAP = 5;
    private final MenuScreen menu;
    private int page, window, cmpA = -1, cmpB = -1;
    private double scroll;
    private int maxScroll;
    private int px, py, pw, ph;
    private String toast;
    private long toastUntil;

    ReportTab(MenuScreen menu) {
        this.menu = menu;
    }

    private static String[] pages(boolean narrow) {
        return narrow ? new String[]{t("Отчёт", "Report", "Bericht"), t("Цели", "Goals", "Ziele"), t("Против.", "Opp.", "Gegner"),
                t("Рекорды", "Records", "Rekorde"), t("Сравн.", "Compare", "Vergl."), t("Карта", "Map", "Karte")}
                : new String[]{t("Отчёт", "Report", "Bericht"), t("Цели", "Goals", "Ziele"), t("Противники", "Opponents", "Gegner"),
                t("Рекорды", "Records", "Rekorde"), t("Сравнение", "Compare", "Vergleich"), t("Карта прицела", "Aim map", "Zielkarte")};
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
        String[] pn = pages(false);
        if (ui.segmentedWidth(pn) > w) pn = pages(true);
        int[] p = f.place(ui.segmentedWidth(pn), false);
        ui.segmented(p[0], p[1], pn, page, i -> {
            page = i;
            scroll = 0;
        });
        List<Report.Session> sessions = snap == null ? List.of() : ProgressData.sessions(snap, s);
        int n = sessions.size();
        List<Integer> wins = Report.windows(n, s.reportStep);
        if (!wins.isEmpty() && !wins.contains(window)) window = wins.get(0);
        if (page != P_COMPARE && page != P_RECORDS) {
            for (int k : wins) {
                String label = k == n && (wins.size() == 1 || k != wins.get(0))
                        ? t("Все (" + n + ")", "All (" + n + ")", "Alle (" + n + ")")
                        : t("Последние " + k, "Last " + k, "Letzte " + k);
                int cw = ui.font.width(label) + 12;
                p = f.place(cw, false);
                int kk = k;
                ui.chip(p[0], p[1] + 3, cw, label, window == k, () -> {
                    window = kk;
                    scroll = 0;
                });
            }
        }
        String sl = t("Сессия: ", "Session: ", "Session: ") + (s.reportByDay ? t("день", "day", "Tag") : t("запись", "recording", "Aufnahme"));
        int sw = ui.font.width(sl) + 12;
        p = f.place(sw, false);
        ui.chip(p[0], p[1] + 3, sw, sl, false, () -> {
            s.reportByDay = !s.reportByDay;
            CombatDataClient.saveSettings();
            window = 0;
            cmpA = cmpB = -1;
        });
        if (page == P_REPORT) {
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
        }
        int[] ip = f.place(Ui.INFO, false);
        ui.info(ip[0], ip[1] + 4, Help.report());
        int top = f.bottom() + 2;
        String info = snap == null ? t("Считаю…", "Computing…", "Berechne…")
                : n + t(" сессий", " sessions", " Sessions") + " (" + (s.reportByDay ? t("дни игры", "play days", "Spieltage") : t("записи", "recordings", "Aufnahmen"))
                + ") · " + t("следующий общий отчёт через ", "next overall report in ", "nächster Gesamtbericht in ") + (s.reportStep - n % s.reportStep);
        if (toast != null && System.currentTimeMillis() < toastUntil) info = toast;
        ui.text(Draw.ellipsize(ui.font, info, w), x, top, toast != null && System.currentTimeMillis() < toastUntil ? Theme.GREEN : Theme.MUTED);
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
        cy = switch (page) {
            case P_GOALS -> goals(ui, cx, cy, cw, r, s);
            case P_RIVALS -> rivals(ui, cx, cy, cw, snap, r);
            case P_RECORDS -> records(ui, cx, cy, cw, sessions);
            case P_COMPARE -> compare(ui, cx, cy, cw, sessions);
            case P_MAP -> aimMap(ui, cx, cy, cw, sessions);
            default -> report(ui, cx, cy, cw, r);
        };
        int content = cy - start + PAD;
        maxScroll = Math.max(0, content - ih);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ui.unclip();
        ui.g.disableScissor();
        ui.scrollbar(px + pw - 5, iy + 2, ih - 4, scroll, ih, content);
    }

    private void flash(String msg) {
        toast = msg;
        toastUntil = System.currentTimeMillis() + 3000;
    }

    private static int heading(Ui ui, int x, int y, String text) {
        ui.text(Theme.head(text), x, y, Theme.ACCENT);
        return y + LINE + 3;
    }

    private static int para(Ui ui, int x, int y, int w, String text, int color) {
        for (String l : ui.wrap(text, w)) {
            ui.text(l, x, y, color);
            y += LINE;
        }
        return y;
    }

    // ---- Report ----

    private int report(Ui ui, int x, int y, int w, Report.Result r) {
        String copy = t("Копировать", "Copy", "Kopieren");
        int bw = ui.buttonWidth(copy);
        String title = t("Последние " + r.sessions() + " сессий", "Last " + r.sessions() + " sessions", "Letzte " + r.sessions() + " Sessions")
                + " · " + Fmt.date(r.fromMs()) + " – " + Fmt.date(r.toMs());
        ui.button(x + w - bw, y - 2, bw, 14, copy, Ui.Kind.SECONDARY, true, () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(Coach.shareText(r, title));
            flash(t("Отчёт скопирован, можно вставить в Discord.", "Report copied, paste it into Discord or anywhere.",
                    "Bericht kopiert, du kannst ihn in Discord oder sonst wo einfügen."));
        });
        y = heading(ui, x, y, t("Итог", "Summary", "Zusammenfassung"));
        ui.text(Draw.ellipsize(ui.font, title, w - bw - 8), x, y, Theme.DIM);
        y += LINE + 2;
        for (String sent : r.headline(false)) y = para(ui, x, y, w, sent, Theme.TEXT);
        y += 8;
        y = heading(ui, x, y, t("Что тренировать", "What to train", "Was du trainieren solltest"));
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
        y = heading(ui, x, y, t("Навыки", "Skills", "Fähigkeiten"));
        y = skillTable(ui, x, y, w, r);
        y += 4;
        return para(ui, x, y, w, t("3 block hit против вас: меньше — лучше. Остальное: больше — лучше.",
                "3 block hits against you: lower is better. Everything else: higher is better.",
                "3 Block Hits gegen dich: weniger ist besser. Alles andere: mehr ist besser."), Theme.DIM);
    }

    private static int skillTable(Ui ui, int x, int y, int w, Report.Result r) {
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
            int cx0 = x, cx1 = x + (int) (at[1] * w), cx2 = x + (int) (at[2] * w), cx3 = x + (int) (at[3] * w);
            ui.text(Draw.ellipsize(ui.font, l.name(), cx1 - cx0 - 4), cx0, y, Theme.MUTED);
            ui.text(Report.pctText(l.value()), cx1, y, c);
            ui.text(Draw.ellipsize(ui.font, SkillScores.gradeName(l.grade()), cx3 - cx2 - 4), cx2, y, c);
            ui.text(trend, cx3, y, tc);
            y += 12;
        }
        return y;
    }

    // ---- Goals ----

    private int goals(Ui ui, int x, int y, int w, Report.Result r, Settings s) {
        y = heading(ui, x, y, t("Мои цели", "My goals", "Meine Ziele"));
        y = para(ui, x, y, w, t("Цель сравнивается с выбранным блоком сессий. Когда она достигнута, придёт сообщение в чат.",
                "A goal is checked against the chosen block of sessions. When you reach it, you get a chat message.",
                "Ein Ziel wird mit dem gewählten Block von Sessions verglichen. Erreichst du es, kommt eine Nachricht im Chat."), Theme.DIM);
        y += 4;
        List<Coach.Goal> goals = new ArrayList<>(Coach.parseGoals(s.goals));
        if (goals.isEmpty()) {
            ui.text(t("Целей пока нет: добавьте ниже.", "No goals yet: add one below.", "Noch keine Ziele: füg unten eins hinzu."), x, y, Theme.MUTED);
            y += LINE + 4;
        }
        for (int gi = 0; gi < goals.size(); gi++) {
            Coach.Goal g = goals.get(gi);
            Report.Line l = r.line(g.skill());
            double v = l == null ? Double.NaN : l.value();
            boolean ok = g.reached(v);
            int bh = 34;
            box(ui, x, y, w, bh);
            String head = Report.name(g.skill()) + "  " + (g.skill() == Report.Skill.CAUGHT ? "≤ " : "≥ ") + g.target() + "%";
            ui.text(head, x + 6, y + 4, Theme.TEXT_STRONG);
            String now = t("сейчас ", "now ", "jetzt ") + Report.pctText(v) + (ok ? "  ✔" : "");
            ui.textRight(now, x + w - 92, y + 4, ok ? Theme.GREEN : Theme.MUTED);
            int barX = x + 6, barW = w - 104, barY = y + 20;
            ui.g.fill(barX, barY, barX + barW, barY + 6, Theme.INSET);
            int fillW = (int) Math.round(barW * g.progress(v));
            if (fillW > 0) ui.g.fill(barX, barY, barX + fillW, barY + 6, ok ? Theme.GREEN : Theme.ACCENT);
            int idx = gi;
            ui.stepper(x + w - 86, y + 8, 56, g.target() + "%", () -> setTarget(s, goals, idx, g.target() - 5),
                    () -> setTarget(s, goals, idx, g.target() + 5));
            ui.button(x + w - 24, y + 8, 18, 16, "×", Ui.Kind.GHOST, true, () -> {
                goals.remove(idx);
                s.goals = Coach.goalsText(goals);
                CombatDataClient.saveSettings();
            });
            y += bh + 4;
        }
        y += 4;
        y = heading(ui, x, y, t("Добавить цель", "Add a goal", "Ziel hinzufügen"));
        Flow f = new Flow(x, y, w);
        for (Report.Skill k : Report.Skill.values()) {
            if (goals.stream().anyMatch(g -> g.skill() == k)) continue;
            String label = "+ " + Report.name(k);
            int cw = ui.font.width(label) + 12;
            int[] p = f.place(cw, false);
            ui.chip(p[0], p[1], cw, label, false, () -> {
                goals.add(new Coach.Goal(k, Coach.defaultTarget(k)));
                s.goals = Coach.goalsText(goals);
                CombatDataClient.saveSettings();
            });
        }
        return f.bottom() + 4;
    }

    private static void setTarget(Settings s, List<Coach.Goal> goals, int i, int target) {
        goals.set(i, new Coach.Goal(goals.get(i).skill(), Math.max(5, Math.min(100, target))));
        s.goals = Coach.goalsText(goals);
        CombatDataClient.saveSettings();
    }

    // ---- Opponents ----

    private int rivals(Ui ui, int x, int y, int w, ProgressData.Snapshot snap, Report.Result r) {
        y = heading(ui, x, y, t("Против кого и почему", "Who you fight and why you lose", "Gegen wen und warum"));
        List<Coach.Rival> list = Coach.rivals(snap.summaries(), r.fromMs(), r.toMs() + 1, 8);
        if (list.isEmpty()) {
            return para(ui, x, y, w, t("Пока мало боёв с одними и теми же противниками. Данные появятся с новыми записями.",
                    "Not enough fights against the same opponents yet. Data appears with new recordings.",
                    "Noch zu wenige Kämpfe gegen dieselben Gegner. Die Daten kommen mit neuen Aufnahmen."), Theme.DIM);
        }
        for (Coach.Rival rv : list) {
            Report.Result rr = rv.report();
            Report.Line tr = rr.line(Report.Skill.TRADES), hit = rr.line(Report.Skill.HITTING);
            String verdict = rr.focus().isEmpty() ? t("Явной слабости против него нет.", "No clear weakness against them.",
                    "Gegen ihn keine klare Schwäche.") : rr.focus().get(0).text();
            List<String> ls = ui.wrap(verdict, w - 12);
            int bh = 6 + LINE + 2 + ls.size() * LINE + 4;
            box(ui, x, y, w, bh);
            ui.text(Draw.ellipsize(ui.font, rv.name() == null ? "?" : rv.name(), w / 2), x + 6, y + 4, Theme.TEXT_STRONG);
            String stats = t("размены ", "trades ", "Trades ") + Report.pctText(tr.value()) + " · " + t("попадания ", "hits ", "Treffer ")
                    + Report.pctText(hit.value()) + " · " + rv.sessions() + t(" зап.", " rec.", " Aufn.");
            ui.textRight(Draw.ellipsize(ui.font, stats, w / 2 - 10), x + w - 6, y + 4, gradeColor(tr.grade()));
            int ly = y + 6 + LINE + 2;
            for (String l : ls) {
                ui.text(l, x + 6, ly, Theme.MUTED);
                ly += LINE;
            }
            y += bh + 4;
        }
        return y;
    }

    // ---- Records and badges ----

    private int records(Ui ui, int x, int y, int w, List<Report.Session> sessions) {
        y = heading(ui, x, y, t("Личные рекорды", "Personal records", "Persönliche Rekorde"));
        for (Coach.Best b : Coach.records(sessions)) {
            ui.text(Draw.ellipsize(ui.font, b.name(), w - 150), x, y, Theme.MUTED);
            ui.textRight(b.valueText(), x + w - 90, y, Theme.TEXT_STRONG);
            if (b.atMs() > 0) ui.textRight(Fmt.date(b.atMs()), x + w, y, Theme.DIM);
            y += 12;
        }
        y += 8;
        Set<String> got = Coach.earned(sessions);
        y = heading(ui, x, y, t("Достижения", "Badges", "Abzeichen") + " (" + got.size() + "/" + Coach.BADGES.size() + ")");
        int cols = w >= 300 ? 2 : 1, gap = 6, cw = (w - gap * (cols - 1)) / cols, bh = 28;
        for (int i = 0; i < Coach.BADGES.size(); i++) {
            Coach.Badge b = Coach.BADGES.get(i);
            boolean on = got.contains(b.id());
            int bx = x + (i % cols) * (cw + gap), by = y + (i / cols) * (bh + gap);
            box(ui, bx, by, cw, bh);
            ui.g.fill(bx + 4, by + 6, bx + 20, by + 22, on ? Theme.AMBER : Theme.INSET);
            ui.textCenter(on ? "★" : "?", bx + 12, by + 10, on ? 0xFF000000 : Theme.DIM);
            ui.text(Draw.ellipsize(ui.font, b.name(), cw - 30), bx + 26, by + 4, on ? Theme.TEXT_STRONG : Theme.DIM);
            ui.text(Draw.ellipsize(ui.font, b.hint(), cw - 30), bx + 26, by + 15, on ? Theme.MUTED : Theme.DIM);
        }
        return y + ((Coach.BADGES.size() + cols - 1) / cols) * (bh + gap);
    }

    // ---- Compare ----

    private int compare(Ui ui, int x, int y, int w, List<Report.Session> sessions) {
        int n = sessions.size();
        if (cmpA < 0 || cmpA >= n) cmpA = n - 1;
        if (cmpB < 0 || cmpB >= n) cmpB = Math.max(0, n - 2);
        y = heading(ui, x, y, t("Сравнить две сессии", "Compare two sessions", "Zwei Sessions vergleichen"));
        y = picker(ui, x, y, w, "A", sessions, cmpA, v -> cmpA = v);
        y = picker(ui, x, y, w, "B", sessions, cmpB, v -> cmpB = v);
        y += 6;
        Report.Session a = sessions.get(cmpA), b = sessions.get(cmpB);
        Report.Result ra = Report.of(a.m(), a.d(), null, null, 1, a.startMs(), a.endMs());
        Report.Result rb = Report.of(b.m(), b.d(), null, null, 1, b.startMs(), b.endMs());
        double[] at = {0, 0.46, 0.64, 0.82};
        cols(ui, x, y, w, at, new String[]{"", "A", "B", "A − B"}, Theme.DIM);
        y += 12;
        for (Report.Skill k : Report.Skill.values()) {
            Report.Line la = ra.line(k), lb = rb.line(k);
            double d = la.value() - lb.value();
            if (k == Report.Skill.CAUGHT) d = -d;
            int cx1 = x + (int) (at[1] * w), cx2 = x + (int) (at[2] * w), cx3 = x + (int) (at[3] * w);
            ui.text(Draw.ellipsize(ui.font, Report.name(k), cx1 - x - 4), x, y, Theme.MUTED);
            ui.text(Report.pctText(la.value()), cx1, y, gradeColor(la.grade()));
            ui.text(Report.pctText(lb.value()), cx2, y, gradeColor(lb.grade()));
            if (Double.isFinite(d)) ui.text((d >= 0 ? "▲ " : "▼ ") + Math.round(Math.abs(d)) + "%", cx3, y,
                    Math.abs(d) < 2 ? Theme.DIM : d > 0 ? Theme.GREEN : Theme.RED);
            y += 12;
        }
        y += 6;
        return para(ui, x, y, w, t("▲: в A лучше, чем в B. Сессии выбираются стрелками ‹ ›.", "▲: A is better than B. Pick sessions with ‹ ›.",
                "▲: A ist besser als B. Sessions wählst du mit ‹ ›."), Theme.DIM);
    }

    private static int picker(Ui ui, int x, int y, int w, String label, List<Report.Session> ss, int cur, java.util.function.IntConsumer set) {
        Report.Session s = ss.get(cur);
        String text = Fmt.date(s.startMs()) + " · " + s.recordings() + t(" зап.", " rec.", " Aufn.") + " · " + s.m().attacks()
                + t(" атак", " attacks", " Angriffe");
        ui.text(label, x, y + 3, Theme.ACCENT);
        ui.button(x + 14, y, 16, 14, "‹", Ui.Kind.SECONDARY, cur > 0, () -> set.accept(cur - 1));
        ui.text(Draw.ellipsize(ui.font, text, w - 60), x + 36, y + 3, Theme.TEXT);
        ui.button(x + w - 16, y, 16, 14, "›", Ui.Kind.SECONDARY, cur < ss.size() - 1, () -> set.accept(cur + 1));
        return y + 18;
    }

    // ---- Aim map ----

    private int aimMap(Ui ui, int x, int y, int w, List<Report.Session> sessions) {
        y = heading(ui, x, y, t("Карта прицела", "Aim map", "Zielkarte"));
        int k = Math.min(window, sessions.size());
        Detail d = Detail.ZERO;
        for (int i = sessions.size() - k; i < sessions.size(); i++) d = d.plus(sessions.get(i).d());
        int total = 0, max = 1;
        for (int c = 0; c < Detail.GRID_CELLS; c++) {
            int all = d.hitsAt(c) + d.missesAt(c);
            total += all;
            max = Math.max(max, all);
        }
        if (total == 0) {
            return para(ui, x, y, w, t("Нет данных: карта собирается в новых записях.", "No data yet: the map is collected in new recordings.",
                    "Noch keine Daten: die Karte wird in neuen Aufnahmen gesammelt."), Theme.DIM);
        }
        int cell = Math.max(10, Math.min(20, (w - 140) / Detail.GRID_W));
        int mw = cell * Detail.GRID_W, mh = cell * Detail.GRID_H, mx = x, my = y;
        Draw.roundBordered(ui.g, mx - 1, my - 1, mw + 2, mh + 2, Theme.R_CTRL, Theme.BORDER, Theme.INSET);
        // Body outline: the hitbox is the middle 2 of 5 half-widths and 1.0 of the 1.6 height range (from y 1.3 down to -0.3).
        int bx0 = mx + (int) Math.round(mw * 1.5 / 5.0), bx1 = mx + (int) Math.round(mw * 3.5 / 5.0);
        int by0 = my + (int) Math.round(mh * 0.3 / 1.6), by1 = my + (int) Math.round(mh * 1.3 / 1.6);
        for (int c = 0; c < Detail.GRID_CELLS; c++) {
            int hi = d.hitsAt(c), mi = d.missesAt(c), all = hi + mi;
            if (all == 0) continue;
            int cx = mx + (c % Detail.GRID_W) * cell, cy = my + (c / Detail.GRID_W) * cell;
            int a = 0x30 + (int) Math.round(0xB0 * all / (double) max);
            int col = hi >= mi ? Theme.alpha(Theme.GREEN, a) : Theme.alpha(Theme.RED, a);
            ui.g.fill(cx + 1, cy + 1, cx + cell - 1, cy + cell - 1, col);
        }
        ui.g.fill(bx0, by0, bx1, by0 + 1, Theme.TEXT);
        ui.g.fill(bx0, by1, bx1, by1 + 1, Theme.TEXT);
        ui.g.fill(bx0, by0, bx0 + 1, by1, Theme.TEXT);
        ui.g.fill(bx1, by0, bx1 + 1, by1 + 1, Theme.TEXT);
        int tx = mx + mw + 12, tw = w - mw - 12, ty = my;
        int hits = 0, misses = 0;
        for (int c = 0; c < Detail.GRID_CELLS; c++) {
            hits += d.hitsAt(c);
            misses += d.missesAt(c);
        }
        ty = para(ui, tx, ty, tw, t("Все взмахи за " + k + " сессий: где был прицел.", "All swings of " + k + " sessions: where your crosshair was.",
                "Alle Schläge aus " + k + " Sessions: wo dein Fadenkreuz war."), Theme.TEXT);
        ty += 4;
        ty = para(ui, tx, ty, tw, t("Рамка: тело противника. Зелёный: больше попаданий, красный: больше промахов. Ярче: чаще.",
                "Frame: the opponent's body. Green: more hits, red: more misses. Brighter: more often.",
                "Rahmen: der Körper des Gegners. Grün: mehr Treffer, rot: mehr Fehlschläge. Heller: häufiger."), Theme.MUTED);
        ty += 4;
        ty = para(ui, tx, ty, tw, hits + t(" попаданий · ", " on target · ", " auf dem Ziel · ") + misses + t(" мимо", " missed", " daneben"), Theme.DIM);
        return Math.max(my + mh + 4, ty);
    }

    // ---- helpers ----

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
