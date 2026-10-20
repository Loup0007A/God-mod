package com.godmod.data;

/** Une offre du marché entre joueurs (/market). L'objet est stocké en SNBT (1 exemplaire) et {@link #count} donne la quantité. */
public class MarketListing {
    public long id;
    public String seller;
    public String sellerName;
    /** Identifiant de l'objet (ex. minecraft:diamond), pour les recherches et l'historique des prix. */
    public String item;
    /** Nom affiché au moment de la mise en vente. */
    public String name;
    public String snbt;
    public int count;
    public long unitPrice;
    public long created;

    /** Objet décodé (non sauvegardé). */
    public transient net.minecraft.world.item.ItemStack cached;

    public MarketListing() {
    }
}
