package com.godmod.data;

/** Quête acceptée par un joueur et son avancement. */
public class QuestProgress {
    public String id;
    public int progress;

    public QuestProgress() {
    }

    public QuestProgress(String id) {
        this.id = id;
    }
}
