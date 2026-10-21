package com.godmod.data;

import java.util.HashMap;
import java.util.Map;

/** Prime sur la tête d'un joueur : versée à celui qui le tue en combat. */
public class Bounty {
    public String target;
    public String targetName;
    public long total;
    /** Participants : uuid → montant versé (remboursé si la prime expire). */
    public Map<String, Long> contributors = new HashMap<>();
    public long created;
    public long updated;
}
