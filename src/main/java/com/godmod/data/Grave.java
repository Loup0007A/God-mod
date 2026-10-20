package com.godmod.data;

import java.util.ArrayList;
import java.util.List;

/** Tombe d'un joueur : les objets perdus à sa mort, gardés à l'endroit du décès. */
public class Grave {
    public long id;
    public String owner;
    public String ownerName;
    /** Joueur qui a donné le coup fatal (peut piller la tombe tout de suite), ou null. */
    public String killer;
    public String killerName;
    public String dimension;
    public double x, y, z;
    public long created;
    /** Mort dans le vide : la tombe n'est pas atteignable, récupération à distance gratuite. */
    public boolean voidDeath;
    /** Objets en SNBT (avec leur quantité). */
    public List<String> items = new ArrayList<>();
    public int count;
}
