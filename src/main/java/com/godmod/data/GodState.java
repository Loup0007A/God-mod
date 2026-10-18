package com.godmod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** État global persistant du mod (sérialisé en JSON). */
public class GodState {
    public int version = 1;
    public Settings settings = new Settings();
    public Map<String, PlayerProfile> players = new HashMap<>();
    public int nextPlotIndex = 0;
    /** Journal pour les modérateurs (anticheat / suivi). */
    public List<String> log = new ArrayList<>();
}
