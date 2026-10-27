package dev.combatdata.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Everything built on top of the report across sessions: personal records, badges, training goals, the verdict
 * against each frequent opponent, a side-by-side comparison and a plain text version for sharing.
 */
public final class Coach {
    private Coach() {
    }

    // ---- Records ----

    /** value: shown number; atMs: when it happened (0 if not a single session). */
    public record Best(String id, double value, boolean percent, long atMs, String ru, String en, String de) {
        public String name() {
            return Lang.t(ru, en, de);
        }

        public String valueText() {
            return !Double.isFinite(value) ? "—" : percent ? Math.round(value) + "%" : String.valueOf(Math.round(value));
        }
    }

    public static List<Best> records(List<Report.Session> sessions) {
        List<Best> out = new ArrayList<>();
        out.add(bestOf(sessions, Report.Skill.AIM, "best-aim", "Лучший прицел за сессию", "Best aim in a session", "Bestes Zielen in einer Session"));
        out.add(bestOf(sessions, Report.Skill.HITTING, "best-hit", "Лучшая точность за сессию", "Best hit rate in a session", "Beste Trefferquote in einer Session"));
        out.add(bestOf(sessions, Report.Skill.JUMP_RESET, "best-jr", "Лучший jump reset за сессию", "Best jump reset in a session", "Bester Jump Reset in einer Session"));
        out.add(bestOf(sessions, Report.Skill.TRADES, "best-trades", "Больше всего выигранных разменов", "Most trades won in a session", "Meiste gewonnene Trades in einer Session"));
        int combo = 0, streak = 0, hits = 0;
        long comboAt = 0, streakAt = 0;
        for (Report.Session s : sessions) {
            if (s.d().get(Detail.MAX_COMBO) > combo) {
                combo = s.d().get(Detail.MAX_COMBO);
                comboAt = s.startMs();
            }
            if (s.d().get(Detail.JR_STREAK) > streak) {
                streak = s.d().get(Detail.JR_STREAK);
                streakAt = s.startMs();
            }
            hits += s.m().confirmed();
        }
        out.add(new Best("combo", combo == 0 ? Double.NaN : combo, false, comboAt, "Самое длинное комбо", "Longest combo", "Längste Combo"));
        out.add(new Best("jr-streak", streak == 0 ? Double.NaN : streak, false, streakAt, "Jump reset подряд", "Jump resets in a row", "Jump Resets in Folge"));
        out.add(new Best("sessions", sessions.size(), false, 0, "Сессий всего", "Sessions in total", "Sessions insgesamt"));
        out.add(new Best("hits", hits, false, 0, "Попаданий всего", "Hits in total", "Treffer insgesamt"));
        return out;
    }

    private static Best bestOf(List<Report.Session> sessions, Report.Skill k, String id, String ru, String en, String de) {
        double best = Double.NaN;
        long at = 0;
        for (Report.Session s : sessions) {
            double v = Report.value(k, s.m(), s.d());
            if (Double.isFinite(v) && (!Double.isFinite(best) || v > best)) {
                best = v;
                at = s.startMs();
            }
        }
        return new Best(id, best, true, at, ru, en, de);
    }

    // ---- Badges ----

    public record Badge(String id, String ru, String en, String de, String hintRu, String hintEn, String hintDe) {
        public String name() {
            return Lang.t(ru, en, de);
        }

        public String hint() {
            return Lang.t(hintRu, hintEn, hintDe);
        }
    }

    public static final List<Badge> BADGES = List.of(
            new Badge("sessions-5", "Начало", "Getting started", "Der Anfang", "5 сессий", "5 sessions", "5 Sessions"),
            new Badge("sessions-25", "Постоянный", "Regular", "Stammspieler", "25 сессий", "25 sessions", "25 Sessions"),
            new Badge("sessions-100", "Ветеран", "Veteran", "Veteran", "100 сессий", "100 sessions", "100 Sessions"),
            new Badge("hits-1000", "Тысяча ударов", "A thousand hits", "Tausend Treffer", "1000 попаданий всего", "1000 hits in total", "1000 Treffer insgesamt"),
            new Badge("aim-90", "Снайпер", "Sharpshooter", "Scharfschütze", "Сессия: прицел от 90% (от 30 взмахов)", "A session with 90%+ aim (30+ swings)", "Eine Session mit 90 %+ Zielen (ab 30 Schlägen)"),
            new Badge("hit-85", "Точный", "Accurate", "Treffsicher", "Сессия: точность от 85% (от 30 атак)", "A session with 85%+ hit rate (30+ attacks)", "Eine Session mit 85 %+ Trefferquote (ab 30 Angriffen)"),
            new Badge("jr-70", "Профи jump reset", "Jump reset pro", "Jump-Reset-Profi", "Сессия: jump reset от 70% (от 10 ударов)", "A session with 70%+ jump reset (10+ hits)", "Eine Session mit 70 %+ Jump Reset (ab 10 Treffern)"),
            new Badge("jr-streak-5", "Серия", "On a roll", "Lauf", "5 jump reset подряд", "5 jump resets in a row", "5 Jump Resets in Folge"),
            new Badge("jr-streak-10", "Неудержимый", "Unstoppable", "Unaufhaltsam", "10 jump reset подряд", "10 jump resets in a row", "10 Jump Resets in Folge"),
            new Badge("combo-5", "Комбо 5", "Combo 5", "Combo 5", "Комбо из 5 ударов", "A 5 hit combo", "Eine Combo mit 5 Treffern"),
            new Badge("combo-8", "Комбо-мастер", "Combo master", "Combo-Meister", "Комбо из 8 ударов", "An 8 hit combo", "Eine Combo mit 8 Treffern"),
            new Badge("trades-65", "Победитель разменов", "Trade winner", "Trade-Sieger", "Сессия: от 65% разменов (от 6)", "A session winning 65%+ of trades (6+)", "Eine Session mit 65 %+ gewonnenen Trades (ab 6)"));

    public static Set<String> earned(List<Report.Session> sessions) {
        Set<String> out = new LinkedHashSet<>();
        int n = sessions.size(), hits = 0, combo = 0, streak = 0;
        for (Report.Session s : sessions) {
            Progress.Metrics m = s.m();
            Detail d = s.d();
            hits += m.confirmed();
            combo = Math.max(combo, d.get(Detail.MAX_COMBO));
            streak = Math.max(streak, d.get(Detail.JR_STREAK));
            if (d.get(Detail.AIM_N) >= 30 && 100.0 * d.get(Detail.AIM_ON) / d.get(Detail.AIM_N) >= 90) out.add("aim-90");
            if (m.attacks() >= 30 && 100.0 * m.confirmed() / m.attacks() >= 85) out.add("hit-85");
            int jr = d.get(Detail.JR_OK) + d.get(Detail.JR_EARLY) + d.get(Detail.JR_LATE) + d.get(Detail.JR_NONE);
            if (jr >= 10 && 100.0 * d.get(Detail.JR_OK) / jr >= 70) out.add("jr-70");
            int tr = d.get(Detail.TR_WON) + d.get(Detail.TR_LOST);
            if (tr >= 6 && 100.0 * d.get(Detail.TR_WON) / tr >= 65) out.add("trades-65");
        }
        if (n >= 5) out.add("sessions-5");
        if (n >= 25) out.add("sessions-25");
        if (n >= 100) out.add("sessions-100");
        if (hits >= 1000) out.add("hits-1000");
        if (streak >= 5) out.add("jr-streak-5");
        if (streak >= 10) out.add("jr-streak-10");
        if (combo >= 5) out.add("combo-5");
        if (combo >= 8) out.add("combo-8");
        // Keep the catalog order.
        Set<String> ordered = new LinkedHashSet<>();
        for (Badge b : BADGES) if (out.contains(b.id())) ordered.add(b.id());
        return ordered;
    }

    public static Badge badge(String id) {
        for (Badge b : BADGES) if (b.id().equals(id)) return b;
        return null;
    }

    // ---- Goals ----

    /** target in percent; for CAUGHT the target is an upper limit. */
    public record Goal(Report.Skill skill, int target) {
        public boolean reached(double value) {
            return Double.isFinite(value) && (skill == Report.Skill.CAUGHT ? value <= target : value >= target);
        }

        /** 0..1 how far along; CAUGHT counts down from 50%. */
        public double progress(double value) {
            if (!Double.isFinite(value)) return 0;
            if (skill == Report.Skill.CAUGHT) return Math.max(0, Math.min(1, (50 - value) / Math.max(1, 50 - target)));
            return Math.max(0, Math.min(1, value / Math.max(1, target)));
        }
    }

    public static int defaultTarget(Report.Skill s) {
        return (int) Report.marks(s)[0];
    }

    public static List<Goal> parseGoals(String s) {
        List<Goal> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;
        for (String part : s.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) continue;
            try {
                Report.Skill k = Report.Skill.valueOf(kv[0].trim());
                int t = Math.max(1, Math.min(100, Integer.parseInt(kv[1].trim())));
                if (out.stream().noneMatch(g -> g.skill() == k)) out.add(new Goal(k, t));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static String goalsText(List<Goal> goals) {
        StringBuilder b = new StringBuilder();
        for (Goal g : goals) {
            if (!b.isEmpty()) b.append(',');
            b.append(g.skill().name()).append(':').append(g.target());
        }
        return b.toString();
    }

    // ---- Opponents ----

    public record Rival(UUID id, String name, int sessions, Progress.Metrics m, Detail d, Report.Result report) {
    }

    /** Opponents met in the given recordings, most fought first; each with their own report. */
    public static List<Rival> rivals(List<Progress.Summary> summaries, long fromMs, long toMs, int limit) {
        Map<UUID, Object[]> acc = new LinkedHashMap<>();
        for (Progress.Summary s : summaries) {
            if (s.wallStartMs() < fromMs || s.wallStartMs() > toMs) continue;
            for (Map.Entry<UUID, Progress.Opp> e : s.opponents().entrySet()) {
                Object[] a = acc.computeIfAbsent(e.getKey(), k -> new Object[]{null, 0, Progress.Metrics.ZERO, Detail.ZERO});
                if (e.getValue().name() != null) a[0] = e.getValue().name();
                a[1] = (int) a[1] + 1;
                a[2] = ((Progress.Metrics) a[2]).plus(e.getValue().m());
                a[3] = ((Detail) a[3]).plus(e.getValue().d());
            }
        }
        List<Rival> out = new ArrayList<>();
        for (Map.Entry<UUID, Object[]> e : acc.entrySet()) {
            Object[] a = e.getValue();
            Progress.Metrics m = (Progress.Metrics) a[2];
            Detail d = (Detail) a[3];
            if (m.attacks() + d.get(Detail.TAKEN_RATED) < 5) continue;
            out.add(new Rival(e.getKey(), (String) a[0], (int) a[1], m, d, Report.of(m, d, null, null, (int) a[1], 0, 0)));
        }
        out.sort(Comparator.comparingInt((Rival r) -> -(r.m().attacks() + r.d().get(Detail.TAKEN_RATED))));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
    }

    // ---- Sharing ----

    /** The report as plain text for the clipboard (Discord, notes). */
    public static String shareText(Report.Result r, String title) {
        StringBuilder b = new StringBuilder("Combat Data · ").append(title).append('\n');
        for (String s : r.headline(false)) b.append(s).append('\n');
        if (!r.focus().isEmpty()) {
            b.append('\n').append(Lang.t("Что тренировать:", "What to train:", "Was du trainieren solltest:")).append('\n');
            int i = 1;
            for (Report.Focus f : r.focus()) b.append(i++).append(". ").append(f.text()).append('\n');
        }
        b.append('\n');
        for (Report.Line l : r.lines()) {
            b.append("- ").append(l.name()).append(": ").append(Report.pctText(l.value())).append(" (").append(SkillScores.gradeName(l.grade())).append(')');
            if (Double.isFinite(l.better())) b.append(", ").append(Report.signed(l.better()));
            b.append('\n');
        }
        return b.toString().trim();
    }
}
