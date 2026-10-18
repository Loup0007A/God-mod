package com.godmod.event;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.registry.ModItems;
import com.godmod.system.ArtifactEffects;
import com.godmod.system.DivineWorld;
import com.godmod.system.FaithSystem;
import com.godmod.system.PowerSystem;
import com.godmod.system.RebellionSystem;
import com.godmod.system.Shop;
import com.godmod.system.TournamentSystem;
import com.godmod.system.WarSystem;
import com.godmod.command.GodCommands;
import com.godmod.util.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Branche toutes les mécaniques du mod sur les événements du jeu (côté serveur). */
public final class GameEvents {
    private static long tickCounter = 0;

    private GameEvents() {
    }

    public static void register() {
        RegisterCommandsEvent.BUS.addListener((Consumer<RegisterCommandsEvent>) e -> GodCommands.register(e.getDispatcher()));
        ServerStartingEvent.BUS.addListener((Consumer<ServerStartingEvent>) e -> GodData.load(e.getServer()));
        ServerStoppedEvent.BUS.addListener((Consumer<ServerStoppedEvent>) e -> GodData.unload());

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener((Consumer<PlayerEvent.PlayerLoggedInEvent>) GameEvents::onLogin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((Consumer<PlayerEvent.PlayerLoggedOutEvent>) GameEvents::onLogout);
        PlayerEvent.PlayerRespawnEvent.BUS.addListener((Consumer<PlayerEvent.PlayerRespawnEvent>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) PowerSystem.applyAttributes(sp);
        });
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener((Consumer<PlayerEvent.PlayerChangedDimensionEvent>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) PowerSystem.applyAttributes(sp);
        });

        TickEvent.ServerTickEvent.Post.BUS.addListener((Consumer<TickEvent.ServerTickEvent.Post>) e -> onServerTick(e.server()));

        LivingDeathEvent.BUS.addListener((Predicate<LivingDeathEvent>) GameEvents::onDeath);
        BlockEvent.BreakEvent.BUS.addListener((Predicate<BlockEvent.BreakEvent>) GameEvents::onBreak);
        BlockEvent.EntityPlaceEvent.BUS.addListener((Predicate<BlockEvent.EntityPlaceEvent>) e -> onPlace(e.getLevel(), e.getPos(), e.getEntity()));
        BlockEvent.EntityMultiPlaceEvent.BUS.addListener((Predicate<BlockEvent.EntityMultiPlaceEvent>) e -> onPlace(e.getLevel(), e.getPos(), e.getEntity()));
        EntityTravelToDimensionEvent.BUS.addListener((Predicate<EntityTravelToDimensionEvent>) GameEvents::onTravel);
    }

    // ------------------------------------------------------------------ Connexion

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !GodData.ready()) return;
        PlayerProfile p = GodData.get(sp);
        p.lastSeen = System.currentTimeMillis();
        if (!p.codexGiven) {
            p.codexGiven = true;
            Shop.give(sp, new ItemStack(ModItems.CODEX.get()));
            Msg.send(sp, Msg.text("Bienvenue, mortel. Votre ascension commence. Ouvrez le Codex divin (clic droit) ou tapez ", ChatFormatting.GOLD)
                    .append(Msg.button("/dieu", "/dieu", "Ouvrir le Panthéon", ChatFormatting.YELLOW)));
            GodData.log("NOUVEAU JOUEUR : " + p.name);
        } else {
            Msg.info(sp, "Bon retour, " + p.tier.label + " " + p.name + ". Tapez /dieu pour ouvrir le Panthéon.");
        }
        GodData.dirty();
        PowerSystem.applyAttributes(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !GodData.ready()) return;
        PlayerProfile p = GodData.get(sp);
        p.lastSeen = System.currentTimeMillis();
        GodData.dirty();
        RebellionSystem.onLogout(sp.level().getServer(), p);
    }

    // ------------------------------------------------------------------ Boucle serveur

    private static void onServerTick(MinecraftServer server) {
        if (!GodData.ready()) return;
        tickCounter++;
        ArtifactEffects.tick(server);
        if (tickCounter % 20 != 0) return;

        RebellionSystem.tick(server);
        TournamentSystem.tick(server);
        WarSystem.tick(server);
        boolean five = tickCounter % 100 == 0;
        boolean minute = tickCounter % 1200 == 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            PlayerProfile p = GodData.get(sp);
            p.playTicks += 20;
            DivineWorld.enforce(sp);
            if (five) PowerSystem.periodic(sp);
            if (minute) PowerSystem.perMinute(sp);
        }
        if (minute) FaithSystem.tickExpired(server);
    }

    // ------------------------------------------------------------------ Combat

    /** Renvoie vrai pour annuler la mort. */
    private static boolean onDeath(LivingDeathEvent e) {
        if (!GodData.ready()) return false;
        LivingEntity dead = e.getEntity();
        Entity attacker = e.getSource().getEntity();

        if (dead instanceof ServerPlayer victim) {
            if (TournamentSystem.isFighting(victim.getUUID().toString())) {
                TournamentSystem.onDuelDefeat(victim);
                return true;
            }
            if (attacker instanceof ServerPlayer killer && killer != victim) {
                FaithSystem.onPlayerKill(killer, victim);
            } else {
                PlayerProfile vp = GodData.get(victim);
                vp.deaths++;
                GodData.dirty();
                RebellionSystem.onRebelDied(victim.level().getServer(), vp);
            }
            return false;
        }

        if (attacker instanceof ServerPlayer killer && dead instanceof Mob) {
            boolean hostile = dead instanceof Enemy;
            PowerSystem.addEssence(killer, hostile ? 3 : 1, "chasse");
            PowerSystem.earnMoney(killer.level().getServer(), GodData.get(killer), hostile ? 2 : 1);
        }
        return false;
    }

    // ------------------------------------------------------------------ Monde divin

    private static boolean onBreak(BlockEvent.BreakEvent e) {
        if (!GodData.ready() || !(e.getLevel() instanceof Level level) || !DivineWorld.isDivine(level)) return false;
        if (!(e.getPlayer() instanceof ServerPlayer sp) || sp.isCreative()) return false;
        PlayerProfile actor = GodData.get(sp);
        PlayerProfile owner = DivineWorld.ownerAt(e.getPos());
        if (owner == null || !DivineWorld.canBuild(actor, owner) || !DivineWorld.insidePlot(owner, e.getPos())) {
            Msg.err(sp, "Vous ne pouvez pas modifier ce monde divin.");
            return true;
        }
        if (!owner.plotStarted && e.getPos().equals(DivineWorld.core(owner))) {
            Msg.err(sp, "Ce bloc de terre originel est indestructible tant que rien n'a été construit.");
            return true;
        }
        return false;
    }

    private static boolean onPlace(net.minecraft.world.level.LevelAccessor levelAccessor, BlockPos pos, Entity entity) {
        if (!GodData.ready() || !(levelAccessor instanceof Level level) || !DivineWorld.isDivine(level)) return false;
        if (!(entity instanceof ServerPlayer sp)) return false;
        PlayerProfile actor = GodData.get(sp);
        PlayerProfile owner = DivineWorld.ownerAt(pos);
        if (!sp.isCreative() && (owner == null || !DivineWorld.canBuild(actor, owner) || !DivineWorld.insidePlot(owner, pos))) {
            Msg.err(sp, "Vous ne pouvez construire que dans votre monde divin (ou celui de vos vassaux).");
            return true;
        }
        if (owner != null && owner.uuid.equals(actor.uuid) && !owner.plotStarted) {
            owner.plotStarted = true;
            GodData.dirty();
            Msg.info(sp, "Votre monde prend vie : le bloc originel n'est plus protégé.");
        }
        return false;
    }

    private static boolean onTravel(EntityTravelToDimensionEvent e) {
        if (!GodData.ready() || !GodData.settings().disableNetherEnd) return false;
        if (e.getDimension() == Level.NETHER || e.getDimension() == Level.END) {
            if (e.getEntity() instanceof ServerPlayer sp) {
                Msg.err(sp, "Le Nether et l'End sont scellés par les dieux. Leurs trésors sont vendus à la boutique (/shop).");
            }
            return true;
        }
        return false;
    }
}
