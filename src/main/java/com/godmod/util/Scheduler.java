package com.godmod.util;

import java.util.ArrayList;
import java.util.List;

/** Exécute des actions après un certain nombre de ticks (sur le thread du serveur). */
public final class Scheduler {
    private record Task(long at, Runnable action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now = 0;

    private Scheduler() {
    }

    public static void later(int ticks, Runnable action) {
        TASKS.add(new Task(now + Math.max(1, ticks), action));
    }

    public static void tick() {
        now++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at() <= now) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.action().run();
            } catch (Exception e) {
                com.godmod.GodMod.LOGGER.error("[God Mod] Tâche planifiée en échec", e);
            }
        }
    }
}
