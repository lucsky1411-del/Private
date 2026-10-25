package dev.combatdata.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The coach view: one simple grade per skill (aim, hitting, jump reset, trades) with the value, what counts as good
 * and one sentence on what the number means. Also used to color key numbers the same way everywhere:
 * green good, yellow fair, red needs work.
 */
public final class SkillScores {
    private SkillScores() {
    }

    public enum Grade { GOOD, FAIR, WORK, NONE }

    public enum Skill { AIM, HITTING, JUMP_RESET, TRADES }

    /** value: shown number; score: 0..100 for sorting; samples: how much data the grade rests on. */
    public record Tile(Skill skill, Grade grade, double score, String value, int samples, Insights.Page page) {
        public String name() {
            return SkillScores.name(skill);
        }

        public String target() {
            return switch (skill) {
                case AIM -> Lang.t("Хорошо: от 85% взмахов на противнике", "Good: 85%+ of swings on the opponent", "Gut: ab 85 % der Schläge auf dem Gegner");
                case HITTING -> Lang.t("Хорошо: от 75% попаданий", "Good: 75%+ of attacks land", "Gut: ab 75 % der Angriffe treffen");
                case JUMP_RESET -> Lang.t("Хорошо: от 60% вовремя", "Good: 60%+ in time", "Gut: ab 60 % rechtzeitig");
                case TRADES -> Lang.t("Хорошо: от 55% выигранных разменов", "Good: 55%+ of trades won", "Gut: ab 55 % gewonnene Trades");
            };
        }

        public String explain() {
            return switch (skill) {
                case AIM -> Lang.t("Был ли прицел на противнике, когда вы били.", "Was your crosshair on the opponent when you swung.",
                        "War dein Fadenkreuz auf dem Gegner, als du geschlagen hast.");
                case HITTING -> Lang.t("Сколько ваших атак сервер засчитал уроном.", "How many of your attacks the server counted as damage.",
                        "Wie viele deiner Angriffe der Server als Schaden gezählt hat.");
                case JUMP_RESET -> Lang.t("Прыгнули ли вы сразу после удара по вам: так меньше отлетаете.",
                        "Did you jump right after getting hit: that cuts the knockback.",
                        "Bist du direkt nach einem Treffer gesprungen: das verringert den Rückstoß.");
                case TRADES -> Lang.t("Кто нанёс больше ударов в коротких обменах.", "Who landed more hits in short exchanges.",
                        "Wer in kurzen Schlagabtauschen mehr Treffer gelandet hat.");
            };
        }

        public String gradeText() {
            return gradeName(grade);
        }
    }

    public static String name(Skill s) {
        return switch (s) {
            case AIM -> Lang.t("Прицел", "Aim", "Zielen");
            case HITTING -> Lang.t("Попадания", "Hitting", "Treffen");
            case JUMP_RESET -> "Jump reset";
            case TRADES -> Lang.t("Размены", "Trades", "Trades");
        };
    }

    public static String gradeName(Grade g) {
        return switch (g) {
            case GOOD -> Lang.t("хорошо", "good", "gut");
            case FAIR -> Lang.t("нормально", "fair", "okay");
            case WORK -> Lang.t("тренировать", "practice", "üben");
            case NONE -> Lang.t("мало данных", "not enough data", "zu wenig Daten");
        };
    }

    /** Higher is better. */
    public static Grade grade(double v, double good, double fair) {
        if (!Double.isFinite(v)) return Grade.NONE;
        return v >= good ? Grade.GOOD : v >= fair ? Grade.FAIR : Grade.WORK;
    }

    /** Lower is better. */
    public static Grade gradeLow(double v, double good, double fair) {
        if (!Double.isFinite(v)) return Grade.NONE;
        return v <= good ? Grade.GOOD : v <= fair ? Grade.FAIR : Grade.WORK;
    }

    public static final int MIN_AIM = 10, MIN_ATTACKS = 10, MIN_JR = 5, MIN_TRADES = 4;

    public static List<Tile> tiles(Stats.Result s, AimAnalysis.Result aim) {
        List<Tile> out = new ArrayList<>();
        int n = aim == null ? 0 : aim.total();
        double v = n >= MIN_AIM ? aim.accuracyPct() : Double.NaN;
        out.add(new Tile(Skill.AIM, grade(v, 85, 70), v, pct(v), n, Insights.Page.ATTACK_AIM));

        Stats.HitStats h = s.hits();
        int attacks = h.confirmed() + h.unconfirmed() + h.misses();
        v = attacks >= MIN_ATTACKS ? h.hitRatePct() : Double.NaN;
        out.add(new Tile(Skill.HITTING, grade(v, 75, 60), v, pct(v), attacks, Insights.Page.ATTACK_HITS));

        Stats.JumpStats j = s.jump();
        v = j.attempts() + j.noAttempt() >= MIN_JR ? 100.0 * j.success() / Math.max(1, j.attempts() + j.noAttempt()) : Double.NaN;
        out.add(new Tile(Skill.JUMP_RESET, grade(v, 60, 40), v, pct(v), j.attempts() + j.noAttempt(), Insights.Page.DEFENSE_JUMP));

        Trades.Summary t = s.trades();
        int decided = t.wins() + t.losses();
        v = t.total() >= MIN_TRADES && decided > 0 ? 100.0 * t.wins() / decided : Double.NaN;
        out.add(new Tile(Skill.TRADES, grade(v, 55, 45), v, pct(v), t.total(), Insights.Page.FIGHTS_TRADES));
        return out;
    }

    static String pct(double v) {
        return Double.isFinite(v) ? Math.round(v) + "%" : "—";
    }

    /** Grades for single numbers shown in cards, so the same value has the same color everywhere. */
    public static Grade reach(double meanBlocks) {
        return grade(meanBlocks, 2.6, 2.2);
    }

    public static Grade charge(double mean) {
        return grade(mean, 0.92, 0.85);
    }

    public static Grade firstHit(double pct) {
        return grade(pct, 55, 40);
    }

    public static Grade caught(double pct) {
        return gradeLow(pct, 10, 25);
    }
}
