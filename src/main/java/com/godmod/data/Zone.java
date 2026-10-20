package com.godmod.data;

/** Zone rectangulaire tracée par un admin avec le bâton de zone (/zone). */
public class Zone {
    /** AFK : compte comme zone AFK · SPAWN : protégée comme le Sanctuaire · LIBRE : non protégée, même dans le Sanctuaire. */
    public enum Type { AFK, SPAWN, LIBRE, COMMANDE }

    public String name;
    public Type type = Type.SPAWN;
    public String dimension;
    public int minX, minY, minZ, maxX, maxY, maxZ;
    public String createdBy;
    /** Zone COMMANDE : commande lancée quand un joueur y entre ({joueur} = son pseudo). */
    public String command;
    /** Vrai : lancée par la console (tous les droits) ; faux : lancée par le joueur lui-même. */
    public boolean asConsole = true;

    public boolean contains(String dim, int x, int y, int z) {
        return dim.equals(dimension) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
