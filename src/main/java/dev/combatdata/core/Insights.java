package dev.combatdata.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Rules compare a recording's stats against thresholds and produce ranked hints for the overview. */
public final class Insights {
    private Insights() {
    }

    public static final int MAX_SHOWN = 3;

    public enum Page { ATTACK_HITS, ATTACK_AIM, ATTACK_STRIKES, DEFENSE_JUMP, DEFENSE_KNOCKBACK, DEFENSE_TAKEN, FIGHTS_TRADES }

    public enum Tone { BAD, TIP, GOOD }

    public record Insight(String id, double importance, Tone tone, String ru, String en, String de, Page page) {
        public String text() {
            return Lang.t(ru, en, de);
        }
    }

    /** aim: detailed aim analysis, null when not computed (the generic aim-miss rule is used then). */
    public record Input(Stats.Result stats, JumpResetEvaluator.Params jump, AimAnalysis.Result aim) {
        public Input(Stats.Result stats, JumpResetEvaluator.Params jump) {
            this(stats, jump, null);
        }

        AimAnalysis.Tip topAimTip() {
            if (aim == null) return null;
            for (AimAnalysis.Tip t : aim.tips()) if (t.tone() != AimAnalysis.Tone.GOOD) return t;
            return null;
        }
    }

    /** Importance = weight * how far the metric exceeds the threshold (at least 1). */
    public interface Rule {
        String id();

        Insight apply(Input in);
    }


    public record JumpOffsetRule(double thresholdMs, int minAttempts, double weight) implements Rule {
        public String id() { return "jr-offset"; }

        public Insight apply(Input in) {
            Stats.JumpStats j = in.stats().jump();
            if (j.attempts() < minAttempts || !Double.isFinite(j.biasMs())) return null;
            double mean = j.biasMs(), lo = in.jump().lowerMs(), hi = in.jump().upperMs();
            double off = mean < lo ? mean - lo : mean > hi ? mean - hi : 0;
            if (Math.abs(off) <= thresholdMs) return null;
            long ms = Math.round(Math.abs(off));
            double imp = weight * Math.max(1, Math.abs(off) / thresholdMs);
            if (off < 0) {
                return new Insight(id(), imp, Tone.BAD,
                        "Jump reset в среднем раньше окна на " + ms + " мс: прыгайте чуть позже",
                        "Jump reset is on average " + ms + " ms before the window: jump a bit later", "Jump Reset liegt im Schnitt " + ms + " ms vor dem Fenster: spring etwas später", Page.DEFENSE_JUMP);
            }
            return new Insight(id(), imp, Tone.BAD,
                    "Jump reset в среднем позже окна на " + ms + " мс: прыгайте чуть раньше",
                    "Jump reset is on average " + ms + " ms after the window: jump a bit earlier", "Jump Reset liegt im Schnitt " + ms + " ms nach dem Fenster: spring etwas früher", Page.DEFENSE_JUMP);
        }
    }

    public record LowChargeRule(double threshold, int minHits, double weight) implements Rule {
        public String id() { return "low-charge"; }

        public Insight apply(Input in) {
            Stats.HitStats h = in.stats().hits();
            if (h.confirmed() < minHits || !Double.isFinite(h.strengthMean()) || h.strengthMean() >= threshold) return null;
            long pct = Math.round(h.strengthMean() * 100);
            double imp = weight * Math.max(1, (threshold - h.strengthMean()) / 0.05);
            return new Insight(id(), imp, Tone.BAD,
                    "Средний заряд атаки " + pct + "%: бьёте раньше перезарядки",
                    "Mean attack charge " + pct + "%: you hit before the cooldown ends", "Ø Schlagaufladung " + pct + "%: du schlägst, bevor der Cooldown vorbei ist", Page.ATTACK_STRIKES);
        }
    }

    public record OutOfRangeRule(double thresholdPct, int minMisses, double weight) implements Rule {
        public String id() { return "out-of-range"; }

        public Insight apply(Input in) {
            Stats.HitStats h = in.stats().hits();
            int total = h.confirmed() + h.unconfirmed() + h.misses(), n = h.missesOutOfRange();
            if (n < minMisses || total == 0) return null;
            double pct = 100.0 * n / total;
            if (pct < thresholdPct) return null;
            double imp = weight * Math.max(1, pct / thresholdPct);
            return new Insight(id(), imp, Tone.TIP,
                    n + " промахов «не дотянулся» (" + Math.round(pct) + "% атак): бейте ближе",
                    n + " out-of-range misses (" + Math.round(pct) + "% of attacks): swing closer", n + " Fehlschläge „zu weit weg“ (" + Math.round(pct) + "% der Angriffe): schlag näher dran", Page.ATTACK_HITS);
        }
    }

    public record AimMissRule(double thresholdPct, int minMisses, double weight) implements Rule {
        public String id() { return "aim-miss"; }

        public Insight apply(Input in) {
            Stats.HitStats h = in.stats().hits();
            int total = h.confirmed() + h.unconfirmed() + h.misses(), n = h.misses() - h.missesOutOfRange();
            if (n < minMisses || total == 0) return null;
            double pct = 100.0 * n / total;
            if (pct < thresholdPct) return null;
            double imp = weight * Math.max(1, pct / thresholdPct);
            // The coach names the actual problem; this generic line would only repeat it.
            if (in.topAimTip() != null) return null;
            return new Insight(id(), imp, Tone.TIP,
                    n + " промахов прицелом (" + Math.round(pct) + "% атак): смотрите на прицел",
                    n + " aim misses (" + Math.round(pct) + "% of attacks): check your aim", n + " Zielfehler (" + Math.round(pct) + "% der Angriffe): achte auf dein Zielen", Page.ATTACK_AIM);
        }
    }

    /** The most important concrete aim problem found by AimAnalysis. */
    public record AimCoachRule(double weight) implements Rule {
        public String id() { return "aim-coach"; }

        public Insight apply(Input in) {
            AimAnalysis.Tip t = in.topAimTip();
            if (t == null) return null;
            return new Insight(id() + ":" + t.id(), weight * Math.max(1, t.importance()),
                    t.tone() == AimAnalysis.Tone.BAD ? Tone.BAD : Tone.TIP, t.ru(), t.en(), t.de(), Page.ATTACK_AIM);
        }
    }

    public record FirstHitRule(double thresholdPct, int minTrades, double weight) implements Rule {
        public String id() { return "first-hit"; }

        public Insight apply(Input in) {
            Trades.Summary t = in.stats().trades();
            if (t.total() < minTrades || t.firstPct() >= thresholdPct) return null;
            long pct = Math.round(t.firstPct());
            double imp = weight * Math.max(1, (thresholdPct - t.firstPct()) / 10);
            return new Insight(id(), imp, Tone.TIP,
                    "Первый удар только в " + pct + "% разменов: начинайте размен раньше противника",
                    "First hit in only " + pct + "% of trades: start trades before your opponent", "Erster Treffer nur in " + pct + "% der Trades: fang Trades vor deinem Gegner an", Page.FIGHTS_TRADES);
        }
    }

    public record SecondLossRule(double thresholdPct, int minTrades, double weight) implements Rule {
        public String id() { return "second-loss"; }

        public Insight apply(Input in) {
            Trades.Summary t = in.stats().trades();
            if (t.countSecond() < minTrades || t.winSecondPct() >= thresholdPct) return null;
            long pct = Math.round(t.winSecondPct());
            double imp = weight * Math.max(1, (thresholdPct - t.winSecondPct()) / 10);
            return new Insight(id(), imp, Tone.BAD,
                    "Когда противник бьёт первым, вы выигрываете " + pct + "% разменов",
                    "When the opponent hits first, you win " + pct + "% of trades", "Wenn der Gegner zuerst trifft, gewinnst du " + pct + "% der Trades", Page.FIGHTS_TRADES);
        }
    }

    public record LossCauseRule(double thresholdPct, int minLosses, double weight) implements Rule {
        public String id() { return "loss-cause"; }

        public Insight apply(Input in) {
            Trades.Summary t = in.stats().trades();
            if (t.losses() < minLosses) return null;
            Trades.Cause top = null;
            int n = 0;
            for (Map.Entry<Trades.Cause, Integer> e : t.lossCauses().entrySet()) {
                if (e.getKey() != Trades.Cause.NONE && e.getValue() > n) {
                    top = e.getKey();
                    n = e.getValue();
                }
            }
            if (top == null) return null;
            double pct = 100.0 * n / t.losses();
            if (pct < thresholdPct) return null;
            long p = Math.round(pct);
            double imp = weight * Math.max(1, pct / thresholdPct);
            return switch (top) {
                case MISS -> new Insight(id(), imp, Tone.BAD,
                        p + "% проигранных разменов начались после вашего промаха",
                        p + "% of lost trades started after your miss", p + "% der verlorenen Trades begannen nach deinem Fehlschlag", Page.ATTACK_HITS);
                case WEAK -> new Insight(id(), imp, Tone.BAD,
                        p + "% проигранных разменов начались после удара без заряда",
                        p + "% of lost trades started after an uncharged hit", p + "% der verlorenen Trades begannen nach einem ungeladenen Schlag", Page.ATTACK_STRIKES);
                case CAUGHT -> new Insight(id(), imp, Tone.BAD,
                        p + "% проигранных разменов начались с 3 block hit против вас",
                        p + "% of lost trades started with a 3 block hit against you", p + "% der verlorenen Trades begannen mit einem 3 Block Hit gegen dich", Page.DEFENSE_TAKEN);
                default -> new Insight(id(), imp, Tone.BAD,
                        p + "% проигранных разменов: неудачный jump reset",
                        p + "% of lost trades: a failed jump reset", p + "% der verlorenen Trades: ein misslungener Jump Reset", Page.DEFENSE_JUMP);
            };
        }
    }

    public record CaughtRule(double thresholdPct, int minHits, double weight) implements Rule {
        public String id() { return "caught"; }

        public Insight apply(Input in) {
            Stats.TakenStats t = in.stats().taken();
            if (t.rated() < minHits || !(t.caughtPct() >= thresholdPct)) return null;
            long pct = Math.round(t.caughtPct());
            double imp = weight * Math.max(1, t.caughtPct() / thresholdPct);
            return new Insight(id(), imp, Tone.BAD,
                    pct + "% ударов противника были 3 block hit против вас: он бил, пока был дальше вашей дальности",
                    pct + "% of opponent hits were 3 block hits against you: they hit from beyond your reach", pct + "% der gegnerischen Treffer waren 3 Block Hits gegen dich: er traf von außerhalb deiner Reichweite", Page.DEFENSE_TAKEN);
        }
    }

    public record GoodJumpRule(double thresholdPct, int minAttempts, double weight) implements Rule {
        public String id() { return "jr-good"; }

        public Insight apply(Input in) {
            Stats.JumpStats j = in.stats().jump();
            if (j.attempts() < minAttempts || j.successRatePct() < thresholdPct) return null;
            long pct = Math.round(j.successRatePct());
            return new Insight(id(), weight * Math.max(1, j.successRatePct() / thresholdPct), Tone.GOOD,
                    "Jump reset удаётся в " + pct + "% попыток",
                    "Jump reset succeeds in " + pct + "% of attempts", "Jump Reset gelingt in " + pct + "% der Versuche", Page.DEFENSE_JUMP);
        }
    }

    public record GoodAccuracyRule(double thresholdPct, int minAttacks, double weight) implements Rule {
        public String id() { return "accuracy-good"; }

        public Insight apply(Input in) {
            Stats.HitStats h = in.stats().hits();
            if (h.confirmed() + h.unconfirmed() + h.misses() < minAttacks || h.hitRatePct() < thresholdPct) return null;
            long pct = Math.round(h.hitRatePct());
            return new Insight(id(), weight * Math.max(1, h.hitRatePct() / thresholdPct), Tone.GOOD,
                    "Точность " + pct + "%: почти каждый удар попадает",
                    "Accuracy " + pct + "%: almost every swing lands", "Trefferquote " + pct + "%: fast jeder Schlag trifft", Page.ATTACK_HITS);
        }
    }

    public record GoodFirstHitRule(double thresholdPct, int minTrades, double weight) implements Rule {
        public String id() { return "first-hit-good"; }

        public Insight apply(Input in) {
            Trades.Summary t = in.stats().trades();
            if (t.total() < minTrades || !(t.firstPct() >= thresholdPct)) return null;
            long pct = Math.round(t.firstPct());
            return new Insight(id(), weight * Math.max(1, t.firstPct() / thresholdPct), Tone.GOOD,
                    "Вы бьёте первым в " + pct + "% разменов",
                    "You hit first in " + pct + "% of trades", "Du triffst zuerst in " + pct + "% der Trades", Page.FIGHTS_TRADES);
        }
    }

    /** Thresholds are conservative to avoid noise on short recordings. */
    public static List<Rule> defaults() {
        return List.of(
                new JumpOffsetRule(15, 5, 1.0),
                new LowChargeRule(0.85, 10, 1.0),
                new OutOfRangeRule(15, 5, 0.9),
                new AimMissRule(15, 5, 0.8),
                new AimCoachRule(0.8),
                new FirstHitRule(40, 6, 0.9),
                new SecondLossRule(30, 4, 0.9),
                new LossCauseRule(50, 4, 0.85),
                new CaughtRule(25, 10, 0.9),
                new GoodJumpRule(70, 10, 0.5),
                new GoodAccuracyRule(80, 20, 0.5),
                new GoodFirstHitRule(60, 6, 0.5));
    }

    public static List<Insight> evaluate(List<Rule> rules, Input in, int limit) {
        List<Insight> out = new ArrayList<>();
        for (Rule r : rules) {
            Insight i = r.apply(in);
            if (i != null) out.add(i);
        }
        // Praise goes after every problem, so it never pushes a problem out of the shown three.
        out.sort(Comparator.comparing((Insight i) -> i.tone() == Tone.GOOD).thenComparing(Comparator.comparingDouble(Insight::importance).reversed())
                .thenComparing(Insight::id));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
    }

    public static List<Insight> evaluate(Input in) {
        return evaluate(defaults(), in, MAX_SHOWN);
    }
}
