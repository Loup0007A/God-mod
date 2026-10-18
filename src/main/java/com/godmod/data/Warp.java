package com.godmod.data;

/** Point de téléportation public créé par un joueur avec /setwarp. */
public class Warp {
    public String name;
    public String owner;
    public String ownerName;
    public String dimension;
    public double x, y, z;
    public float yaw, pitch;
    public long created = System.currentTimeMillis();
    public int visits = 0;
}
