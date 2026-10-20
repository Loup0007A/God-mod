package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.progress.EventSystem;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Anti-lag :
 * <ul>
 *   <li>au plus 15 créatures d'un même type par chunk (villageois, golems, animaux apprivoisés ou nommés exceptés) ;</li>
 *   <li>nettoyage des objets au sol toutes les 90 secondes, annoncé à l'avance (❄ dans le tableau latéral).</li>
 * </ul>
 */
public final class LagControl {
    private static long nextClear = 0;

    private LagControl() {
    }

    // ------------------------------------------------------------------ Limite de créatures

    private static boolean exempt(Entity e) {
        return e instanceof AbstractVillager || e instanceof IronGolem || e instanceof SnowGolem
                || (e instanceof TamableAnimal t && t.isTame()) || e.hasCustomName() || EventSystem.isEventMob(e);
    }

    /** Vrai s'il faut empêcher cette créature d'apparaître (trop d'individus du même type dans le chunk). */
    public static boolean tooMany(Level level, Entity e) {
        int limit = GodData.settings().entityChunkLimit;
        if (limit <= 0 || !(e instanceof Mob) || exempt(e)) return false;
        EntityType<?> type = e.getType();
        int cx = e.blockPosition().getX() >> 4, cz = e.blockPosition().getZ() >> 4;
        AABB box = new AABB(cx * 16, level.getMinY(), cz * 16, cx * 16 + 16, level.getMaxY(), cz * 16 + 16);
        List<Mob> same = level.getEntitiesOfClass(Mob.class, box, m -> m.getType() == type && m != e && !exempt(m));
        return same.size() >= limit;
    }

    // ------------------------------------------------------------------ Nettoyage des objets au sol

    public static long secondsLeft() {
        int every = GodData.settings().clearlagSeconds;
        if (every <= 0 || nextClear == 0) return -1;
        return Math.max(0, (nextClear - System.currentTimeMillis() + 999) / 1000);
    }

    /** Appelé chaque seconde. */
    public static void tickSecond(MinecraftServer server) {
        int every = GodData.settings().clearlagSeconds;
        if (every <= 0) {
            nextClear = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (nextClear == 0 || nextClear - now > every * 1000L) nextClear = now + every * 1000L;
        long left = secondsLeft();
        if (left == 30 || left == 10) {
            Msg.broadcast(server, "❄ Nettoyage des objets au sol dans " + left + " secondes !", ChatFormatting.AQUA);
        } else if (left > 0 && left <= 5) {
            Component c = Msg.text("❄ Nettoyage des objets au sol dans " + left + " s", ChatFormatting.AQUA);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) sp.displayClientMessage(c, true);
        }
        if (left <= 0) {
            int n = clear(server);
            nextClear = now + every * 1000L;
            Msg.broadcast(server, "❄ Nettoyage : " + n + " objet(s) au sol supprimé(s). Prochain dans " + Msg.time(every * 1000L) + ".", ChatFormatting.AQUA);
            for (ServerPlayer sp : server.getPlayerList().getPlayers())
                com.godmod.util.Fx.sound(sp, SoundEvents.GLASS_BREAK, 0.4F, 1.6F);
        }
    }

    /** Supprime tous les objets au sol ; renvoie leur nombre (piles). */
    public static int clear(MinecraftServer server) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) {
            List<ItemEntity> items = new ArrayList<>();
            for (Entity e : l.getAllEntities()) if (e instanceof ItemEntity it && it.isAlive() && !it.isNoGravity()) items.add(it); // objets décoratifs (NoGravity) épargnés
            for (ItemEntity it : items) {
                it.discard();
                n++;
            }
        }
        if (n > 0) GodData.log("CLEARLAG : " + n + " objet(s) supprimé(s)");
        return n;
    }

    /** Nettoyage immédiat demandé par un admin ; le compte à rebours repart. */
    public static int clearNow(MinecraftServer server) {
        int n = clear(server);
        nextClear = System.currentTimeMillis() + Math.max(1, GodData.settings().clearlagSeconds) * 1000L;
        return n;
    }
}
