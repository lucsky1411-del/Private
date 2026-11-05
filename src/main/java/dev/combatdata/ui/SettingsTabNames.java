package dev.combatdata.ui;

/** Setting value names shared with screens outside this package (Mod Menu quick settings). */
public final class SettingsTabNames {
    private SettingsTabNames() {
    }

    public static String nav(int v) {
        String[] n = SettingsTab.navNames();
        return v >= 0 && v < n.length ? n[v] : n[0];
    }

    public static String bufferSave(int v) {
        return SettingsTab.bufferSaveName(v);
    }
}
