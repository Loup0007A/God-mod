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
    /** Warps publics des joueurs (clé = nom en minuscules). */
    public Map<String, Warp> warps = new HashMap<>();
    /** Générateurs posés par les joueurs : "dimension|x|y|z" -> identifiant du mob. */
    public Map<String, String> spawners = new HashMap<>();
    /** Points interactifs (caisses, tableau des quêtes…) : "dimension|x|y|z" -> type. */
    public Map<String, String> points = new HashMap<>();
    /** Zones tracées au bâton de zone (clé = nom en minuscules). */
    public Map<String, Zone> zones = new HashMap<>();
    /** Marché entre joueurs. */
    public List<MarketListing> market = new ArrayList<>();
    public long nextMarketId = 1;
    /** Historique par objet puis par jour : {quantité mise en vente, quantité vendue, pièces échangées}. */
    public Map<String, Map<String, long[]>> marketHistory = new HashMap<>();
}
