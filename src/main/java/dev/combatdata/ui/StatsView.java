package dev.combatdata.ui;

import dev.combatdata.CombatDataClient;
import dev.combatdata.client.ProgressData;
import dev.combatdata.core.*;
import static dev.combatdata.core.Lang.t;

import java.util.*;

final class StatsView {
    static final int OVERVIEW = 0, ATTACK = 1, DEFENSE = 2, FIGHTS = 3;
    static final int A_HITS = 0, A_AIM = 1, A_STRIKES = 2, A_COMBO = 3, A_WTAP = 4;
    static final int D_JUMP = 0, D_KNOCKBACK = 1, D_TAKEN = 2;
    static final int F_OPPONENTS = 0, F_FIGHTS = 1, F_TRADES = 2, F_DISTANCE = 3, F_TIMELINE = 4;

    static String[] subs() {
        if (Lang.german()) return new String[]{"Übersicht", "Angriff", "Verteidigung", "Kämpfe"};
        return Lang.english() ? new String[]{"Overview", "Attack", "Defense", "Fights"}
                : new String[]{"Обзор", "Атака", "Защита", "Бои"};
    }

    private static String[] modes(int sub, boolean narrow) {
        boolean en = Lang.english();
        if (Lang.german()) return switch (sub) {
            case ATTACK -> narrow ? new String[]{"Treffer", "Zielen", "Arten", "Combo", "W-Tap"}
                    : new String[]{"Treffer", "Zielen", "Schlagarten", "Combos", "W-Tap"};
            case DEFENSE -> new String[]{"Jump Reset", "Rückstoß", narrow ? "Auf mich" : "Treffer auf mich"};
            default -> new String[]{"Gegner", "Kämpfe", "Trades", narrow ? "Dist." : "Distanz", narrow ? "Zeit" : "Zeitleiste"};
        };
        return switch (sub) {
            case ATTACK -> en ? (narrow ? new String[]{"Hits", "Aim", "Types", "Combo", "W-tap"}
                    : new String[]{"Hits", "Aim", "Strikes", "Combos", "W-tap"})
                    : narrow ? new String[]{"Попад.", "Прицел", "Удары", "Комбо", "W-tap"}
                    : new String[]{"Попадания", "Прицел", "Удары", "Комбо", "W-tap"};
            case DEFENSE -> en ? new String[]{"Jump reset", "Knockback", narrow ? "On me" : "Hits on me"}
                    : new String[]{"Jump reset", "Отлёт", narrow ? "По мне" : "Удары по мне"};
            default -> en ? new String[]{narrow ? "Opp." : "Opponents", "Fights", "Trades", narrow ? "Dist." : "Distance", narrow ? "Time" : "Timeline"}
                    : new String[]{narrow ? "Против." : "Противники", "Бои", narrow ? "Разм." : "Размены", narrow ? "Дист." : "Дистанция", narrow ? "Лента" : "Таймлайн"};
        };
    }

    /**
     * Pages shown in the selector. The simple view hides the detail pages (W-tap, knockback, timeline); a hidden page
     * opened from an insight still shows, with the full list.
     */
    private static int[] modeMap(int sub, int cur) {
        int n = modes(sub, false).length;
        int[] all = new int[n];
        for (int i = 0; i < n; i++) all[i] = i;
        if (CombatDataClient.settings().expertMode) return all;
        int[] simple = switch (sub) {
            case ATTACK -> new int[]{A_HITS, A_AIM, A_STRIKES, A_COMBO};
            case DEFENSE -> new int[]{D_JUMP, D_TAKEN};
            default -> new int[]{F_OPPONENTS, F_FIGHTS, F_TRADES, F_DISTANCE};
        };
        for (int v : simple) if (v == cur) return simple;
        return all;
    }

    private static String[] pick(String[] names, int[] map) {
        String[] out = new String[map.length];
        for (int i = 0; i < map.length; i++) out[i] = names[map[i]];
        return out;
    }

    interface Header {
        void draw(Ui ui, int x, int y, int w);
    }

    private static final int LINE = 11, ROW = 12, PAD = 8;

    private record Kv(String label, String value, int color) {
        Kv(String label, String value) {
            this(label, value, Theme.TEXT);
        }
    }

    private record Card(String title, List<Kv> rows, int main, String info) {
        Card(String title, List<Kv> rows, int main) {
            this(title, rows, main, null);
        }

        Card with(String text) {
            return new Card(title, rows, main, text);
        }

        int shown(boolean open) {
            return open ? rows.size() : Math.min(main, rows.size());
        }

        int height(boolean open) {
            return 14 + shown(open) * LINE + 6;
        }
    }

    private record Line(long t, String time, String text, int color) {
    }

    private final MenuScreen menu;
    private Recording rec;
    private Calibration.Result calib;
    private Stats.Result all, res;
    private List<Event> events = List.of();
    private List<JumpResetEvaluator.Attempt> attempts = List.of();
    private List<Line> timeline = List.of();
    private List<Trades.Trade> trades = List.of();
    private WTap.Result wtap = WTap.analyze(List.of());
    private List<Insights.Insight> insights = List.of();
    private AimAnalysis.Result aimRes = AimAnalysis.empty();
    private String key;
    private UUID opp;
    private String oppName;
    private long timeFrom = -1, timeTo = -1;
    private int sub;
    private int attackMode, defenseMode, fightsMode;
    private boolean hitList, jrList, takenList;
    private boolean showProjectiles = true;
    private final Set<String> expanded = new HashSet<>();
    private String note;
    private double scroll;
    private int maxScroll;
    private int px, py, pw, ph;
    private boolean showFails = true, showNoJump = true, showMisses = true, showUnconfirmed = true, showFlyOut = true;
    private StatFilter.WeaponView weaponOverride;
    private boolean filtersOff, showFiltered;
    private StatFilter sf = StatFilter.NONE;
    private List<Event> rawEvents = List.of();
    private Chart jrChart = new Chart(), hitChart = new Chart(), takenChart = new Chart();
    private List<Chart.Point> jrPoints = List.of(), hitPoints = List.of(), takenPoints = List.of();
    private double takenRange = AttackEvent.DEFAULT_RANGE;
    private Chart distChart = new Chart();
    private UUID distOpp;
    private String distName;
    private List<Distance.Sample> distSeries = List.of();
    private Chart.Line distLine;
    private List<Chart.Point> distPoints = List.of();
    private double distMean = Double.NaN;
    private boolean replayOn;
    private final ReplayView replayView = new ReplayView();
    private Cooldown.Summary cdSum = Cooldown.summary(List.of());
    private double rangeLine = AttackEvent.DEFAULT_RANGE;
    private long computeMs;

    StatsView(MenuScreen menu) {
        this.menu = menu;
    }

    void set(Recording r, boolean keepFilter) {
        rec = r;
        key = null;
        if (!keepFilter) {
            opp = null;
            oppName = null;
            timeFrom = timeTo = -1;
            weaponOverride = null;
            filtersOff = false;
            scroll = 0;
            jrChart = new Chart();
            hitChart = new Chart();
            takenChart = new Chart();
            distChart = new Chart();
        }
    }

    /** How long the last full recompute took; the live view uses it to refresh less often on a big buffer. */
    long computeMs() {
        return computeMs;
    }

    void setNote(String n) {
        note = n;
    }

    boolean hasData() {
        return rec != null;
    }

    private StatFilter effectiveFilter(Settings s) {
        StatFilter base = filtersOff ? StatFilter.NONE : s.statFilter();
        return weaponOverride != null ? base.withWeapon(weaponOverride) : base;
    }

    private StatFilter.WeaponView weaponView() {
        return sf.weapon();
    }

    private void ensure() {
        Settings s = CombatDataClient.settings();
        StatFilter cur = effectiveFilter(s);
        String k = s.jumpParams() + "/" + s.interpMs + "/" + opp + "/" + timeFrom + "/" + timeTo + "/" + showFails
                + showNoJump + showMisses + showUnconfirmed + showFlyOut + Lang.english() + "/" + cur
                + showFiltered + "/" + s.tradeParams() + "/" + showProjectiles;
        if (k.equals(key)) return;
        key = k;
        long started = System.nanoTime();
        sf = cur;
        all = Stats.compute(rec, s.jumpParams(), Stats.Filter.ALL, AttackEvent.DEFAULT_RANGE, s.interpMs, sf, s.tradeParams());
        boolean filtered = opp != null || timeFrom >= 0;
        Stats.Filter f = !filtered ? Stats.Filter.ALL
                : new Stats.Filter(timeFrom >= 0 ? timeFrom : 0, timeFrom >= 0 ? timeTo : Long.MAX_VALUE, opp);
        res = !filtered ? all : Stats.compute(rec, s.jumpParams(), f, AttackEvent.DEFAULT_RANGE, s.interpMs, sf, s.tradeParams());
        rawEvents = Stats.events(rec, f);
        StatFilter.Applied ap = sf.apply(rawEvents);
        events = ap.kept();
        attempts = JumpResetEvaluator.evaluate(events, s.jumpParams());
        timeline = buildTimeline();
        trades = Trades.split(events, s.tradeParams(), sf, s.jumpParams());
        jrPoints = buildJumpPoints(s);
        hitPoints = buildHitPoints(s);
        takenPoints = buildTakenPoints();
        buildDistance();
        // Intervals use all attacks (a filtered hand hit would otherwise lengthen the interval); only filtered ones go into stats.
        List<Cooldown.Interval> kept = new ArrayList<>();
        for (Cooldown.Interval i : Cooldown.intervals(rawEvents)) if (sf.attack(i.attack()) == null) kept.add(i);
        cdSum = Cooldown.summary(kept);
        wtap = WTap.analyze(events);
        calib = Calibration.analyze(attempts, s.jumpParams());
        aimRes = AimAnalysis.analyze(events, AttackEvent.DEFAULT_RANGE);
        insights = Insights.evaluate(new Insights.Input(res, s.jumpParams(), aimRes));
        computeMs = (System.nanoTime() - started) / 1_000_000L;
    }

    private void filter(UUID id, String name) {
        if (id != null && id.equals(opp)) id = null;
        opp = id;
        oppName = id == null ? null : name;
        scroll = 0;
    }

    private int graphKind() {
        if (res == null) return 0;
        if (sub == DEFENSE && defenseMode == D_JUMP && !jrList) return 1;
        if (sub == ATTACK && attackMode == A_HITS && !hitList) return 2;
        if (sub == DEFENSE && defenseMode == D_TAKEN && !takenList && res.taken().rated() > 0) return 4;
        if (sub == FIGHTS && fightsMode == F_DISTANCE && !distSeries.isEmpty()) return 5;
        return 0;
    }

    private boolean chartShown() {
        return graphKind() != 0;
    }

    private Chart chart() {
        return switch (graphKind()) {
            case 1 -> jrChart;
            case 4 -> takenChart;
            case 5 -> distChart;
            default -> hitChart;
        };
    }

    /** Search results that point at a stats page: their names in the current language. */
    static String[] pages(int sub) {
        return sub == OVERVIEW ? new String[0] : modes(sub, false);
    }

    void open(int sub, int mode) {
        this.sub = sub;
        switch (sub) {
            case ATTACK -> attackMode = mode;
            case DEFENSE -> defenseMode = mode;
            case FIGHTS -> fightsMode = mode;
            default -> {
            }
        }
        hitList = jrList = takenList = false;
        scroll = 0;
    }

    private void go(Insights.Page page) {
        switch (page) {
            case ATTACK_HITS -> { sub = ATTACK; attackMode = A_HITS; hitList = false; }
            case ATTACK_AIM -> { sub = ATTACK; attackMode = A_AIM; }
            case ATTACK_STRIKES -> { sub = ATTACK; attackMode = A_STRIKES; }
            case DEFENSE_JUMP -> { sub = DEFENSE; defenseMode = D_JUMP; jrList = false; }
            case DEFENSE_KNOCKBACK -> { sub = DEFENSE; defenseMode = D_KNOCKBACK; }
            case DEFENSE_TAKEN -> { sub = DEFENSE; defenseMode = D_TAKEN; }
            case FIGHTS_TRADES -> { sub = FIGHTS; fightsMode = F_TRADES; }
        }
        scroll = 0;
    }

    void render(Ui ui, int x, int y, int w, int h, Header header) {
        ensure();
        int sw = ui.segmented(x, y, subs(), sub, i -> {
            sub = i;
            scroll = 0;
        });
        if (header != null && w - sw - 8 > 20) header.draw(ui, x + sw + 8, y, w - sw - 8);
        int top = controls(ui, x, y + 22, w) + 2;
        if (chartShown()) {
            px = pw = 0;
            graph(ui, x, top, w, y + h - top);
            return;
        }
        px = x;
        py = top;
        pw = w;
        ph = y + h - top;
        Draw.roundBordered(ui.g, px, py, pw, ph, Theme.R_PANEL, Theme.BORDER, Theme.PANEL);
        int ix = px + 1, iy = py + 1, iw = pw - 2, ih = ph - 2;
        ui.g.enableScissor(ix, iy, ix + iw, iy + ih);
        ui.clip(ix, iy, iw, ih);
        int ctop = iy + PAD - (int) scroll, cx = ix + PAD, cw = iw - PAD * 2;
        int bottom = switch (sub) {
            case OVERVIEW -> overview(ui, cx, ctop, cw);
            case ATTACK -> switch (attackMode) {
                case A_HITS -> hitsTab(ui, cx, ctop, cw);
                case A_AIM -> aimTab(ui, cx, ctop, cw);
                case A_STRIKES -> strikesTab(ui, cx, ctop, cw);
                case A_WTAP -> wtapTab(ui, cx, ctop, cw);
                default -> comboTab(ui, cx, ctop, cw);
            };
            case DEFENSE -> defenseMode == D_KNOCKBACK ? kbTab(ui, cx, ctop, cw)
                    : defenseMode == D_TAKEN ? takenTab(ui, cx, ctop, cw) : jumpTab(ui, cx, ctop, cw);
            default -> switch (fightsMode) {
                case F_OPPONENTS -> opponentsTab(ui, cx, ctop, cw);
                case F_FIGHTS -> fightsTab(ui, cx, ctop, cw);
                case F_TRADES -> tradesTab(ui, cx, ctop, cw);
                case F_DISTANCE -> distanceTab(ui, cx, ctop, cw);
                default -> timelineTab(ui, cx, ctop, cw);
            };
        };
        int content = bottom - ctop + PAD * 2;
        maxScroll = Math.max(0, content - ih);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ui.unclip();
        ui.g.disableScissor();
        ui.scrollbar(px + pw - 5, iy + 2, ih - 4, scroll, ih, content);
    }

    boolean scrolled(double mx, double my, double dy) {
        if (graphKind() == 5 && replayOn && replayView.scrolled(mx, my, dy)) return true;
        if (chartShown()) return chart().scrolled(mx, my, dy);
        if (mx < px || mx >= px + pw || my < py || my >= py + ph) return false;
        // Clamp now, otherwise one frame is drawn past the edge and the panel flickers.
        scroll = Math.max(0, Math.min(scroll - dy * 24, maxScroll));
        return true;
    }

    boolean pressed(double mx, double my) {
        if (graphKind() == 5 && replayOn && replayView.pressed(mx, my)) return true;
        return chartShown() && chart().pressed(mx, my);
    }

    void dragged(double mx, double my, double dx) {
        replayView.dragged(mx);
        if (chartShown()) chart().dragged(mx, dx);
    }

    void released(double mx, double my) {
        if (replayView.released()) return;
        if (chartShown()) chart().released(mx, my);
    }


    private int controls(Ui ui, int x, int y, int w) {
        Flow f = new Flow(x, y, w);
        boolean any = false;
        if (sub != OVERVIEW) {
            int cur = sub == ATTACK ? attackMode : sub == DEFENSE ? defenseMode : fightsMode;
            int[] map = modeMap(sub, cur);
            String[] m = pick(modes(sub, false), map);
            if (ui.segmentedWidth(m) > w) m = pick(modes(sub, true), map);
            int[] p = f.place(ui.segmentedWidth(m), false);
            int shown = 0;
            for (int k = 0; k < map.length; k++) if (map[k] == cur) shown = k;
            ui.segmented(p[0], p[1], m, shown, i -> {
                if (sub == ATTACK) attackMode = map[i];
                else if (sub == DEFENSE) defenseMode = map[i];
                else fightsMode = map[i];
                scroll = 0;
            });
            any = true;
            boolean hits = sub == ATTACK && attackMode == A_HITS, jr = sub == DEFENSE && defenseMode == D_JUMP,
                    tk = sub == DEFENSE && defenseMode == D_TAKEN;
            if (hits || jr || tk) {
                String[] gl = {t("График", "Chart", "Diagramm"), t("Список", "List", "Liste")};
                p = f.place(ui.segmentedWidth(gl), false);
                ui.segmented(p[0], p[1], gl, (hits ? hitList : jr ? jrList : takenList) ? 1 : 0, i -> {
                    if (hits) hitList = i == 1;
                    else if (jr) jrList = i == 1;
                    else takenList = i == 1;
                    scroll = 0;
                });
            }
        }
        {
            boolean ex = CombatDataClient.settings().expertMode;
            String label = t("Вид: ", "View: ", "Ansicht: ") + (ex ? t("эксперт", "expert", "Experte") : t("простой", "simple", "einfach"));
            int cw = ui.font.width(label) + 12;
            int[] p = f.place(cw, false);
            ui.chip(p[0], p[1] + 3, cw, label, ex, () -> {
                CombatDataClient.settings().expertMode = !CombatDataClient.settings().expertMode;
                CombatDataClient.saveSettings();
                scroll = 0;
            });
            any = true;
        }
        if (rec != null) {
            StatFilter.WeaponView wv = weaponView();
            String label = t("Оружие: ", "Weapon: ", "Waffe: ") + weaponViewName(wv);
            int cw = ui.font.width(label) + 12;
            int[] p = f.place(cw, false);
            ui.chip(p[0], p[1] + 3, cw, label, wv != StatFilter.WeaponView.ALL, () -> {
                StatFilter.WeaponView[] vs = StatFilter.WeaponView.values();
                weaponOverride = vs[(wv.ordinal() + 1) % vs.length];
            });
            any = true;
        }
        if (graphKind() != 0) {
            toggleChip(ui, f, w < 360 ? t("Отфильтр.", "Filtered", "Gefiltert") : t("Показывать отфильтрованное", "Show filtered", "Gefilterte anzeigen"),
                    showFiltered, () -> showFiltered = !showFiltered);
        }
        if (opp != null) {
            String label = t("Противник: ", "Opponent: ", "Gegner: ") + Draw.ellipsize(ui.font, oppName, w / 3) + "  ×";
            int cw = ui.font.width(label) + 12;
            int[] p = f.place(cw, false);
            ui.chip(p[0], p[1] + 3, cw, label, true, () -> filter(null, null));
            any = true;
        }
        if (timeFrom >= 0) {
            String label = t("Участок ", "Range ", "Bereich ") + Fmt.clock(timeFrom) + "–" + Fmt.clock(timeTo) + "  ×";
            int cw = ui.font.width(label) + 12;
            int[] p = f.place(cw, false);
            ui.chip(p[0], p[1] + 3, cw, label, true, () -> timeFrom = timeTo = -1);
            any = true;
        }
        int g = graphKind();
        if (g == 1) {
            toggleChip(ui, f, t("Неудачные", "Failed", "Misslungen"), showFails, () -> showFails = !showFails);
            toggleChip(ui, f, t("Без прыжка", "No jump", "Kein Sprung"), showNoJump, () -> showNoJump = !showNoJump);
            toggleChip(ui, f, "Fly out", showFlyOut, () -> showFlyOut = !showFlyOut);
        } else if (g == 5) {
            toggleChip(ui, f, t("Повтор", "Replay", "Wiederholung"), replayOn, () -> replayOn = !replayOn);
        } else if (g == 4) {
            toggleChip(ui, f, t("Снаряды", "Projectiles", "Geschosse"), showProjectiles, () -> showProjectiles = !showProjectiles);
        } else if (g == 2) {
            toggleChip(ui, f, t("Промахи", "Misses", "Fehlschläge"), showMisses, () -> showMisses = !showMisses);
            toggleChip(ui, f, t("Без подтв.", "Unconfirmed", "Unbestätigt"), showUnconfirmed, () -> showUnconfirmed = !showUnconfirmed);
        }
        if (g != 0) {
            Chart c = chart();
            if (c.zoomed()) {
                String l1 = t("По участку", "This range", "Dieser Bereich"), l2 = t("Весь масштаб", "Full view", "Gesamtansicht");
                int w2 = ui.buttonWidth(l2), w1 = ui.buttonWidth(l1);
                int[] p = f.place(w2, false);
                ui.button(p[0], p[1] + 1, w2, 16, l2, Ui.Kind.GHOST, true, c::resetView);
                p = f.place(w1, false);
                ui.button(p[0], p[1] + 1, w1, 16, l1, Ui.Kind.SECONDARY, true, () -> {
                    timeFrom = c.view().from();
                    timeTo = c.view().to();
                });
            } else {
                String hint = t("Колесо: масштаб", "Wheel: zoom", "Mausrad: Zoom");
                int[] p = f.place(ui.font.width(hint), true);
                if (p != null) ui.text(hint, p[0], Ui.textY(p[1] + 1, 16), Theme.DIM);
            }
        }
        int[] ip = f.place(Ui.INFO, false);
        ui.info(ip[0], ip[1] + 4, modeHelp());
        return any || g != 0 ? f.bottom() + 2 : y - 2;
    }

    private String modeHelp() {
        return switch (sub) {
            case OVERVIEW -> Help.overview();
            case ATTACK -> switch (attackMode) {
                case A_HITS -> hitList ? Help.hits() : Help.hitsChart();
                case A_AIM -> Help.aimTab();
                case A_STRIKES -> Help.types() + "\n" + Help.charge();
                case A_WTAP -> Help.wtap();
                default -> Help.combo();
            };
            case DEFENSE -> switch (defenseMode) {
                case D_KNOCKBACK -> Help.knockback();
                case D_TAKEN -> Help.taken();
                default -> jrList ? Help.jump() : Help.jumpChart() + "\n" + Help.jump();
            };
            default -> switch (fightsMode) {
                case F_OPPONENTS -> Help.opponents();
                case F_FIGHTS -> Help.fights();
                case F_TRADES -> Help.trades();
                case F_DISTANCE -> replayOn ? Help.replay() : Help.distance();
                default -> Help.timeline();
            };
        };
    }

    private static String weaponViewName(StatFilter.WeaponView v) {
        return switch (v) {
            case ALL -> t("Все", "All", "Alle");
            case SWORD -> t("Меч", "Sword", "Schwert");
            case HAND -> t("Рука", "Hand");
            case OTHER -> t("Другое", "Other", "Andere");
        };
    }

    static String weaponName(Weapon w) {
        return switch (w) {
            case SWORD -> t("Меч", "Sword", "Schwert");
            case AXE -> t("Топор", "Axe", "Axt");
            case MACE -> t("Булава", "Mace", "Streitkolben");
            case SPEAR -> t("Копьё", "Spear", "Speer");
            case HAND -> t("Рука", "Hand");
            case OTHER -> t("Другое", "Other", "Andere");
            case UNKNOWN -> t("Не меч (старая запись)", "Not a sword (old recording)", "Kein Schwert (alte Aufnahme)");
        };
    }

    static String hiddenName(StatFilter.Hidden h) {
        return switch (h) {
            case SWORD -> t("меч", "sword", "Schwert");
            case AXE -> t("топор", "axe", "Axt");
            case MACE -> t("булава", "mace", "Streitkolben");
            case SPEAR -> t("копьё", "spear", "Speer");
            case HAND -> t("рука", "hand", "Hand");
            case OTHER_WEAPON -> t("другое оружие", "other weapon", "andere Waffe");
            case UNKNOWN_WEAPON -> t("не меч", "not a sword", "kein Schwert");
            case TEAMMATE -> t("тиммейты", "teammates", "Teammitglieder");
            case NPC -> "NPC";
            case HIGH_PING -> t("высокий пинг", "high ping", "hoher Ping");
        };
    }

    private static String projectileName(String typeId) {
        String id = shortId(typeId);
        if (id == null) return t("неизвестно", "unknown", "unbekannt");
        return switch (id) {
            case "fishing_bobber" -> t("удочка", "rod", "Angel");
            case "arrow", "spectral_arrow" -> t("стрела", "arrow", "Pfeil");
            case "trident" -> t("трезубец", "trident", "Dreizack");
            case "snowball" -> t("снежок", "snowball", "Schneeball");
            case "egg" -> t("яйцо", "egg", "Ei");
            default -> id;
        };
    }

    private static String shortId(String id) {
        return id == null ? null : id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static String weaponText(Weapon w, String item) {
        String s = weaponName(w);
        return item == null || w == Weapon.HAND ? s : s + " (" + shortId(item) + ")";
    }

    private static String incomingKindText(IncomingHitEvent h) {
        if (h.kind() == null) return t("ближний (старая запись)", "melee (old recording)", "Nahkampf (alte Aufnahme)");
        if (h.kindOr() == IncomingHitEvent.Kind.PROJECTILE) {
            return t("снаряд · ", "projectile · ", "Geschoss · ") + projectileName(h.projectile());
        }
        return t("ближний · ", "melee · ", "Nahkampf · ") + weaponText(h.weaponOr(), h.attackerItem());
    }

    private static void toggleChip(Ui ui, Flow f, String label, boolean on, Runnable toggle) {
        int w = ui.font.width(label) + 12;
        int[] p = f.place(w, false);
        ui.chip(p[0], p[1] + 3, w, label, on, toggle);
    }

    private void graph(Ui ui, int x, int y, int w, int h) {
        if (graphKind() == 5) {
            distChart.cursor = replayOn ? replayView.time() : -1;
            distChart.onTimeClick = replayOn ? replayView::seek : null;
            if (replayOn) {
                int rh = h < 170 ? h : (int) (h * 0.6);
                replayView.render(ui, x, y, w, rh, distPoints, rangeLine, distName);
                if (rh == h) return;
                y += rh + 4;
                h -= rh + 4;
            }
        }
        Settings s = CombatDataClient.settings();
        String summary;
        int[] colors;
        String[] names;
        if (graphKind() == 1) {
            Stats.JumpStats j = res.jump();
            summary = t("Попыток ", "Attempts ", "Versuche ") + j.attempts() + t(" · успех ", " · success ", " · Erfolg ") + Fmt.pct(j.successRatePct()) + " · σ "
                    + (j.attempts() == 0 ? "—" : Fmt.num(j.sigmaMs(), 0) + t(" мс", " ms"));
            colors = new int[]{Theme.GREEN, Theme.RED, Theme.MUTED, Theme.BLUE};
            names = new String[]{t("успех", "success", "Erfolg"), t("рано/поздно", "early/late", "zu früh/zu spät"), t("без прыжка", "no jump", "kein Sprung"), "fly out"};
        } else if (graphKind() == 5) {
            summary = t("Противник ", "Opponent ", "Gegner ") + nz(distName) + t(" · средняя дистанция ", " · mean distance ", " · Ø Distanz ") + Fmt.num(distMean, 2)
                    + t(" бл.", " bl.", " Bl.") + (opp == null ? t(" · другой: фильтр в Противниках", " · other: filter in Opponents", " · andere: in „Gegner“ filtern") : "");
            colors = new int[]{Theme.GREEN, Theme.RED, Theme.AMBER};
            names = new String[]{t("ваш удар", "your hit", "dein Treffer"), t("его удар", "their hit", "sein Treffer"), "3 block hit"};
        } else if (graphKind() == 4) {
            Stats.TakenStats tk = res.taken();
            summary = t("Ударов по вам ", "Hits on you ", "Treffer auf dich ") + tk.rated() + t(" · ср. дистанция ", " · avg. distance ", " · Ø Distanz ") + Fmt.num(tk.reachMean(), 2)
                    + t(" · 3 block hit против вас ", " · 3 block hit against you ", " · 3 Block Hit gegen dich ") + tk.caught() + " (" + Fmt.pct(tk.caughtPct()) + ")";
            colors = new int[]{Theme.RED, Theme.AMBER, Theme.DIM};
            names = new String[]{t("удар", "hit", "Treffer"), "3 block hit", t("снаряд", "projectile", "Geschoss")};
        } else {
            Stats.HitStats hs = res.hits();
            summary = t("Точность ", "Accuracy ", "Trefferquote ") + Fmt.pct(hs.hitRatePct()) + t(" · ср. дистанция ", " · avg. distance ", " · Ø Distanz ")
                    + (hs.confirmed() == 0 ? "—" : Fmt.num(hs.reachMean(), 2)) + " · 3 block hit " + res.threeBlock().count();
            colors = new int[]{Theme.GREEN, Theme.AMBER, Theme.RED, Theme.DIM};
            names = new String[]{t("попал", "hit", "Treffer"), "3 block hit", t("промах", "miss", "Fehlschlag"), t("без подтв.", "unconfirmed", "unbestätigt")};
        }
        if (showFiltered) {
            names = Arrays.copyOf(names, names.length + 1);
            colors = Arrays.copyOf(colors, colors.length + 1);
            names[names.length - 1] = t("отфильтр.", "filtered", "gefiltert");
            colors[colors.length - 1] = Theme.FILTERED;
        }
        int legendW = 0;
        for (String n : names) legendW += ui.font.width(n) + 16;
        boolean twoLines = w - legendW < Math.min(ui.font.width(summary), 160) + 8;
        if (twoLines) {
            line(ui, w, summary, x, y, Theme.TEXT);
            y += LINE + 1;
            h -= LINE + 1;
        }
        int lx = x + w;
        for (int i = names.length - 1; i >= 0; i--) {
            lx -= ui.font.width(names[i]);
            ui.text(names[i], lx, y, Theme.MUTED);
            lx -= 8;
            Draw.circle(ui.g, lx, y + 1, 5, colors[i]);
            lx -= 8;
        }
        if (!twoLines) line(ui, lx - x - 8, summary, x, y, Theme.TEXT);
        Chart c = chart();
        c.total(rec.durationMs());
        if (graphKind() == 1) {
            c.render(ui, x, y + 12, w, h - 12, jrPoints, new Chart.Axis(-s.jrPreMs, s.jrPostMs, 50,
                    v -> String.valueOf((long) v), s.jrLowerMs, s.jrUpperMs, Theme.alpha(Theme.GREEN, 0x40),
                    new double[]{0}, new int[]{Theme.MUTED}));
        } else if (graphKind() == 4) {
            c.render(ui, x, y + 12, w, h - 12, takenPoints, new Chart.Axis(0, 6, 1, v -> String.valueOf((long) v),
                    0, 0, 0, new double[]{takenRange}, new int[]{Theme.ACCENT}));
        } else if (graphKind() == 5) {
            c.render(ui, x, y + 12, w, h - 12, distPoints, new Chart.Axis(0, 8, 1, v -> String.valueOf((long) v),
                    0, 0, 0, new double[]{rangeLine}, new int[]{Theme.ACCENT}), distLine);
        } else {
            c.render(ui, x, y + 12, w, h - 12, hitPoints, new Chart.Axis(0, 6, 1, v -> String.valueOf((long) v),
                    0, 0, 0, new double[]{rangeLine}, new int[]{Theme.ACCENT}));
        }
    }

    private List<Chart.Point> buildTakenPoints() {
        List<Chart.Point> out = new ArrayList<>();
        takenRange = AttackEvent.DEFAULT_RANGE;
        boolean rangeSet = false;
        for (Event e : rawEvents) {
            if (!(e instanceof IncomingHitEvent h) || !Double.isFinite(h.reach())) continue;
            if (!rangeSet && Double.isFinite(h.range())) {
                takenRange = h.range();
                rangeSet = true;
            }
            StatFilter.Hidden why = sf.incoming(h);
            int color;
            String what;
            if (why != null) {
                if (!showFiltered) continue;
                color = Theme.FILTERED;
                what = t("отфильтровано: ", "filtered: ", "gefiltert: ") + hiddenName(why);
            } else if (!h.rated()) {
                if (!showProjectiles) continue;
                color = Theme.DIM;
                what = t("снаряд", "projectile", "Geschoss");
            } else if (h.caught()) {
                color = Theme.AMBER;
                what = t("3 block hit против вас", "3 block hit against you", "3 Block Hit gegen dich");
            } else {
                color = Theme.RED;
                what = weaponName(h.weaponOr());
            }
            out.add(new Chart.Point(h.t(), h.reach(), color, false, Fmt.clockMs(h.t()) + " · " + nz(h.attackerName()) + " · "
                    + Fmt.num(h.reach(), 2) + t(" бл. · ", " bl. · ", " Bl. · ") + what, () -> takenDetails(h), h));
        }
        return out;
    }

    private void buildDistance() {
        distOpp = opp != null ? opp : Distance.mainOpponent(events);
        distName = opp != null ? oppName : null;
        distSeries = Distance.series(rawEvents, distOpp);
        distPoints = List.of();
        distLine = null;
        distMean = Double.NaN;
        if (distSeries.isEmpty()) {
            replayView.set(List.of(), null, List.of());
            return;
        }
        long[] ts = new long[distSeries.size()];
        double[] vs = new double[distSeries.size()];
        double sum = 0;
        for (int i = 0; i < ts.length; i++) {
            ts[i] = distSeries.get(i).t();
            vs[i] = distSeries.get(i).d();
            sum += vs[i];
        }
        distMean = sum / ts.length;
        distLine = new Chart.Line(ts, vs, Theme.MUTED, Distance.MAX_GAP_MS);
        int interp = CombatDataClient.settings().interpMs;
        List<Chart.Point> out = new ArrayList<>();
        for (Event e : rawEvents) {
            if (e instanceof AttackEvent a && a.outcome() == Outcome.HIT_CONFIRMED && distOpp.equals(a.target())) {
                if (distName == null) distName = a.targetName();
                double d = Distance.at(distSeries, a.t());
                StatFilter.Hidden why = sf.attack(a);
                if (Double.isNaN(d) || (why != null && !showFiltered)) continue;
                int color = why != null ? Theme.FILTERED : ThreeBlockHit.is(a, interp) ? Theme.AMBER : Theme.GREEN;
                out.add(new Chart.Point(a.t(), d, color, false, Fmt.clockMs(a.t()) + t(" · ваш удар · ", " · your hit · ", " · dein Treffer · ")
                        + Fmt.num(d, 2) + t(" бл.", " bl.", " Bl.") + (why != null ? t(" · отфильтровано", " · filtered", " · gefiltert") : ""),
                        () -> hitDetails(a, CombatDataClient.settings()), a));
            } else if (e instanceof IncomingHitEvent h && distOpp.equals(h.attacker())) {
                if (distName == null) distName = h.attackerName();
                double d = Distance.at(distSeries, h.t());
                StatFilter.Hidden why = sf.incoming(h);
                if (Double.isNaN(d) || (why != null && !showFiltered)) continue;
                int color = why != null ? Theme.FILTERED : h.caught() ? Theme.AMBER : Theme.RED;
                out.add(new Chart.Point(h.t(), d, color, false, Fmt.clockMs(h.t()) + t(" · его удар · ", " · their hit · ", " · sein Treffer · ")
                        + Fmt.num(d, 2) + t(" бл.", " bl.", " Bl.") + (why != null ? t(" · отфильтровано", " · filtered", " · gefiltert") : ""),
                        () -> takenDetails(h), h));
            }
        }
        distPoints = out;
        replayView.set(Replay.frames(rawEvents, distOpp, timeFrom >= 0 ? timeFrom : 0, timeFrom >= 0 ? timeTo : Long.MAX_VALUE), distOpp, attempts);
    }

    private int distanceTab(Ui ui, int x, int y, int w) {
        if (distOpp != null && rec.skipped(Features.POSITIONS)) return skipped(ui, w, t("Позиции во время боя", "Positions during fights", "Positionen im Kampf"), x, y);
        String msg = distOpp == null ? t("Ударов нет: не с кем считать дистанцию.", "No hits: no opponent to measure distance to.", "Keine Treffer: kein Gegner, zu dem man die Distanz messen könnte.")
                : t("Нет позиций: они пишутся во время боя в новых записях (Настройки → Основные → Что записывать → «Позиции во время боя»).",
                "No positions: they are recorded during fights in newer recordings (Settings → General → What to record → Positions during fights).", "Keine Positionen: Sie werden in neueren Aufnahmen während Kämpfen aufgenommen (Einstellungen → Allgemein → Was aufnehmen → Positionen im Kampf).");
        line(ui, w, msg, x, y, Theme.DIM);
        return y + LINE;
    }

    private List<String[]> takenDetails(IncomingHitEvent h) {
        List<String[]> rows = new ArrayList<>();
        StatFilter.Hidden why = sf.incoming(h);
        rows.add(new String[]{(h.caught() ? t("3 block hit против вас", "3 block hit against you", "3 Block Hit gegen dich") : t("Удар по вам", "Hit on you", "Treffer auf dich"))
                + (why != null ? t(" · не в статистике: ", " · not counted: ", " · nicht gezählt: ") + hiddenName(why) : ""), ""});
        rows.add(new String[]{t("Время от начала", "Time from start", "Zeit ab Start"), Fmt.clockMs(h.t())});
        rows.add(new String[]{t("Кто ударил", "Hit by", "Getroffen von"), nz(h.attackerName())});
        rows.add(new String[]{t("Удар", "Hit", "Treffer"), incomingKindText(h)});
        rows.add(new String[]{t("Он на вашем экране", "Them on your screen", "Er auf deinem Bildschirm"), Fmt.num(h.reach(), 2) + t(" бл.", " bl.", " Bl.")});
        rows.add(new String[]{t("Ваша дальность", "Your range", "Deine Reichweite"), Fmt.num(h.rangeOr(AttackEvent.DEFAULT_RANGE), 2) + t(" бл.", " bl.", " Bl.")});
        rows.add(new String[]{t("Его скорость к вам", "Their speed to you", "Sein Tempo auf dich zu"), Fmt.signed(h.attackerTowardBps(), 1) + t(" бл/с", " bl/s", " Bl./s")});
        rows.add(new String[]{t("Ваша скорость от него", "Your speed away", "Dein Tempo weg"), Fmt.signed(h.selfAwayBps(), 1) + t(" бл/с", " bl/s", " Bl./s")});
        rows.add(new String[]{t("Пинг его / ваш", "Ping theirs / yours", "Ping seiner / deiner"), (h.attackerPingMs() >= 0 ? h.attackerPingMs() + t(" мс", " ms") : "—")
                + " / " + (h.pingMs() > 0 ? h.pingMs() + t(" мс", " ms") : "—")});
        rows.add(new String[]{t("Отлёт за 0,5 с", "Knockback in 0.5 s", "Rückstoß in 0,5 s"), kbText(h.kbAway())});
        rows.add(new String[]{t("Сервер", "Server"), serverAt(h.t())});
        return rows;
    }

    private static String resultName(JumpResetEvaluator.Result r) {
        return switch (r) {
            case SUCCESS -> t("Успех", "Success", "Erfolg");
            case TOO_EARLY -> t("Рано", "Early", "Zu früh");
            case TOO_LATE -> t("Поздно", "Late", "Zu spät");
            case NO_ATTEMPT -> t("Без прыжка", "No jump", "Kein Sprung");
            case NOT_APPLICABLE -> t("Не оценивается", "Not rated", "Nicht bewertet");
            case FLY_OUT -> "Fly out";
        };
    }

    private List<Chart.Point> buildJumpPoints(Settings s) {
        List<Chart.Point> out = new ArrayList<>();
        for (JumpResetEvaluator.Attempt a : attempts) {
            IncomingHitEvent h = a.hit();
            switch (a.result()) {
                case NOT_APPLICABLE -> { }
                case NO_ATTEMPT -> {
                    if (showNoJump) out.add(new Chart.Point(h.t(), 0, Theme.MUTED, true,
                            Fmt.clockMs(h.t()) + t(" · без прыжка · ", " · no jump · ", " · kein Sprung · ") + nz(h.attackerName()), () -> jumpDetails(a, s), h));
                }
                case FLY_OUT -> {
                    if (showFlyOut) out.add(new Chart.Point(h.t(), a.deltaMs(), Theme.BLUE, false, Fmt.clockMs(h.t()) + " · "
                            + Fmt.signed(a.deltaMs(), 0) + t(" мс · ", " ms · ") + "fly out · " + kbText(h.kbAway()),
                            () -> jumpDetails(a, s), h));
                }
                default -> {
                    boolean ok = a.result() == JumpResetEvaluator.Result.SUCCESS;
                    if (!ok && !showFails) continue;
                    int color = a.held() && s.jrExcludeHeld ? Theme.DIM : ok ? Theme.GREEN : Theme.RED;
                    out.add(new Chart.Point(h.t(), a.deltaMs(), color, false, Fmt.clockMs(h.t()) + " · "
                            + Fmt.signed(a.deltaMs(), 0) + t(" мс · ", " ms · ") + resultName(a.result()), () -> jumpDetails(a, s), h));
                }
            }
        }
        if (showFiltered) {
            for (JumpResetEvaluator.Attempt a : JumpResetEvaluator.evaluate(rawEvents, s.jumpParams())) {
                IncomingHitEvent h = a.hit();
                StatFilter.Hidden why = sf.incoming(h);
                if (why == null || a.result() == JumpResetEvaluator.Result.NOT_APPLICABLE) continue;
                out.add(new Chart.Point(h.t(), a.hasJump() ? a.deltaMs() : 0, Theme.FILTERED, !a.hasJump(), Fmt.clockMs(h.t())
                        + t(" · отфильтровано: ", " · filtered: ", " · gefiltert: ") + hiddenName(why) + " · " + nz(h.attackerName()),
                        () -> jumpDetails(a, s), h));
            }
        }
        return out;
    }

    private List<String[]> jumpDetails(JumpResetEvaluator.Attempt a, Settings s) {
        IncomingHitEvent h = a.hit();
        long d = a.deltaMs();
        String rel = !a.hasJump() ? "—" : d < s.jrLowerMs ? t("раньше на ", "early by ", "zu früh um ") + (s.jrLowerMs - d) + t(" мс", " ms")
                : d > s.jrUpperMs ? t("позже на ", "late by ", "zu spät um ") + (d - s.jrUpperMs) + t(" мс", " ms") : t("в окне ", "in window ", "im Fenster ") + s.jrLowerMs + "…" + s.jrUpperMs + t(" мс", " ms");
        List<String[]> rows = new ArrayList<>();
        StatFilter.Hidden why = sf.incoming(h);
        rows.add(new String[]{why != null ? t("Не в статистике: ", "Not counted: ", "Nicht gezählt: ") + hiddenName(why)
                : a.result() == JumpResetEvaluator.Result.FLY_OUT ? t("Fly out (не jump reset)", "Fly out (not a jump reset)", "Fly out (kein Jump Reset)")
                : "Jump reset: " + resultName(a.result()), ""});
        rows.add(new String[]{t("Время от начала", "Time from start", "Zeit ab Start"), Fmt.clockMs(h.t())});
        rows.add(new String[]{t("Удар", "Hit", "Treffer"), incomingKindText(h)});
        if (Double.isFinite(h.reach())) rows.add(new String[]{t("Он на вашем экране", "Them on your screen", "Er auf deinem Bildschirm"), Fmt.num(h.reach(), 2) + t(" бл.", " bl.", " Bl.")
                + (h.caught() ? t(" · 3 block hit против вас", " · 3 block hit against you", " · 3 Block Hit gegen dich") : "")});
        rows.add(new String[]{t("Прыжок", "Jump", "Sprung"), a.hasJump() ? Fmt.signed(d, 0) + t(" мс после удара", " ms after the hit", " ms nach dem Treffer") : t("не было", "none", "keine")});
        rows.add(new String[]{t("Относительно окна", "Relative to window", "Relativ zum Fenster"), rel});
        JumpEvent je = a.hasJump() ? jumpAt(a.jumpT()) : null;
        if (je != null) rows.add(new String[]{t("Клавиши при прыжке", "Keys at jump", "Tasten beim Sprung"), keysText(je)});
        rows.add(new String[]{t("Кто ударил", "Hit by", "Getroffen von"), nz(h.attackerName())});
        rows.add(new String[]{t("В момент удара", "At the hit", "Beim Treffer"), h.groundedAtHit() ? t("на земле", "on ground", "am Boden") : t("в воздухе", "in the air", "in der Luft")});
        rows.add(new String[]{t("Прыжок зажат", "Jump held", "Sprung gehalten"), h.jumpHeld() ? (s.jrExcludeHeld ? t("да, исключён", "yes, excluded", "ja, ausgeschlossen") : t("да", "yes", "ja")) : t("нет", "no", "nein")});
        rows.add(new String[]{t("Нокбэк", "Knockback", "Rückstoß"), h.knockbackApplied() ? t("да", "yes", "ja") : t("нет", "no", "nein")});
        rows.add(new String[]{t("Отлёт за 0,5 с", "Knockback in 0.5 s", "Rückstoß in 0,5 s"), kbText(h.kbAway())});
        rows.add(new String[]{t("Пинг", "Ping"), h.pingMs() > 0 ? h.pingMs() + t(" мс", " ms") : "—"});
        rows.add(new String[]{t("T удара / прыжка", "T of hit / jump", "Zeit Treffer / Sprung"), Fmt.clockMs(h.t()) + " / " + (a.hasJump() ? Fmt.clockMs(a.jumpT()) : "—")});
        rows.add(new String[]{t("Сервер", "Server"), serverAt(h.t())});
        return rows;
    }

    private JumpEvent jumpAt(long t) {
        for (Event e : events) if (e instanceof JumpEvent j && j.t() == t) return j;
        return null;
    }

    static String keysText(JumpEvent j) {
        if (!j.keysKnown()) return t("неизвестно (старая запись)", "unknown (old recording)", "unbekannt (alte Aufnahme)");
        List<String> k = new ArrayList<>();
        if (j.has(JumpEvent.KEY_FORWARD)) k.add("W");
        if (j.has(JumpEvent.KEY_LEFT)) k.add("A");
        if (j.has(JumpEvent.KEY_BACK)) k.add("S");
        if (j.has(JumpEvent.KEY_RIGHT)) k.add("D");
        String s = k.isEmpty() ? t("нет", "none", "keine") : String.join("+", k);
        return j.has(JumpEvent.KEY_SPRINT) ? s + t(", спринт", ", sprint", ", Sprint") : s;
    }

    private List<Chart.Point> buildHitPoints(Settings s) {
        List<Chart.Point> out = new ArrayList<>();
        rangeLine = AttackEvent.DEFAULT_RANGE;
        boolean rangeSet = false;
        for (Event e : rawEvents) {
            if (!(e instanceof AttackEvent a)) continue;
            if (!rangeSet && Double.isFinite(a.range())) {
                rangeLine = a.range();
                rangeSet = true;
            }
            StatFilter.Hidden why = sf.attack(a);
            if (why != null) {
                if (showFiltered) out.add(new Chart.Point(a.t(), a.reach(), Theme.FILTERED, false, Fmt.clockMs(a.t()) + " · "
                        + nz(a.targetName()) + " · " + Fmt.num(a.reach(), 2) + t(" бл. · отфильтровано: ", " bl. · filtered: ", " Bl. · gefiltert: ")
                        + hiddenName(why), () -> hitDetails(a, s), a));
                continue;
            }
            int color;
            switch (a.outcome()) {
                case HIT_CONFIRMED -> color = ThreeBlockHit.is(a, s.interpMs) ? Theme.AMBER : Theme.GREEN;
                case HIT_UNCONFIRMED -> {
                    if (!showUnconfirmed) continue;
                    color = Theme.DIM;
                }
                default -> {
                    if (!showMisses) continue;
                    color = Theme.RED;
                }
            }
            out.add(new Chart.Point(a.t(), a.reach(), color, false, Fmt.clockMs(a.t()) + " · " + nz(a.targetName()) + " · "
                    + Fmt.num(a.reach(), 2) + t(" бл. · ", " bl. · ", " Bl. · ") + outcomeName(a, s.interpMs), () -> hitDetails(a, s), a));
        }
        return out;
    }

    private Cooldown.Summary cdSummary() {
        return cdSum;
    }

    private static String outcomeName(AttackEvent a, int interp) {
        return switch (a.outcome()) {
            case HIT_CONFIRMED -> ThreeBlockHit.is(a, interp) ? "3 block hit" : t("Попал", "Hit", "Treffer");
            case HIT_UNCONFIRMED -> t("Без подтверждения", "Unconfirmed", "Unbestätigt");
            case MISS -> a.outOfRangeMiss(a.rangeOr(AttackEvent.DEFAULT_RANGE)) ? t("Не дотянулся", "Out of range", "Zu weit weg") : t("Мимо", "Missed", "Daneben");
        };
    }

    private List<String[]> hitDetails(AttackEvent a, Settings s) {
        List<String[]> rows = new ArrayList<>();
        StatFilter.Hidden why = sf.attack(a);
        rows.add(new String[]{outcomeName(a, s.interpMs) + (why != null ? t(" · не в статистике: ", " · not counted: ", " · nicht gezählt: ") + hiddenName(why) : ""), ""});
        rows.add(new String[]{t("Время от начала", "Time from start", "Zeit ab Start"), Fmt.clockMs(a.t())});
        rows.add(new String[]{t("Цель", "Target", "Ziel"), nz(a.targetName())});
        rows.add(new String[]{t("Оружие", "Weapon", "Waffe"), weaponText(a.weaponOr(), a.item())});
        rows.add(new String[]{a.outcome() == Outcome.MISS ? t("До ближайшей точки", "To the nearest point", "Zum nächsten Punkt") : t("Дистанция", "Distance", "Distanz"), Fmt.num(a.reach(), 2) + t(" бл.", " bl.", " Bl.")});
        if (a.outcome() == Outcome.MISS) {
            rows.add(new String[]{t("Луч до хитбокса", "Ray to hitbox", "Strahl zur Hitbox"), Double.isFinite(a.rayDist()) ? Fmt.num(a.rayDist(), 2) + t(" бл.", " bl.", " Bl.") : t("мимо", "missed", "daneben")});
        }
        rows.add(new String[]{t("Дальность атаки", "Attack range", "Reichweite"), Fmt.num(a.rangeOr(AttackEvent.DEFAULT_RANGE), 2) + t(" бл.", " bl.", " Bl.")});
        rows.add(new String[]{t("Тип удара", "Hit type", "Schlagart"), typeName(a.type())});
        rows.add(new String[]{t("Заряд", "Charge", "Aufladung"), Fmt.pct(a.strength() * 100)});
        rows.add(new String[]{t("Прицел yaw / pitch", "Aim yaw / pitch", "Ziel Yaw / Pitch"), Fmt.signed(a.aimYawDeg(), 1) + "° / " + Fmt.signed(a.aimPitchDeg(), 1) + "°"});
        rows.add(new String[]{t("Высота попадания", "Hit height", "Trefferhöhe"), Double.isFinite(a.hitV()) ? Fmt.pct(a.hitV() * 100) + t(" роста", " of height", " der Höhe") : "—"});
        if (a.outcome() == Outcome.HIT_CONFIRMED) {
            double od = ThreeBlockHit.opponentDistance(a, s.interpMs);
            rows.add(new String[]{t("У противника на экране", "On opponent's screen", "Auf dem Bildschirm des Gegners"), Double.isNaN(od) ? t("нет данных", "no data", "keine Daten") : "≈ " + Fmt.num(od, 1) + t(" бл.", " bl.", " Bl.")});
        }
        rows.add(new String[]{t("Пинг ваш / цели", "Ping yours / target", "Ping deiner / Ziel"), (a.pingMs() > 0 ? a.pingMs() + t(" мс", " ms") : "—") + " / "
                + (a.targetPingMs() >= 0 ? a.targetPingMs() + t(" мс", " ms") : "—")});
        rows.add(new String[]{t("Сервер", "Server"), serverAt(a.t())});
        return rows;
    }

    private String serverAt(long t) {
        String s = null;
        for (Event e : rec.events()) {
            if (e.t() > t) break;
            if (e instanceof SegmentEvent seg) s = seg.server();
        }
        return s == null ? "—" : s;
    }

    private boolean visible(int y, int h) {
        return y + h >= py && y <= py + ph;
    }


    private int overview(Ui ui, int x, int y, int w) {
        if (note != null) {
            line(ui, w, note, x, y, Theme.MUTED);
            y += LINE + 4;
        }
        y = hiddenLine(ui, x, y, w);
        y = coachBlock(ui, x, y, w);
        y = compareLine(ui, x, y, w);
        y = insightsBlock(ui, x, y, w);
        boolean expert = CombatDataClient.settings().expertMode;
        List<Card> cards = new ArrayList<>();
        Stats.HitStats hs = res.hits();
        int attacksTotal = hs.confirmed() + hs.unconfirmed() + hs.misses();
        if (attacksTotal == 0 && res.combo().taken() == 0) {
            cards.add(new Card(t("Бой", "Fight", "Kampf"), List.of(new Kv(t("Боевых событий нет", "No combat events", "Keine Kampfereignisse"), "")), 1));
        } else {
            cards.add(hitsCard(hs).with(Help.hits()));
            cards.add(jumpCard(res.jump()).with(Help.jump()));
        }
        if (attacksTotal > 0 && !expert) {
            cards.add(aimCard(hs, aimRes).with(Help.aim()));
            cards.add(tradesCard(res.trades()).with(Help.trades()));
        } else if (attacksTotal > 0 || res.combo().taken() > 0) {
            if (res.taken().rated() > 0) cards.add(takenCard(res.taken(), res.threeBlock()).with(Help.taken()));
            cards.add(reachCard(hs).with(Help.reach()));
            cards.add(aimCard(hs, aimRes).with(Help.aim()));
            cards.add(threeBlockCard(hs).with(Help.threeBlock()));
            cards.add(comboCard(res.combo()).with(Help.combo()));
            cards.add(tradesCard(res.trades()).with(Help.trades()));
            cards.add(chargeCard(hs).with(Help.charge()));
            cards.add(typesCard(hs).with(Help.types()));
            if (wtap.hasInput()) cards.add(wtapCard(wtap).with(Help.wtap()));
            cards.add(new Card(t("Пинг", "Ping"), List.of(
                    new Kv(t("Средний", "Mean", "Ø"), res.pingMean() > 0 ? Fmt.num(res.pingMean(), 0) + t(" мс", " ms") : "—"),
                    new Kv(t("Разброс σ", "Spread σ", "Streuung σ"), res.pingMean() > 0 ? Fmt.num(res.pingSigma(), 0) + t(" мс", " ms") : "—")), 2).with(Help.ping()));
        }
        if (expert) cards.add(recordCard());
        int cols = w >= 330 ? 2 : 1, gap = 8, cw = (w - gap * (cols - 1)) / cols;
        int[] colY = new int[cols];
        Arrays.fill(colY, y);
        for (Card c : cards) {
            int col = 0;
            for (int i = 1; i < cols; i++) if (colY[i] < colY[col]) col = i;
            int cx = x + col * (cw + gap);
            boolean open = expanded.contains(c.title());
            if (visible(colY[col], c.height(open))) card(ui, cx, colY[col], cw, c, open);
            colY[col] += c.height(open) + gap;
        }
        int bottom = y;
        for (int v : colY) bottom = Math.max(bottom, v);
        return glossaryBlock(ui, x, bottom + 2, w);
    }

    /** Key numbers: graded color, or strong text when there is too little data to judge. */
    private static int graded(SkillScores.Grade g) {
        return g == SkillScores.Grade.NONE ? Theme.TEXT_STRONG : gradeColor(g);
    }

    private static int gradeColor(SkillScores.Grade g) {
        return switch (g) {
            case GOOD -> Theme.GREEN;
            case FAIR -> Theme.AMBER;
            case WORK -> Theme.RED;
            case NONE -> Theme.DIM;
        };
    }

    /** One tile per skill: grade, value, what is good and what the number means. Click opens the skill's page. */
    private int coachBlock(Ui ui, int x, int y, int w) {
        Stats.HitStats hs = res.hits();
        if (hs.confirmed() + hs.unconfirmed() + hs.misses() + res.combo().taken() == 0) return y;
        List<SkillScores.Tile> tiles = SkillScores.tiles(res, aimRes);
        int cols = w >= 440 ? 4 : w >= 220 ? 2 : 1, gap = 6, tw = (w - gap * (cols - 1)) / cols;
        int th = 4 + LINE * 2 + 2;
        int maxLines = 1;
        for (SkillScores.Tile tl : tiles) maxLines = Math.max(maxLines, Math.min(3, ui.wrap(tl.explain(), tw - 14).size()));
        th += maxLines * LINE + 4;
        for (int i = 0; i < tiles.size(); i++) {
            SkillScores.Tile tl = tiles.get(i);
            int tx = x + (i % cols) * (tw + gap), ty = y + (i / cols) * (th + gap);
            int color = gradeColor(tl.grade());
            boolean hov = ui.hover(tx, ty, tw, th);
            Draw.round(ui.g, tx, ty, tw, th, Theme.R_CTRL, hov ? Theme.ROW_HOVER : Theme.WINDOW);
            ui.g.fill(tx, ty + 3, tx + 3, ty + th - 3, color);
            ui.text(Draw.ellipsize(ui.font, tl.name(), tw - 60), tx + 8, ty + 4, Theme.TEXT_STRONG);
            ui.textRight(tl.gradeText(), tx + tw - 5, ty + 4, color);
            String v = tl.value();
            ui.text(v, tx + 8, ty + 4 + LINE, color);
            line(ui, tw - 16 - ui.font.width(v), "  " + tl.target(), tx + 8 + ui.font.width(v), ty + 4 + LINE, Theme.DIM);
            int ly = ty + 6 + LINE * 2;
            List<String> ls = ui.wrap(tl.explain(), tw - 14);
            for (int k = 0; k < Math.min(3, ls.size()); k++) {
                ui.text(ls.get(k), tx + 8, ly, Theme.MUTED);
                ly += LINE;
            }
            Insights.Page page = tl.page();
            ui.click(tx, ty, tw, th, () -> go(page));
        }
        int rowsN = (tiles.size() + cols - 1) / cols;
        return y + rowsN * (th + gap) + 2;
    }

    private boolean glossaryOpen;

    /** Terms used across the mod, explained once in plain words. */
    private int glossaryBlock(Ui ui, int x, int y, int w) {
        String head = (glossaryOpen ? "▾ " : "▸ ") + t("Словарь терминов", "Glossary", "Begriffe erklärt");
        boolean hov = ui.hover(x, y, w, 14);
        ui.text(head, x, Ui.textY(y, 14), hov ? Theme.TEXT_STRONG : Theme.MUTED);
        ui.click(x, y, w, 14, () -> glossaryOpen = !glossaryOpen);
        y += 16;
        if (!glossaryOpen) return y;
        for (String[] e : Help.glossary()) {
            ui.text(e[0], x + 4, y, Theme.TEXT_STRONG);
            y += LINE;
            for (String l : ui.wrap(e[1], w - 12)) {
                if (visible(y, LINE)) ui.text(l, x + 8, y, Theme.MUTED);
                y += LINE;
            }
            y += 3;
        }
        return y;
    }

    private int hiddenLine(Ui ui, int x, int y, int w) {
        String text, bl;
        Runnable action;
        if (filtersOff) {
            text = t("Фильтры статистики выключены для этой записи", "Stat filters are off for this recording", "Statistikfilter sind für diese Aufnahme aus");
            bl = t("Вернуть", "Restore", "Wiederherstellen");
            action = () -> {
                filtersOff = false;
                weaponOverride = null;
            };
        } else {
            int n = res.hiddenTotal();
            if (n == 0) return y;
            List<String> parts = new ArrayList<>();
            for (Map.Entry<StatFilter.Hidden, Integer> e : res.hidden().entrySet()) parts.add(hiddenName(e.getKey()) + " " + e.getValue());
            text = t("Скрыто фильтрами: ", "Hidden by filters: ", "Durch Filter ausgeblendet: ") + n + " " + (Lang.german() ? "Treffer" : Lang.english() ? (n == 1 ? "hit" : "hits")
                    : Lang.plural(n, "удар", "удара", "ударов")) + " (" + String.join(", ", parts) + ")";
            bl = t("Показать", "Show", "Anzeigen");
            action = () -> {
                filtersOff = true;
                weaponOverride = StatFilter.WeaponView.ALL;
            };
        }
        int bw = ui.buttonWidth(bl);
        line(ui, w - bw - Ui.INFO - 12, text, x, Ui.textY(y, 14), Theme.DIM);
        ui.info(x + w - bw - Ui.INFO - 5, y + 2, Help.hidden());
        ui.button(x + w - bw, y, bw, 14, bl, Ui.Kind.GHOST, true, action);
        return y + 18;
    }

    private static final int COMPARE_WITH = 5;
    private String compareKey;
    private List<Progress.Delta> deltas = List.of();
    private int compareN;

    private int compareLine(Ui ui, int x, int y, int w) {
        Settings s = CombatDataClient.settings();
        ProgressData.request(s, false);
        ProgressData.Snapshot snap = ProgressData.snapshot();
        String want = Progress.cacheKey(s.statFilter(), s.jumpParams(), s.interpMs, s.tradeGapMs);
        // Not keyed on the recording identity: the live buffer is a new object every second, but recompute is only needed on new events.
        long lastT = rec.events().isEmpty() ? 0 : rec.events().get(rec.events().size() - 1).t();
        String k = ProgressData.version() + "/" + want + "/" + rec.wallStartMs() / 60_000 + "/" + rec.events().size() + "/" + lastT;
        if (!k.equals(compareKey)) {
            compareKey = k;
            deltas = List.of();
            compareN = 0;
            if (snap != null && snap.key().equals(want)) {
                List<Progress.Summary> prev = Progress.previous(snap.summaries(), rec.wallStartMs(), COMPARE_WITH);
                compareN = prev.size();
                if (!prev.isEmpty()) deltas = Progress.compare(
                        Progress.summarize(null, rec, s.jumpParams(), s.interpMs, s.statFilter(), s.tradeGapMs, false).all(), prev);
            }
        }
        if (deltas.isEmpty()) return y;
        List<String> parts = new ArrayList<>();
        for (Progress.Delta d : deltas) {
            if (parts.size() == 3) break;
            String v = d.metric().percent ? Fmt.signed(d.diff(), 0) + "%" : Fmt.signed(d.diff(), 2) + t(" бл.", " bl.", " Bl.");
            parts.add(ProgressTab.metricName(d.metric(), false).toLowerCase(Locale.ROOT) + " " + v);
        }
        String head = Lang.german() ? "Verglichen mit " + (compareN == 1 ? "der letzten Aufnahme: " : "den letzten " + compareN + " Aufnahmen: ")
                : Lang.english() ? "Compared with the last " + compareN + (compareN == 1 ? " recording: " : " recordings: ")
                : "По сравнению с " + (compareN == 1 ? "прошлой записью: " : "прошлыми " + compareN + " записями: ");
        line(ui, w, head + String.join(", ", parts), x, y, Theme.MUTED);
        return y + LINE + 4;
    }

    private int insightsBlock(Ui ui, int x, int y, int w) {
        if (insights.isEmpty()) {
            if (res.hits().confirmed() + res.hits().misses() + res.combo().taken() == 0) return y;
            line(ui, w, t("Выводы: явных проблем не найдено", "Insights: no obvious problems found", "Erkenntnisse: keine offensichtlichen Probleme gefunden"), x, y, Theme.DIM);
            return y + LINE + 6;
        }
        int rh = 16;
        for (Insights.Insight in : insights) {
            int color = switch (in.tone()) {
                case BAD -> Theme.RED;
                case TIP -> Theme.AMBER;
                case GOOD -> Theme.GREEN;
            };
            boolean hov = ui.hover(x, y, w, rh - 2);
            Draw.round(ui.g, x, y, w, rh - 2, Theme.R_CTRL, hov ? Theme.ROW_HOVER : Theme.WINDOW);
            ui.g.fill(x, y + 2, x + 3, y + rh - 4, color);
            ui.text(Draw.ellipsize(ui.font, in.text(), w - 22), x + 8, Ui.textY(y, rh - 2), hov ? Theme.TEXT_STRONG : Theme.TEXT);
            ui.textRight("›", x + w - 5, Ui.textY(y, rh - 2), hov ? Theme.TEXT_STRONG : Theme.MUTED);
            Insights.Page page = in.page();
            ui.click(x, y, w, rh - 2, () -> go(page));
            y += rh;
        }
        return y + 4;
    }

    private void card(Ui ui, int x, int y, int w, Card c, boolean open) {
        Draw.round(ui.g, x, y, w, c.height(open), Theme.R_CTRL, Theme.WINDOW);
        boolean more = c.rows().size() > c.main();
        int titleW = w - 12;
        int right = x + w - 6;
        if (c.info() != null) {
            ui.info(right - Ui.INFO, y + 3, c.info());
            right -= Ui.INFO + 5;
            titleW -= Ui.INFO + 5;
        }
        if (more) {
            String full = open ? t("Свернуть ▴", "Less ▴", "Weniger ▴") : t("Подробнее ▾", "More ▾", "Mehr ▾"), arrow = open ? "▴" : "▾";
            String label = ui.font.width(c.title()) + ui.font.width(full) + 20 + (c.info() != null ? Ui.INFO + 5 : 0) <= w ? full : arrow;
            boolean hov = ui.hover(x, y, right - x, 14);
            ui.textRight(label, right, y + 4, hov ? Theme.TEXT_STRONG : Theme.DIM);
            titleW -= ui.font.width(label) + 6;
            String key = c.title();
            ui.click(x, y, right - x, 14, () -> {
                if (!expanded.remove(key)) expanded.add(key);
            });
        }
        ui.text(Draw.ellipsize(ui.font, c.title(), titleW), x + 6, y + 4, Theme.TEXT_STRONG);
        int ry = y + 16;
        for (int i = 0; i < c.shown(open); i++) {
            Kv kv = c.rows().get(i);
            int vw = ui.font.width(kv.value());
            ui.text(Draw.ellipsize(ui.font, kv.label(), w - 16 - vw), x + 6, ry, Theme.MUTED);
            ui.textRight(Draw.ellipsize(ui.font, kv.value(), w - 12), x + w - 6, ry, kv.color());
            ry += LINE;
        }
    }

    private Card recordCard() {
        int attacks = 0, incoming = 0, jumps = 0, markers = 0;
        Set<String> servers = new LinkedHashSet<>();
        for (Event e : rec.events()) {
            if (e instanceof AttackEvent) attacks++;
            else if (e instanceof IncomingHitEvent) incoming++;
            else if (e instanceof JumpEvent) jumps++;
            else if (e instanceof MarkerEvent) markers++;
            else if (e instanceof SegmentEvent s && s.server() != null) servers.add(s.server());
        }
        List<Kv> rows = new ArrayList<>();
        rows.add(new Kv(t("Длительность", "Duration", "Dauer"), Fmt.clock(rec.durationMs())));
        rows.add(new Kv(t("Атак / получено / прыжков", "Attacks / taken / jumps", "Angriffe / eingesteckt / Sprünge"), attacks + " / " + incoming + " / " + jumps));
        rows.add(new Kv(t("Игрок", "Player", "Spieler"), rec.player() == null ? "?" : rec.player()));
        if (markers > 0) rows.add(new Kv(t("Меток", "Markers", "Markierungen"), String.valueOf(markers)));
        rows.add(new Kv(t("Серверы", "Servers", "Server"), servers.isEmpty() ? "—" : String.join(", ", servers)));
        return new Card(t("Запись", "Recording", "Aufnahme"), rows, 2);
    }

    private static Card hitsCard(Stats.HitStats h) {
        return new Card(t("Попадания", "Hits", "Treffer"), List.of(
                new Kv(t("Точность", "Accuracy", "Trefferquote"), Fmt.pct(h.hitRatePct()), graded(SkillScores.grade(
                        h.confirmed() + h.unconfirmed() + h.misses() >= SkillScores.MIN_ATTACKS ? h.hitRatePct() : Double.NaN, 75, 60))),
                new Kv(t("Подтверждено уроном", "Confirmed by damage", "Durch Schaden bestätigt"), String.valueOf(h.confirmed()), Theme.GREEN),
                new Kv(t("Промахов", "Misses", "Fehlschläge"), String.valueOf(h.misses()), h.misses() > 0 ? Theme.RED : Theme.TEXT),
                new Kv(t("  из них не дотянулся", "  of them out of range", "  davon zu weit weg"), String.valueOf(h.missesOutOfRange())),
                new Kv(t("Без подтверждения", "Unconfirmed", "Unbestätigt"), String.valueOf(h.unconfirmed()))), 3);
    }

    private static Card reachCard(Stats.HitStats h) {
        boolean none = h.confirmed() == 0;
        return new Card(t("Дистанция ударов, блоков", "Hit distance, blocks", "Trefferdistanz, Blöcke"), List.of(
                new Kv(t("Средняя", "Mean", "Ø"), none ? "—" : Fmt.num(h.reachMean(), 2), graded(none ? SkillScores.Grade.NONE : SkillScores.reach(h.reachMean()))),
                new Kv(t("Дальше дальности", "Past range", "Über Reichweite"), none ? "—" : Fmt.pct(h.sharePastRangePct())),
                new Kv(t("Медиана", "Median"), none ? "—" : Fmt.num(h.reachMedian(), 2)),
                new Kv(t("Максимум", "Max", "Max."), none ? "—" : Fmt.num(h.reachMax(), 2)),
                new Kv(t("Разброс σ", "Spread σ", "Streuung σ"), none ? "—" : Fmt.num(h.reachSigma(), 2))), 2);
    }

    private static Card aimCard(Stats.HitStats h, AimAnalysis.Result ar) {
        boolean none = h.aimSamples() == 0;
        double bias = h.aimYawBias();
        String side = none || Math.abs(bias) < 0.05 ? "" : bias > 0 ? t(" (прицел левее)", " (aim to the left)", " (Ziel zu weit links)") : t(" (прицел правее)", " (aim to the right)", " (Ziel zu weit rechts)");
        String misses = ar.aimMisses() == 0 ? "—" : t("← ", "← ") + ar.sideMisses(AimAnalysis.Side.LEFT) + "  → " + ar.sideMisses(AimAnalysis.Side.RIGHT)
                + "  ↑ " + ar.sideMisses(AimAnalysis.Side.ABOVE) + "  ↓ " + ar.sideMisses(AimAnalysis.Side.BELOW);
        return new Card(t("Прицел", "Aim", "Zielen"), List.of(
                new Kv(t("Прицел на противнике", "Crosshair on opponent", "Fadenkreuz auf Gegner"), ar.total() == 0 ? "—" : Fmt.pct(ar.accuracyPct()),
                        graded(SkillScores.grade(ar.total() >= SkillScores.MIN_AIM ? ar.accuracyPct() : Double.NaN, 85, 70))),
                new Kv(t("Промахи (куда ушёл прицел)", "Misses (where the crosshair went)", "Fehlschläge (wohin das Fadenkreuz ging)"), misses),
                new Kv(t("По горизонтали (yaw)", "Horizontal (yaw)", "Horizontal (Yaw)"), none ? "—" : Fmt.num(h.aimYawAbsMean(), 1) + "°"),
                new Kv(t("Средний сдвиг yaw", "Mean yaw offset", "Ø Yaw-Versatz"), none ? "—" : Fmt.signed(bias, 1) + "°" + side),
                new Kv(t("По вертикали (pitch)", "Vertical (pitch)", "Vertikal (Pitch)"), none ? "—" : Fmt.num(h.aimPitchAbsMean(), 1) + "°"),
                new Kv(t("Высота попадания", "Hit height", "Trefferhöhe"), Double.isNaN(h.hitVMean()) ? "—" : Fmt.pct(h.hitVMean() * 100) + t(" роста", " of height", " der Höhe")),
                new Kv(t("В расчёте (от 1 блока)", "Counted (from 1 block)", "Gezählt (ab 1 Block)"), String.valueOf(h.aimSamples()))), 3);
    }

    private Card threeBlockCard(Stats.HitStats h) {
        Stats.ThreeBlockStats t = res.threeBlock();
        if (t.classified() == 0) {
            return new Card("3 block hit", List.of(new Kv(t("Нет данных", "No data", "Keine Daten"), t("старая запись или пинг скрыт", "old recording or ping hidden", "alte Aufnahme oder Ping verborgen"))), 1);
        }
        return new Card("3 block hit", List.of(
                new Kv(t("Количество", "Count", "Anzahl"), t.count() + " (" + Fmt.pct(t.sharePct()) + ")", Theme.AMBER),
                new Kv(t("У противника в среднем", "On opponent's screen, mean", "Auf dem Bildschirm des Gegners, Ø"), "≈ " + Fmt.num(t.oppDistMean(), 2) + t(" бл.", " bl.", " Bl.")),
                new Kv(t("Оценено ударов", "Hits rated", "Bewertete Treffer"), String.valueOf(t.classified())),
                new Kv(t("Без данных", "No data", "Keine Daten"), String.valueOf(Math.max(0, h.confirmed() - t.classified()))),
                new Kv(t("Интерполяция", "Interpolation"), CombatDataClient.settings().interpMs + t(" мс", " ms"))), 2);
    }

    private static Card jumpCard(Stats.JumpStats j) {
        boolean none = j.attempts() == 0, noSucc = j.success() == 0;
        List<Kv> rows = new ArrayList<>(List.of(
                new Kv(t("Успешных", "Successful", "Erfolgreich"), j.success() + (none ? "" : " (" + Fmt.pct(j.successRatePct()) + ")"),
                        graded(SkillScores.grade(j.attempts() >= SkillScores.MIN_JR ? j.successRatePct() : Double.NaN, 60, 40))),
                new Kv(t("Попыток", "Attempts", "Versuche"), String.valueOf(j.attempts())),
                new Kv(t("Рано / поздно", "Early / late", "Zu früh / zu spät"), j.early() + " / " + j.late(), j.early() + j.late() > 0 ? Theme.RED : Theme.TEXT),
                new Kv(t("Без прыжка", "No jump", "Kein Sprung"), String.valueOf(j.noAttempt())),
                new Kv(t("Среднее успешных", "Mean of successful", "Ø der erfolgreichen"), noSucc ? "—" : Fmt.num(j.meanSuccessMs(), 0) + t(" мс", " ms")),
                new Kv(t("Медиана успешных", "Median of successful", "Median der erfolgreichen"), noSucc ? "—" : Fmt.num(j.medianSuccessMs(), 0) + t(" мс", " ms")),
                new Kv(t("Разброс σ", "Spread σ", "Streuung σ"), none ? "—" : Fmt.num(j.sigmaMs(), 0) + t(" мс", " ms")),
                new Kv(t("Системный сдвиг", "Systematic offset", "Systematischer Versatz"), none ? "—" : Fmt.signed(j.biasMs(), 0) + t(" мс", " ms"))));
        if (j.held() > 0) rows.add(new Kv(t("Зажатый прыжок (исключено)", "Held jump (excluded)", "Gehaltener Sprung (ausgeschlossen)"), String.valueOf(j.held())));
        if (j.flyOut() > 0) {
            rows.add(new Kv("Fly out", String.valueOf(j.flyOut()), Theme.BLUE));
            rows.add(new Kv(t("  отлёт: fly out / без прыжка", "  knockback: fly out / no jump", "  Rückstoß: Fly out / kein Sprung"),
                    kbText(j.flyOutKbMean()) + " / " + kbText(j.noJumpKbMean())));
        }
        return new Card("Jump reset", rows, 3);
    }

    private static Card comboCard(Stats.ComboStats c) {
        return new Card(t("Комбо и размен", "Combos and trades", "Combos und Trades"), List.of(
                new Kv(t("Нанесено / получено", "Dealt / taken", "Ausgeteilt / eingesteckt"), c.dealt() + " / " + c.taken(), Theme.TEXT_STRONG),
                new Kv(t("Размен", "Trade"), Double.isNaN(c.trade()) ? (c.dealt() > 0 ? "∞" : "—") : Fmt.num(c.trade(), 2)),
                new Kv(t("Макс. комбо", "Max combo", "Max. Combo"), String.valueOf(c.maxCombo())),
                new Kv(t("Нанесено ударов", "Hits dealt", "Ausgeteilte Treffer"), String.valueOf(c.dealt()), Theme.GREEN),
                new Kv(t("Получено ударов", "Hits taken", "Eingesteckte Treffer"), String.valueOf(c.taken()), Theme.RED),
                new Kv(t("Среднее комбо (от 2)", "Mean combo (from 2)", "Ø Combo (ab 2)"), c.avgCombo() > 0 ? Fmt.num(c.avgCombo(), 1) : "—")), 3);
    }

    private Card chargeCard(Stats.HitStats hs) {
        return new Card(t("Заряд атаки", "Attack charge", "Schlagaufladung"), List.of(
                new Kv(t("Средний заряд", "Mean charge", "Ø Aufladung"), hs.confirmed() == 0 ? "—" : Fmt.pct(hs.strengthMean() * 100),
                        graded(hs.confirmed() == 0 ? SkillScores.Grade.NONE : SkillScores.charge(hs.strengthMean()))),
                new Kv(t("Без заряда (по интервалам)", "Uncharged (by intervals)", "Ungeladen (nach Abständen)"), cdSummary().n() == 0 ? "—"
                        : cdSummary().rushed() + " (" + Fmt.pct(cdSummary().rushedPct()) + ")"),
                new Kv(t("Полный (выше 0.9)", "Full (above 0.9)", "Voll (über 0,9)"), hs.confirmed() == 0 ? "—" : Fmt.pct(hs.fullChargePct())),
                new Kv(t("Лишнее ожидание после порога", "Extra wait after threshold", "Zusätzliche Wartezeit nach Schwelle"), Double.isNaN(cdSummary().waitMs()) ? "—"
                        : Fmt.num(cdSummary().waitMs(), 0) + t(" мс", " ms"))), 2);
    }

    private static final HitType[] TYPE_ORDER = {HitType.SPRINT, HitType.CRIT, HitType.WEAK, HitType.NORMAL, HitType.SWEEP};

    private static Card typesCard(Stats.HitStats h) {
        List<Kv> rows = new ArrayList<>();
        for (HitType t : TYPE_ORDER) {
            int n = h.types().getOrDefault(t, 0);
            rows.add(new Kv(typeName(t), n + (h.confirmed() == 0 ? "" : " (" + Fmt.pct(100.0 * n / h.confirmed()) + ")")));
        }
        return new Card(t("Типы ударов", "Hit types", "Schlagarten"), rows, 3);
    }

    static String typeName(HitType t) {
        return switch (t) {
            case WEAK -> t("Слабый", "Weak", "Schwach");
            case NORMAL -> t("Обычный", "Normal");
            case SWEEP -> t("Разящий", "Sweep");
            case CRIT -> t("Крит", "Crit");
            case SPRINT -> t("С разбега", "Sprint");
        };
    }


    private int kbTab(Ui ui, int x, int y, int w) {
        Settings s = CombatDataClient.settings();
        Calibration.Result c = calib;
        line(ui, w, t("Отлёт: насколько вас отбросило за 0,5 с после удара (по линии от атакующего). Меньше лучше.", "Knockback: how far you were pushed in 0.5 s after the hit (along the line from the attacker). Less is better.", "Rückstoß: wie weit du in 0,5 s nach dem Treffer weggedrückt wurdest (entlang der Linie vom Angreifer). Weniger ist besser."),
                x, y, Theme.MUTED);
        y += LINE + 4;
        if (c.samples() == 0 && rec.skipped(Features.KNOCKBACK)) return skipped(ui, w, t("Отлёт после удара", "Knockback after a hit", "Rückstoß nach einem Treffer"), x, y);
        if (c.samples() == 0) {
            line(ui, w, t("Нет данных об отлёте: нужны полученные удары в записи новой версии мода.", "No knockback data: needs hits taken in a recording from a newer mod version.", "Keine Rückstoßdaten: braucht eingesteckte Treffer in einer Aufnahme einer neueren Mod-Version."), x, y, Theme.DIM);
            return y + LINE;
        }
        line(ui, w, t("Без прыжка: ", "No jump: ", "Kein Sprung: ") + kbText(c.baselineKb()) + " (" + c.baselineN() + t(") · в окне ", ") · in window ", ") · im Fenster ") + s.jrLowerMs + "…"
                + s.jrUpperMs + t(" мс: ", " ms: ") + kbText(c.curKb()) + " (" + c.curN() + t(") · попыток с прыжком ", ") · jump attempts ", ") · Sprungversuche ") + c.samples(),
                x, y, Theme.TEXT);
        if (res.jump().flyOut() > 0) {
            y += LINE;
            line(ui, w, "Fly out: " + kbText(res.jump().flyOutKbMean()) + " (" + res.jump().flyOut() + ")"
                    + t(" · не входит в калибровку", " · not used for calibration", " · nicht für Kalibrierung genutzt"), x, y, Theme.BLUE);
        }
        y += LINE + 6;
        double max = Double.isFinite(c.baselineKb()) ? c.baselineKb() : 0;
        for (Calibration.Bin b : c.bins()) if (b.n() > 0) max = Math.max(max, b.meanKb());
        max = Math.max(0.5, max * 1.1);
        int n = c.bins().size(), bw = Math.max(2, w / n), h = 70, by = y + 12;
        long lo = c.bins().get(0).fromMs(), span = c.bins().get(n - 1).toMs() - lo;
        if (c.hasRecommendation()) {
            int rx1 = x + (int) ((c.recLower() - lo) * (bw * n) / span), rx2 = x + (int) ((c.recUpper() - lo) * (bw * n) / span);
            ui.g.fill(rx1, by, Math.max(rx1 + 1, rx2), by + h, Theme.alpha(Theme.AMBER, 0x30));
        }
        int hover = -1;
        for (int i = 0; i < n; i++) {
            Calibration.Bin b = c.bins().get(i);
            int bx = x + i * bw;
            if (ui.hover(bx, by, bw, h)) {
                hover = i;
                Draw.round(ui.g, bx, by, bw, h, 2, Theme.ROW_HOVER);
            }
            if (b.n() == 0) continue;
            int bh = Math.max(1, (int) (h * b.meanKb() / max));
            boolean inWindow = b.fromMs() >= s.jrLowerMs && b.toMs() - 1 <= s.jrUpperMs;
            int color = hover == i ? Theme.TEXT_STRONG : b.n() < 3 ? Theme.DIM : inWindow ? Theme.GREEN : Theme.MUTED;
            ui.g.fill(bx + 1, by + h - bh, bx + bw - 1, by + h, color);
        }
        ui.g.fill(x, by + h, x + bw * n, by + h + 1, Theme.BORDER);
        if (Double.isFinite(c.baselineKb())) {
            int ly = by + h - (int) (h * c.baselineKb() / max);
            Draw.dashH(ui.g, x, x + bw * n, ly, 3, 2, Theme.RED);
        }
        int zx = x + (int) ((0 - lo) * (bw * n) / span);
        ui.g.fill(zx, by, zx + 1, by + h, Theme.BORDER);
        String title = t("Средний отлёт по дельте прыжка (корзины по ", "Mean knockback by jump delta (bins of ", "Ø Rückstoß nach Sprungzeitpunkt (Schritte von ") + Calibration.BIN_MS + t(" мс), пунктир: без прыжка", " ms), dashed: no jump", " ms), gestrichelt: kein Sprung");
        if (hover >= 0) {
            Calibration.Bin b = c.bins().get(hover);
            title = b.fromMs() + "…" + b.toMs() + t(" мс: ", " ms: ") + (b.n() == 0 ? t("нет попыток", "no attempts", "keine Versuche") : kbText(b.meanKb()) + " (" + b.n() + ")");
        }
        line(ui, w, title, x, y, hover >= 0 ? Theme.TEXT_STRONG : Theme.MUTED);
        ui.text(lo + t(" мс", " ms"), x, by + h + 3, Theme.DIM);
        ui.textCenter("0", zx, by + h + 3, Theme.DIM);
        ui.textRight("+" + (lo + span) + t(" мс", " ms"), x + bw * n, by + h + 3, Theme.DIM);
        y = by + h + 16;
        if (c.hasRecommendation()) {
            String rec = t("Рекомендуемое окно: ", "Recommended window: ", "Empfohlenes Fenster: ") + c.recLower() + "…" + c.recUpper() + t(" мс, отлёт ", " ms, knockback ", " ms, Rückstoß ") + kbText(c.recKb())
                    + " (" + c.recN() + t(" попыток)", " attempts)", " Versuche)");
            boolean same = c.recLower() == s.jrLowerMs && c.recUpper() == s.jrUpperMs;
            String bl = t("Применить это окно", "Apply this window", "Dieses Fenster übernehmen");
            int bwid = ui.buttonWidth(bl);
            line(ui, same ? w : w - bwid - 8, rec, x, Ui.textY(y, 16), Theme.AMBER);
            if (!same) ui.button(x + w - bwid, y, bwid, 16, bl, Ui.Kind.PRIMARY, true,
                    () -> menu.settingsTab.adoptJumpWindow((int) c.recLower(), (int) c.recUpper()));
            else ui.textRight(t("уже применено", "already applied", "schon übernommen"), x + w, Ui.textY(y, 16), Theme.GREEN);
        } else {
            line(ui, w, t("Для рекомендации нужно не меньше ", "A recommendation needs at least ", "Eine Empfehlung braucht mindestens ") + Calibration.MIN_SAMPLES + t(" попыток с прыжком, сейчас ", " jump attempts, now ", " Sprungversuche, jetzt ")
                    + c.samples(), x, Ui.textY(y, 16), Theme.DIM);
        }
        y += 20;
        line(ui, w, t("Ширина окна берётся из настроек, ищется положение. По всем записям: Настройки → Дополнительно.", "Window width comes from settings, only its position is fitted. For all recordings: Settings → Advanced.", "Die Fensterbreite kommt aus den Einstellungen, nur die Lage wird angepasst. Für alle Aufnahmen: Einstellungen → Erweitert."),
                x, y, Theme.DIM);
        return y + LINE;
    }

    private static String kbText(double v) {
        return Double.isFinite(v) ? Fmt.num(v, 2) + t(" бл.", " bl.", " Bl.") : "—";
    }


    /** Head/leg boundaries: the player model is 32 units tall, the head is the top 8, legs the bottom 12. */
    private static final double HEAD_V = 24.0 / 32, LEGS_V = 12.0 / 32;

    private int aimTab(Ui ui, int x, int y, int w) {
        int interp = CombatDataClient.settings().interpMs;
        AimAnalysis.Result ar = aimRes;
        if (ar.total() == 0) {
            ui.text(t("Нет ударов для расчёта (удары ближе 1 блока не учитываются)", "No swings to analyze (swings closer than 1 block are not counted)", "Keine Schläge zum Auswerten (Schläge unter 1 Block werden nicht gezählt)"), x, y, Theme.DIM);
            return y + LINE;
        }
        boolean narrow = w < 330;
        int sh = 150, cardW = narrow ? Math.min(w, 120) : 120, cardH = sh + 44;
        Draw.round(ui.g, x, y, cardW, cardH, Theme.R_CTRL, Theme.WINDOW);
        ui.textCenter(t("Вид спереди", "Front view", "Vorderansicht"), x + cardW / 2, y + 4, Theme.MUTED);
        double unit = sh / 32.0, hw = sh * 0.3 / 1.8;
        int ax = x + cardW / 2, top = y + 26;
        var g = ui.g;
        int body = Theme.RAISED, limbs = Theme.faded(Theme.RAISED_HOVER, 0.3);
        g.fill(ax - (int) (4 * unit), top, ax + (int) (4 * unit), top + (int) (8 * unit), body);
        g.fill(ax - (int) (4 * unit), top + (int) (8 * unit), ax + (int) (4 * unit), top + (int) (20 * unit), body);
        g.fill(ax - (int) (8 * unit), top + (int) (8 * unit), ax - (int) (4 * unit), top + (int) (20 * unit), limbs);
        g.fill(ax + (int) (4 * unit), top + (int) (8 * unit), ax + (int) (8 * unit), top + (int) (20 * unit), limbs);
        g.fill(ax - (int) (4 * unit), top + (int) (20 * unit), ax - 1, top + sh, limbs);
        g.fill(ax, top + (int) (20 * unit), ax + (int) (4 * unit), top + sh, limbs);
        // Sweet spot: chest to neck, the part that leaves the most room when the opponent moves.
        int bandTop = top + (int) Math.round((1 - AimAnalysis.BEST_HI) * sh), bandBot = top + (int) Math.round((1 - AimAnalysis.BEST_LO) * sh);
        g.fill(ax - (int) hw, bandTop, ax + (int) hw, bandBot, Theme.alpha(Theme.GREEN, 0x30));
        g.renderOutline(ax - (int) hw, top, (int) (hw * 2), sh, Theme.CTRL_HOVER);
        int minX = x + 2, maxX = x + cardW - 3, minY = y + 15, maxY = y + cardH - 3;
        int head = 0, legs = 0;
        for (AimAnalysis.Sample sm : ar.samples()) {
            int cx = ax + (int) Math.round(sm.x() * hw), cy = top + (int) Math.round((1 - sm.y()) * sh);
            cx = Math.max(minX, Math.min(maxX, cx));
            cy = Math.max(minY, Math.min(maxY, cy));
            int color;
            if (!sm.onTarget()) color = Theme.alpha(Theme.RED, 0xC0);
            else if (sm.a().outcome() == Outcome.MISS) color = Theme.alpha(Theme.MUTED, 0xA0);
            else if (ThreeBlockHit.is(sm.a(), interp)) color = Theme.alpha(Theme.AMBER, 0xC0);
            else color = Theme.alpha(Theme.GREEN, 0xA0);
            Draw.circle(g, cx - 1, cy - 1, 3, color);
            if (sm.onTarget()) {
                if (sm.y() >= HEAD_V) head++;
                else if (sm.y() < LEGS_V) legs++;
            }
        }
        // Where to aim (green cross) and where you aim on average (accent cross), joined by a line.
        int tx = ax, ty = top + (int) Math.round((1 - AimAnalysis.BEST_Y) * sh);
        if (Double.isFinite(ar.meanX()) && Double.isFinite(ar.meanY())) {
            int mx = ax + (int) Math.round(Math.max(-2.2, Math.min(2.2, ar.meanX())) * hw);
            int my = top + (int) Math.round((1 - Math.max(-0.05, Math.min(1.05, ar.meanY()))) * sh);
            if (Math.abs(mx - tx) + Math.abs(my - ty) > 4) Draw.line(g, mx, my, tx, ty, Theme.alpha(Theme.TEXT, 0x70));
            g.fill(mx - 4, my, mx + 5, my + 1, Theme.ACCENT);
            g.fill(mx, my - 4, mx + 1, my + 5, Theme.ACCENT);
        }
        g.fill(tx - 3, ty, tx + 4, ty + 1, Theme.GREEN);
        g.fill(tx, ty - 3, tx + 1, ty + 4, Theme.GREEN);

        int rx = narrow ? x : x + cardW + 10, rw = narrow ? w : w - cardW - 10, ry = narrow ? y + cardH + 8 : y;
        int n = ar.total(), on = ar.onTarget();
        line(ui, rw, t("Прицел на противнике: ", "Crosshair on the opponent: ", "Fadenkreuz auf dem Gegner: ") + Fmt.pct(ar.accuracyPct()) + t(" взмахов (", " of swings (", " der Schläge (")
                + on + "/" + n + ")", rx, ry, Theme.TEXT_STRONG);
        ry += LINE;
        line(ui, rw, t("Промахов прицелом ", "Aim misses ", "Zielfehler ") + ar.aimMisses() + t(" · не дотянулся (прицел верный) ", " · out of range (aim was fine) ", " · zu weit weg (Ziel war richtig) ")
                + ar.outOfRange(), rx, ry, Theme.MUTED);
        ry += LINE;
        if (ar.aimMisses() > 0) {
            line(ui, rw, t("Промахи: левее ", "Misses: left ", "Fehlschläge: links ") + ar.sideMisses(AimAnalysis.Side.LEFT)
                    + t(" · правее ", " · right ", " · rechts ") + ar.sideMisses(AimAnalysis.Side.RIGHT)
                    + t(" · выше ", " · over ", " · drüber ") + ar.sideMisses(AimAnalysis.Side.ABOVE)
                    + t(" · ниже ", " · under ", " · drunter ") + ar.sideMisses(AimAnalysis.Side.BELOW), rx, ry, Theme.MUTED);
            ry += LINE;
        }
        if (on > 0) {
            line(ui, rw, t("Голова ", "Head ", "Kopf ") + Fmt.pct(100.0 * head / on) + t(" · тело ", " · body ", " · Körper ") + Fmt.pct(100.0 * (on - head - legs) / on)
                    + t(" · ноги ", " · legs ", " · Beine ") + Fmt.pct(100.0 * legs / on) + t(" · в зоне груди ", " · in the chest zone ", " · im Brustbereich ")
                    + Fmt.pct(ar.inBestPct()), rx, ry, Theme.MUTED);
            ry += LINE;
        }
        line(ui, rw, t("Зелёный крест: куда целиться · второй: ваш средний прицел · красные: промахи", "Green cross: where to aim · other cross: your average · red: misses", "Grünes Kreuz: wohin zielen · anderes Kreuz: dein Durchschnitt · rot: Fehlschläge"),
                rx, ry, Theme.DIM);
        ry += LINE + 6;

        ui.text(t("Что улучшить", "What to improve", "Was verbessern"), rx, ry, Theme.TEXT_STRONG);
        ry += LINE + 2;
        if (ar.tips().isEmpty()) {
            for (String l : ui.wrap(ar.total() < AimAnalysis.MIN_SWINGS
                    ? t("Мало взмахов для советов: нужно от " + AimAnalysis.MIN_SWINGS + ".", "Too few swings for tips: needs " + AimAnalysis.MIN_SWINGS + " or more.", "Zu wenige Schläge für Tipps: braucht " + AimAnalysis.MIN_SWINGS + " oder mehr.")
                    : t("Явных проблем с прицелом не найдено.", "No clear aim problems found.", "Keine klaren Probleme beim Zielen gefunden."), rw)) {
                ui.text(l, rx, ry, Theme.DIM);
                ry += LINE;
            }
        }
        for (AimAnalysis.Tip tip : ar.tips()) {
            int color = switch (tip.tone()) {
                case BAD -> Theme.RED;
                case TIP -> Theme.AMBER;
                case GOOD -> Theme.GREEN;
            };
            List<String> ls = ui.wrap(tip.text(), rw - 10);
            int th = ls.size() * LINE + 4;
            Draw.round(g, rx, ry, rw, th, Theme.R_CTRL, Theme.WINDOW);
            g.fill(rx, ry + 2, rx + 3, ry + th - 2, color);
            int ly = ry + 3;
            for (String l : ls) {
                ui.text(l, rx + 7, ly, Theme.TEXT);
                ly += LINE;
            }
            ry += th + 3;
        }
        y = Math.max(y + cardH, ry) + 8;

        ui.text(t("Попадание прицелом по ситуациям", "Aim by situation", "Zielen nach Situation"), x, y, Theme.TEXT_STRONG);
        y += LINE + 2;
        double[] at = {0, 0.62, 0.82};
        cols(ui, x, y, w, at, new String[]{"", t("На цели", "On target", "Auf dem Ziel"), t("Взмахов", "Swings", "Schläge")}, fill(3, Theme.DIM));
        y += ROW;
        if (ar.hasMotion()) {
            y = splitRow(ui, x, y, w, at, t("Противник стоит / идёт прямо", "Opponent still / straight", "Gegner steht / läuft gerade"), ar.still());
            y = splitRow(ui, x, y, w, at, t("Противник стрейфит", "Opponent strafing", "Gegner strafet"), ar.strafing());
            y = splitRow(ui, x, y, w, at, t("Противник на земле", "Opponent on the ground", "Gegner am Boden"), ar.targetLevel());
            y = splitRow(ui, x, y, w, at, t("Противник в прыжке", "Opponent jumping", "Gegner springt"), ar.targetJumping());
            y = splitRow(ui, x, y, w, at, t("Мышь спокойно", "Mouse steady", "Maus ruhig"), ar.calm());
            y = splitRow(ui, x, y, w, at, t("Мышь резко (от " + (int) AimAnalysis.FLICK_DPS + "°/с)", "Mouse flicking (" + (int) AimAnalysis.FLICK_DPS + "°/s+)", "Maus schnell gedreht (" + (int) AimAnalysis.FLICK_DPS + "°/s+)"), ar.flicking());
        }
        y = splitRow(ui, x, y, w, at, t("Вы на земле", "You on the ground", "Du am Boden"), ar.ground());
        y = splitRow(ui, x, y, w, at, t("Вы в прыжке", "You in the air", "Du in der Luft"), ar.air());
        String[] dl = {t("1–2 блока", "1–2 blocks", "1–2 Blöcke"), t("2–2,5 блока", "2–2.5 blocks", "2–2,5 Blöcke"), t("2,5–3 блока", "2.5–3 blocks", "2,5–3 Blöcke"), t("3+ блока", "3+ blocks", "3+ Blöcke")};
        for (int i = 0; i < dl.length; i++) y = splitRow(ui, x, y, w, at, dl[i], ar.byDistance().get(i));
        y += 4;
        if (ar.hasMotion() && Double.isFinite(ar.leadDeg())) {
            double l = ar.leadDeg();
            String lead = Math.abs(l) < 0.3 ? t("прицел держится на нём", "the crosshair stays on them", "das Fadenkreuz bleibt auf ihm")
                    : l < 0 ? Fmt.num(-l, 1) + t("° позади", "° behind", "° dahinter") : Fmt.num(l, 1) + t("° впереди", "° ahead", "° voraus");
            String track = Double.isFinite(ar.trackRatio()) ? t(" · мышь успевает на ", " · mouse keeps up ", " · Maus kommt mit zu ") + Fmt.pct(ar.trackRatio() * 100) : "";
            line(ui, w, t("Против стрейфа: ", "Against strafes: ", "Gegen Strafes: ") + lead + track, x, y, Math.abs(l) >= 0.8 ? Theme.AMBER : Theme.MUTED);
            y += LINE;
        } else if (!ar.hasMotion()) {
            for (String l : ui.wrap(t("Стрейф, прыжки противника и скорость мыши видны в новых записях (или если писались позиции).",
                    "Strafing, opponent jumps and mouse speed show for new recordings (or when positions were recorded).", "Strafen, Sprünge des Gegners und Mausgeschwindigkeit gibt es bei neuen Aufnahmen (oder wenn Positionen aufgenommen wurden)."), w)) {
                ui.text(l, x, y, Theme.DIM);
                y += LINE;
            }
        }
        y += 6;
        if (!CombatDataClient.settings().expertMode) return y;

        double[] yaw = ar.samples().stream().mapToDouble(sm -> sm.a().aimYawDeg()).toArray();
        double[] pitch = ar.samples().stream().mapToDouble(sm -> sm.a().aimPitchDeg()).toArray();
        y = hist(ui, x, y, w, 34, Stats.histogram(yaw, -15, 15, 15), -15, 2, "°",
                t("Yaw, ср. ", "Yaw, avg. ", "Yaw, Ø ") + Fmt.signed(-ar.biasDeg(), 1) + t("°, разброс ", "°, spread ", "°, Streuung ") + Fmt.num(ar.spreadDeg(), 1)
                        + t("° (плюс: цель правее прицела)", "° (plus: target right of the crosshair)", "° (plus: Ziel rechts vom Fadenkreuz)"), Theme.GREEN);
        y = hist(ui, x, y + 4, w, 34, Stats.histogram(pitch, -15, 15, 15), -15, 2, "°",
                t("Pitch, все взмахи (плюс: цель ниже прицела)", "Pitch, all swings (plus: target below the crosshair)", "Pitch, alle Schläge (plus: Ziel unter dem Fadenkreuz)"), Theme.MUTED);
        return y;
    }

    private int splitRow(Ui ui, int x, int y, int w, double[] at, String label, AimAnalysis.Split s) {
        if (s.n() == 0) return y;
        double p = s.pct();
        int c = s.n() < 5 ? Theme.DIM : p >= 85 ? Theme.GREEN : p >= 70 ? Theme.TEXT : p >= 55 ? Theme.AMBER : Theme.RED;
        if (visible(y, ROW)) cols(ui, x, y, w, at, new String[]{label, Fmt.pct(p), String.valueOf(s.n())},
                new int[]{Theme.MUTED, c, Theme.DIM});
        return y + ROW;
    }

    private int strikesTab(Ui ui, int x, int y, int w) {
        Stats.HitStats hs = res.hits();
        if (hs.confirmed() == 0) {
            ui.text(t("Подтверждённых ударов нет", "No confirmed hits", "Keine bestätigten Treffer"), x, y, Theme.DIM);
            return y + LINE;
        }
        ui.text(t("Типы ударов", "Hit types", "Schlagarten"), x, y, Theme.TEXT_STRONG);
        y += LINE + 2;
        int maxT = 1;
        for (HitType t : HitType.values()) maxT = Math.max(maxT, hs.types().getOrDefault(t, 0));
        int lw = 64, vw = 70, bw = w - lw - vw - 8;
        for (HitType t : HitType.values()) {
            int c = hs.types().getOrDefault(t, 0);
            ui.text(typeName(t), x, y, Theme.MUTED);
            Draw.round(ui.g, x + lw, y, bw, 8, 2, Theme.INSET);
            if (c > 0) Draw.round(ui.g, x + lw, y, Math.max(2, bw * c / maxT), 8, 2,
                    t == HitType.WEAK ? Theme.RED : t == HitType.CRIT ? Theme.AMBER : Theme.GREEN);
            ui.textRight(c + " (" + Fmt.pct(100.0 * c / hs.confirmed()) + ")", x + w, y, Theme.TEXT);
            y += LINE + 1;
        }
        y += 6;
        List<Double> str = new ArrayList<>();
        for (Event e : events) if (e instanceof AttackEvent a && a.outcome() == Outcome.HIT_CONFIRMED) str.add((double) a.strength());
        double[] sa = str.stream().mapToDouble(Double::doubleValue).toArray();
        y = hist(ui, x, y, w, 40, Stats.histogram(sa, 0, 1.0000001, 10), 0, 10, "%",
                t("Заряд атаки: средний ", "Attack charge: mean ", "Schlagaufladung: Ø ") + Fmt.pct(hs.strengthMean() * 100) + t(", полный (выше 90%) ", ", full (above 90%) ", ", voll (über 90 %) ") + Fmt.pct(hs.fullChargePct()),
                Theme.ACCENT);
        return y;
    }

    private static String methodName(WTap.Method m) {
        return switch (m) {
            case W_TAP -> "W-tap";
            case S_TAP -> "S-tap";
            case SPRINT_KEY -> t("клавиша спринта", "sprint key", "Sprinttaste");
            case AUTO -> t("сам (спринт зажат)", "auto (sprint held)", "automatisch (Sprint gehalten)");
            case NONE -> t("без сброса", "no reset", "kein Reset");
        };
    }

    private static String ms(double v) {
        return Double.isFinite(v) ? Fmt.num(v, 0) + t(" мс", " ms") : "—";
    }

    private int wtapTab(Ui ui, int x, int y, int w) {
        WTap.Result r = wtap;
        if (!r.hasInput() && rec.skipped(Features.INPUT)) return skipped(ui, w, t("Нажатия W, S и спринта", "W, S and sprint presses", "W-, S- und Sprint-Tasten"), x, y);
        if (!r.hasInput()) {
            line(ui, w, t("Нет данных: нажатия W и спринта пишутся в записях новой версии мода.",
                    "No data: W and sprint presses are recorded from a newer mod version.", "Keine Daten: W- und Sprint-Tasten werden erst ab einer neueren Mod-Version aufgenommen."), x, y, Theme.DIM);
            return y + LINE;
        }
        line(ui, w, t("SPRINT-ударов в сериях ", "Sprint hits in series ", "Sprint-Treffer in Serie ") + r.sprintHits() + t(" · со сбросом спринта перед ударом ", " · with a sprint reset before ", " · mit Sprint-Reset vor ")
                + Fmt.pct(r.afterResetPct()) + t(" · ударов без спринта ", " · hits without sprint ", " · Treffer ohne Sprint ") + r.noSprintHits(), x, y, Theme.TEXT);
        y += LINE;
        List<String> parts = new ArrayList<>();
        for (WTap.Method m : WTap.Method.values()) {
            int n = r.methods().getOrDefault(m, 0);
            if (n > 0) parts.add(methodName(m) + " " + n);
        }
        line(ui, w, t("Как возвращали спринт: ", "How sprint came back: ", "Wie der Sprint zurückkam: ") + (parts.isEmpty() ? "—" : String.join(", ", parts)), x, y, Theme.MUTED);
        y += LINE;
        line(ui, w, t("W отпущена в среднем через ", "W released on average ", "W im Schnitt losgelassen ") + ms(r.releaseMeanMs()) + t(" после удара, сброс длится ", " after the hit, reset lasts ", " nach dem Treffer, Reset dauert ")
                + ms(r.resetMeanMs()), x, y, Theme.MUTED);
        y += LINE;
        line(ui, w, t("Серий, прерванных после удара без спринта: ", "Combos broken after a hit without sprint: ", "Combos abgebrochen nach einem Treffer ohne Sprint: ") + r.brokenNoSprint() + t(" из ", " of ", " von ") + r.combos(),
                x, y, r.combos() > 0 && r.brokenNoSprint() * 2 >= r.combos() ? Theme.RED : Theme.MUTED);
        y += LINE + 6;
        if (r.resetDurations().length > 0) {
            y = hist(ui, x, y, w, 40, Stats.histogram(r.resetDurations(), 0, 300, 15), 0, 20, t(" мс", " ms"),
                    t("Длительность сброса (W отпущена или S нажата), 20 мс на столбец", "Reset duration (W up or S down), 20 ms per bar", "Reset-Dauer (W los oder S gedrückt), 20 ms pro Balken"), Theme.ACCENT);
            y += 4;
        }
        line(ui, w, t("После удара с нокбэком спринт сбрасывается. С зажатыми W и спринтом он вернётся через тик сам,",
                "A knockback hit resets sprint. With W and sprint held it comes back a tick later by itself,", "Ein Treffer mit Rückstoß setzt den Sprint zurück. Mit gehaltenem W und Sprint kommt er einen Tick später von selbst zurück,"), x, y, Theme.DIM);
        y += LINE;
        line(ui, w, t("без клавиши спринта нужен W-tap или S-tap. Сброс в любом случае замедляет вас (скорость × 0,6).",
                "without the sprint key you need a W-tap or S-tap. Either way the hit slows you down (speed × 0.6).", "ohne Sprinttaste brauchst du einen W-Tap oder S-Tap. So oder so bremst dich der Treffer (Tempo × 0,6)."), x, y, Theme.DIM);
        return y + LINE;
    }

    private static Card wtapCard(WTap.Result r) {
        List<Kv> rows = new ArrayList<>(List.of(
                new Kv(t("SPRINT-ударов со сбросом", "Sprint hits after reset", "Sprint-Treffer nach Reset"), Fmt.pct(r.afterResetPct()) + " (" + r.afterReset() + "/" + r.sprintHits() + ")", Theme.TEXT_STRONG),
                new Kv(t("Сброс длится", "Reset lasts", "Reset dauert"), ms(r.resetMeanMs())),
                new Kv(t("W отпущена через", "W released after", "W losgelassen nach"), ms(r.releaseMeanMs())),
                new Kv(t("Ударов без спринта", "Hits without sprint", "Treffer ohne Sprint"), String.valueOf(r.noSprintHits())),
                new Kv(t("Комбо прервано без спринта", "Combos broken without sprint", "Combos ohne Sprint abgebrochen"), r.brokenNoSprint() + " / " + r.combos())));
        for (WTap.Method m : WTap.Method.values()) {
            int n = r.methods().getOrDefault(m, 0);
            if (n > 0) rows.add(new Kv("  " + methodName(m), String.valueOf(n)));
        }
        return new Card("W-tap", rows, 2);
    }

    private int comboTab(Ui ui, int x, int y, int w) {
        Stats.ComboStats c = res.combo();
        line(ui, w, t("Нанесено ", "Dealt ", "Ausgeteilt ") + c.dealt() + t(" · получено ", " · taken ", " · eingesteckt ") + c.taken() + t(" · размен ", " · trade ", " · Trade ")
                + (Double.isNaN(c.trade()) ? (c.dealt() > 0 ? "∞" : "—") : Fmt.num(c.trade(), 2)), x, y, Theme.TEXT);
        y += LINE;
        line(ui, w, t("Макс. комбо ", "Max combo ", "Max. Combo ") + c.maxCombo() + t(" · среднее (от 2) ", " · mean (from 2) ", " · Ø (ab 2) ")
                + (c.avgCombo() > 0 ? Fmt.num(c.avgCombo(), 1) : "—"), x, y, Theme.MUTED);
        y += LINE + 6;
        if (c.dealt() == 0) {
            ui.text(t("Подтверждённых ударов нет", "No confirmed hits", "Keine bestätigten Treffer"), x, y, Theme.DIM);
            return y + LINE;
        }
        double[] combos = Stats.comboLengths(events).stream().mapToDouble(Integer::doubleValue).toArray();
        int[] cc = Stats.histogram(combos, 0.5, 10.5, 10);
        return hist(ui, x, y, w, 40, cc, 1, 1, "",
                t("Длина серий ударов (до ответного удара): макс. ", "Combo length (until hit back): max ", "Combo-Länge (bis zum Gegentreffer): max. ") + c.maxCombo() + t(", последний столбец 10+", ", last column 10+", ", letzte Spalte 10+"),
                Theme.GREEN);
    }

    private int hist(Ui ui, int x, int y, int w, int h, int[] counts, double min, double step, String unit,
                     String title, int color) {
        int n = counts.length, max = 1, total = 0;
        for (int c : counts) {
            max = Math.max(max, c);
            total += c;
        }
        int by = y + 12, bw = Math.max(1, w / n), hover = -1;
        for (int i = 0; i < n; i++) {
            int bx = x + i * bw;
            if (ui.hover(bx, by, bw, h)) {
                hover = i;
                Draw.round(ui.g, bx, by, bw, h, 2, Theme.ROW_HOVER);
            }
            int bh = counts[i] == 0 ? 0 : Math.max(1, h * counts[i] / max);
            ui.g.fill(bx + 1, by + h - bh, bx + bw - 1, by + h, hover == i ? Theme.TEXT_STRONG : color);
        }
        ui.g.fill(x, by + h, x + bw * n, by + h + 1, Theme.BORDER);
        String t = title;
        if (hover >= 0) {
            String range = unit.isEmpty() ? binInt(min, step, hover, n)
                    : Fmt.num(min + hover * step, 0) + "…" + Fmt.num(min + (hover + 1) * step, 0) + unit;
            t = range + ": " + counts[hover] + (total > 0 ? " (" + Fmt.pct(100.0 * counts[hover] / total) + ")" : "");
        }
        line(ui, w, t, x, y, hover >= 0 ? Theme.TEXT_STRONG : Theme.MUTED);
        String lo = Fmt.num(min, 0) + unit, hi = unit.isEmpty() ? Fmt.num(min + (n - 1) * step, 0) + "+" : Fmt.num(min + n * step, 0) + unit;
        ui.text(lo, x, by + h + 3, Theme.DIM);
        ui.textRight(hi, x + bw * n, by + h + 3, Theme.DIM);
        if (min < 0 && min + n * step > 0) ui.textCenter("0", x + (int) ((-min) / step * bw), by + h + 3, Theme.DIM);
        return by + h + 14;
    }

    private static String binInt(double min, double step, int i, int n) {
        long v = Math.round(min + i * step);
        return t("Серия ", "Combo ") + v + (i == n - 1 ? "+" : "");
    }


    private static void cols(Ui ui, int x, int y, int w, double[] at, String[] text, int[] color) {
        for (int i = 0; i < text.length; i++) {
            int cx = x + (int) (at[i] * w), next = i + 1 < at.length ? x + (int) (at[i + 1] * w) : x + w;
            ui.text(Draw.ellipsize(ui.font, text[i], next - cx - 4), cx, y, color[i]);
        }
    }

    private static int[] fill(int n, int c) {
        int[] a = new int[n];
        Arrays.fill(a, c);
        return a;
    }

    private int header(Ui ui, int x, int y, int w, double[] at, String... names) {
        cols(ui, x, y, w, at, names, fill(names.length, Theme.DIM));
        ui.g.fill(x, y + 10, x + w, y + 11, Theme.BORDER);
        return y + 14;
    }

    private int jumpTab(Ui ui, int x, int y, int w) {
        Stats.JumpStats j = res.jump();
        Settings s = CombatDataClient.settings();
        line(ui, w, t("Попыток ", "Attempts ", "Versuche ") + j.attempts() + t(" · успех ", " · success ", " · Erfolg ") + Fmt.pct(j.successRatePct()) + t(" · среднее успешных ", " · mean of successful ", " · Ø der erfolgreichen ")
                + (j.success() == 0 ? "—" : Fmt.num(j.meanSuccessMs(), 0) + t(" мс", " ms")) + " · σ "
                + (j.attempts() == 0 ? "—" : Fmt.num(j.sigmaMs(), 0) + t(" мс", " ms")), x, y, Theme.TEXT);
        y += LINE;
        line(ui, w, t("Окно успеха ", "Success window ", "Erfolgsfenster ") + s.jrLowerMs + "…" + s.jrUpperMs + t(" мс после удара · рано ", " ms after the hit · early ", " ms nach dem Treffer · zu früh ") + j.early() + t(" · поздно ", " · late ", " · zu spät ")
                + j.late() + t(" · без прыжка ", " · no jump ", " · kein Sprung ") + j.noAttempt() + (j.flyOut() > 0 ? " · fly out " + j.flyOut() : ""), x, y, Theme.MUTED);
        y += LINE + 6;
        if (attempts.isEmpty()) {
            ui.text(t("Полученных ударов нет", "No hits taken", "Keine Treffer eingesteckt"), x, y, Theme.DIM);
            return y + LINE;
        }
        double[] at = {0, .16, .36, .50, .74};
        y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Результат", "Result", "Ergebnis"), t("Дельта", "Delta"), t("Кто ударил", "Hit by", "Getroffen von"), t("Состояние", "State", "Zustand"));
        for (JumpResetEvaluator.Attempt a : attempts) {
            if (visible(y, ROW)) {
                String result;
                int rc;
                switch (a.result()) {
                    case SUCCESS -> { result = t("Успех", "Success", "Erfolg"); rc = Theme.GREEN; }
                    case TOO_EARLY -> { result = t("Рано", "Early", "Zu früh"); rc = Theme.RED; }
                    case TOO_LATE -> { result = t("Поздно", "Late", "Zu spät"); rc = Theme.RED; }
                    case NO_ATTEMPT -> { result = t("Без прыжка", "No jump", "Kein Sprung"); rc = Theme.MUTED; }
                    case FLY_OUT -> { result = "Fly out"; rc = Theme.BLUE; }
                    default -> { result = t("Не оценивается", "Not rated", "Nicht bewertet"); rc = Theme.DIM; }
                }
                if (a.held()) result += s.jrExcludeHeld ? t(" (зажат, искл.)", " (held, excl.)", " (gehalten, ausgeschl.)") : t(" (зажат)", " (held)", " (gehalten)");
                IncomingHitEvent h = a.hit();
                String state = (h.groundedAtHit() ? t("на земле", "on ground", "am Boden") : t("в воздухе", "in the air", "in der Luft")) + (h.knockbackApplied() ? "" : t(", без нокбэка", ", no knockback", ", kein Rückstoß"));
                cols(ui, x, y, w, at, new String[]{Fmt.clockMs(h.t()), result,
                                a.hasJump() ? Fmt.signed(a.deltaMs(), 0) + t(" мс", " ms") : "—", nz(h.attackerName()), state},
                        new int[]{Theme.MUTED, rc, Theme.TEXT, Theme.TEXT, Theme.MUTED});
            }
            y += ROW;
        }
        return y;
    }

    private int hitsTab(Ui ui, int x, int y, int w) {
        Stats.HitStats h = res.hits();
        Stats.ThreeBlockStats t = res.threeBlock();
        int interp = CombatDataClient.settings().interpMs;
        line(ui, w, t("Точность ", "Accuracy ", "Trefferquote ") + Fmt.pct(h.hitRatePct()) + t(" · попаданий ", " · hits ", " · Treffer ") + h.confirmed() + t(" · промахов ", " · misses ", " · Fehlschläge ") + h.misses()
                + t(" · средняя дистанция ", " · mean distance ", " · Ø Distanz ") + (h.confirmed() == 0 ? "—" : Fmt.num(h.reachMean(), 2)), x, y, Theme.TEXT);
        y += LINE;
        line(ui, w, "3 block hit: " + (t.classified() == 0 ? t("нет данных", "no data", "keine Daten") : t.count() + t(" из ", " of ", " von ") + t.classified())
                + t(" · последний столбец: дистанция на экране противника", " · last column: distance on the opponent's screen", " · letzte Spalte: Distanz auf dem Bildschirm des Gegners"), x, y, Theme.MUTED);
        y += LINE + 6;
        List<AttackEvent> list = new ArrayList<>();
        for (Event e : events) if (e instanceof AttackEvent a) list.add(a);
        if (list.isEmpty()) {
            ui.text(t("Атак нет", "No attacks", "Keine Angriffe"), x, y, Theme.DIM);
            return y + LINE;
        }
        boolean narrow = w < 360;
        double[] at = narrow ? new double[]{0, .22, .48, .78} : new double[]{0, .14, .32, .50, .60, .73, .82, .91};
        if (narrow) y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Цель", "Target", "Ziel"), t("Исход", "Result", "Ergebnis"), t("Дист.", "Dist."));
        else y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Цель", "Target", "Ziel"), t("Исход", "Result", "Ergebnis"), t("Дист.", "Dist."), t("Тип", "Type", "Art"), t("Заряд", "Charge", "Aufladung"), "Yaw", t("У цели", "At target", "Am Ziel"));
        for (AttackEvent a : list) {
            if (visible(y, ROW)) {
                boolean tb = ThreeBlockHit.is(a, interp);
                String out;
                int oc;
                switch (a.outcome()) {
                    case HIT_CONFIRMED -> { out = tb ? "3 block hit" : t("Попал", "Hit", "Treffer"); oc = tb ? Theme.AMBER : Theme.GREEN; }
                    case HIT_UNCONFIRMED -> { out = t("Без подтв.", "Unconfirmed", "Unbestätigt"); oc = Theme.MUTED; }
                    default -> {
                        out = a.outOfRangeMiss(a.rangeOr(AttackEvent.DEFAULT_RANGE)) ? t("Не дотянулся", "Out of range", "Zu weit weg") : t("Мимо", "Missed", "Daneben");
                        oc = Theme.RED;
                    }
                }
                double od = ThreeBlockHit.classifiable(a, interp) ? ThreeBlockHit.opponentDistance(a, interp) : Double.NaN;
                if (narrow) cols(ui, x, y, w, at, new String[]{Fmt.clockMs(a.t()), nz(a.targetName()), out, Fmt.num(a.reach(), 2)},
                        new int[]{Theme.MUTED, Theme.TEXT, oc, Theme.TEXT});
                else cols(ui, x, y, w, at, new String[]{Fmt.clockMs(a.t()), nz(a.targetName()), out, Fmt.num(a.reach(), 2),
                                typeName(a.type()), Fmt.pct(a.strength() * 100), Fmt.signed(a.aimYawDeg(), 1) + "°",
                                Double.isNaN(od) ? "—" : "≈ " + Fmt.num(od, 1)},
                        new int[]{Theme.MUTED, Theme.TEXT, oc, Theme.TEXT, Theme.MUTED, Theme.MUTED, Theme.MUTED,
                                tb ? Theme.AMBER : Theme.MUTED});
            }
            y += ROW;
        }
        return y;
    }

    private int opponentsTab(Ui ui, int x, int y, int w) {
        List<Stats.OpponentStats> list = all.opponents();
        line(ui, w, t("Клик по строке: вся статистика только по этому противнику. Повторный клик снимает фильтр.", "Click a row: all stats for this opponent only. Click again to clear the filter.", "Klick auf eine Zeile: alle Werte nur für diesen Gegner. Nochmal klicken hebt den Filter auf."), x, y, Theme.MUTED);
        y += LINE + 6;
        if (list.isEmpty()) {
            ui.text(t("Противников нет", "No opponents", "Keine Gegner"), x, y, Theme.DIM);
            return y + LINE;
        }
        double[] at = {0, .34, .48, .61, .74, .87};
        y = header(ui, x, y, w, at, t("Игрок", "Player", "Spieler"), t("Нанесено", "Dealt", "Ausgeteilt"), t("Промахов", "Misses", "Fehlschläge"), t("Получено", "Taken", "Eingesteckt"), t("Точность", "Accuracy", "Trefferquote"), t("Дист.", "Dist."));
        int rh = 14;
        for (Stats.OpponentStats o : list) {
            if (visible(y, rh)) {
                boolean sel = o.uuid().equals(opp), hov = ui.hover(x - 4, y - 2, w + 8, rh);
                if (sel) Draw.round(ui.g, x - 4, y - 3, w + 8, rh, Theme.R_CTRL, Theme.RAISED_HOVER);
                else if (hov) Draw.round(ui.g, x - 4, y - 3, w + 8, rh, Theme.R_CTRL, Theme.ROW_HOVER);
                cols(ui, x, y, w, at, new String[]{nz(o.name()), String.valueOf(o.dealt()), String.valueOf(o.missed()),
                                String.valueOf(o.taken()), Fmt.pct(o.hitRatePct()), o.dealt() == 0 ? "—" : Fmt.num(o.reachMean(), 2)},
                        new int[]{sel ? Theme.ACCENT : Theme.TEXT_STRONG, Theme.GREEN, Theme.TEXT, Theme.RED, Theme.TEXT, Theme.TEXT});
                UUID id = o.uuid();
                String nm = o.name();
                ui.click(x - 4, y - 3, w + 8, rh, () -> filter(id, nm));
            }
            y += rh;
        }
        return y;
    }

    private List<Line> buildTimeline() {
        List<Line> out = new ArrayList<>();
        for (Event e : events) {
            if (e instanceof SegmentEvent s) {
                out.add(new Line(s.t(), Fmt.clockMs(s.t()), t("Сервер ", "Server ") + nz(s.server()) + ", " + nz(s.dimension()), Theme.MUTED));
            } else if (e instanceof MarkerEvent m) {
                out.add(new Line(m.t(), Fmt.clockMs(m.t()), t("Метка: ", "Marker: ", "Markierung: ") + nz(m.label()), Theme.ACCENT));
            }
        }
        int n = 0;
        for (Stats.Fight f : Stats.fights(events, Stats.FIGHT_GAP_MS, sf)) {
            n++;
            out.add(new Line(f.startMs(), Fmt.clock(f.startMs()) + "–" + Fmt.clock(f.endMs()),
                    t("Бой ", "Fight ", "Kampf ") + n + ": " + String.join(", ", f.opponents()) + t(" · атак ", " · attacks ", " · Angriffe ") + f.attacks() + t(" · нанесено ", " · dealt ", " · ausgeteilt ")
                            + f.dealt() + t(" · получено ", " · taken ", " · eingesteckt ") + f.taken(), Theme.TEXT));
        }
        out.sort(Comparator.comparingLong(Line::t));
        return out;
    }

    private int fightsTab(Ui ui, int x, int y, int w) {
        List<Stats.Fight> fights = Stats.fights(events, Stats.FIGHT_GAP_MS, sf);
        line(ui, w, t("Боёв ", "Fights ", "Kämpfe ") + fights.size() + t(" · новый бой после паузы больше ", " · a new fight after a pause over ", " · neuer Kampf nach einer Pause über ")
                + Stats.FIGHT_GAP_MS / 1000 + t(" с без ударов · клик: размены боя", " s without hits · click: trades of the fight", " s ohne Treffer · Klick: Trades des Kampfs"), x, y, Theme.MUTED);
        y += LINE;
        int small = Stats.fights(events, Stats.FIGHT_GAP_MS).size() - fights.size();
        if (sf.minFightHits() > 1) {
            line(ui, w, t("Бои меньше ", "Fights under ", "Kämpfe unter ") + sf.minFightHits() + t(" подтверждённых ударов не показаны", " confirmed hits are hidden", " bestätigte Treffer ausgeblendet")
                    + (small > 0 ? " (" + small + ")" : ""), x, y, Theme.DIM);
            y += LINE;
        }
        y += 6;
        if (fights.isEmpty()) {
            ui.text(t("Боёв нет", "No fights", "Keine Kämpfe"), x, y, Theme.DIM);
            return y + LINE;
        }
        double[] at = {0, .26, .64, .76, .88};
        y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Противники", "Opponents", "Gegner"), t("Атак", "Atk.", "Angr."), t("Нанес.", "Dealt", "Ausgeteilt"), t("Получ.", "Taken", "Eingesteckt"));
        for (Stats.Fight f : fights) {
            if (visible(y, ROW)) {
                if (ui.hover(x - 4, y - 2, w + 8, ROW)) Draw.round(ui.g, x - 4, y - 2, w + 8, ROW, Theme.R_CTRL, Theme.ROW_HOVER);
                cols(ui, x, y, w, at, new String[]{Fmt.clock(f.startMs()) + "–" + Fmt.clock(f.endMs()),
                                String.join(", ", f.opponents()), String.valueOf(f.attacks()), String.valueOf(f.dealt()), String.valueOf(f.taken())},
                        new int[]{Theme.MUTED, Theme.TEXT, Theme.TEXT, Theme.GREEN, Theme.RED});
                long from = f.startMs(), to = f.endMs();
                ui.click(x - 4, y - 2, w + 8, ROW, () -> {
                    timeFrom = from;
                    timeTo = to;
                    fightsMode = F_TRADES;
                    scroll = 0;
                });
            }
            y += ROW;
        }
        return y;
    }

    private int takenTab(Ui ui, int x, int y, int w) {
        Stats.TakenStats tk = res.taken();
        if (tk.rated() == 0 && rec.skipped(Features.TAKEN)) return skipped(ui, w, t("Дистанция ударов по вам", "Distance of hits on you", "Distanz der Treffer auf dich"), x, y);
        if (tk.rated() == 0) {
            line(ui, w, t("Нет данных: дистанция ударов по вам пишется в записях новой версии мода, только ближние удары.",
                    "No data: distance of hits on you is recorded from a newer mod version, melee hits only.", "Keine Daten: Die Distanz der Treffer auf dich wird erst ab einer neueren Mod-Version aufgenommen, nur Nahkampf."), x, y, Theme.DIM);
            return y + LINE;
        }
        Stats.HitStats hs = res.hits();
        Stats.ThreeBlockStats mine = res.threeBlock();
        line(ui, w, t("Ударов по вам ", "Hits on you ", "Treffer auf dich ") + tk.rated() + t(" · средняя дистанция ", " · mean distance ", " · Ø Distanz ") + Fmt.num(tk.reachMean(), 2)
                + t(" бл. (ваша ", " bl. (yours ", " Bl. (deine ") + (hs.confirmed() == 0 ? "—" : Fmt.num(hs.reachMean(), 2)) + t(") · максимум ", ") · max ", ") · max. ")
                + Fmt.num(tk.reachMax(), 2), x, y, Theme.TEXT);
        y += LINE;
        line(ui, w, t("3 block hit против вас: ", "3 block hit against you: ", "3 Block Hit gegen dich: ") + tk.caught() + " (" + Fmt.pct(tk.caughtPct()) + ")"
                + t(" · ваших: ", " · yours: ", " · deine: ") + (mine.classified() == 0 ? "—" : mine.count() + " (" + Fmt.pct(mine.sharePct()) + ")"),
                x, y, tk.caughtPct() >= 25 ? Theme.RED : Theme.MUTED);
        y += LINE;
        line(ui, w, t("3 block hit против вас: на вашем экране противник был дальше вашей дальности атаки, ответить вы не могли.",
                "3 block hit against you: on your screen the opponent was beyond your attack range, you could not hit back.", "3 Block Hit gegen dich: Auf deinem Bildschirm war der Gegner außerhalb deiner Reichweite, du konntest nicht zurückschlagen."), x, y, Theme.DIM);
        y += LINE + 6;
        double[] at = {0, .40, .60, .80};
        y = header(ui, x, y, w, at, t("Оружие противника", "Opponent weapon", "Waffe des Gegners"), t("Ударов", "Hits", "Treffer"), t("Дист.", "Dist."), "3BH");
        for (Weapon wp : Weapon.values()) {
            Stats.WeaponTaken wt = tk.byWeapon().get(wp);
            if (wt == null) continue;
            cols(ui, x, y, w, at, new String[]{weaponName(wp), String.valueOf(wt.n()), Fmt.num(wt.reachMean(), 2),
                    wt.caught() + " (" + Fmt.pct(100.0 * wt.caught() / wt.n()) + ")"}, new int[]{Theme.TEXT, Theme.TEXT, Theme.TEXT,
                    wt.caught() > 0 ? Theme.AMBER : Theme.MUTED});
            y += ROW;
        }
        y += 8;
        boolean narrow = w < 360;
        at = narrow ? new double[]{0, .24, .56, .78} : new double[]{0, .14, .36, .54, .66, .78, .88};
        if (narrow) y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Кто", "Who", "Wer"), t("Дист.", "Dist."), "3BH");
        else y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Кто ударил", "Hit by", "Getroffen von"), t("Оружие", "Weapon", "Waffe"), t("Дист.", "Dist."),
                t("Его ск.", "Their v", "Sein v"), t("Ваша ск.", "Your v", "Dein v"), t("Пинг", "Ping"));
        for (Event e : events) {
            if (!(e instanceof IncomingHitEvent h) || !h.rated()) continue;
            if (visible(y, ROW)) {
                int dc = h.caught() ? Theme.AMBER : Theme.TEXT;
                String d = Fmt.num(h.reach(), 2) + (h.caught() ? " !" : "");
                if (narrow) cols(ui, x, y, w, at, new String[]{Fmt.clockMs(h.t()), nz(h.attackerName()), d, h.caught() ? t("да", "yes", "ja") : ""},
                        new int[]{Theme.MUTED, Theme.TEXT, dc, Theme.AMBER});
                else cols(ui, x, y, w, at, new String[]{Fmt.clockMs(h.t()), nz(h.attackerName()), weaponName(h.weaponOr()), d,
                                Fmt.signed(h.attackerTowardBps(), 1), Fmt.signed(h.selfAwayBps(), 1),
                                h.attackerPingMs() >= 0 ? h.attackerPingMs() + t(" мс", " ms") : "—"},
                        new int[]{Theme.MUTED, Theme.TEXT, Theme.MUTED, dc, Theme.MUTED, Theme.MUTED, Theme.MUTED});
            }
            y += ROW;
        }
        return y;
    }

    private static Card takenCard(Stats.TakenStats tk, Stats.ThreeBlockStats mine) {
        return new Card(t("Удары по вам", "Hits on you", "Treffer auf dich"), List.of(
                new Kv(t("3 block hit против вас", "3 block hit against you", "3 Block Hit gegen dich"), tk.caught() + " (" + Fmt.pct(tk.caughtPct()) + ")",
                        graded(SkillScores.caught(tk.caughtPct()))),
                new Kv(t("Средняя дистанция", "Mean distance", "Ø Distanz"), Fmt.num(tk.reachMean(), 2) + t(" бл.", " bl.", " Bl.")),
                new Kv(t("Ваши 3 block hit", "Your 3 block hits", "Deine 3 Block Hits"), mine.classified() == 0 ? "—" : Fmt.pct(mine.sharePct())),
                new Kv(t("Максимум", "Max", "Max."), Fmt.num(tk.reachMax(), 2) + t(" бл.", " bl.", " Bl.")),
                new Kv(t("Ударов с замером", "Hits measured", "Gemessene Treffer"), String.valueOf(tk.rated()))), 2);
    }

    static String causeName(Trades.Cause c) {
        if (c == null) return "";
        return switch (c) {
            case MISS -> t("промах", "miss", "Fehlschlag");
            case WEAK -> t("удар без заряда", "uncharged hit", "ungeladener Schlag");
            case CAUGHT -> t("3 block hit против вас", "3 block hit against you", "3 Block Hit gegen dich");
            case FAILED_JR -> t("неудачный jump reset", "failed jump reset", "misslungener Jump Reset");
            case NONE -> t("без явной причины", "no clear cause", "keine klare Ursache");
        };
    }

    private int tradesTab(Ui ui, int x, int y, int w) {
        Trades.Summary s = res.trades();
        long gap = CombatDataClient.settings().tradeGapMs;
        line(ui, w, t("Разменов ", "Trades ") + s.total() + t(" · первый удар ", " · first hit ", " · erster Treffer ") + Fmt.pct(s.firstPct())
                + t(" · побед / поражений / ничьих ", " · won / lost / drawn ", " · gewonnen / verloren / unentschieden ") + s.wins() + " / " + s.losses() + " / " + s.draws(), x, y, Theme.TEXT);
        y += LINE;
        line(ui, w, t("Счёт: удары (вес). Вес: крит 1,5, ваш слабый удар по заряду; победитель ", "Score: hits (weight). Weight: crit 1.5, your weak hit by charge; winner ", "Punktestand: Treffer (Gewicht). Gewicht: Crit 1,5, dein schwacher Schlag nach Aufladung; Sieger ")
                + (CombatDataClient.settings().tradeByWeight ? t("по весу", "by weight", "nach Gewicht") : t("по числу ударов", "by hit count", "nach Trefferzahl"))
                + t(" (Настройки → Дополнительно)", " (Settings → Advanced)", " (Einstellungen → Erweitert)"), x, y, Theme.DIM);
        y += LINE;
        line(ui, w, t("Побед, когда били первым: ", "Won when hitting first: ", "Gewonnen, wenn zuerst getroffen: ") + Fmt.pct(s.winFirstPct()) + " (" + s.winsFirst() + "/" + s.countFirst() + ")"
                + t(" · вторым: ", " · second: ", " · als Zweiter: ") + Fmt.pct(s.winSecondPct()) + " (" + s.winsSecond() + "/" + s.countSecond() + ")", x, y, Theme.MUTED);
        y += LINE;
        if (s.losses() > 0) {
            List<String> parts = new ArrayList<>();
            for (Trades.Cause c : Trades.Cause.values()) {
                int n = s.lossCauses().getOrDefault(c, 0);
                if (n > 0) parts.add(causeName(c) + " " + n);
            }
            line(ui, w, t("Проигрыши начинались с: ", "Losses started with: ", "Niederlagen begannen mit: ") + String.join(", ", parts), x, y, Theme.MUTED);
            y += LINE;
        }
        line(ui, w, t("Новый размен после паузы больше ", "A new trade after a pause over ", "Neuer Trade nach einer Pause über ") + Fmt.num(gap / 1000.0, 1)
                + t(" с (Настройки → Дополнительно)", " s (Settings → Advanced)", " s (Einstellungen → Erweitert)"), x, y, Theme.DIM);
        y += LINE + 6;
        if (trades.isEmpty()) {
            ui.text(t("Разменов нет", "No trades", "Keine Trades"), x, y, Theme.DIM);
            return y + LINE;
        }
        boolean narrow = w < 360;
        double[] at = narrow ? new double[]{0, .20, .46, .76} : new double[]{0, .13, .33, .42, .60, .72};
        if (narrow) y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Противник", "Opponent", "Gegner"), t("Счёт", "Score", "Punktestand"), t("Итог", "Result", "Ergebnis"));
        else y = header(ui, x, y, w, at, t("Время", "Time", "Zeit"), t("Противник", "Opponent", "Gegner"), t("Первый", "First", "Zuerst"), t("Счёт", "Score", "Punktestand"),
                t("Итог", "Result", "Ergebnis"), t("Причина", "Cause", "Ursache"));
        for (Trades.Trade tr : trades) {
            if (visible(y, ROW)) {
                String res;
                int rc;
                switch (tr.result()) {
                    case WIN -> { res = t("победа", "won", "gewonnen"); rc = Theme.GREEN; }
                    case LOSS -> { res = t("поражение", "lost", "verloren"); rc = Theme.RED; }
                    default -> { res = t("ничья", "draw", "unentschieden"); rc = Theme.MUTED; }
                }
                String first = tr.first() == Trades.Side.ME ? t("вы", "you", "du") : t("он", "they", "er");
                String score = tr.mine() + ":" + tr.theirs() + " (" + Fmt.num(tr.mineWeight(), 1) + ":" + Fmt.num(tr.theirsWeight(), 1) + ")";
                if (narrow) cols(ui, x, y, w, at, new String[]{Fmt.clock(tr.startMs()), nz(tr.name()), score + " · " + first, res},
                        new int[]{Theme.MUTED, Theme.TEXT, Theme.TEXT, rc});
                else cols(ui, x, y, w, at, new String[]{Fmt.clockMs(tr.startMs()), nz(tr.name()), first, score, res, causeName(tr.cause())},
                        new int[]{Theme.MUTED, Theme.TEXT, tr.first() == Trades.Side.ME ? Theme.GREEN : Theme.MUTED, Theme.TEXT, rc, Theme.MUTED});
            }
            y += ROW;
        }
        return y;
    }

    private static Card tradesCard(Trades.Summary s) {
        if (s.total() == 0) return new Card(t("Размены", "Trades"), List.of(new Kv(t("Разменов нет", "No trades", "Keine Trades"), "")), 1);
        List<Kv> rows = new ArrayList<>(List.of(
                new Kv(t("Первый удар", "First hit", "Erster Treffer"), Fmt.pct(s.firstPct()) + " (" + s.first() + "/" + s.total() + ")",
                        graded(s.total() >= SkillScores.MIN_TRADES ? SkillScores.firstHit(s.firstPct()) : SkillScores.Grade.NONE)),
                new Kv(t("Победы / поражения / ничьи", "Won / lost / drawn", "Gewonnen / verloren / unentschieden"), s.wins() + " / " + s.losses() + " / " + s.draws()),
                new Kv(t("Вес ударов: ваших / его", "Hit weight: yours / theirs", "Treffergewicht: deins / seins"), Fmt.num(s.weightMine(), 1) + " / " + Fmt.num(s.weightTheirs(), 1)),
                new Kv(t("Побед, когда вторым", "Won when second", "Gewonnen als Zweiter"), Fmt.pct(s.winSecondPct()), s.countSecond() > 0 && s.winSecondPct() < 30 ? Theme.RED : Theme.TEXT),
                new Kv(t("Побед, когда первым", "Won when first", "Gewonnen als Erster"), Fmt.pct(s.winFirstPct()))));
        for (Trades.Cause c : Trades.Cause.values()) {
            int n = s.lossCauses().getOrDefault(c, 0);
            if (n > 0) rows.add(new Kv(t("  проигрыш: ", "  lost: ", "  verloren: ") + causeName(c), String.valueOf(n)));
        }
        return new Card(t("Размены", "Trades"), rows, 3);
    }

    private int timelineTab(Ui ui, int x, int y, int w) {
        line(ui, w, t("Бои (пауза больше ", "Fights (a pause over ", "Kämpfe (eine Pause über ") + Stats.FIGHT_GAP_MS / 1000 + t(" с начинает новый), метки и смены сервера.", " s starts a new one), markers and server changes.", " s beginnt einen neuen), Markierungen und Serverwechsel."),
                x, y, Theme.MUTED);
        y += LINE + 6;
        if (timeline.isEmpty()) {
            ui.text(t("Событий нет", "No events", "Keine Ereignisse"), x, y, Theme.DIM);
            return y + LINE;
        }
        double[] at = {0, .2};
        for (Line l : timeline) {
            if (visible(y, ROW)) cols(ui, x, y, w, at, new String[]{l.time(), l.text()}, new int[]{Theme.MUTED, l.color()});
            y += ROW;
        }
        return y;
    }

    private static int skipped(Ui ui, int w, String what, int x, int y) {
        String s = t("Не записывалось: «" + what + "» было выключено для этой записи (лёгкая запись). Включить для новых записей: Настройки → Основные → Что записывать.",
                "Not recorded: \"" + what + "\" was off for this recording (lite recording). Turn it on for new recordings: Settings → General → What to record.", "Nicht aufgenommen: „" + what + "“ war für diese Aufnahme aus (leichte Aufnahme). Für neue Aufnahmen einschalten: Einstellungen → Allgemein → Was aufnehmen.");
        for (String l : ui.wrap(s, w)) {
            ui.text(l, x, y, Theme.MUTED);
            y += LINE;
        }
        return y;
    }

    private static void line(Ui ui, int w, String s, int x, int y, int color) {
        ui.text(Draw.ellipsize(ui.font, s, w), x, y, color);
    }

    private static String nz(String s) {
        return s == null ? "?" : s;
    }
}
