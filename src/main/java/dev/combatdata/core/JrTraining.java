package dev.combatdata.core;

/**
 * Jump reset training: counts every hit you take and how many you answered with a jump in the window, the current
 * streak and the best one. Hits without any jump count as misses, so the rate is honest.
 */
public final class JrTraining {
    private int hits, success, streak, best;
    private LiveCombat.Jr last;
    private long lastOff, lastAt;
    /** A hit is waiting for its jump; if the next hit comes first, the jump never happened. */
    private boolean waiting;

    public void reset() {
        hits = success = streak = best = 0;
        waiting = false;
        last = null;
        lastOff = 0;
        lastAt = 0;
    }

    /** A hit on you; a jump verdict may follow. */
    public void hit() {
        if (waiting) noJump();
        hits++;
        waiting = true;
    }

    public void verdict(LiveCombat.Jr r, long offMs, long at) {
        waiting = false;
        last = r;
        lastOff = offMs;
        lastAt = at;
        if (r == LiveCombat.Jr.SUCCESS) {
            success++;
            best = Math.max(best, ++streak);
        } else {
            streak = 0;
        }
    }

    /** A hit passed without any jump in time: breaks the streak. */
    public void noJump() {
        streak = 0;
    }

    public int hits() {
        return hits;
    }

    public int success() {
        return success;
    }

    public int streak() {
        return streak;
    }

    public int best() {
        return best;
    }

    public double ratePct() {
        return hits == 0 ? Double.NaN : 100.0 * success / hits;
    }

    public LiveCombat.Jr last() {
        return last;
    }

    public long lastOffMs() {
        return lastOff;
    }

    public long lastAt() {
        return lastAt;
    }

    public String lastText() {
        if (last == null) return Lang.t("Ждём удар по вам…", "Waiting for a hit on you…", "Warte auf einen Treffer…");
        return switch (last) {
            case SUCCESS -> Lang.t("✔ вовремя", "✔ in time", "✔ rechtzeitig");
            case EARLY -> Lang.t("рано на " + lastOff + " мс", "early by " + lastOff + " ms", "zu früh um " + lastOff + " ms");
            case LATE -> Lang.t("поздно на " + lastOff + " мс", "late by " + lastOff + " ms", "zu spät um " + lastOff + " ms");
        };
    }
}
