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
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.common.util.Result;
import com.godmod.progress.CrateSystem;
import com.godmod.progress.Hud;
import com.godmod.progress.JobSystem;
import com.godmod.progress.PointSystem;
import com.godmod.progress.QuestSystem;
import com.godmod.progress.SpawnerSystem;
import com.godmod.system.MineReset;
import com.godmod.system.PowerActions;
import com.godmod.util.Scheduler;
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
import com.godmod.system.Travel;
import com.godmod.system.TpaSystem;
import com.godmod.progress.EventSystem;
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
        ServerAboutToStartEvent.BUS.addListener((Consumer<ServerAboutToStartEvent>) e -> MineReset.onServerAboutToStart(e.getServer()));
        ServerStartingEvent.BUS.addListener((Consumer<ServerStartingEvent>) e -> GodData.load(e.getServer()));
        ServerStartedEvent.BUS.addListener((Consumer<ServerStartedEvent>) e -> onServerStarted(e.getServer()));
        net.minecraftforge.event.server.ServerStoppingEvent.BUS.addListener((Consumer<net.minecraftforge.event.server.ServerStoppingEvent>) e -> {
            if (GodData.ready()) EventSystem.onServerStopping(e.getServer());
            if (GodData.ready()) com.godmod.system.Trade.cancelAll();
            com.godmod.system.Trailer.stop(e.getServer(), "arrêt du serveur");
        });
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
            if (EventSystem.isEventMob(e.getEntity())) e.getDrops().clear(); // serviteurs du Titan : pas de butin à farmer
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) com.godmod.system.Graves.onPlayerDrops(sp, e.getDrops(), e.getSource());
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
        BlockEvent.BreakEvent.BUS.addListener((Predicate<BlockEvent.BreakEvent>) e -> {
            // Forge lit le RÉSULTAT de cet événement (pas l'annulation) : il faut DENY pour bloquer la casse.
            boolean deny = onBreak(e);
            if (deny) e.setResult(Result.DENY);
            else if (GodData.ready() && e.getPlayer() instanceof ServerPlayer sp && e.getLevel() instanceof ServerLevel sl) {
                try {
                    SpawnerSystem.onBroken(sl, e.getPos(), e.getState());
                    JobSystem.onBroken(sp, sl, e.getPos(), e.getState());
                } catch (Exception ex) {
                    GodMod.LOGGER.error("[God Mod] Erreur métiers (casse)", ex);
                }
            }
            return deny;
        });
        BlockEvent.EntityPlaceEvent.BUS.addListener((Predicate<BlockEvent.EntityPlaceEvent>) e -> {
            boolean deny = onPlace(e.getLevel(), e.getPos(), e.getEntity());
            if (!deny && GodData.ready() && e.getLevel() instanceof ServerLevel sl) {
                JobSystem.onPlaced(sl, e.getPos());
                if (e.getEntity() instanceof ServerPlayer pl) com.godmod.progress.StatsSystem.add(GodData.get(pl), com.godmod.progress.StatsSystem.PLACED, 1);
                if (e.getEntity() instanceof ServerPlayer sp) SpawnerSystem.onPlaced(sp, sl, e.getPos(), e.getPlacedBlock());
            }
            return deny;
        });
        BlockEvent.EntityMultiPlaceEvent.BUS.addListener((Predicate<BlockEvent.EntityMultiPlaceEvent>) e -> onPlace(e.getLevel(), e.getPos(), e.getEntity()));
        EntityTravelToDimensionEvent.BUS.addListener((Predicate<EntityTravelToDimensionEvent>) GameEvents::onTravel);

        // Verrous : joueur non connecté (mot de passe), muet ou immobilisé.
        ServerChatEvent.BUS.addListener((Predicate<ServerChatEvent>) GameEvents::onChat);
        CommandEvent.BUS.addListener((Predicate<CommandEvent>) GameEvents::onCommand);
        PlayerInteractEvent.RightClickItem.BUS.addListener((Predicate<PlayerInteractEvent.RightClickItem>) GameEvents::onRightClickItem);
        PlayerInteractEvent.RightClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.RightClickBlock>) e ->
                // Objet divin sur un bloc : on bloque l'interaction ; le clic sur l'objet suit (RightClickItem).
                locked(e.getEntity())
                        || (e.getEntity() instanceof ServerPlayer wp && GodData.ready() && e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                            && com.godmod.system.ZoneSystem.click(wp, e.getPos(), false))
                        || pointUsed(e.getEntity(), e.getPos())
                        || (DivineItems.isDivine(e.getItemStack()) && !SpawnerSystem.KIND.equals(DivineItems.kind(e.getItemStack())))
                        || hubGrief(e.getEntity(), e.getItemStack()) || blockInteractDenied(e.getEntity(), e.getPos(), e.getItemStack()));

        // Artisanat, fonte, élevage, commerce : quêtes et métiers.
        PlayerEvent.ItemCraftedEvent.BUS.addListener((Consumer<PlayerEvent.ItemCraftedEvent>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready() && !AuthSystem.isLocked(sp)) JobSystem.onCraft(sp, e.getCrafting());
        });
        PlayerEvent.ItemSmeltedEvent.BUS.addListener((Consumer<PlayerEvent.ItemSmeltedEvent>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready() && !AuthSystem.isLocked(sp)) JobSystem.onSmelt(sp, e.getSmelting());
        });
        net.minecraftforge.event.entity.living.BabyEntitySpawnEvent.BUS.addListener((Predicate<net.minecraftforge.event.entity.living.BabyEntitySpawnEvent>) e -> {
            if (GodData.ready() && e.getCausedByPlayer() instanceof ServerPlayer sp && e.getChild() != null) JobSystem.onBreed(sp, e.getChild());
            return false;
        });
        net.minecraftforge.event.entity.player.TradeWithVillagerEvent.BUS.addListener((Consumer<net.minecraftforge.event.entity.player.TradeWithVillagerEvent>) e -> {
            if (GodData.ready() && e.getEntity() instanceof ServerPlayer sp) JobSystem.onTrade(sp);
        });

        // Générateur lave + eau : la production dépend du prestige du propriétaire de l'île.
        BlockEvent.FluidPlaceBlockEvent.BUS.addListener((Predicate<BlockEvent.FluidPlaceBlockEvent>) e -> {
            if (GodData.ready() && e.getLevel() instanceof Level fl) {
                net.minecraft.world.level.block.state.BlockState rep = com.godmod.system.GeneratorSystem.onFluidPlace(fl, e.getPos(), e.getNewState());
                if (rep != null) e.setNewState(rep);
            }
            return false;
        });
        // Anti-lag : 15 créatures d'un même type par chunk au plus.
        EntityJoinLevelEvent.BUS.addListener((Predicate<EntityJoinLevelEvent>) e -> GodData.ready() && !e.loadedFromDisk()
                && com.godmod.system.LagControl.tooMany(e.getLevel(), e.getEntity()));
        // Affichage d'une tombe disparue pendant que son chunk était déchargé.
        EntityJoinLevelEvent.BUS.addListener((Consumer<EntityJoinLevelEvent>) e -> {
            if (e.loadedFromDisk() && e.getLevel().getServer() != null && com.godmod.system.Graves.isStaleMarker(e.getEntity())) {
                Entity ent = e.getEntity();
                e.getLevel().getServer().execute(ent::discard);
            }
        });

        // Pêche : métier Pêcheur.
        ItemFishedEvent.BUS.addListener((Predicate<ItemFishedEvent>) e -> {
            if (e.getEntity() instanceof ServerPlayer sp && GodData.ready()) JobSystem.onFish(sp, e.getDrops());
            return false;
        });

        // Sanctuaire : pas de monstres, et les explosions n'y cassent rien.
        EntityJoinLevelEvent.BUS.addListener((Predicate<EntityJoinLevelEvent>) e -> GodData.ready() && !e.loadedFromDisk()
                && e.getEntity() instanceof Enemy && !EventSystem.isEventMob(e.getEntity()) && Hub.isProtected(e.getLevel(), e.getEntity().blockPosition()));
        ExplosionEvent.Detonate.BUS.addListener((Consumer<ExplosionEvent.Detonate>) e -> {
            if (GodData.ready()) e.getAffectedBlocks().removeIf(pos -> Hub.isProtected(e.getLevel(), pos));
        });
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.LeftClickBlock>) e -> locked(e.getEntity())
                || (e.getEntity() instanceof ServerPlayer wp && GodData.ready() && com.godmod.system.ZoneSystem.click(wp, e.getPos(), true)));
        // Coffres 3D des caisses : la zone cliquable ouvre l'aperçu de la caisse.
        PlayerInteractEvent.EntityInteract.BUS.addListener((Predicate<PlayerInteractEvent.EntityInteract>) e -> {
            if (!GodData.ready() || !(e.getEntity() instanceof ServerPlayer sp)) return false;
            com.godmod.progress.CrateSystem.Crate c = com.godmod.progress.CrateDecor.crateOf(e.getTarget());
            if (c == null) return false;
            if (!locked(sp)) sp.level().getServer().execute(() -> com.godmod.progress.CrateSystem.openPreview(sp, c));
            return true;
        });
        PlayerInteractEvent.EntityInteract.BUS.addListener((Predicate<PlayerInteractEvent.EntityInteract>) e ->
                locked(e.getEntity()) || entityInteractDenied(e.getEntity(), e.getTarget()));
        AttackEntityEvent.BUS.addListener((Predicate<AttackEntityEvent>) e -> locked(e.getEntity()) || frozen(e.getEntity()));
        EntityItemPickupEvent.BUS.addListener((Predicate<EntityItemPickupEvent>) e -> locked(e.getEntity()));
        LivingAttackEvent.BUS.addListener((Predicate<LivingAttackEvent>) e -> {
            boolean deny = locked(e.getEntity()) || locked(e.getSource().getEntity()) || pvpDenied(e.getEntity(), e.getSource().getEntity())
                    || (e.getEntity() instanceof ServerPlayer prot && (Travel.isProtected(prot)
                    || (com.godmod.system.Parachute.active(prot) && e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL))));
            // Combat entre joueurs : pas de téléportation pendant 12 s (anti-fuite).
            if (!deny && e.getEntity() instanceof ServerPlayer v && e.getSource().getEntity() instanceof ServerPlayer a && v != a && e.getAmount() > 0)
                Travel.tagCombat(v, a);
            return deny;
        });
        // Dégâts infligés (après armure) : participation au combat contre le Titan.
        net.minecraftforge.event.entity.living.LivingDamageEvent.BUS.addListener((Predicate<net.minecraftforge.event.entity.living.LivingDamageEvent>) e -> {
            if (GodData.ready()) EventSystem.onDamage(e.getEntity(), e.getSource().getEntity(), e.getAmount());
            return false;
        });
    }

    /** Clic droit sur une caisse ou un tableau du Sanctuaire. */
    private static boolean pointUsed(Entity e, BlockPos pos) {
        if (!(e instanceof ServerPlayer sp) || !GodData.ready() || AuthSystem.isLocked(sp)) return false;
        if (Hub.canEditHub(sp) && sp.isShiftKeyDown()) return false; // un admin en édition peut modifier le bloc
        return PointSystem.use(sp, pos);
    }

    /**
     * Clic droit sur un bloc : le Sanctuaire est intouchable (sauf mode édition) ; sur une île divine,
     * il faut la permission COFFRES pour un conteneur, ou INTERAGIR pour le reste (portes, leviers…).
     */
    private static boolean blockInteractDenied(Entity e, BlockPos pos, ItemStack stack) {
        if (!(e instanceof ServerPlayer sp) || !GodData.ready()) return false;
        Level level = sp.level();
        if (Hub.isProtected(level, pos)) return !Hub.canEditHub(sp);
        if (Hub.isSpawn(level)) return false; // zone libre du Sanctuaire
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
        if (Hub.isProtected(sp.level(), target.blockPosition())) return !Hub.canEditHub(sp) && !(target instanceof ServerPlayer);
        if (Hub.isSpawn(sp.level())) return false;
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
        if (Hub.isProtected(v.level(), v.blockPosition())) {
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
        com.godmod.system.PackSystem.refresh(server, null);
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
        if (com.godmod.system.Trailer.active()) {
            Msg.err(sp, "Le chat est en pause pendant le tournage du trailer.");
            return true;
        }
        PlayerProfile p = GodData.get(sp);
        if (Moderation.isMuted(p)) {
            Msg.err(sp, "Vous êtes muet encore " + Msg.time(p.mutedUntil - System.currentTimeMillis())
                    + (p.muteReason == null ? "." : " : " + p.muteReason));
            return true;
        }
        // Le chat est géré par le mod (canaux, onglets, mise en forme) : le message vanilla est annulé.
        String raw = e.getRawText();
        sp.level().getServer().execute(() -> {
            com.godmod.system.AfkSystem.onActivity(sp);
            com.godmod.system.ChatSystem.onChat(sp, raw);
        });
        return true;
    }

    private static boolean onCommand(CommandEvent e) {
        if (!GodData.ready()) return false;
        Entity ent = e.getParseResults().getContext().getSource().getEntity();
        if (!(ent instanceof ServerPlayer sp)) return false;
        String cmd = e.getParseResults().getReader().getString();
        if (!cmd.startsWith("afk") && !cmd.startsWith("/afk")) com.godmod.system.AfkSystem.onActivity(sp);
        if (!AuthSystem.isLocked(sp)) {
            // Un joueur muet ne contourne pas la sanction par les messages privés.
            PlayerProfile p = GodData.get(sp);
            String root = (cmd.startsWith("/") ? cmd.substring(1) : cmd).split(" ", 2)[0].toLowerCase();
            if (Moderation.isMuted(p) && java.util.Set.of("msg", "tell", "w", "me", "teammsg", "tm", "say",
                    "mp", "r", "reponse", "g", "p", "local", "commerce", "canal").contains(root)) {
                Msg.err(sp, "Vous êtes muet encore " + Msg.time(p.mutedUntil - System.currentTimeMillis()) + " : messages privés impossibles.");
                return true;
            }
            return false;
        }
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
            if (sp.isShiftKeyDown()) Menus.openPowers(sp);
            else Menus.openMain(sp);
            return true;
        }
        if (SpawnerSystem.KIND.equals(kind)) return false; // un générateur se pose normalement
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
        if (com.godmod.system.AntiBot.onLogin(sp, p)) return;
        Hub.teleport(sp); // chaque connexion commence au Sanctuaire
        AuthSystem.onLogin(sp);
        if (!AuthSystem.isLocked(sp)) GameStart.afterAuth(sp, p);
        else PowerSystem.applyAttributes(sp);
        com.godmod.system.Market.onLogin(sp);
        com.godmod.system.ChatSystem.onLogin(sp);
        com.godmod.system.PackSystem.onLogin(sp);
        com.godmod.system.ClientMod.onLogin(sp);
        if (!AuthSystem.isLocked(sp)) com.godmod.system.MailSystem.onLogin(sp);
        if (!AuthSystem.isLocked(sp)) com.godmod.system.VoteSystem.onLogin(sp);
        int kits = com.godmod.system.Kits.available(p);
        if (kits > 0 && !AuthSystem.isLocked(sp)) Msg.send(sp, Msg.prefix().append(Msg.text(kits + " kit(s) à récupérer ! ", ChatFormatting.GREEN))
                .append(Msg.button("[/kit]", "/kit", "Ouvrir les kits", ChatFormatting.YELLOW)));
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !GodData.ready()) return;
        boolean wasLocked = AuthSystem.isLocked(sp);
        AuthSystem.onLogout(sp);
        Moderation.onLogout(sp);
        DivineWorld.onLogout(sp);
        Hub.onLogout(sp);
        Hud.onLogout(sp);
        CrateSystem.onLogout(sp);
        PowerActions.onLogout(sp);
        Travel.onLogout(sp);
        com.godmod.system.Parachute.onLogout(sp);
        TpaSystem.onLogout(sp);
        EventSystem.onLogout(sp);
        com.godmod.system.AfkSystem.onLogout(sp);
        com.godmod.system.ZoneSystem.onLogout(sp);
        com.godmod.system.Market.onLogout(sp);
        com.godmod.system.Trade.onLogout(sp);
        com.godmod.system.Homes.onLogout(sp);
        com.godmod.system.VoteSystem.onLogout(sp);
        com.godmod.system.ChatSystem.onLogout(sp);
        com.godmod.system.ClientMod.onLogout(sp);
        com.godmod.system.MailSystem.onLogout(sp);
        PlayerProfile p = GodData.get(sp);
        p.lastSeen = System.currentTimeMillis();
        GodData.dirty();
        if (!wasLocked) RebellionSystem.onLogout(sp.level().getServer(), p);
    }

    // ------------------------------------------------------------------ Boucle serveur

    private static void onServerTick(MinecraftServer server) {
        if (!GodData.ready()) return;
        tickCounter++;
        com.godmod.system.Trade.tick(server);
        com.godmod.progress.CrateDecor.tick(server);
        AuthSystem.tick(server);
        Moderation.tick(server);
        ArtifactEffects.tick(server);
        BlockJobs.tick(server);
        Scheduler.tick();
        CrateSystem.tick(server);
        PowerActions.tick(server);
        Travel.tick(server);
        com.godmod.system.Trailer.tick(server);
        com.godmod.system.ZoneSystem.tick(server);
        com.godmod.system.Parachute.tick(server);
        TpaSystem.tick(server);
        EventSystem.tick(server);
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
        com.godmod.system.AfkSystem.tickSecond(server, minute);
        if (minute) com.godmod.system.Market.tickMinute(server);
        if (minute) com.godmod.system.PackSystem.tickMinute(server);
        if (minute) com.godmod.system.BountySystem.tickMinute(server);
        com.godmod.system.Announcer.tickSecond(server);
        com.godmod.system.Graves.tickSecond(server, (int) (tickCounter / 20));
        com.godmod.system.ClientMod.tickSecond(server);
        com.godmod.system.LagControl.tickSecond(server);
        com.godmod.system.ZoneSystem.tickSecond(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (AuthSystem.isLocked(sp)) continue;
            PlayerProfile p = GodData.get(sp);
            p.playTicks += 20;
            com.godmod.system.Perks.tickFly(sp);
            DivineWorld.syncBorder(sp);
            Hud.update(sp);
            if (five) {
                JobSystem.perks(sp);
                com.godmod.progress.AscensionQuests.check(sp);
            }
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
            com.godmod.system.Homes.remember(victim, true); // /back : retour au lieu de la mort
            if (attacker instanceof ServerPlayer killer && killer != victim) {
                com.godmod.system.BountySystem.onKill(killer, victim);
                FaithSystem.onPlayerKill(killer, victim);
            } else {
                PlayerProfile vp = GodData.get(victim);
                vp.deaths++;
                GodData.dirty();
                RebellionSystem.onRebelDied(victim.level().getServer(), vp);
            }
            return false;
        }

        EventSystem.onDeath(dead.level().getServer(), dead);
        if (attacker instanceof ServerPlayer killer && dead instanceof Mob) {
            JobSystem.onKill(killer, dead);
        }
        return false;
    }

    // ------------------------------------------------------------------ Monde divin

    private static boolean onBreak(BlockEvent.BreakEvent e) {
        if (locked(e.getPlayer())) return true;
        if (e.getPlayer() instanceof ServerPlayer wb && com.godmod.system.ZoneSystem.isWand(wb.getMainHandItem())) return true; // le bâton ne casse rien
        if (GodData.ready() && e.getLevel() instanceof Level hl && e.getPlayer() instanceof ServerPlayer hp
                && Hub.isProtected(hl, e.getPos()) && !Hub.canEditHub(hp)) {
            hp.displayClientMessage(Msg.text("Le Sanctuaire est protégé par les dieux.", ChatFormatting.RED), true);
            return true;
        }
        // Coffre au trésor (événement) et autres points interactifs : on ne les casse pas.
        if (GodData.ready() && e.getLevel() instanceof Level pl && e.getPlayer() instanceof ServerPlayer pp
                && PointSystem.at(pl, e.getPos()) != null && !Hub.canEditHub(pp)) {
            pp.displayClientMessage(Msg.text("Faites un clic droit dessus plutôt que de le casser.", ChatFormatting.YELLOW), true);
            return true;
        }
        if (!GodData.ready() || !(e.getLevel() instanceof Level level) || !DivineWorld.isDivine(level)) return false;
        if (!(e.getPlayer() instanceof ServerPlayer sp) || adminBypass(sp)) return false;
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
        if (!adminBypass(sp) && (owner == null || !DivineWorld.has(actor, owner, com.godmod.data.IslandPerm.POSER) || !DivineWorld.insidePlot(owner, pos))) {
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

    /** Seul un admin en créatif passe outre les permissions des îles (le créatif seul ne suffit pas). */
    private static boolean adminBypass(ServerPlayer sp) {
        return sp.isCreative() && Moderation.roleOf(sp).atLeast(com.godmod.data.Role.ADMIN);
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
