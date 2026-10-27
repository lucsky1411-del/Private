package dev.combatdata.client;

import dev.combatdata.CombatDataClient;
import dev.combatdata.core.*;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.*;

public final class ProgressData {
    public record Snapshot(String key, List<Progress.Summary> summaries, Map<String, Recordings.Entry> entries) {
    }

    private static volatile Snapshot snapshot;
    private static volatile boolean running, stale = true;
    private static volatile String status;
    private static volatile int version;
    private static String runningKey;

    private ProgressData() {
    }

    private static Path cacheFile() {
        return FabricLoader.getInstance().getGameDir().resolve("combatdata").resolve("progress-cache.json");
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static boolean running() {
        return running;
    }

    public static String status() {
        return status;
    }

    public static int version() {
        return version;
    }

    public static void invalidate() {
        stale = true;
    }

    public static synchronized void request(Settings s, boolean force) {
        StatFilter sf = s.statFilter();
        JumpResetEvaluator.Params p = s.jumpParams();
        int interp = s.interpMs;
        long gap = s.tradeGapMs;
        String key = Progress.cacheKey(sf, p, interp, gap);
        Snapshot cur = snapshot;
        if (running) {
            if (key.equals(runningKey)) return;
        } else if (!force && !stale && cur != null && cur.key().equals(key)) return;
        if (running) {
            stale = true;
            return;
        }
        running = true;
        stale = false;
        runningKey = key;
        Thread t = new Thread(() -> run(key, sf, p, interp, gap), "combatdata-progress");
        t.setDaemon(true);
        t.start();
    }

    /** Sessions as the report counts them (play days or recordings, as set). */
    public static List<Report.Session> sessions(Snapshot snap, Settings s) {
        return Report.sessions(snap.summaries(), s.reportByDay, java.time.ZoneId.systemDefault());
    }

    /**
     * Tells the player in chat when a new overall report is ready (every N sessions), a badge is earned or a training
     * goal is reached. The first run only remembers what is already there, so old achievements are not announced.
     */
    private static void announceMilestone(Snapshot snap) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        // Settings belong to the main thread.
        mc.execute(() -> {
            Settings s = CombatDataClient.settings();
            List<Report.Session> ss = sessions(snap, s);
            int n = ss.size(), m = Report.milestone(n, s.reportStep);
            boolean init = !s.announced.startsWith("init;");
            Set<String> done = new LinkedHashSet<>(Arrays.asList(s.announced.replaceFirst("^init;", "").split(",")));
            done.remove("");
            List<String> news = new ArrayList<>();
            if (m > s.lastReport) {
                boolean first = s.lastReport == 0 && n > s.reportStep;
                s.lastReport = m;
                if (!first) news.add(Lang.t("Новый общий отчёт: " + m + " сессий. Меню → Отчёт",
                        "New overall report: " + m + " sessions. Menu → Report",
                        "Neuer Gesamtbericht: " + m + " Sessions. Menü → Bericht"));
            }
            for (String id : Coach.earned(ss)) {
                if (done.add("b:" + id) && !init) {
                    Coach.Badge b = Coach.badge(id);
                    news.add(Lang.t("Новое достижение: ", "New badge: ", "Neues Abzeichen: ") + b.name() + " (" + b.hint() + ")");
                }
            }
            Report.Result r = Report.last(ss, s.reportStep);
            for (Coach.Goal g : Coach.parseGoals(s.goals)) {
                Report.Line l = r.line(g.skill());
                String key = "g:" + g.skill() + ":" + g.target();
                if (l != null && g.reached(l.value()) && done.add(key) && !init) {
                    news.add(Lang.t("Цель достигнута: ", "Goal reached: ", "Ziel erreicht: ") + l.name() + " " + Report.pctText(l.value())
                            + " (" + Lang.t("цель ", "goal ", "Ziel ") + g.target() + "%)");
                }
            }
            s.announced = "init;" + String.join(",", done);
            CombatDataClient.saveSettings();
            for (String msg : news) Recorder.chat(mc, msg);
        });
    }

    private static void run(String key, StatFilter sf, JumpResetEvaluator.Params p, int interp, long gap) {
        try {
            ProgressCache cache = ProgressCache.load(cacheFile());
            List<Recordings.Entry> list = Recordings.list();
            List<Progress.Summary> out = new ArrayList<>();
            Map<String, Recordings.Entry> byFile = new HashMap<>();
            Set<String> names = new HashSet<>();
            int i = 0;
            for (Recordings.Entry e : list) {
                i++;
                if (e.header() == null) continue;
                // A friend's recording is about them, not you: it stays out of your progress and reports.
                if (Recordings.foreign(e.header())) continue;
                String name = e.file().getFileName().toString();
                names.add(name);
                byFile.put(name, e);
                Progress.Summary s = cache.get(name, e.modified(), key);
                if (s == null) {
                    status = i + " / " + list.size();
                    try {
                        s = Progress.summarize(name, Cdrec.read(e.file()), p, interp, sf, gap);
                        cache.put(name, e.modified(), key, s);
                    } catch (Exception ex) {
                        CombatDataClient.LOG.warn("[CD] прогресс: не читается {}: {}", name, ex.toString());
                        continue;
                    }
                }
                out.add(s);
            }
            cache.retain(names);
            if (cache.dirty()) {
                try {
                    cache.save(cacheFile());
                } catch (Exception ex) {
                    CombatDataClient.LOG.warn("[CD] прогресс: не удалось сохранить кэш: {}", ex.toString());
                }
            }
            out.sort(Comparator.comparingLong(Progress.Summary::wallStartMs));
            snapshot = new Snapshot(key, List.copyOf(out), Map.copyOf(byFile));
            version++;
            announceMilestone(snapshot);
        } catch (Exception ex) {
            CombatDataClient.LOG.error("[CD] прогресс: ошибка расчёта", ex);
        } finally {
            status = null;
            synchronized (ProgressData.class) {
                running = false;
                runningKey = null;
            }
        }
    }
}
