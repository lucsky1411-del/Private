package dev.combatdata.core;

/**
 * Strings live next to the code as t("ru", "en", "de") pairs so translations are not lost. AUTO follows the game
 * language. A string without a German version falls back to English.
 */
public final class Lang {
    public static final int AUTO = 0, RU = 1, EN = 2, DE = 3;
    private static volatile int current = EN;

    private Lang() {
    }

    /** True for every non-Russian language: code that only knows Russian and English shows English then. */
    public static boolean english() {
        return current != RU;
    }

    public static boolean german() {
        return current == DE;
    }

    public static int current() {
        return current;
    }

    public static String t(String ru, String en) {
        return current == RU ? ru : en;
    }

    public static String t(String ru, String en, String de) {
        return switch (current) {
            case RU -> ru;
            case DE -> de;
            default -> en;
        };
    }

    public static int resolve(int setting, String mcCode) {
        if (setting == RU || setting == EN || setting == DE) return setting;
        String c = mcCode == null ? "" : mcCode.toLowerCase(java.util.Locale.ROOT);
        if (c.startsWith("ru")) return RU;
        if (c.startsWith("de")) return DE;
        return EN;
    }

    public static boolean resolveEnglish(int setting, String mcCode) {
        return resolve(setting, mcCode) != RU;
    }

    public static void apply(int setting, String mcCode) {
        current = resolve(setting, mcCode);
    }

    public static void set(int lang) {
        current = lang == RU || lang == DE ? lang : EN;
    }

    public static void setEnglish(boolean en) {
        current = en ? EN : RU;
    }

    public static String plural(long n, String one, String few, String many) {
        long a = Math.abs(n) % 100, b = a % 10;
        if (a >= 11 && a <= 14) return many;
        if (b == 1) return one;
        if (b >= 2 && b <= 4) return few;
        return many;
    }
}
