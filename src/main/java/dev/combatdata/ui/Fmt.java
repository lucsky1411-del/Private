package dev.combatdata.ui;

import dev.combatdata.client.Recordings;
import static dev.combatdata.core.Lang.t;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Fmt {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE_EN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Fmt() {
    }

    static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    static String clockMs(long ms) {
        long v = Math.max(0, ms), s = v / 1000;
        return String.format("%02d:%02d.%03d", s / 60, s % 60, v % 1000);
    }

    static String num(double v, int digits) {
        if (!Double.isFinite(v)) return "—";
        return local(String.format(Locale.ROOT, "%." + digits + "f", v));
    }

    static String signed(double v, int digits) {
        if (!Double.isFinite(v)) return "—";
        return local(String.format(Locale.ROOT, "%+." + digits + "f", v));
    }

    /** German writes decimals with a comma. */
    static String local(String s) {
        return dev.combatdata.core.Lang.german() ? s.replace('.', ',') : s;
    }

    static String pct(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.0f%%", v) : "—";
    }

    static String date(long epochMs) {
        return (dev.combatdata.core.Lang.current() == dev.combatdata.core.Lang.EN ? DATE_EN : DATE).format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }

    static String title(Recordings.Entry e) {
        if (e.header() == null) return e.stem() + t(" (не читается)", " (unreadable)", " (nicht lesbar)");
        if (Recordings.foreign(e.header())) return "[" + e.header().player() + "] " + titleOwn(e);
        return titleOwn(e);
    }

    private static String titleOwn(Recordings.Entry e) {
        if (e.defaultName()) {
            String server = e.header().servers().isEmpty() ? t("сервер неизвестен", "unknown server", "unbekannter Server") : String.join(", ", e.header().servers());
            return server;
        }
        return e.stem();
    }

    static String meta(Recordings.Entry e) {
        if (e.header() == null) return t("Файл повреждён или создан другой версией", "File is damaged or made by another version", "Datei ist beschädigt oder stammt von einer anderen Version");
        var h = e.header();
        StringBuilder sb = new StringBuilder(clock(h.durationMs()));
        sb.append(t(" · ударов ", " · hits ", " · Treffer ")).append(h.hitsConfirmed()).append(t(" из ", " of ", " von ")).append(h.attacks());
        if (!e.defaultName() && !h.servers().isEmpty()) sb.append(" · ").append(String.join(", ", h.servers()));
        if (!h.opponents().isEmpty()) sb.append(" · ").append(String.join(", ", h.opponents()));
        return sb.toString();
    }

    static String size(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024.0)) + t(" КБ", " KB");
        return String.format(Locale.ROOT, t("%.1f МБ", "%.1f MB"), bytes / 1048576.0);
    }

    static String when(Recordings.Entry e) {
        return date(e.header() != null ? e.header().wallStartMs() : e.modified());
    }
}
