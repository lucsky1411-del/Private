package dev.combatdata.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Counts behind the coach report, stored per recording so many sessions can be added up: where misses went, aim
 * against strafing targets, flicks, air hits, how jump resets failed, why trades were lost. Plain sums only, so
 * adding sessions weighs every swing equally. Older cache entries without it read as zero.
 */
public final class Detail {
    public static final int AIM_N = 0, AIM_ON = 1, MISS_L = 2, MISS_R = 3, MISS_UP = 4, MISS_DOWN = 5, MISS_OOR = 6,
            STRAFE_N = 7, STRAFE_ON = 8, STILL_N = 9, STILL_ON = 10, LEAD_TENTHS = 11, LEAD_N = 12, FLICK_N = 13,
            FLICK_ON = 14, CALM_N = 15, CALM_ON = 16, AIR_N = 17, AIR_ON = 18, GROUND_N = 19, GROUND_ON = 20,
            JR_OK = 21, JR_EARLY = 22, JR_LATE = 23, JR_NONE = 24, TR_WON = 25, TR_LOST = 26, LOSS_MISS = 27,
            LOSS_WEAK = 28, LOSS_CAUGHT = 29, LOSS_JR = 30, TAKEN_RATED = 31, TAKEN_CAUGHT = 32, HIT_Y_SUM = 33,
            HIT_Y_N = 34, MAX_COMBO = 35, JR_STREAK = 36, GRID0 = 37;
    /** Crosshair map: GRID_W x GRID_H cells over the body (x -2.5..2.5 half-widths, y -0.3..1.3), hits then misses. */
    public static final int GRID_W = 7, GRID_H = 10, GRID_CELLS = GRID_W * GRID_H, COUNT = GRID0 + 2 * GRID_CELLS;
    /** These hold the best value instead of a sum when sessions are added. */
    private static final int[] MAX_FIELDS = {MAX_COMBO, JR_STREAK};

    public static final Detail ZERO = new Detail(new int[COUNT]);

    private final int[] v;

    private Detail(int[] v) {
        this.v = v;
    }

    public int get(int i) {
        return v[i];
    }

    public Detail plus(Detail o) {
        int[] r = v.clone();
        for (int i = 0; i < COUNT; i++) r[i] += o.v[i];
        for (int i : MAX_FIELDS) r[i] = Math.max(v[i], o.v[i]);
        return new Detail(r);
    }

    public static Detail of(Stats.Result s, AimAnalysis.Result a) {
        return of(s, a, null);
    }

    public static int gridCell(double x, double y) {
        int cx = (int) Math.floor((x + 2.5) / 5.0 * GRID_W), cy = (int) Math.floor((1.3 - y) / 1.6 * GRID_H);
        cx = Math.max(0, Math.min(GRID_W - 1, cx));
        cy = Math.max(0, Math.min(GRID_H - 1, cy));
        return cy * GRID_W + cx;
    }

    public int hitsAt(int cell) {
        return v[GRID0 + cell];
    }

    public int missesAt(int cell) {
        return v[GRID0 + GRID_CELLS + cell];
    }

    /** attempts: jump reset attempts in order, for the longest streak; null leaves it 0. */
    public static Detail of(Stats.Result s, AimAnalysis.Result a, List<JumpResetEvaluator.Attempt> attempts) {
        int[] v = new int[COUNT];
        v[MAX_COMBO] = s.combo().maxCombo();
        if (attempts != null) {
            int run = 0, best = 0;
            for (JumpResetEvaluator.Attempt at : attempts) {
                switch (at.result()) {
                    case SUCCESS -> best = Math.max(best, ++run);
                    case TOO_EARLY, TOO_LATE, NO_ATTEMPT -> run = 0;
                    default -> {
                    }
                }
            }
            v[JR_STREAK] = best;
        }
        if (a != null) {
            v[AIM_N] = a.total();
            v[AIM_ON] = a.onTarget();
            v[MISS_L] = a.sideMisses(AimAnalysis.Side.LEFT);
            v[MISS_R] = a.sideMisses(AimAnalysis.Side.RIGHT);
            v[MISS_UP] = a.sideMisses(AimAnalysis.Side.ABOVE);
            v[MISS_DOWN] = a.sideMisses(AimAnalysis.Side.BELOW);
            v[MISS_OOR] = a.outOfRange();
            v[STRAFE_N] = a.strafing().n();
            v[STRAFE_ON] = a.strafing().on();
            v[STILL_N] = a.still().n();
            v[STILL_ON] = a.still().on();
            if (Double.isFinite(a.leadDeg())) {
                v[LEAD_N] = a.strafing().n();
                v[LEAD_TENTHS] = (int) Math.round(a.leadDeg() * 10 * a.strafing().n());
            }
            v[FLICK_N] = a.flicking().n();
            v[FLICK_ON] = a.flicking().on();
            v[CALM_N] = a.calm().n();
            v[CALM_ON] = a.calm().on();
            v[AIR_N] = a.air().n();
            v[AIR_ON] = a.air().on();
            v[GROUND_N] = a.ground().n();
            v[GROUND_ON] = a.ground().on();
            for (AimAnalysis.Sample sm : a.samples()) {
                if (!Double.isFinite(sm.x()) || !Double.isFinite(sm.y())) continue;
                v[GRID0 + (sm.onTarget() ? 0 : GRID_CELLS) + gridCell(sm.x(), sm.y())]++;
            }
            if (a.onTarget() > 0 && Double.isFinite(a.meanY())) {
                v[HIT_Y_N] = a.onTarget();
                v[HIT_Y_SUM] = (int) Math.round(a.meanY() * 100 * a.onTarget());
            }
        }
        Stats.JumpStats j = s.jump();
        v[JR_OK] = j.success();
        v[JR_EARLY] = j.early();
        v[JR_LATE] = j.late();
        v[JR_NONE] = j.noAttempt();
        Trades.Summary t = s.trades();
        v[TR_WON] = t.wins();
        v[TR_LOST] = t.losses();
        v[LOSS_MISS] = t.lossCauses().getOrDefault(Trades.Cause.MISS, 0);
        v[LOSS_WEAK] = t.lossCauses().getOrDefault(Trades.Cause.WEAK, 0);
        v[LOSS_CAUGHT] = t.lossCauses().getOrDefault(Trades.Cause.CAUGHT, 0);
        v[LOSS_JR] = t.lossCauses().getOrDefault(Trades.Cause.FAILED_JR, 0);
        v[TAKEN_RATED] = s.taken().rated();
        v[TAKEN_CAUGHT] = s.taken().caught();
        return new Detail(v);
    }

    /** Builds a Detail from raw counts in index order (tests and JSON). */
    public static Detail ofCounts(int... counts) {
        return new Detail(Arrays.copyOf(counts, COUNT));
    }

    public List<Integer> toJson() {
        List<Integer> out = new ArrayList<>(COUNT);
        for (int x : v) out.add(x);
        return out;
    }

    public static Detail fromJson(Object o) {
        if (!(o instanceof List<?> l)) return ZERO;
        int[] v = new int[COUNT];
        for (int i = 0; i < Math.min(COUNT, l.size()); i++) v[i] = l.get(i) instanceof Number n ? n.intValue() : 0;
        return new Detail(v);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Detail d && Arrays.equals(v, d.v);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(v);
    }

    /** Share in percent, NaN below min samples. */
    public static double pct(int on, int n, int min) {
        return n < Math.max(1, min) ? Double.NaN : 100.0 * on / n;
    }
}
