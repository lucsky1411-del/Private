package dev.combatdata.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aim coaching: where the crosshair was relative to the opponent on every swing (hits and misses), which way the
 * misses went, how the aim behaves against strafing or jumping targets, while flicking, in the air and by distance,
 * and what to change. Out-of-range misses count as "on target": the aim was right, only the distance was not.
 *
 * Crosshair position is in hitbox units: x from -1 (left edge) to 1 (right edge), y from 0 (feet) to 1 (top of
 * the head); values outside that range are off the body. Motion fields come from the attack itself (0.2.1+) or,
 * for older recordings, from position events around the attack.
 */
public final class AimAnalysis {
    private AimAnalysis() {
    }

    public static final double HALF_W = 0.3, HEIGHT = 1.8;
    /** Target moving across the screen faster than this counts as strafing. */
    public static final double STRAFE_BPS = 1.5;
    /** Target going up or down faster than this counts as jumping or falling. */
    public static final double JUMP_BPS = 2.0;
    /** Crosshair turning faster than this at the click counts as a flick. */
    public static final double FLICK_DPS = 300;
    /** The aim sweet spot: chest to neck. Hitting there leaves the most room for the target to move. */
    public static final double BEST_LO = 0.5, BEST_HI = 0.8, BEST_Y = 0.65;
    /** Hits this far out towards the side edge count as "barely". */
    public static final double EDGE_X = 0.7;
    public static final double[] DIST_EDGES = {1.0, 2.0, 2.5, 3.0};

    public enum Side { LEFT, RIGHT, ABOVE, BELOW }

    public enum Tone { BAD, TIP, GOOD }

    /**
     * One swing. x, y: crosshair in hitbox units. side: target speed across the screen (plus = to the right),
     * up: target vertical speed, yawRate: crosshair turn speed (plus = right); NaN when unknown.
     */
    public record Sample(AttackEvent a, boolean onTarget, double x, double y, double dist, double side, double up,
                         double yawRate, Side miss) {
        /** Horizontal crosshair offset from the target center in degrees, plus = crosshair right of the target. */
        public double crossDeg() {
            return -a.aimYawDeg();
        }

        public boolean strafing() {
            return Double.isFinite(side) && Math.abs(side) >= STRAFE_BPS;
        }

        public boolean flick() {
            return Double.isFinite(yawRate) && Math.abs(yawRate) >= FLICK_DPS;
        }

        /** Angular speed of the target across the screen, degrees/s, plus = to the right. */
        public double targetDps() {
            return Double.isFinite(side) ? Math.toDegrees(side / Math.max(0.5, dist)) : Double.NaN;
        }
    }

    public record Split(int n, int on) {
        public static final Split EMPTY = new Split(0, 0);

        public double pct() {
            return n == 0 ? Double.NaN : 100.0 * on / n;
        }

        Split add(boolean hit) {
            return new Split(n + 1, on + (hit ? 1 : 0));
        }
    }

    public record Tip(String id, Tone tone, double importance, String ru, String en, String de) {
        public String text() {
            return Lang.t(ru, en, de);
        }
    }

    /**
     * meanX/meanY: average crosshair point of the swings that were on target. biasDeg: average horizontal offset
     * over all swings (plus = right of the target), spreadDeg: its sigma. leadDeg: against strafing targets, how
     * far the crosshair was ahead (plus) or behind (minus) in the direction the target moved. trackRatio: median
     * of your crosshair speed over the target's angular speed (1 = kept up, below 1 = too slow).
     */
    public record Result(List<Sample> samples, int onTarget, int aimMisses, int outOfRange,
                         Map<Side, Integer> missSides, double meanX, double meanY, double biasDeg, double spreadDeg,
                         double edgePct, double inBestPct, Split still, Split strafing, double leadDeg, int behind,
                         int ahead, double trackRatio, Split calm, Split flicking, Split ground, Split air,
                         Split targetLevel, Split targetJumping, int belowJumping, List<Split> byDistance,
                         boolean hasMotion, List<Tip> tips) {
        public int total() {
            return samples.size();
        }

        public double accuracyPct() {
            return samples.isEmpty() ? Double.NaN : 100.0 * onTarget / samples.size();
        }

        public int sideMisses(Side s) {
            return missSides.getOrDefault(s, 0);
        }

    }

    public static Result empty() {
        return analyze(List.of(), AttackEvent.DEFAULT_RANGE);
    }

    public static Result analyze(List<Event> ev, double range) {
        Motion motion = Motion.index(ev);
        List<Sample> ss = new ArrayList<>();
        int outOfRange = 0;
        for (Event e : ev) {
            if (!(e instanceof AttackEvent a) || a.reach() < Stats.MIN_AIM_REACH || !Double.isFinite(a.aimYawDeg())
                    || !Double.isFinite(a.aimPitchDeg())) continue;
            boolean oor = a.outOfRangeMiss(a.rangeOr(range));
            if (oor) outOfRange++;
            ss.add(sample(a, a.outcome() != Outcome.MISS || oor, motion));
        }
        return summarize(ss, outOfRange);
    }

    static Sample sample(AttackEvent a, boolean on, Motion motion) {
        double dist = a.reach() + HALF_W;
        double cx = Math.tan(Math.toRadians(-a.aimYawDeg())) * dist / HALF_W;
        double cy = 0.5 + Math.tan(Math.toRadians(a.aimPitchDeg())) * dist / HEIGHT;
        double x = cx, y = cy;
        if (a.outcome() != Outcome.MISS && Double.isFinite(a.hitU()) && Double.isFinite(a.hitV())) {
            x = a.hitU();
            y = a.hitV();
        }
        double side = a.sideBps(), up = a.targetUpBps(), yr = a.yawRateDps();
        if (motion != null && (!Double.isFinite(side) || !Double.isFinite(up) || !Double.isFinite(yr))) {
            double[] m = motion.at(a);
            if (m != null) {
                if (!Double.isFinite(side)) side = m[0];
                if (!Double.isFinite(up)) up = m[1];
                if (!Double.isFinite(yr)) yr = m[2];
            }
        }
        return new Sample(a, on, x, y, dist, side, up, yr, on ? null : missSide(cx, cy));
    }

    /** Which way the crosshair passed the body: the side it overshot by the most blocks. */
    static Side missSide(double x, double y) {
        double ex = (Math.abs(x) - 1) * HALF_W;
        double ey = (y > 1 ? y - 1 : -y) * HEIGHT;
        if (ex <= 0 && ey <= 0) {
            // Just on the edge: a lag or rounding miss, pick the closer edge.
            ex = Math.abs(x) * HALF_W;
            ey = Math.abs(y - 0.5) * HEIGHT * 0.5;
        }
        if (ex >= ey) return x >= 0 ? Side.RIGHT : Side.LEFT;
        return y >= 0.5 ? Side.ABOVE : Side.BELOW;
    }

    static Result summarize(List<Sample> ss, int outOfRange) {
        Map<Side, Integer> sides = new EnumMap<>(Side.class);
        int on = 0, misses = 0, edge = 0, best = 0, behind = 0, ahead = 0, belowJump = 0;
        double sx = 0, sy = 0;
        List<Double> cross = new ArrayList<>(), lead = new ArrayList<>(), track = new ArrayList<>();
        Split still = Split.EMPTY, strafe = Split.EMPTY, calm = Split.EMPTY, flick = Split.EMPTY, ground = Split.EMPTY,
                air = Split.EMPTY, level = Split.EMPTY, jumping = Split.EMPTY;
        Split[] dist = new Split[DIST_EDGES.length];
        java.util.Arrays.fill(dist, Split.EMPTY);
        boolean motion = false;
        for (Sample s : ss) {
            cross.add(s.crossDeg());
            if (s.onTarget()) {
                on++;
                sx += s.x();
                sy += s.y();
                if (Math.abs(s.x()) >= EDGE_X) edge++;
                if (s.y() >= BEST_LO && s.y() <= BEST_HI) best++;
            } else {
                misses++;
                sides.merge(s.miss(), 1, Integer::sum);
            }
            if (Double.isFinite(s.side())) {
                motion = true;
                if (s.strafing()) {
                    strafe = strafe.add(s.onTarget());
                    double dir = Math.signum(s.side()), l = s.crossDeg() * dir;
                    lead.add(l);
                    if (!s.onTarget() && (s.miss() == Side.LEFT || s.miss() == Side.RIGHT)) {
                        if (l < 0) behind++;
                        else ahead++;
                    }
                    double need = Math.abs(s.targetDps());
                    if (Double.isFinite(s.yawRate()) && need >= 20) track.add(s.yawRate() * dir / need);
                } else still = still.add(s.onTarget());
            }
            if (Double.isFinite(s.yawRate())) {
                if (s.flick()) flick = flick.add(s.onTarget());
                else calm = calm.add(s.onTarget());
            }
            if (s.a().flags() != null) {
                if (s.a().flags().onGround()) ground = ground.add(s.onTarget());
                else air = air.add(s.onTarget());
            }
            if (Double.isFinite(s.up())) {
                if (Math.abs(s.up()) >= JUMP_BPS) {
                    jumping = jumping.add(s.onTarget());
                    if (s.miss() == Side.BELOW) belowJump++;
                } else level = level.add(s.onTarget());
            }
            double d = s.a().reach();
            int b = 0;
            for (int i = 1; i < DIST_EDGES.length; i++) if (d >= DIST_EDGES[i]) b = i;
            dist[b] = dist[b].add(s.onTarget());
        }
        double[] c = cross.stream().mapToDouble(Double::doubleValue).toArray();
        double[] l = lead.stream().mapToDouble(Double::doubleValue).toArray();
        double[] tr = track.stream().mapToDouble(Double::doubleValue).toArray();
        Result r = new Result(List.copyOf(ss), on, misses, outOfRange, sides, on == 0 ? Double.NaN : sx / on,
                on == 0 ? Double.NaN : sy / on, c.length == 0 ? Double.NaN : Stats.mean(c),
                c.length == 0 ? Double.NaN : Stats.sigma(c), on == 0 ? Double.NaN : 100.0 * edge / on,
                on == 0 ? Double.NaN : 100.0 * best / on, still, strafe, l.length == 0 ? Double.NaN : Stats.mean(l),
                behind, ahead, tr.length < 3 ? Double.NaN : Stats.median(tr), calm, flick, ground, air, level, jumping,
                belowJump, List.of(dist), motion, List.of());
        return withTips(r);
    }

    private static Result withTips(Result r) {
        List<Tip> tips = tips(r);
        return new Result(r.samples(), r.onTarget(), r.aimMisses(), r.outOfRange(), r.missSides(), r.meanX(),
                r.meanY(), r.biasDeg(), r.spreadDeg(), r.edgePct(), r.inBestPct(), r.still(), r.strafing(),
                r.leadDeg(), r.behind(), r.ahead(), r.trackRatio(), r.calm(), r.flicking(), r.ground(), r.air(),
                r.targetLevel(), r.targetJumping(), r.belowJumping(), r.byDistance(), r.hasMotion(), tips);
    }

    /** Minimum swings before any tip; fewer is noise. */
    public static final int MIN_SWINGS = 10;

    static List<Tip> tips(Result r) {
        List<Tip> out = new ArrayList<>();
        if (r.total() < MIN_SWINGS) return out;
        int miss = r.aimMisses();
        double acc = r.accuracyPct();

        // Which way the misses go.
        int left = r.sideMisses(Side.LEFT), right = r.sideMisses(Side.RIGHT), above = r.sideMisses(Side.ABOVE),
                below = r.sideMisses(Side.BELOW);
        if (miss >= 4) {
            int horiz = left + right;
            if (horiz >= 4 && Math.max(left, right) >= 0.7 * horiz) {
                boolean l = left > right;
                int n = Math.max(left, right);
                out.add(new Tip("aim-miss-side", Tone.BAD, 1.0 + n / (double) Math.max(1, miss),
                        n + " из " + miss + " промахов прицел был " + (l ? "левее" : "правее") + " противника: цельтесь чуть "
                                + (l ? "правее" : "левее"),
                        n + " of " + miss + " misses had the crosshair " + (l ? "left" : "right") + " of the opponent: aim a bit more to the "
                                + (l ? "right" : "left"), "Bei " + n + " von " + miss + " Fehlschlägen lag dein Fadenkreuz " + (l ? "links" : "rechts") + " neben dem Gegner: ziel etwas mehr nach "
                                + (l ? "rechts" : "links")));
            }
            if (above >= 3 && above >= 0.4 * miss) {
                out.add(new Tip("aim-miss-high", Tone.BAD, 0.9 + above / (double) miss,
                        above + " из " + miss + " промахов ушли над головой: цельтесь ниже, в грудь",
                        above + " of " + miss + " misses went over the head: aim lower, at the chest", above + " von " + miss + " Fehlschlägen gingen über den Kopf: ziel tiefer, auf die Brust"));
            }
            if (below >= 3 && below >= 0.4 * miss) {
                out.add(new Tip("aim-miss-low", Tone.BAD, 0.9 + below / (double) miss,
                        below + " из " + miss + " промахов ушли ниже ног: цельтесь выше, в грудь",
                        below + " of " + miss + " misses went below the feet: aim higher, at the chest", below + " von " + miss + " Fehlschlägen gingen unter den Füßen durch: ziel höher, auf die Brust"));
            }
        }

        // Tracking strafing targets.
        Split st = r.strafing(), sl = r.still();
        if (st.n() >= 8 && Double.isFinite(r.leadDeg())) {
            double gap = sl.n() >= 5 ? sl.pct() - st.pct() : 0;
            boolean worse = gap >= 10 || st.pct() < 80;
            if (r.leadDeg() <= -0.8 && worse) {
                String rr = Double.isFinite(r.trackRatio()) ? " (мышь успевает на " + Math.round(r.trackRatio() * 100) + "%)" : "";
                String re = Double.isFinite(r.trackRatio()) ? " (your mouse keeps up " + Math.round(r.trackRatio() * 100) + "%)" : "";
                String rd = Double.isFinite(r.trackRatio()) ? " (deine Maus kommt zu " + Math.round(r.trackRatio() * 100) + "% mit)" : "";
                out.add(new Tip("aim-lag", Tone.BAD, 1.1 + Math.abs(r.leadDeg()) / 3 + Math.max(0, gap) / 50,
                        "Против стрейфа прицел отстаёт на " + Fmt1.f(-r.leadDeg()) + "°" + rr
                                + ": ведите прицел вместе с противником, держите его чуть впереди",
                        "Against strafes your crosshair trails by " + Fmt1.f(-r.leadDeg()) + "°" + re
                                + ": move with the opponent and keep the crosshair slightly ahead", "Gegen seitlich laufende Gegner hängt dein Fadenkreuz " + Fmt1.f(-r.leadDeg()) + "° hinterher" + rd
                                + ": geh mit dem Gegner mit und halt das Fadenkreuz leicht vor ihm"));
            } else if (r.leadDeg() >= 1.2 && worse) {
                out.add(new Tip("aim-overshoot", Tone.BAD, 1.0 + r.leadDeg() / 3,
                        "Против стрейфа прицел убегает вперёд на " + Fmt1.f(r.leadDeg()) + "°: ведите мышь спокойнее, не перекидывайте",
                        "Against strafes your crosshair runs " + Fmt1.f(r.leadDeg()) + "° ahead: move the mouse more calmly, do not overshoot", "Gegen seitlich laufende Gegner läuft dein Fadenkreuz " + Fmt1.f(r.leadDeg()) + "° voraus: beweg die Maus ruhiger, schieß nicht übers Ziel hinaus"));
            } else if (gap >= 15) {
                out.add(new Tip("aim-strafe", Tone.TIP, 0.8 + gap / 50,
                        "По стрейфящему противнику попадаете " + Math.round(st.pct()) + "%, по стоящему " + Math.round(sl.pct())
                                + "%: тренируйте трекинг, следите за его телом, а не за прицелом",
                        "You hit " + Math.round(st.pct()) + "% against strafing opponents and " + Math.round(sl.pct())
                                + "% against still ones: practice tracking, watch their body, not the crosshair", "Du triffst " + Math.round(st.pct()) + "% gegen seitlich laufende Gegner und " + Math.round(sl.pct())
                                + "% gegen stehende: übe Tracking, schau auf den Körper, nicht aufs Fadenkreuz"));
            }
        }

        // Clicking while flicking.
        Split fl = r.flicking(), ca = r.calm();
        if (fl.n() >= 5 && ca.n() >= 5 && ca.pct() - fl.pct() >= 15) {
            out.add(new Tip("aim-flick", Tone.BAD, 0.9 + (ca.pct() - fl.pct()) / 50,
                    "Удары на резком повороте мыши попадают " + Math.round(fl.pct()) + "% против " + Math.round(ca.pct())
                            + "%: сначала наведите прицел, потом бейте",
                    "Swings during a fast mouse turn land " + Math.round(fl.pct()) + "% vs " + Math.round(ca.pct())
                            + "%: get the crosshair on them first, then click", "Schläge während einer schnellen Mausbewegung treffen " + Math.round(fl.pct()) + "%, ruhige " + Math.round(ca.pct())
                            + "%: erst Fadenkreuz auf den Gegner, dann klicken"));
        }

        // Jumping opponents.
        Split jp = r.targetJumping(), lv = r.targetLevel();
        if (jp.n() >= 6 && lv.n() >= 5 && lv.pct() - jp.pct() >= 15) {
            out.add(new Tip("aim-jump-target", Tone.TIP, 0.8 + (lv.pct() - jp.pct()) / 50,
                    "Когда противник прыгает, попадаете " + Math.round(jp.pct()) + "% (иначе " + Math.round(lv.pct())
                            + "%): ведите прицел вверх-вниз вместе с ним",
                    "When the opponent jumps you hit " + Math.round(jp.pct()) + "% (otherwise " + Math.round(lv.pct())
                            + "%): follow them up and down with the crosshair", "Wenn der Gegner springt, triffst du " + Math.round(jp.pct()) + "% (sonst " + Math.round(lv.pct())
                            + "%): geh mit dem Fadenkreuz mit hoch und runter"));
        }

        // Own jumps / crits.
        Split ai = r.air(), gr = r.ground();
        if (ai.n() >= 6 && gr.n() >= 5 && gr.pct() - ai.pct() >= 15) {
            out.add(new Tip("aim-air", Tone.TIP, 0.8 + (gr.pct() - ai.pct()) / 50,
                    "В прыжке попадаете " + Math.round(ai.pct()) + "%, на земле " + Math.round(gr.pct())
                            + "%: в прыжке держите прицел на теле, не дёргайте мышь",
                    "In the air you hit " + Math.round(ai.pct()) + "%, on the ground " + Math.round(gr.pct())
                            + "%: keep the crosshair on the body while jumping, do not jerk the mouse", "In der Luft triffst du " + Math.round(ai.pct()) + "%, am Boden " + Math.round(gr.pct())
                            + "%: halt das Fadenkreuz beim Springen auf dem Körper, reiß die Maus nicht herum"));
        }

        // Long range: the body is a thin target.
        Split far = r.byDistance().get(DIST_EDGES.length - 1), mid = r.byDistance().get(1);
        if (far.n() >= 6 && mid.n() >= 5 && mid.pct() - far.pct() >= 20) {
            out.add(new Tip("aim-far", Tone.TIP, 0.7 + (mid.pct() - far.pct()) / 60,
                    "С 3+ блоков попадаете " + Math.round(far.pct()) + "% (вблизи " + Math.round(mid.pct())
                            + "%): там противник узкий, подойдите ближе или цельтесь точнее в центр",
                    "From 3+ blocks you hit " + Math.round(far.pct()) + "% (closer " + Math.round(mid.pct())
                            + "%): they are thin there, step closer or aim more at the center", "Ab 3 Blöcken triffst du " + Math.round(far.pct()) + "% (näher " + Math.round(mid.pct())
                            + "%): dort ist er schmal, geh näher ran oder ziel mehr auf die Mitte"));
        }

        // Steady offset even on hits.
        if (Double.isFinite(r.biasDeg()) && Math.abs(r.biasDeg()) >= 1.0 && Math.abs(r.biasDeg()) >= 0.35 * r.spreadDeg()
                && out.stream().noneMatch(t -> t.id().equals("aim-miss-side"))) {
            boolean l = r.biasDeg() < 0;
            out.add(new Tip("aim-bias", Tone.TIP, 0.7 + Math.abs(r.biasDeg()) / 4,
                    "Прицел в среднем на " + Fmt1.f(Math.abs(r.biasDeg())) + "° " + (l ? "левее" : "правее")
                            + " центра противника: держите его по центру тела",
                    "Your crosshair sits " + Fmt1.f(Math.abs(r.biasDeg())) + "° " + (l ? "left" : "right")
                            + " of the opponent's center on average: keep it on the middle of the body", "Dein Fadenkreuz sitzt im Schnitt " + Fmt1.f(Math.abs(r.biasDeg())) + "° " + (l ? "links" : "rechts")
                            + " von der Gegnermitte: halt es in der Körpermitte"));
        }

        // Height on the body.
        if (r.onTarget() >= 10 && Double.isFinite(r.meanY())) {
            if (r.meanY() > 0.85) {
                out.add(new Tip("aim-height-high", Tone.TIP, 0.6 + (r.meanY() - 0.85) * 4,
                        "Бьёте в основном в голову (" + Math.round(r.meanY() * 100) + "% роста): цельтесь в грудь или шею, там больше запаса",
                        "You mostly hit the head (" + Math.round(r.meanY() * 100) + "% of height): aim at the chest or neck, it leaves more room", "Du triffst meist den Kopf (" + Math.round(r.meanY() * 100) + "% der Höhe): ziel auf Brust oder Hals, da hast du mehr Spielraum"));
            } else if (r.meanY() < 0.4) {
                out.add(new Tip("aim-height-low", Tone.TIP, 0.6 + (0.4 - r.meanY()) * 4,
                        "Бьёте в основном по ногам (" + Math.round(r.meanY() * 100) + "% роста): поднимите прицел до груди, прыжки противника не уведут его",
                        "You mostly hit the legs (" + Math.round(r.meanY() * 100) + "% of height): raise the crosshair to the chest so their jumps do not lose it", "Du triffst meist die Beine (" + Math.round(r.meanY() * 100) + "% der Höhe): heb das Fadenkreuz auf Brusthöhe, damit du ihn bei Sprüngen nicht verlierst"));
            }
        }

        // Barely hitting.
        if (r.onTarget() >= 15 && Double.isFinite(r.edgePct()) && r.edgePct() >= 35) {
            out.add(new Tip("aim-edge", Tone.TIP, 0.6 + r.edgePct() / 100,
                    Math.round(r.edgePct()) + "% попаданий по самому краю хитбокса: это почти промахи, держите прицел ближе к центру",
                    Math.round(r.edgePct()) + "% of hits landed on the very edge of the hitbox: those are near misses, keep closer to the center", Math.round(r.edgePct()) + "% der Treffer ganz am Rand der Hitbox: das sind Beinahe-Fehlschläge, bleib näher an der Mitte"));
        }

        // Shaky aim.
        if (Double.isFinite(r.spreadDeg()) && r.spreadDeg() >= 6 && r.total() >= 15) {
            out.add(new Tip("aim-shaky", Tone.TIP, 0.5 + r.spreadDeg() / 20,
                    "Прицел гуляет (разброс " + Fmt1.f(r.spreadDeg()) + "°): попробуйте чувствительность чуть ниже и держите прицел на противнике между ударами",
                    "Your aim wanders (spread " + Fmt1.f(r.spreadDeg()) + "°): try a slightly lower sensitivity and keep the crosshair on them between hits", "Dein Zielen wackelt (Streuung " + Fmt1.f(r.spreadDeg()) + "°): probier eine etwas niedrigere Empfindlichkeit und halt das Fadenkreuz zwischen den Schlägen auf ihm"));
        }

        if (out.isEmpty() && acc >= 88 && r.total() >= 20) {
            out.add(new Tip("aim-good", Tone.GOOD, 0.5,
                    "Прицел в порядке: " + Math.round(acc) + "% взмахов на противнике",
                    "Aim looks good: " + Math.round(acc) + "% of swings were on the opponent", "Zielen sieht gut aus: " + Math.round(acc) + "% der Schläge waren auf dem Gegner"));
        }
        out.sort(Comparator.comparing((Tip t) -> t.tone() == Tone.GOOD).thenComparing(Comparator.comparingDouble(Tip::importance).reversed())
                .thenComparing(Tip::id));
        return List.copyOf(out);
    }

    /** Short number formatting for tips (core has no UI Fmt): one decimal, like Fmt.num. */
    static final class Fmt1 {
        static String f(double v) {
            String s = String.format(java.util.Locale.ROOT, "%.1f", v);
            return Lang.german() ? s.replace('.', ',') : s;
        }
    }

    /**
     * Target and crosshair motion from position events, for recordings made before the attack stored it.
     * Uses the event at the attack tick and the one before it.
     */
    static final class Motion {
        private final Map<Integer, PositionEvent> byTick;

        private Motion(Map<Integer, PositionEvent> byTick) {
            this.byTick = byTick;
        }

        static Motion index(List<Event> ev) {
            Map<Integer, PositionEvent> m = new HashMap<>();
            for (Event e : ev) if (e instanceof PositionEvent p) m.put(p.tick(), p);
            return m.isEmpty() ? null : new Motion(m);
        }

        /** [side b/s, target up b/s, yaw rate deg/s], or null. */
        double[] at(AttackEvent a) {
            PositionEvent cur = null, prev = null;
            for (int d = 0; d <= 1 && cur == null; d++) cur = byTick.get(a.tick() - d);
            if (cur == null) return null;
            int gap = 0;
            for (int d = 1; d <= 3 && prev == null; d++) {
                prev = byTick.get(cur.tick() - d);
                gap = d;
            }
            if (prev == null || a.target() == null) return null;
            PositionEvent.Other o1 = prev.find(a.target()), o2 = cur.find(a.target());
            if (o1 == null || o2 == null) return null;
            double k = 20.0 / gap;
            double yaw = Math.toRadians(cur.yaw());
            double lx = -Math.sin(yaw), lz = Math.cos(yaw);
            double rx = -lz, rz = lx;
            double rdx = (o2.x() - o1.x()) - (cur.x() - prev.x()), rdz = (o2.z() - o1.z()) - (cur.z() - prev.z());
            double side = (rdx * rx + rdz * rz) * k;
            double up = (o2.y() - o1.y()) * k;
            double yawRate = Geometry.wrap180(cur.yaw() - prev.yaw()) * k;
            return new double[]{side, up, yawRate};
        }
    }
}
