package com.godmod.event;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.system.ArtifactEffects;
import com.godmod.system.AuthSystem;
import com.godmod.system.GameStart;
import com.godmod.system.Moderation;
import com.godmod.system.WorldBuilder;
import com.godmod.menu.Menus;
import com.godmod.util.DivineItems;
import com.godmod.data.Specialization;
import com.godmod.GodMod;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
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
        ServerStartedEvent.BUS.addListener((Consumer<ServerStartedEvent>) e -> onServerStarted(e.getServer()));
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

        // Verrous : joueur non connecté (mot de passe), muet ou immobilisé.
        ServerChatEvent.BUS.addListener((Predicate<ServerChatEvent>) GameEvents::onChat);
        CommandEvent.BUS.addListener((Predicate<CommandEvent>) GameEvents::onCommand);
        PlayerInteractEvent.RightClickItem.BUS.addListener((Predicate<PlayerInteractEvent.RightClickItem>) GameEvents::onRightClickItem);
        PlayerInteractEvent.RightClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.RightClickBlock>) e ->
                // Objet divin sur un bloc : on bloque l'interaction ; le clic sur l'objet suit (RightClickItem).
                locked(e.getEntity()) || DivineItems.isDivine(e.getItemStack()));
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.LeftClickBlock>) e -> locked(e.getEntity()));
        PlayerInteractEvent.EntityInteract.BUS.addListener((Predicate<PlayerInteractEvent.EntityInteract>) e -> locked(e.getEntity()));
        AttackEntityEvent.BUS.addListener((Predicate<AttackEntityEvent>) e -> locked(e.getEntity()) || frozen(e.getEntity()));
        EntityItemPickupEvent.BUS.addListener((Predicate<EntityItemPickupEvent>) e -> locked(e.getEntity()));
        LivingAttackEvent.BUS.addListener((Predicate<LivingAttackEvent>) e -> locked(e.getEntity()) || locked(e.getSource().getEntity()));
    }

    private static boolean locked(Entity e) {
        return e instanceof ServerPlayer sp && GodData.ready() && AuthSystem.isLocked(sp);
    }

    private static boolean frozen(Entity e) {
        return e instanceof ServerPlayer sp && Moderation.isFrozen(sp);
    }

    // ------------------------------------------------------------------ Démarrage

    private static void onServerStarted(MinecraftServer server) {
        if (!GodData.ready()) GodData.load(server);
        try {
            WorldBuilder.onServerStarted(server);
        } catch (Exception ex) {
            GodMod.LOGGER.error("[God Mod] Échec de la structuration du monde", ex);
        }
        GodMod.LOGGER.info("[God Mod] Prêt : {} profils, authentification {}, boutique premium {}.",
                GodData.state().players.size(),
                GodData.settings().authEnabled ? "activée" : "désactivée",
                GodData.settings().payEnabled ? "activée" : "désactivée");
    }

    // ------------------------------------------------------------------ Chat & commandes

    private static boolean onChat(ServerChatEvent e) {
        ServerPlayer sp = e.getPlayer();
        if (!GodData.ready()) return false;
        if (AuthSystem.isLocked(sp)) {
            Msg.err(sp, "Connectez-vous d'abord : /login <motdepasse> (ou /register si c'est votre première fois).");
            return true;
        }
        PlayerProfile p = GodData.get(sp);
        if (Moderation.isMuted(p)) {
            Msg.err(sp, "Vous êtes muet encore " + Msg.time(p.mutedUntil - System.currentTimeMillis())
                    + (p.muteReason == null ? "." : " : " + p.muteReason));
            return true;
        }
        return false;
    }

    private static boolean onCommand(CommandEvent e) {
        if (!GodData.ready()) return false;
        Entity ent = e.getParseResults().getContext().getSource().getEntity();
        if (!(ent instanceof ServerPlayer sp) || !AuthSystem.isLocked(sp)) return false;
        String cmd = e.getParseResults().getReader().getString();
        if (AuthSystem.allowedCommand(cmd)) return false;
        Msg.err(sp, "Connectez-vous d'abord : /login <motdepasse> (ou /register <motdepasse> <motdepasse>).");
        return true;
    }

    // ------------------------------------------------------------------ Objets divins

    private static boolean onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (locked(e.getEntity())) return true;
        ItemStack stack = e.getItemStack();
        if (!DivineItems.isDivine(stack)) return false;
        return useDivine(e.getEntity(), stack);
    }

    /** Utilise un objet divin ; renvoie toujours vrai pour bloquer le comportement vanilla (lancer de trident...). */
    private static boolean useDivine(Entity entity, ItemStack stack) {
        if (!(entity instanceof ServerPlayer sp) || !GodData.ready()) return true;
        String kind = DivineItems.kind(stack);
        if (DivineItems.CODEX.equals(kind)) {
            Menus.openMain(sp);
            return true;
        }
        Specialization spec = DivineItems.specialization(stack);
        if (spec != null) ArtifactEffects.use(sp, stack, spec);
        return true;
    }

    // ------------------------------------------------------------------ Connexion

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !GodData.ready()) return;
        PlayerProfile p = GodData.get(sp);
        p.lastSeen = System.currentTimeMillis();
        GodData.dirty();
        if (Moderation.isBanned(p)) {
            GodData.log("BAN : connexion refusée pour " + p.name);
            sp.connection.disconnect(Moderation.banMessage(p));
            return;
        }
        AuthSystem.onLogin(sp);
        if (!AuthSystem.isLocked(sp)) GameStart.afterAuth(sp, p);
        else PowerSystem.applyAttributes(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !GodData.ready()) return;
        boolean wasLocked = AuthSystem.isLocked(sp);
        AuthSystem.onLogout(sp);
        Moderation.onLogout(sp);
        PlayerProfile p = GodData.get(sp);
        p.lastSeen = System.currentTimeMillis();
        GodData.dirty();
        if (!wasLocked) RebellionSystem.onLogout(sp.level().getServer(), p);
    }

    // ------------------------------------------------------------------ Boucle serveur

    private static void onServerTick(MinecraftServer server) {
        if (!GodData.ready()) return;
        tickCounter++;
        AuthSystem.tick(server);
        Moderation.tick(server);
        ArtifactEffects.tick(server);
        if (tickCounter % 20 != 0) return;

        RebellionSystem.tick(server);
        TournamentSystem.tick(server);
        WarSystem.tick(server);
        boolean five = tickCounter % 100 == 0;
        boolean minute = tickCounter % 1200 == 0;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (AuthSystem.isLocked(sp)) continue;
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
        if (locked(e.getPlayer())) return true;
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
        if (locked(entity)) return true;
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
