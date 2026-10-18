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
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import com.godmod.system.BlockJobs;
import com.godmod.system.CodexGuard;
import com.godmod.system.Hub;
import com.godmod.system.Ranks;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Branche toutes les mécaniques du mod sur les événements du jeu (côté serveur). */
public final class GameEvents {
    private static long tickCounter = 0;
    private static long lastTickError = 0;

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
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) {
                // Après une mort, on réapparaît toujours au Sanctuaire.
                if (!e.isEndConquered()) Hub.teleport(sp);
                PowerSystem.applyAttributes(sp);
            }
        });
        PlayerEvent.PlayerChangedDimensionEvent.BUS.addListener((Consumer<PlayerEvent.PlayerChangedDimensionEvent>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) {
                DivineWorld.onDimensionChange(sp);
                PowerSystem.applyAttributes(sp);
            }
        });

        // Rang affiché dans la liste des joueurs (Tab) et dans le chat.
        PlayerEvent.TabListNameFormat.BUS.addListener((Consumer<PlayerEvent.TabListNameFormat>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) e.setDisplayName(Ranks.decorate(sp, sp.getName()));
        });
        PlayerEvent.NameFormat.BUS.addListener((Consumer<PlayerEvent.NameFormat>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) e.setDisplayname(Ranks.decorate(sp, e.getUsername()));
        });

        // Codex : impossible à jeter, ne tombe pas à la mort (il revient tout seul dans sa case).
        ItemTossEvent.BUS.addListener((Predicate<ItemTossEvent>) e -> CodexGuard.isCodex(e.getEntity().getItem()));
        LivingDropsEvent.BUS.addListener((Predicate<LivingDropsEvent>) e -> {
            e.getDrops().removeIf(item -> CodexGuard.isCodex(item.getItem()));
            return false;
        });

        // Pistons : aucun bloc ne passe la bordure d'un monde divin.
        PistonEvent.Pre.BUS.addListener((Predicate<PistonEvent.Pre>) e -> e.getLevel() instanceof Level level && GodData.ready()
                && DivineWorld.isDivine(level)
                && DivineWorld.pistonCrossesBorder(level, e.getPos(), e.getDirection(), e.getPistonMoveType().isExtend()));

        TickEvent.ServerTickEvent.Post.BUS.addListener((Consumer<TickEvent.ServerTickEvent.Post>) e -> {
            // Une erreur dans le mod ne doit jamais faire planter tout le serveur : on la note et on continue.
            try {
                onServerTick(e.server());
            } catch (Exception ex) {
                long now = System.currentTimeMillis();
                if (now - lastTickError > 10_000) {
                    lastTickError = now;
                    GodMod.LOGGER.error("[God Mod] Erreur pendant le tick (le serveur continue)", ex);
                }
            }
        });

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
                locked(e.getEntity()) || DivineItems.isDivine(e.getItemStack()) || hubGrief(e.getEntity(), e.getItemStack())
                        || blockInteractDenied(e.getEntity(), e.getPos(), e.getItemStack()));

        // Sanctuaire : pas de monstres, et les explosions n'y cassent rien.
        EntityJoinLevelEvent.BUS.addListener((Predicate<EntityJoinLevelEvent>) e -> GodData.ready() && !e.loadedFromDisk()
                && e.getEntity() instanceof Enemy && Hub.isProtected(e.getLevel(), e.getEntity().blockPosition()));
        ExplosionEvent.Detonate.BUS.addListener((Consumer<ExplosionEvent.Detonate>) e -> {
            if (GodData.ready()) e.getAffectedBlocks().removeIf(pos -> Hub.isProtected(e.getLevel(), pos));
        });
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.LeftClickBlock>) e -> locked(e.getEntity()));
        PlayerInteractEvent.EntityInteract.BUS.addListener((Predicate<PlayerInteractEvent.EntityInteract>) e ->
                locked(e.getEntity()) || entityInteractDenied(e.getEntity(), e.getTarget()));
        AttackEntityEvent.BUS.addListener((Predicate<AttackEntityEvent>) e -> locked(e.getEntity()) || frozen(e.getEntity()));
        EntityItemPickupEvent.BUS.addListener((Predicate<EntityItemPickupEvent>) e -> locked(e.getEntity()));
        LivingAttackEvent.BUS.addListener((Predicate<LivingAttackEvent>) e -> locked(e.getEntity()) || locked(e.getSource().getEntity())
                || pvpDenied(e.getEntity(), e.getSource().getEntity()));
    }

    /**
     * Clic droit sur un bloc : le Sanctuaire est intouchable (sauf mode édition) ; sur une île divine,
     * il faut la permission COFFRES pour un conteneur, ou INTERAGIR pour le reste (portes, leviers…).
     */
    private static boolean blockInteractDenied(Entity e, BlockPos pos, ItemStack stack) {
        if (!(e instanceof ServerPlayer sp) || !GodData.ready()) return false;
        Level level = sp.level();
        if (Hub.isSpawn(level)) return !Hub.canEditHub(sp);
        if (!DivineWorld.isDivine(level) || sp.isCreative() && Moderation.roleOf(sp).atLeast(com.godmod.data.Role.ADMIN)) return false;
        PlayerProfile owner = DivineWorld.ownerAt(pos);
        if (owner == null) return true;
        PlayerProfile actor = GodData.get(sp);
        boolean container = level.getBlockEntity(pos) != null;
        if (container) {
            if (DivineWorld.has(actor, owner, com.godmod.data.IslandPerm.COFFRES)) return false;
            sp.displayClientMessage(Msg.text("Vous n'avez pas la permission d'ouvrir les coffres de " + owner.name + ".", ChatFormatting.RED), true);
            return true;
        }
        // Avec un bloc en main, c'est une pose : la permission POSER est vérifiée à la pose.
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) return false;
        if (DivineWorld.has(actor, owner, com.godmod.data.IslandPerm.INTERAGIR)) return false;
        sp.displayClientMessage(Msg.text("Vous n'avez pas la permission d'interagir sur l'île de " + owner.name + ".", ChatFormatting.RED), true);
        return true;
    }

    private static boolean entityInteractDenied(Entity e, Entity target) {
        if (!(e instanceof ServerPlayer sp) || !GodData.ready()) return false;
        if (Hub.isSpawn(sp.level())) return !Hub.canEditHub(sp) && !(target instanceof ServerPlayer);
        if (!DivineWorld.isDivine(sp.level())) return false;
        PlayerProfile owner = DivineWorld.ownerAt(target.blockPosition());
        if (owner == null) return false;
        return !DivineWorld.has(GodData.get(sp), owner, com.godmod.data.IslandPerm.INTERAGIR);
    }

    /**
     * Combats entre joueurs : interdits au Sanctuaire et sur une île sans PvP,
     * sauf duels de tournoi, rébellions et guerres en cours.
     */
    private static boolean pvpDenied(Entity victim, Entity attacker) {
        if (!(victim instanceof ServerPlayer v) || !(attacker instanceof ServerPlayer a) || v == a || !GodData.ready()) return false;
        PlayerProfile pv = GodData.get(v), pa = GodData.get(a);
        boolean special = (TournamentSystem.isFighting(pv.uuid) && TournamentSystem.isFighting(pa.uuid))
                || WarSystem.atWar(pv, pa) || RebellionSystem.involving(pv.uuid) != null && RebellionSystem.involving(pv.uuid) == RebellionSystem.involving(pa.uuid);
        if (special) return false;
        if (Hub.isSpawn(v.level())) {
            a.displayClientMessage(Msg.text("Le Sanctuaire est une zone de paix (hors duels).", ChatFormatting.RED), true);
            return true;
        }
        if (DivineWorld.isDivine(v.level())) {
            PlayerProfile owner = DivineWorld.ownerAt(v.blockPosition());
            if (owner != null && !owner.islandPvp) {
                a.displayClientMessage(Msg.text("Le combat est désactivé sur cette île.", ChatFormatting.RED), true);
                return true;
            }
        }
        return false;
    }

    /** Seaux (lave, eau…), briquet et boules de feu sont interdits dans le Sanctuaire. */
    private static boolean hubGrief(Entity e, ItemStack stack) {
        if (!(e instanceof ServerPlayer sp) || !GodData.ready() || Hub.canEditHub(sp)) return false;
        if (!Hub.isProtected(sp.level(), sp.blockPosition())) return false;
        boolean risky = stack.getItem() instanceof net.minecraft.world.item.BucketItem
                || stack.is(net.minecraft.world.item.Items.FLINT_AND_STEEL) || stack.is(net.minecraft.world.item.Items.FIRE_CHARGE);
        if (risky) sp.displayClientMessage(Msg.text("Le Sanctuaire est protégé par les dieux.", ChatFormatting.RED), true);
        return risky;
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
        if (hubGrief(e.getEntity(), e.getItemStack())) return true;
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
        Hub.teleport(sp); // chaque connexion commence au Sanctuaire
        AuthSystem.onLogin(sp);
        if (!AuthSystem.isLocked(sp)) GameStart.afterAuth(sp, p);
        else PowerSystem.applyAttributes(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !GodData.ready()) return;
        boolean wasLocked = AuthSystem.isLocked(sp);
        AuthSystem.onLogout(sp);
        Moderation.onLogout(sp);
        DivineWorld.onLogout(sp);
        Hub.onLogout(sp);
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
        BlockJobs.tick(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (AuthSystem.isLocked(sp)) continue;
            DivineWorld.enforce(sp);
            CodexGuard.tick(sp);
            Hub.fallNet(sp);
            Hub.tickPortals(sp);
        }
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
            DivineWorld.syncBorder(sp);
            if (minute) Ranks.refresh(sp);
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
        if (GodData.ready() && e.getLevel() instanceof Level hl && e.getPlayer() instanceof ServerPlayer hp
                && Hub.isProtected(hl, e.getPos()) && !Hub.canEditHub(hp)) {
            hp.displayClientMessage(Msg.text("Le Sanctuaire est protégé par les dieux.", ChatFormatting.RED), true);
            return true;
        }
        if (!GodData.ready() || !(e.getLevel() instanceof Level level) || !DivineWorld.isDivine(level)) return false;
        if (!(e.getPlayer() instanceof ServerPlayer sp) || sp.isCreative()) return false;
        PlayerProfile actor = GodData.get(sp);
        PlayerProfile owner = DivineWorld.ownerAt(e.getPos());
        if (owner == null || !DivineWorld.has(actor, owner, com.godmod.data.IslandPerm.CASSER) || !DivineWorld.insidePlot(owner, e.getPos())) {
            sp.displayClientMessage(Msg.text("Vous n'avez pas la permission de casser ici.", ChatFormatting.RED), true);
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
        if (GodData.ready() && levelAccessor instanceof Level hl && entity instanceof ServerPlayer hp
                && Hub.isProtected(hl, pos) && !Hub.canEditHub(hp)) {
            hp.displayClientMessage(Msg.text("Le Sanctuaire est protégé par les dieux.", ChatFormatting.RED), true);
            return true;
        }
        if (!GodData.ready() || !(levelAccessor instanceof Level level) || !DivineWorld.isDivine(level)) return false;
        if (!(entity instanceof ServerPlayer sp)) return false;
        PlayerProfile actor = GodData.get(sp);
        PlayerProfile owner = DivineWorld.ownerAt(pos);
        if (!sp.isCreative() && (owner == null || !DivineWorld.has(actor, owner, com.godmod.data.IslandPerm.POSER) || !DivineWorld.insidePlot(owner, pos))) {
            sp.displayClientMessage(Msg.text("Vous n'avez pas la permission de poser des blocs ici.", ChatFormatting.RED), true);
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
