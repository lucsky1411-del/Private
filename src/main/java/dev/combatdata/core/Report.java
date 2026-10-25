package dev.combatdata.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The coach report: turns the numbers of one recording or of many sessions into a verdict. One line per skill with a
 * grade and the change against the block of sessions before, the strongest and weakest skill, and up to three focus
 * points with concrete advice taken from the details (which way misses go, how jump resets fail, why trades are lost).
 */
public final class Report {
    private Report() {
    }

    public enum Skill { AIM, HITTING, JUMP_RESET, TRADES, CHARGE, CAUGHT }

    /** value / previous in percent (NaN without enough data); for CAUGHT lower is better. */
    public record Line(Skill skill, double value, double previous, SkillScores.Grade grade, int samples) {
        public double trend() {
            return Double.isFinite(value) && Double.isFinite(previous) ? value - previous : Double.NaN;
        }

        /** Trend with the sign turned so that plus always means better. */
        public double better() {
            double t = trend();
            return skill == Skill.CAUGHT ? -t : t;
        }

        public String name() {
            return Report.name(skill);
        }
    }

    public record Focus(Skill skill, double priority, String ru, String en, String de) {
        public String text() {
            return Lang.t(ru, en, de);
        }
    }

    public record Result(int sessions, long fromMs, long toMs, List<Line> lines, List<Focus> focus, boolean hasPrevious) {
        public Line line(Skill s) {
            for (Line l : lines) if (l.skill() == s) return l;
            return null;
        }

        public boolean empty() {
            for (Line l : lines) if (Double.isFinite(l.value())) return false;
            return true;
        }

        /** Strongest skill: the one furthest above its "good" mark. */
        public Line best() {
            Line best = null;
            double bs = -1e9;
            for (Line l : lines) {
                if (l.grade() != SkillScores.Grade.GOOD) continue;
                double s = margin(l);
                if (s > bs) {
                    bs = s;
                    best = l;
                }
            }
            return best;
        }

        /** The summary in a few sentences, most important first. */
        public List<String> headline(boolean single) {
            List<String> out = new ArrayList<>();
            if (empty()) {
                out.add(Lang.t("Пока мало данных для вывода: нужны бои с ударами.", "Not enough data for a verdict yet: it needs fights with hits.",
                        "Noch zu wenig Daten für ein Urteil: es braucht Kämpfe mit Treffern."));
                return out;
            }
            int good = 0, work = 0, rated = 0;
            for (Line l : lines) {
                if (l.grade() == SkillScores.Grade.NONE) continue;
                rated++;
                if (l.grade() == SkillScores.Grade.GOOD) good++;
                if (l.grade() == SkillScores.Grade.WORK) work++;
            }
            String scope = single ? Lang.t("В этой записи", "In this recording", "In dieser Aufnahme")
                    : sessions == 1 ? Lang.t("За последнюю сессию", "Over your last session", "In deiner letzten Session")
                    : Lang.t("За последние " + sessions + " сессий", "Over your last " + sessions + " sessions", "Über deine letzten " + sessions + " Sessions");
            String overall = work == 0 && good == rated ? Lang.t("всё в зелёной зоне", "everything is in the green", "alles im grünen Bereich")
                    : work == 0 ? Lang.t("в целом уверенно", "solid overall", "insgesamt solide")
                    : work >= Math.max(2, rated / 2) ? Lang.t("есть что подтянуть", "there is a lot to work on", "es gibt einiges zu verbessern")
                    : Lang.t("в целом неплохо, но есть одна-две слабые стороны", "decent overall, with one or two weak spots",
                    "insgesamt ordentlich, mit ein, zwei Schwachstellen");
            out.add(scope + ": " + overall + ".");
            Line b = best();
            if (b != null) out.add(Lang.t("Сильная сторона: ", "Strength: ", "Stärke: ") + b.name() + " (" + pctText(b.value()) + ").");
            if (!focus.isEmpty()) {
                Line w = line(focus.get(0).skill());
                out.add(Lang.t("Главное, что стоит улучшить: ", "Biggest thing to improve: ", "Größte Baustelle: ") + name(focus.get(0).skill())
                        + (w != null && Double.isFinite(w.value()) ? " (" + pctText(w.value()) + ")" : "") + ".");
            }
            if (hasPrevious) {
                Line up = null, down = null;
                for (Line l : lines) {
                    double d = l.better();
                    if (!Double.isFinite(d)) continue;
                    if (d >= 3 && (up == null || d > up.better())) up = l;
                    if (d <= -3 && (down == null || d < down.better())) down = l;
                }
                if (up != null || down != null) {
                    String s = "";
                    if (up != null) s += Lang.t("Лучше, чем раньше: ", "Better than before: ", "Besser als vorher: ") + up.name() + " " + signed(up.better());
                    if (down != null) s += (s.isEmpty() ? "" : "; ") + Lang.t("хуже: ", "worse: ", "schlechter: ") + down.name() + " " + signed(down.better());
                    out.add(s + ".");
                } else {
                    out.add(Lang.t("По сравнению с прошлым блоком почти без изменений.", "About the same as the block before.",
                            "Etwa gleich wie im Block davor."));
                }
            }
            return out;
        }
    }

    static double margin(Line l) {
        double[] g = marks(l.skill());
        if (!Double.isFinite(l.value())) return -1e9;
        return l.skill() == Skill.CAUGHT ? (g[0] - l.value()) / 100 : (l.value() - g[0]) / Math.max(1, g[0]);
    }

    /** {good, fair} marks; for CAUGHT the values are upper limits. */
    static double[] marks(Skill s) {
        return switch (s) {
            case AIM -> new double[]{85, 70};
            case HITTING -> new double[]{75, 60};
            case JUMP_RESET -> new double[]{60, 40};
            case TRADES -> new double[]{55, 45};
            case CHARGE -> new double[]{92, 85};
            case CAUGHT -> new double[]{10, 25};
        };
    }

    static double weight(Skill s) {
        return switch (s) {
            case AIM, JUMP_RESET -> 1.0;
            case HITTING -> 0.9;
            case TRADES -> 0.8;
            case CHARGE, CAUGHT -> 0.7;
        };
    }

    public static String name(Skill s) {
        return switch (s) {
            case AIM -> Lang.t("Прицел", "Aim", "Zielen");
            case HITTING -> Lang.t("Попадания", "Hitting", "Treffen");
            case JUMP_RESET -> "Jump reset";
            case TRADES -> Lang.t("Размены", "Trades", "Trades");
            case CHARGE -> Lang.t("Заряд удара", "Attack charge", "Schlagaufladung");
            case CAUGHT -> Lang.t("3 block hit против вас", "3 block hits against you", "3 Block Hits gegen dich");
        };
    }

    static String pctText(double v) {
        return Double.isFinite(v) ? Math.round(v) + "%" : "—";
    }

    static String signed(double v) {
        return (v >= 0 ? "+" : "−") + Math.round(Math.abs(v)) + "%";
    }

    static double value(Skill s, Progress.Metrics m, Detail d) {
        return switch (s) {
            case AIM -> Detail.pct(d.get(Detail.AIM_ON), d.get(Detail.AIM_N), SkillScores.MIN_AIM);
            case HITTING -> Detail.pct(m.confirmed(), m.attacks(), SkillScores.MIN_ATTACKS);
            case JUMP_RESET -> Detail.pct(d.get(Detail.JR_OK), d.get(Detail.JR_OK) + d.get(Detail.JR_EARLY) + d.get(Detail.JR_LATE)
                    + d.get(Detail.JR_NONE), SkillScores.MIN_JR);
            case TRADES -> Detail.pct(d.get(Detail.TR_WON), d.get(Detail.TR_WON) + d.get(Detail.TR_LOST), SkillScores.MIN_TRADES);
            case CHARGE -> m.confirmed() < SkillScores.MIN_ATTACKS ? Double.NaN : 100.0 * m.strengthSum() / m.confirmed();
            case CAUGHT -> Detail.pct(d.get(Detail.TAKEN_CAUGHT), d.get(Detail.TAKEN_RATED), 10);
        };
    }

    static int samples(Skill s, Progress.Metrics m, Detail d) {
        return switch (s) {
            case AIM -> d.get(Detail.AIM_N);
            case HITTING, CHARGE -> s == Skill.HITTING ? m.attacks() : m.confirmed();
            case JUMP_RESET -> d.get(Detail.JR_OK) + d.get(Detail.JR_EARLY) + d.get(Detail.JR_LATE) + d.get(Detail.JR_NONE);
            case TRADES -> d.get(Detail.TR_WON) + d.get(Detail.TR_LOST);
            case CAUGHT -> d.get(Detail.TAKEN_RATED);
        };
    }

    static SkillScores.Grade grade(Skill s, double v) {
        double[] g = marks(s);
        return s == Skill.CAUGHT ? SkillScores.gradeLow(v, g[0], g[1]) : SkillScores.grade(v, g[0], g[1]);
    }

    public static Result of(Progress.Metrics m, Detail d, Progress.Metrics prevM, Detail prevD, int sessions, long from, long to) {
        boolean hasPrev = prevM != null && prevD != null;
        List<Line> lines = new ArrayList<>();
        for (Skill s : Skill.values()) {
            double v = value(s, m, d);
            lines.add(new Line(s, v, hasPrev ? value(s, prevM, prevD) : Double.NaN, grade(s, v), samples(s, m, d)));
        }
        List<Focus> focus = new ArrayList<>();
        for (Line l : lines) {
            if (l.grade() != SkillScores.Grade.WORK && l.grade() != SkillScores.Grade.FAIR) continue;
            double gap = -margin(l);
            double pr = weight(l.skill()) * Math.max(0.05, gap) * (l.grade() == SkillScores.Grade.WORK ? 1.5 : 1.0);
            if (Double.isFinite(l.better()) && l.better() <= -5) pr *= 1.3;
            focus.add(advice(l, m, d, pr));
        }
        focus.sort(Comparator.comparingDouble(Focus::priority).reversed());
        return new Result(sessions, from, to, List.copyOf(lines), List.copyOf(focus.subList(0, Math.min(3, focus.size()))), hasPrev);
    }

    /** Concrete advice for one weak skill, picked from what the details say went wrong. */
    static Focus advice(Line l, Progress.Metrics m, Detail d, double pr) {
        String v = pctText(l.value());
        switch (l.skill()) {
            case AIM -> {
                int miss = d.get(Detail.AIM_N) - d.get(Detail.AIM_ON);
                int left = d.get(Detail.MISS_L), right = d.get(Detail.MISS_R), up = d.get(Detail.MISS_UP), down = d.get(Detail.MISS_DOWN);
                double strafe = Detail.pct(d.get(Detail.STRAFE_ON), d.get(Detail.STRAFE_N), 8), still = Detail.pct(d.get(Detail.STILL_ON), d.get(Detail.STILL_N), 5);
                double lead = d.get(Detail.LEAD_N) == 0 ? Double.NaN : d.get(Detail.LEAD_TENTHS) / 10.0 / d.get(Detail.LEAD_N);
                double flick = Detail.pct(d.get(Detail.FLICK_ON), d.get(Detail.FLICK_N), 5), calm = Detail.pct(d.get(Detail.CALM_ON), d.get(Detail.CALM_N), 5);
                if (Double.isFinite(strafe) && Double.isFinite(still) && still - strafe >= 10 && Double.isFinite(lead) && lead <= -0.6) {
                    return new Focus(l.skill(), pr,
                            "Прицел: против стрейфа попадаете " + pctText(strafe) + " (стоящих " + pctText(still) + "), прицел отстаёт. Ведите мышь вместе с противником, чуть впереди него.",
                            "Aim: against strafing opponents you hit " + pctText(strafe) + " (still ones " + pctText(still) + "), your crosshair trails. Move with them, slightly ahead.",
                            "Zielen: Gegen seitlich laufende Gegner triffst du " + pctText(strafe) + " (stehende " + pctText(still) + "), dein Fadenkreuz hängt hinterher. Geh mit der Maus mit, leicht vor ihm.");
                }
                if (Double.isFinite(flick) && Double.isFinite(calm) && calm - flick >= 15) {
                    return new Focus(l.skill(), pr,
                            "Прицел: на резком повороте мыши попадаете " + pctText(flick) + " против " + pctText(calm) + ". Сначала наведите прицел, потом бейте.",
                            "Aim: while flicking you hit " + pctText(flick) + " vs " + pctText(calm) + " when steady. Put the crosshair on them first, then click.",
                            "Zielen: Bei schnellen Mausbewegungen triffst du " + pctText(flick) + ", ruhig " + pctText(calm) + ". Erst das Fadenkreuz auf den Gegner, dann klicken.");
                }
                if (miss >= 4 && left + right >= 0.6 * miss && Math.max(left, right) >= 0.7 * (left + right)) {
                    boolean lf = left > right;
                    return new Focus(l.skill(), pr,
                            "Прицел " + v + ": промахи в основном " + (lf ? "слева" : "справа") + " от противника. Держите прицел чуть " + (lf ? "правее" : "левее") + ".",
                            "Aim " + v + ": your misses mostly pass " + (lf ? "left" : "right") + " of the opponent. Keep the crosshair a bit more to the " + (lf ? "right" : "left") + ".",
                            "Zielen " + v + ": Deine Fehlschläge gehen meist " + (lf ? "links" : "rechts") + " am Gegner vorbei. Halt das Fadenkreuz etwas weiter " + (lf ? "rechts" : "links") + ".");
                }
                if (miss >= 4 && up + down >= 0.5 * miss) {
                    boolean hi = up > down;
                    return new Focus(l.skill(), pr,
                            "Прицел " + v + ": промахи в основном " + (hi ? "над головой" : "под ногами") + ". Цельтесь в грудь.",
                            "Aim " + v + ": your misses mostly go " + (hi ? "over the head" : "under the feet") + ". Aim at the chest.",
                            "Zielen " + v + ": Deine Fehlschläge gehen meist " + (hi ? "über den Kopf" : "unter den Füßen durch") + ". Ziel auf die Brust.");
                }
                return new Focus(l.skill(), pr,
                        "Прицел " + v + ": держите прицел на груди противника и между ударами, а не только в момент клика.",
                        "Aim " + v + ": keep the crosshair on the opponent's chest between hits too, not only when you click.",
                        "Zielen " + v + ": Halt das Fadenkreuz auch zwischen den Schlägen auf der Brust des Gegners, nicht nur beim Klicken.");
            }
            case HITTING -> {
                double oor = Detail.pct(d.get(Detail.MISS_OOR), m.attacks(), SkillScores.MIN_ATTACKS);
                double charge = value(Skill.CHARGE, m, d);
                if (Double.isFinite(oor) && oor >= 12) {
                    return new Focus(l.skill(), pr,
                            "Попадания " + v + ": " + pctText(oor) + " ударов были дальше вашей дальности. Бейте, когда противник ближе 3 блоков.",
                            "Hitting " + v + ": " + pctText(oor) + " of your swings were out of reach. Swing when they are closer than 3 blocks.",
                            "Treffen " + v + ": " + pctText(oor) + " deiner Schläge waren außerhalb der Reichweite. Schlag erst, wenn der Gegner näher als 3 Blöcke ist.");
                }
                if (Double.isFinite(charge) && charge < 88) {
                    return new Focus(l.skill(), pr,
                            "Попадания " + v + ": удары часто без полного заряда (" + pctText(charge) + "). Реже кликайте, ждите перезарядку.",
                            "Hitting " + v + ": your hits are often not fully charged (" + pctText(charge) + "). Click less, wait for the cooldown.",
                            "Treffen " + v + ": Deine Schläge sind oft nicht voll aufgeladen (" + pctText(charge) + "). Klick weniger und warte den Cooldown ab.");
                }
                return new Focus(l.skill(), pr,
                        "Попадания " + v + ": многие удары мимо. Сначала прицел на противнике, потом удар.",
                        "Hitting " + v + ": many swings miss. Crosshair on the opponent first, then swing.",
                        "Treffen " + v + ": Viele Schläge gehen daneben. Erst das Fadenkreuz auf den Gegner, dann schlagen.");
            }
            case JUMP_RESET -> {
                int early = d.get(Detail.JR_EARLY), late = d.get(Detail.JR_LATE), none = d.get(Detail.JR_NONE);
                int all = d.get(Detail.JR_OK) + early + late + none;
                if (none >= early && none >= late) {
                    return new Focus(l.skill(), pr,
                            "Jump reset " + v + ": после " + pctText(100.0 * none / Math.max(1, all)) + " ударов по вам прыжка не было. Прыгайте на каждый удар.",
                            "Jump reset " + v + ": you did not jump after " + pctText(100.0 * none / Math.max(1, all)) + " of the hits on you. Jump on every hit.",
                            "Jump Reset " + v + ": Nach " + pctText(100.0 * none / Math.max(1, all)) + " der Treffer auf dich bist du gar nicht gesprungen. Spring bei jedem Treffer.");
                }
                boolean lt = late >= early;
                return new Focus(l.skill(), pr,
                        "Jump reset " + v + ": чаще всего прыжок " + (lt ? "слишком поздно" : "слишком рано") + ". " + (lt ? "Прыгайте сразу, как только вас ударили." : "Прыгайте только после удара, не заранее."),
                        "Jump reset " + v + ": most failed jumps were " + (lt ? "too late" : "too early") + ". " + (lt ? "Jump the moment you get hit." : "Jump only after the hit lands, not before."),
                        "Jump Reset " + v + ": Die meisten Sprünge waren " + (lt ? "zu spät" : "zu früh") + ". " + (lt ? "Spring genau in dem Moment, in dem du getroffen wirst." : "Spring erst, wenn der Treffer da ist, nicht vorher."));
            }
            case TRADES -> {
                int[] c = {d.get(Detail.LOSS_MISS), d.get(Detail.LOSS_WEAK), d.get(Detail.LOSS_CAUGHT), d.get(Detail.LOSS_JR)};
                int lost = Math.max(1, d.get(Detail.TR_LOST)), top = 0;
                for (int i = 1; i < 4; i++) if (c[i] > c[top]) top = i;
                if (c[top] >= 0.35 * lost && c[top] >= 2) {
                    String p = pctText(100.0 * c[top] / lost);
                    return switch (top) {
                        case 0 -> new Focus(l.skill(), pr, "Размены " + v + ": " + p + " проигранных начались с вашего промаха. Не бейте наугад, бейте наверняка.",
                                "Trades " + v + ": " + p + " of lost ones started with your miss. Do not swing blind, swing when it lands.",
                                "Trades " + v + ": " + p + " der verlorenen begannen mit deinem Fehlschlag. Schlag nicht ins Blaue, sondern wenn es sicher trifft.");
                        case 1 -> new Focus(l.skill(), pr, "Размены " + v + ": " + p + " проигранных начались с удара без заряда. Ждите полный заряд перед первым ударом.",
                                "Trades " + v + ": " + p + " of lost ones started with an uncharged hit. Wait for a full charge before the first hit.",
                                "Trades " + v + ": " + p + " der verlorenen begannen mit einem ungeladenen Schlag. Warte vor dem ersten Schlag auf volle Aufladung.");
                        case 2 -> new Focus(l.skill(), pr, "Размены " + v + ": " + p + " проигранных начались с 3 block hit против вас. Не идите прямо на противника, заходите стрейфом.",
                                "Trades " + v + ": " + p + " of lost ones started with a 3 block hit against you. Do not walk straight at them, come in with a strafe.",
                                "Trades " + v + ": " + p + " der verlorenen begannen mit einem 3 Block Hit gegen dich. Lauf nicht gerade auf ihn zu, komm seitlich (Strafe).");
                        default -> new Focus(l.skill(), pr, "Размены " + v + ": " + p + " проигранных начались с неудачного jump reset. Тренируйте прыжок сразу после удара.",
                                "Trades " + v + ": " + p + " of lost ones started with a failed jump reset. Practice jumping right after the hit.",
                                "Trades " + v + ": " + p + " der verlorenen begannen mit einem misslungenen Jump Reset. Übe, direkt nach dem Treffer zu springen.");
                    };
                }
                double first = m.trades() < SkillScores.MIN_TRADES ? Double.NaN : 100.0 * m.firstHits() / m.trades();
                return new Focus(l.skill(), pr,
                        "Размены " + v + (Double.isFinite(first) ? ", первый удар в " + pctText(first) : "") + ". Начинайте размен со спринтом и бейте первым.",
                        "Trades " + v + (Double.isFinite(first) ? ", first hit in " + pctText(first) : "") + ". Start trades with sprint and land the first hit.",
                        "Trades " + v + (Double.isFinite(first) ? ", erster Treffer in " + pctText(first) : "") + ". Geh mit Sprint in den Trade und triff zuerst.");
            }
            case CHARGE -> {
                return new Focus(l.skill(), pr,
                        "Заряд " + v + ": вы кликаете слишком часто. Ждите полную перезарядку, тогда удар сильнее и возможен крит.",
                        "Charge " + v + ": you click too often. Wait for the full cooldown: the hit is stronger and can crit.",
                        "Aufladung " + v + ": Du klickst zu oft. Warte den vollen Cooldown ab, dann ist der Schlag stärker und kann critten.");
            }
            default -> {
                return new Focus(l.skill(), pr,
                        v + " ударов по вам были 3 block hit: противник бил издалека. Не подходите по прямой, держите дистанцию или заходите сбоку.",
                        v + " of hits on you were 3 block hits: they hit from far. Do not approach in a straight line, keep distance or come from the side.",
                        v + " der Treffer auf dich waren 3 Block Hits: er hat von weit weg getroffen. Lauf nicht gerade auf ihn zu, halt Abstand oder komm von der Seite.");
            }
        }
    }

    /** One session: a day of play or one recording, with everything in it added up. */
    public record Session(long startMs, long endMs, int recordings, Progress.Metrics m, Detail d) {
    }

    public static List<Session> sessions(List<Progress.Summary> all, boolean byDay, ZoneId zone) {
        List<Progress.Summary> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingLong(Progress.Summary::wallStartMs));
        List<Session> out = new ArrayList<>();
        LocalDate day = null;
        long st = 0, en = 0;
        int n = 0;
        Progress.Metrics m = Progress.Metrics.ZERO;
        Detail d = Detail.ZERO;
        for (Progress.Summary s : sorted) {
            if (s.all().attacks() == 0 && s.detail().get(Detail.JR_OK) + s.detail().get(Detail.JR_NONE) == 0) continue;
            LocalDate dd = Instant.ofEpochMilli(s.wallStartMs()).atZone(zone).toLocalDate();
            if (n > 0 && (!byDay || !dd.equals(day))) {
                out.add(new Session(st, en, n, m, d));
                n = 0;
                m = Progress.Metrics.ZERO;
                d = Detail.ZERO;
            }
            if (n == 0) st = s.wallStartMs();
            day = dd;
            en = s.wallStartMs() + s.durationMs();
            n++;
            m = m.plus(s.all());
            d = d.plus(s.detail());
        }
        if (n > 0) out.add(new Session(st, en, n, m, d));
        return out;
    }

    /** Report over the last n sessions, compared with the n before them. */
    public static Result last(List<Session> sessions, int n) {
        int size = sessions.size(), k = Math.min(n, size);
        Progress.Metrics m = Progress.Metrics.ZERO, pm = Progress.Metrics.ZERO;
        Detail d = Detail.ZERO, pd = Detail.ZERO;
        for (int i = size - k; i < size; i++) {
            m = m.plus(sessions.get(i).m());
            d = d.plus(sessions.get(i).d());
        }
        int prevFrom = Math.max(0, size - 2 * k), prevN = size - k - prevFrom;
        for (int i = prevFrom; i < size - k; i++) {
            pm = pm.plus(sessions.get(i).m());
            pd = pd.plus(sessions.get(i).d());
        }
        boolean hasPrev = prevN >= Math.max(1, k / 2);
        long from = k == 0 ? 0 : sessions.get(size - k).startMs(), to = k == 0 ? 0 : sessions.get(size - 1).endMs();
        return of(m, d, hasPrev ? pm : null, hasPrev ? pd : null, k, from, to);
    }

    /** Report sizes offered: one, two and four steps, then everything, as far as there are sessions. */
    public static List<Integer> windows(int sessions, int step) {
        List<Integer> out = new ArrayList<>();
        for (int k : new int[]{step, 2 * step, 4 * step}) if (k <= sessions) out.add(k);
        if (sessions > 0 && (out.isEmpty() || out.get(out.size() - 1) < sessions)) out.add(sessions);
        return out;
    }

    /** The last milestone reached (a multiple of step), 0 before the first. */
    public static int milestone(int sessions, int step) {
        return step <= 0 ? 0 : sessions / step * step;
    }
}
