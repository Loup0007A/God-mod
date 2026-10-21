package com.godmod.data;

import java.util.ArrayList;
import java.util.List;

/** Lettre du courrier : un message, et éventuellement des objets (SNBT) et des pièces à récupérer. */
public class Letter {
    public long id;
    public String from;
    public String fromName;
    public String text = "";
    public List<String> items = new ArrayList<>();
    public long money;
    public long sent;
    public boolean read;
}
