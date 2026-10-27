package dev.combatdata.core;

import java.util.ArrayList;
import java.util.List;

import static dev.combatdata.core.Lang.t;

public final class FightSummary {
    public static final long QUIET_MS = 10_000;
    public static final long SHOW_MS = 5_000;
    public static final int LAYOUT_TWO_LINES = 0, LAYOUT_ONE_LINE = 1, LAYOUT_COLUMN = 2;
    public static final int NEUTRAL = 0, GOOD = 1, MID = 2, BAD = 3;

    /** metrics / detail: the fight's numbers for the coach tip (null in old callers). */
    public record Result(long startMs, long endMs, String opponent, int hits, int swings, int jrSuccess, int jrAttempts,
                         int maxCombo, int tradesWon, int tradesLost, double reachMean, int threeBlock, int taken,
                         Progress.Metrics metrics, Detail detail) {
        public Result(long startMs, long endMs, String opponent, int hits, int swings, int jrSuccess, int jrAttempts,
                      int maxCombo, int tradesWon, int tradesLost, double reachMean, int threeBlock, int taken) {
            this(startMs, endMs, opponent, hits, swings, jrSuccess, jrAttempts, maxCombo, tradesWon, tradesLost, reachMean,
                    threeBlock, taken, null, null);
        }

        /** The single most useful thing to change after this fight, short enough for the HUD; null if nothing stands out. */
        public String tip() {
            return metrics == null || detail == null ? null : Report.shortTip(metrics, detail);
        }

        public Result(long startMs, long endMs, String opponent, int hits, int swings, int jrSuccess, int jrAttempts,
                      int maxCombo, int tradesWon, int tradesLost) {
            this(startMs, endMs, opponent, hits, swings, jrSuccess, jrAttempts, maxCombo, tradesWon, tradesLost, Double.NaN, 0, 0);
        }
    }

    public record Style(boolean title, boolean hits, boolean jr, boolean combo, boolean trades, boolean reach,
                        boolean threeBlock, boolean taken, int layout, boolean colors) {
        public static final Style DEFAULT = new Style(true, true, true, true, true, false, false, false, LAYOUT_TWO_LINES, true);
    }

    public record Part(String before, String value, String after, int quality) {
        public String text() {
            return before + value + after;
        }
    }

    private FightSummary() {
    }

    public static Result last(Recording r, long nowMs, long quietMs, JumpResetEvaluator.Params p, int interpMs,
                              StatFilter sf, Trades.Params tp) {
        List<Stats.Fight> fights = Stats.fights(sf.apply(r.events()).kept(), Stats.FIGHT_GAP_MS);
        if (fights.isEmpty()) return null;
        Stats.Fight f = fights.get(fights.size() - 1);
        if (nowMs - f.endMs() < quietMs || !sf.fightCounts(f)) return null;
        // Jumps after the last hit taken still count for jump reset, so the range is slightly wider than the fight.
        Stats.Filter range = new Stats.Filter(Math.max(0, f.startMs() - p.preMs()), f.endMs() + p.postMs(), null);
        Stats.Result s = Stats.compute(r, p, range, AttackEvent.DEFAULT_RANGE, interpMs, sf, tp);
        String opp = null;
        int best = -1;
        for (Stats.OpponentStats o : s.opponents()) {
            int n = o.dealt() + o.taken();
            if (n > best && o.name() != null) {
                best = n;
                opp = o.name();
            }
        }
        Stats.HitStats h = s.hits();
        List<Event> ev = sf.apply(Stats.events(r, range)).kept();
        Detail d = Detail.of(s, AimAnalysis.analyze(ev, AttackEvent.DEFAULT_RANGE), JumpResetEvaluator.evaluate(ev, p));
        return new Result(f.startMs(), f.endMs(), opp, h.confirmed(), h.confirmed() + h.unconfirmed() + h.misses(),
                s.jump().success(), s.jump().attempts(), s.combo().maxCombo(), s.trades().wins(), s.trades().losses(),
                h.confirmed() > 0 ? h.reachMean() : Double.NaN, s.threeBlock().count(), s.combo().taken(),
                Progress.Metrics.of(s), d);
    }

    public static List<Part> parts(Result r, Style st) {
        List<Part> out = new ArrayList<>();
        if (st.hits() && r.swings() > 0) {
            double rate = (double) r.hits() / r.swings();
            out.add(new Part("", r.hits() + "/" + r.swings(), t(" попаданий", " hits", " Treffer"), rate >= 0.7 ? GOOD : rate >= 0.5 ? MID : BAD));
        }
        if (st.jr() && r.jrAttempts() > 0) {
            double rate = (double) r.jrSuccess() / r.jrAttempts();
            out.add(new Part("JR ", r.jrSuccess() + "/" + r.jrAttempts(), "", rate >= 0.6 ? GOOD : rate >= 0.35 ? MID : BAD));
        }
        if (st.combo() && r.maxCombo() >= 2) out.add(new Part(t("комбо ", "combo ", "Combo "), String.valueOf(r.maxCombo()), "", r.maxCombo() >= 4 ? GOOD : NEUTRAL));
        if (st.trades() && r.tradesWon() + r.tradesLost() > 0) {
            int q = r.tradesWon() > r.tradesLost() ? GOOD : r.tradesWon() == r.tradesLost() ? MID : BAD;
            out.add(new Part(t("размены ", "trades ", "Trades "), r.tradesWon() + ":" + r.tradesLost(), "", q));
        }
        if (st.reach() && Double.isFinite(r.reachMean()))
            out.add(new Part(t("дист. ", "reach ", "Reichweite "), String.format(java.util.Locale.ROOT, "%.2f", r.reachMean()), "", NEUTRAL));
        if (st.threeBlock() && r.threeBlock() > 0) out.add(new Part("3BH ", String.valueOf(r.threeBlock()), "", GOOD));
        if (st.taken()) out.add(new Part(t("получено ", "taken ", "eingesteckt "), String.valueOf(r.taken()), "", NEUTRAL));
        return out;
    }

    public static String text(Result r) {
        return join(parts(r, Style.DEFAULT));
    }

    public static String join(List<Part> parts) {
        StringBuilder sb = new StringBuilder();
        for (Part p : parts) {
            if (!sb.isEmpty()) sb.append(" · ");
            sb.append(p.text());
        }
        return sb.toString();
    }

    public static String title(Result r) {
        long s = Math.max(0, (r.endMs() - r.startMs()) / 1000);
        String time = s / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60);
        return (r.opponent() != null ? t("Бой с ", "Fight vs ", "Kampf gegen ") + r.opponent() : t("Бой", "Fight", "Kampf")) + " · " + time;
    }

    public static Result sample() {
        // Jump resets mostly late, so the preview shows a tip line too.
        int[] c = new int[Detail.COUNT];
        c[Detail.AIM_N] = 11;
        c[Detail.AIM_ON] = 10;
        c[Detail.JR_OK] = 1;
        c[Detail.JR_LATE] = 4;
        return new Result(0, 42_000, "Steve", 8, 11, 3, 5, 4, 3, 1, 2.84, 2, 6,
                new Progress.Metrics(11, 8, 5, 1, 8 * 2.84, 0, 0, 8 * 0.95, 4, 3), Detail.ofCounts(c));
    }
}
