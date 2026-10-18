package com.godmod.command;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.system.Moderation;
import com.godmod.data.Settings;
import com.godmod.data.Specialization;
import com.godmod.data.Stat;
import com.godmod.data.Tier;
import com.godmod.menu.Menus;
import com.godmod.system.DivineWorld;
import com.godmod.system.FaithSystem;
import com.godmod.system.PowerActions;
import com.godmod.system.PowerSystem;
import com.godmod.system.RebellionSystem;
import com.godmod.system.Shop;
import com.godmod.system.TournamentSystem;
import com.godmod.system.WarSystem;
import com.godmod.util.Msg;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Toutes les commandes du mod. Les joueurs n'ont besoin que de /dieu : le reste est accessible depuis l'interface. */
public final class GodCommands {

    private GodCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) names.add(p.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> ONLINE = (ctx, b) ->
            SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b);

    private static SuggestionProvider<CommandSourceStack> words(String... w) {
        return (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.asList(w), b);
    }

    /** Exécute une action joueur ; la fonction renvoie un message d'erreur ou null. */
    private static int run(CommandContext<CommandSourceStack> ctx, Function<ServerPlayer, String> action) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!GodData.ready()) return 0;
        String err = action.apply(player);
        if (err != null && !err.isEmpty()) {
            Msg.err(player, err);
            return 0;
        }
        return 1;
    }

    private static PlayerProfile profileArg(CommandContext<CommandSourceStack> ctx, String arg) {
        return GodData.byName(StringArgumentType.getString(ctx, arg));
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text(text, ChatFormatting.WHITE)), false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerGod(d);
        registerWorld(d);
        registerTravel(d);
        registerIsland(d);
        registerProgress(d);
        registerShop(d);
        registerConflicts(d);
        registerAdmin(d);
        StaffCommands.register(d);
    }

    // ------------------------------------------------------------------ /dieu

    private static void registerGod(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> dieu = Commands.literal("dieu")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openMain(p);
                    return null;
                }))
                .then(Commands.literal("profil")
                        .executes(ctx -> run(ctx, p -> {
                            sendProfile(p, GodData.get(p), true);
                            return null;
                        }))
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return "Joueur inconnu.";
                                    PlayerProfile me = GodData.get(p);
                                    boolean details = me.uuid.equals(t.uuid) || GodData.isAbove(me.uuid, t);
                                    sendProfile(p, t, details);
                                    return null;
                                }))))
                .then(Commands.literal("stats")
                        .executes(ctx -> run(ctx, p -> {
                            Menus.openStats(p);
                            return null;
                        }))
                        .then(Commands.argument("caracteristique", StringArgumentType.word()).suggests(words("force", "vitalite", "agilite", "esprit"))
                                .executes(ctx -> run(ctx, p -> {
                                    Stat s = Stat.parse(StringArgumentType.getString(ctx, "caracteristique"));
                                    if (s == null) return "Caractéristique inconnue : force, vitalite, agilite, esprit.";
                                    String err = PowerSystem.upgradeStat(p, s);
                                    if (err == null) Msg.ok(p, s.label + " passe au niveau " + GodData.get(p).stat(s) + ".");
                                    return err;
                                }))))
                .then(Commands.literal("specialisation")
                        .then(Commands.argument("domaine", StringArgumentType.word()).suggests(words("ocean", "ciel", "cosmos"))
                                .executes(ctx -> run(ctx, p -> {
                                    Specialization s = Specialization.parse(StringArgumentType.getString(ctx, "domaine"));
                                    if (s == null) return "Domaine inconnu : ocean, ciel ou cosmos.";
                                    return Menus.chooseSpecialization(p, s);
                                }))))
                .then(Commands.literal("choix")
                        .then(Commands.literal("trone").executes(ctx -> run(ctx, p -> FaithSystem.resolveThrone(p, true))))
                        .then(Commands.literal("refuser").executes(ctx -> run(ctx, p -> FaithSystem.resolveThrone(p, false)))))
                .then(Commands.literal("codex").executes(ctx -> run(ctx, p -> {
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        if (com.godmod.util.DivineItems.CODEX.equals(com.godmod.util.DivineItems.kind(p.getInventory().getItem(i))))
                            return "Vous avez déjà un Codex divin dans votre inventaire.";
                    }
                    Shop.give(p, com.godmod.util.DivineItems.codex());
                    Msg.ok(p, "Un nouveau Codex divin vous a été remis.");
                    return null;
                })))
                .then(Commands.literal("fideles").executes(ctx -> run(ctx, p -> {
                    Menus.openFollowers(p, 0);
                    return null;
                })))
                .then(Commands.literal("liberer")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return "Joueur inconnu.";
                                    String err = FaithSystem.releaseFollower(p, t);
                                    if (err == null) Msg.ok(p, t.name + " est libéré.");
                                    return err;
                                }))))
                .then(Commands.literal("pouvoir")
                        .executes(ctx -> run(ctx, p -> {
                            Menus.openPowers(p);
                            return null;
                        }))
                        .then(Commands.literal("benir").executes(ctx -> run(ctx, p -> {
                            PowerActions.bless(p);
                            return null;
                        })))
                        .then(Commands.literal("convoquer").executes(ctx -> run(ctx, p -> {
                            PowerActions.summonAll(p);
                            return null;
                        })))
                        .then(Commands.literal("chatier")
                                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                        .executes(ctx -> run(ctx, p -> {
                                            PlayerProfile t = profileArg(ctx, "joueur");
                                            if (t == null) return "Joueur inconnu.";
                                            PowerActions.smite(p, t);
                                            return null;
                                        }))))
                        .then(Commands.literal("temps")
                                .then(Commands.argument("mode", StringArgumentType.word()).suggests(words("jour", "nuit", "pluie", "orage", "clair"))
                                        .executes(ctx -> run(ctx, p -> {
                                            PowerActions.time(p, StringArgumentType.getString(ctx, "mode").toLowerCase());
                                            return null;
                                        }))))
                        .then(Commands.literal("espace")
                                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE)
                                        .executes(ctx -> run(ctx, p -> {
                                            ServerPlayer t = p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur"));
                                            if (t == null) return "Joueur introuvable ou hors ligne.";
                                            PowerActions.space(p, t);
                                            return null;
                                        })))));
        d.register(dieu);
        d.register(Commands.literal("pantheon").executes(ctx -> run(ctx, p -> {
            Menus.openMain(p);
            return null;
        })));
        d.register(Commands.literal("classement").executes(ctx -> run(ctx, p -> {
            Menus.openRanking(p);
            return null;
        })));
    }

    private static void sendProfile(ServerPlayer viewer, PlayerProfile t, boolean details) {
        viewer.sendSystemMessage(Msg.prefix().append(Msg.text(t.name + " — ", ChatFormatting.WHITE))
                .append(Component.literal(t.tier.label).withStyle(t.tier.color)));
        viewer.sendSystemMessage(Msg.text("  Puissance : " + PowerSystem.powerScore(t) + "  ·  Fidèles : "
                + GodData.totalFollowers(t.uuid) + "  ·  Dieu : " + (t.master == null ? "aucun" : name(t.master)), ChatFormatting.GRAY));
        if (details) {
            viewer.sendSystemMessage(Msg.text("  Essence " + t.essence + " · Foi " + (int) t.faith + "/" + (int) t.maxFaith()
                    + " · " + t.money + " pièces" + (GodData.settings().payEnabled ? " · " + t.gems + " gemmes" : ""), ChatFormatting.GRAY));
            viewer.sendSystemMessage(Msg.text("  Force " + t.stat(Stat.FORCE) + " · Vitalité " + t.stat(Stat.VITALITE)
                    + " · Agilité " + t.stat(Stat.AGILITE) + " · Esprit " + t.stat(Stat.ESPRIT)
                    + " · Domaine " + t.specialization.label, ChatFormatting.GRAY));
        }
    }

    private static String name(String uuid) {
        PlayerProfile p = GodData.find(uuid);
        return p == null ? "?" : p.name;
    }

    // ------------------------------------------------------------------ /monde

    private static void registerWorld(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("monde")
                .executes(ctx -> run(ctx, DivineWorld::sendHome))
                .then(Commands.literal("quitter").executes(ctx -> run(ctx, p -> {
                    DivineWorld.leave(p);
                    return null;
                })))
                .then(Commands.literal("visiter")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return "Joueur inconnu.";
                                    return DivineWorld.visit(p, t);
                                })))));
    }

    // ------------------------------------------------------------------ Spawn, monde ressource, warps

    private static final SuggestionProvider<CommandSourceStack> WARPS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (com.godmod.data.Warp w : GodData.state().warps.values()) names.add(w.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static void registerTravel(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("spawn").executes(ctx -> run(ctx, p -> {
            com.godmod.system.Hub.teleport(p);
            Msg.ok(p, "Bienvenue au Sanctuaire.");
            return null;
        })));

        d.register(Commands.literal("setwarp").then(Commands.argument("nom", StringArgumentType.word())
                .executes(ctx -> run(ctx, p -> com.godmod.system.WarpSystem.set(p, StringArgumentType.getString(ctx, "nom"))))));
        d.register(Commands.literal("warp")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openWarps(p, 0);
                    return null;
                }))
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(WARPS)
                        .executes(ctx -> run(ctx, p -> com.godmod.system.WarpSystem.go(p, StringArgumentType.getString(ctx, "nom"))))));
        d.register(Commands.literal("warps").executes(ctx -> run(ctx, p -> {
            Menus.openWarps(p, 0);
            return null;
        })));
        d.register(Commands.literal("delwarp").then(Commands.argument("nom", StringArgumentType.word()).suggests(WARPS)
                .executes(ctx -> run(ctx, p -> com.godmod.system.WarpSystem.delete(p, StringArgumentType.getString(ctx, "nom"))))));
    }

    // ------------------------------------------------------------------ Progression : métiers, quêtes, caisses, HUD, pouvoirs

    private static void registerProgress(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("metiers").executes(ctx -> run(ctx, p -> {
            Menus.openJobs(p);
            return null;
        })));
        d.register(Commands.literal("metier").executes(ctx -> run(ctx, p -> {
            Menus.openJobs(p);
            return null;
        })));
        d.register(Commands.literal("quetes").executes(ctx -> run(ctx, p -> {
            Menus.openQuests(p);
            return null;
        })));
        d.register(Commands.literal("quests").executes(ctx -> run(ctx, p -> {
            Menus.openQuests(p);
            return null;
        })));
        d.register(Commands.literal("caisses").executes(ctx -> run(ctx, p -> {
            Menus.openKeys(p);
            return null;
        })));
        d.register(Commands.literal("cles").executes(ctx -> run(ctx, p -> {
            Menus.openKeys(p);
            return null;
        })));
        d.register(Commands.literal("progression").executes(ctx -> run(ctx, p -> {
            Menus.openGuide(p);
            return null;
        })));
        d.register(Commands.literal("hud").executes(ctx -> run(ctx, p -> {
            PlayerProfile pr = GodData.get(p);
            pr.hud = !pr.hud;
            GodData.dirty();
            if (!pr.hud) com.godmod.progress.Hud.hide(p);
            Msg.ok(p, "Affichage de la progression " + (pr.hud ? "activé." : "masqué."));
            return null;
        })));
        SuggestionProvider<CommandSourceStack> powers = (ctx, b) -> {
            List<String> names = new ArrayList<>();
            for (PowerActions.Power pw : PowerActions.Power.values()) names.add(pw.name().toLowerCase());
            return SharedSuggestionProvider.suggest(names, b);
        };
        d.register(Commands.literal("pouvoir")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openPowers(p);
                    return null;
                }))
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(powers)
                        .executes(ctx -> run(ctx, p -> {
                            PowerActions.Power pw = PowerActions.Power.parse(StringArgumentType.getString(ctx, "nom"));
                            if (pw == null) return "Pouvoir inconnu. /pouvoir pour la liste.";
                            PowerActions.cast(p, pw);
                            return null;
                        }))));

        // Clés de caisse (admins) : /cle donner <joueur> <type> [nombre]
        d.register(Commands.literal("cle").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("donner").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(words("commune", "rare", "epique", "divine"))
                                .executes(ctx -> giveKey(ctx, 1))
                                .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> giveKey(ctx, IntegerArgumentType.getInteger(ctx, "nombre"))))))));

        // Monde ressource : réinitialisation (admins)
        d.register(Commands.literal("mine")
                .executes(ctx -> run(ctx, com.godmod.system.Hub::toMine))
                .then(Commands.literal("reset").requires(Moderation.require(Role.ADMIN))
                        .executes(ctx -> mineReset(ctx, false))
                        .then(Commands.literal("maintenant").executes(ctx -> mineReset(ctx, true)))));
        d.register(Commands.literal("ressources").executes(ctx -> run(ctx, com.godmod.system.Hub::toMine)));

        // Modérateurs : /modo ajouter|retirer|liste (admins)
        d.register(Commands.literal("modo").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> run(ctx, p -> {
                    Menus.openTeam(p);
                    return null;
                }))
                .then(Commands.literal("ajouter").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> setRole(ctx, Role.MODERATEUR))))
                .then(Commands.literal("admin").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> setRole(ctx, Role.ADMIN))))
                .then(Commands.literal("retirer").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> setRole(ctx, Role.JOUEUR))))
                .then(Commands.literal("liste").executes(ctx -> {
                    for (PlayerProfile pp : GodData.state().players.values())
                        if (pp.role != null && pp.role != Role.JOUEUR) reply(ctx, pp.role.label + " : " + pp.name);
                    reply(ctx, "(Les opérateurs du serveur sont aussi admins.)");
                    return 1;
                })));
    }

    private static int setRole(CommandContext<CommandSourceStack> ctx, Role role) {
        PlayerProfile t = profileArg(ctx, "joueur");
        if (t == null) return fail(ctx, "Joueur inconnu (il doit s'être connecté au moins une fois).");
        reply(ctx, Moderation.setRole(ctx.getSource().getServer(), t, role, ctx.getSource().getTextName()));
        return 1;
    }

    private static int giveKey(CommandContext<CommandSourceStack> ctx, int n) {
        PlayerProfile t = profileArg(ctx, "joueur");
        if (t == null) return fail(ctx, "Joueur inconnu.");
        com.godmod.progress.CrateSystem.Crate c = com.godmod.progress.CrateSystem.Crate.parse(StringArgumentType.getString(ctx, "type"));
        if (c == null) return fail(ctx, "Type inconnu : commune, rare, epique, divine.");
        com.godmod.progress.CrateSystem.addKey(t, c, n);
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : " + n + " clé(s) " + c.label + " -> " + t.name);
        ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
        if (sp != null) Msg.ok(sp, "Vous recevez " + n + " clé(s) " + c.label + " ! Les caisses sont au Sanctuaire.");
        reply(ctx, n + " clé(s) " + c.label + " données à " + t.name + ".");
        return 1;
    }

    private static int mineReset(CommandContext<CommandSourceStack> ctx, boolean now) {
        String res = com.godmod.system.MineReset.schedule(ctx.getSource().getServer(), ctx.getSource().getTextName(), now);
        if (res.startsWith("!")) return fail(ctx, res.substring(1));
        reply(ctx, res);
        return 1;
    }

    // ------------------------------------------------------------------ /ile : permissions du monde divin

    private static void registerIsland(CommandDispatcher<CommandSourceStack> d) {
        SuggestionProvider<CommandSourceStack> targets = (ctx, b) -> {
            List<String> names = new ArrayList<>(List.of("fideles", "visiteurs"));
            if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) names.add(p.name);
            return SharedSuggestionProvider.suggest(names, b);
        };
        SuggestionProvider<CommandSourceStack> perms = (ctx, b) -> {
            List<String> names = new ArrayList<>();
            for (com.godmod.data.IslandPerm perm : com.godmod.data.IslandPerm.values()) names.add(perm.name().toLowerCase());
            return SharedSuggestionProvider.suggest(names, b);
        };
        d.register(Commands.literal("ile")
                .executes(ctx -> run(ctx, p -> {
                    if (!DivineWorld.hasWorld(GodData.get(p))) return "Vous n'avez pas encore de monde divin (devenez Dieu).";
                    Menus.openIsland(p);
                    return null;
                }))
                .then(Commands.literal("ajouter").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> run(ctx, p -> islandEdit(p, StringArgumentType.getString(ctx, "joueur"), null, true)))))
                .then(Commands.literal("retirer").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> run(ctx, p -> islandEdit(p, StringArgumentType.getString(ctx, "joueur"), null, false)))))
                .then(Commands.literal("perm").then(Commands.argument("cible", StringArgumentType.word()).suggests(targets)
                        .then(Commands.argument("permission", StringArgumentType.word()).suggests(perms)
                                .then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off"))
                                        .executes(ctx -> run(ctx, p -> {
                                            com.godmod.data.IslandPerm perm = com.godmod.data.IslandPerm.parse(StringArgumentType.getString(ctx, "permission"));
                                            if (perm == null) return "Permission inconnue : entrer, casser, poser, interagir, coffres.";
                                            String etat = StringArgumentType.getString(ctx, "etat");
                                            return islandEdit(p, StringArgumentType.getString(ctx, "cible"), perm, etat.equalsIgnoreCase("on") || etat.equalsIgnoreCase("oui"));
                                        }))))))
                .then(Commands.literal("pvp").then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off"))
                        .executes(ctx -> run(ctx, p -> {
                            PlayerProfile me = GodData.get(p);
                            if (!DivineWorld.hasWorld(me)) return "Vous n'avez pas de monde divin.";
                            me.islandPvp = StringArgumentType.getString(ctx, "etat").equalsIgnoreCase("on");
                            GodData.dirty();
                            Msg.ok(p, "Combat entre joueurs sur votre île : " + (me.islandPvp ? "activé." : "désactivé."));
                            return null;
                        })))));
    }

    /** perm == null : ajouter (toutes les permissions) ou retirer un joueur. */
    public static String islandEdit(ServerPlayer player, String target, com.godmod.data.IslandPerm perm, boolean on) {
        PlayerProfile me = GodData.get(player);
        if (!DivineWorld.hasWorld(me)) return "Vous n'avez pas de monde divin.";
        String key;
        String label;
        if (target.equalsIgnoreCase("fideles") || target.equalsIgnoreCase("fidèles")) {
            key = "fideles";
            label = "vos fidèles";
        } else if (target.equalsIgnoreCase("visiteurs")) {
            key = "visiteurs";
            label = "les visiteurs";
        } else {
            PlayerProfile t = GodData.byName(target);
            if (t == null) return "Joueur inconnu.";
            if (t.uuid.equals(me.uuid)) return "Vous avez déjà tous les droits sur votre île.";
            key = t.uuid;
            label = t.name;
        }
        if (perm == null) {
            if (key.equals("fideles") || key.equals("visiteurs")) return "Utilisez /ile perm " + key + " <permission> <on|off>.";
            if (on) for (com.godmod.data.IslandPerm pp : com.godmod.data.IslandPerm.values()) DivineWorld.setPerm(me, key, pp, true);
            else me.islandMembers.remove(key);
            GodData.dirty();
            Msg.ok(player, on ? label + " peut désormais construire et tout utiliser sur votre île." : label + " n'a plus aucune permission sur votre île.");
            return null;
        }
        DivineWorld.setPerm(me, key, perm, on);
        Msg.ok(player, "Permission « " + perm.label + " » " + (on ? "accordée à " : "retirée à ") + label + ".");
        return null;
    }

    // ------------------------------------------------------------------ /shop

    private static void registerShop(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("shop")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openShop(p);
                    return null;
                }))
                .then(Commands.literal("vendre")
                        .executes(ctx -> run(ctx, p -> Shop.sell(p, p.getMainHandItem(), true)))
                        .then(Commands.literal("un").executes(ctx -> run(ctx, p -> Shop.sell(p, p.getMainHandItem(), false))))));
        d.register(Commands.literal("argent").executes(ctx -> run(ctx, p -> {
            PlayerProfile pr = GodData.get(p);
            Msg.info(p, "Vous avez " + pr.money + " pièces" + (GodData.settings().payEnabled ? " et " + pr.gems + " gemmes." : "."));
            return null;
        })));
    }

    // ------------------------------------------------------------------ Rébellion, tournoi, trône, guerre

    private static void registerConflicts(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rebellion")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openRebellion(p);
                    return null;
                }))
                .then(Commands.literal("declarer").executes(ctx -> run(ctx, RebellionSystem::declare)))
                .then(Commands.literal("rejoindre").executes(ctx -> run(ctx, RebellionSystem::join))));

        d.register(Commands.literal("tournoi")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openTournament(p);
                    return null;
                }))
                .then(Commands.literal("rejoindre").executes(ctx -> run(ctx, TournamentSystem::join)))
                .then(Commands.literal("quitter").executes(ctx -> run(ctx, TournamentSystem::leave)))
                .then(Commands.literal("creer").requires(Moderation.require(Role.MODERATEUR))
                        .executes(ctx -> createTournament(ctx, 1000))
                        .then(Commands.argument("prix", LongArgumentType.longArg(0))
                                .executes(ctx -> createTournament(ctx, LongArgumentType.getLong(ctx, "prix")))))
                .then(Commands.literal("annuler").requires(Moderation.require(Role.MODERATEUR))
                        .executes(ctx -> {
                            String err = TournamentSystem.cancel(ctx.getSource().getServer());
                            if (err != null) ctx.getSource().sendFailure(Component.literal(err));
                            return 1;
                        })));

        d.register(Commands.literal("trone")
                .then(Commands.literal("reclamer")
                        .then(Commands.argument("dieu", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile god = profileArg(ctx, "dieu");
                                    if (god == null) return "Dieu inconnu.";
                                    return TournamentSystem.claimThrone(p, god);
                                })))));

        d.register(Commands.literal("guerre")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openWar(p);
                    return null;
                }))
                .then(Commands.literal("declarer")
                        .then(Commands.argument("dieu", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile god = profileArg(ctx, "dieu");
                                    if (god == null) return "Dieu inconnu.";
                                    return WarSystem.declare(p, god);
                                })))));
    }

    private static int createTournament(CommandContext<CommandSourceStack> ctx, long prize) {
        String err = TournamentSystem.create(ctx.getSource().getServer(), prize, null);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------ Administration

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pay").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("enable").executes(ctx -> setPay(ctx, true)))
                .then(Commands.literal("disable").executes(ctx -> setPay(ctx, false)))
                .then(Commands.literal("status").executes(ctx -> {
                    reply(ctx, "Fonctionnalités payantes : " + (GodData.settings().payEnabled ? "activées" : "désactivées"));
                    return 1;
                })));

        d.register(Commands.literal("godadmin").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("voir")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return fail(ctx, "Joueur inconnu.");
                                    reply(ctx, t.name + " [" + t.uuid + "]");
                                    reply(ctx, t.tier.label + " · Essence " + t.essence + " · Foi " + (int) t.faith
                                            + " · Argent " + t.money + " · Gemmes " + t.gems);
                                    reply(ctx, "Dieu : " + (t.master == null ? "aucun" : name(t.master)) + " · Fidèles "
                                            + GodData.followersOf(t.uuid).size() + "/" + GodData.totalFollowers(t.uuid)
                                            + " · Domaine " + t.specialization.label);
                                    reply(ctx, "Stats F" + t.stat(Stat.FORCE) + " V" + t.stat(Stat.VITALITE) + " A" + t.stat(Stat.AGILITE)
                                            + " E" + t.stat(Stat.ESPRIT) + " (" + PowerSystem.spentStatPoints(t) + "/" + PowerSystem.totalStatPoints(t) + ")");
                                    reply(ctx, "Tués " + t.kills + " · Morts " + t.deaths + " · Déicides " + t.godsSlain
                                            + " · Tournois " + t.tournamentsWon + " · Jeu " + Msg.time(t.playTicks * 50)
                                            + " · Vu il y a " + Msg.time(System.currentTimeMillis() - t.lastSeen));
                                    return 1;
                                })))
                .then(Commands.literal("essence")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("valeur", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            t.essence = LongArgumentType.getLong(ctx, "valeur");
                                            return "Essence de " + t.name + " = " + t.essence;
                                        })))))
                .then(Commands.literal("argent")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("valeur", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            t.money = LongArgumentType.getLong(ctx, "valeur");
                                            return "Argent de " + t.name + " = " + t.money;
                                        })))))
                .then(Commands.literal("gemmes")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("valeur", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            t.gems = LongArgumentType.getLong(ctx, "valeur");
                                            return "Gemmes de " + t.name + " = " + t.gems;
                                        })))))
                .then(Commands.literal("palier")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("palier", StringArgumentType.word())
                                        .suggests(words("MORTEL", "EVEILLE", "DEMI_DIEU", "DIEU", "DIEU_SUPREME"))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            Tier tier;
                                            try {
                                                tier = Tier.valueOf(StringArgumentType.getString(ctx, "palier").toUpperCase());
                                            } catch (IllegalArgumentException e) {
                                                return "!Palier inconnu.";
                                            }
                                            PowerSystem.setTier(ctx.getSource().getServer(), t, tier, "fixé par un admin :");
                                            return "Palier de " + t.name + " = " + tier.label;
                                        })))))
                .then(Commands.literal("maitre")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("maitre", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            String m = StringArgumentType.getString(ctx, "maitre");
                                            if (m.equalsIgnoreCase("aucun")) {
                                                t.master = null;
                                                return t.name + " est libre.";
                                            }
                                            PlayerProfile mp = GodData.byName(m);
                                            if (mp == null || mp.uuid.equals(t.uuid) || GodData.isAbove(t.uuid, mp)) return "!Maître invalide.";
                                            t.master = mp.uuid;
                                            return t.name + " est fidèle de " + mp.name + ".";
                                        })))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> adminEdit(ctx, t -> {
                                    FaithSystem.release(ctx.getSource().getServer(), t);
                                    PlayerProfile fresh = new PlayerProfile(t.uuid, t.name);
                                    fresh.plotIndex = t.plotIndex;
                                    GodData.state().players.put(t.uuid, fresh);
                                    ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                    if (sp != null) PowerSystem.applyAttributes(sp);
                                    return "Profil de " + t.name + " réinitialisé.";
                                }))))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("reconstruire").executes(ctx -> {
                            com.godmod.system.WorldBuilder.build(ctx.getSource().getServer(), ctx.getSource());
                            reply(ctx, "Reconstruction du Sanctuaire lancée (quelques secondes).");
                            return 1;
                        }))
                        .then(Commands.literal("vider").executes(ctx -> {
                            com.godmod.system.WorldBuilder.clear(ctx.getSource().getServer(), ctx.getSource());
                            reply(ctx, "Nettoyage complet du Sanctuaire lancé.");
                            return 1;
                        }))
                        .then(Commands.literal("definir").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (!com.godmod.system.Hub.isSpawn(p.level())) return fail(ctx, "Placez-vous dans le Sanctuaire (/spawn).");
                            Settings s = GodData.settings();
                            s.hubSet = true;
                            s.hubX = p.getX();
                            s.hubY = p.getY();
                            s.hubZ = p.getZ();
                            GodData.dirty();
                            reply(ctx, "Point d'apparition du Sanctuaire défini ici.");
                            return 1;
                        }))
                        .then(Commands.literal("edition").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            boolean on = com.godmod.system.Hub.toggleEditor(p);
                            reply(ctx, on ? "Mode édition du Sanctuaire ACTIVÉ : vous pouvez casser, poser et interagir."
                                    : "Mode édition du Sanctuaire désactivé : il est de nouveau protégé.");
                            return 1;
                        })))
                .then(Commands.literal("point")
                        .then(Commands.literal("placer").then(Commands.argument("type", StringArgumentType.word())
                                .suggests(words("commune", "rare", "epique", "divine", "quetes", "metiers"))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String type = com.godmod.progress.PointSystem.parse(StringArgumentType.getString(ctx, "type"));
                                    if (type == null) return fail(ctx, "Type inconnu : commune, rare, epique, divine, quetes, metiers.");
                                    var hit = lookedBlock(p);
                                    if (hit == null) return fail(ctx, "Regardez le bloc (coffre, pupitre…) à transformer, à moins de 6 blocs.");
                                    com.godmod.progress.PointSystem.set(p.level(), hit, type);
                                    reply(ctx, "Point « " + type + " » posé sur le bloc visé. Clic droit dessus pour l'utiliser.");
                                    return 1;
                                })))
                        .then(Commands.literal("retirer").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var hit = lookedBlock(p);
                            if (hit == null || !com.godmod.progress.PointSystem.remove(p.level(), hit)) return fail(ctx, "Aucun point sur le bloc visé.");
                            reply(ctx, "Point retiré.");
                            return 1;
                        })))
                .then(Commands.literal("clearile")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> adminEdit(ctx, t -> {
                                    if (t.plotIndex < 0) return "!" + t.name + " n'a pas de monde divin.";
                                    var divine = DivineWorld.level(ctx.getSource().getServer());
                                    if (divine == null) return "!Monde divin non chargé.";
                                    var core = DivineWorld.core(t);
                                    int r = DivineWorld.radius(t) + 2;
                                    String who = t.name;
                                    var src = ctx.getSource();
                                    com.godmod.system.BlockJobs.submit(divine, "Nettoyage du monde de " + who,
                                            com.godmod.system.BlockJobs.clearColumns(divine, core.getX() - r, core.getZ() - r,
                                                    core.getX() + r + 1, core.getZ() + r + 1, divine.getMinY(), divine.getMaxY(), 0),
                                            () -> {
                                                t.plotStarted = false;
                                                DivineWorld.preparePlot(divine, t);
                                                GodData.dirty();
                                                src.sendSuccess(() -> Msg.prefix().append(Msg.text("Monde de " + who + " nettoyé.", ChatFormatting.GREEN)), true);
                                            },
                                            msg -> src.sendSuccess(() -> Msg.text(msg, ChatFormatting.GRAY), false));
                                    return "Nettoyage du monde divin de " + t.name + " lancé (il est remis à son bloc de terre initial).";
                                }))))
                .then(Commands.literal("clearzone")
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(1, 96))
                                .executes(ctx -> clearZone(ctx, IntegerArgumentType.getInteger(ctx, "rayon"), 64))
                                .then(Commands.argument("hauteur", IntegerArgumentType.integer(1, 384))
                                        .executes(ctx -> clearZone(ctx, IntegerArgumentType.getInteger(ctx, "rayon"),
                                                IntegerArgumentType.getInteger(ctx, "hauteur"))))))
                .then(Commands.literal("arene").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Settings s = GodData.settings();
                    s.arenaSet = true;
                    s.arenaX = p.getX();
                    s.arenaY = p.getY();
                    s.arenaZ = p.getZ();
                    GodData.dirty();
                    reply(ctx, "Arène des tournois placée ici (les duellistes apparaissent à ±6 blocs en X).");
                    return 1;
                }))
                .then(Commands.literal("mode")
                        .then(Commands.literal("serveur").executes(ctx -> setMode(ctx, 1.0, 1.0, 72, 7)))
                        .then(Commands.literal("petit").executes(ctx -> setMode(ctx, 0.25, 2.0, 12, 2))))
                .then(Commands.literal("cooldown")
                        .then(Commands.argument("heures", IntegerArgumentType.integer(0, 24 * 30)).executes(ctx -> {
                            GodData.settings().rebellionCooldownHours = IntegerArgumentType.getInteger(ctx, "heures");
                            GodData.dirty();
                            reply(ctx, "Délai entre rébellions : " + GodData.settings().rebellionCooldownHours + " h.");
                            return 1;
                        })))
                .then(Commands.literal("inactivite")
                        .then(Commands.argument("jours", IntegerArgumentType.integer(1, 365)).executes(ctx -> {
                            GodData.settings().inactiveGodDays = IntegerArgumentType.getInteger(ctx, "jours");
                            GodData.dirty();
                            reply(ctx, "Un dieu est inactif après " + GodData.settings().inactiveGodDays + " jours.");
                            return 1;
                        })))
                .then(Commands.literal("netherend")
                        .then(Commands.literal("bloquer").executes(ctx -> setNetherEnd(ctx, true)))
                        .then(Commands.literal("autoriser").executes(ctx -> setNetherEnd(ctx, false))))
                .then(Commands.literal("journal")
                        .executes(ctx -> showLog(ctx, 15))
                        .then(Commands.argument("lignes", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> showLog(ctx, IntegerArgumentType.getInteger(ctx, "lignes"))))));
    }

    /** Efface un cylindre (rayon, hauteur centrée sur l'admin) dans le monde où il se trouve : îles, constructions… */
    private static int clearZone(CommandContext<CommandSourceStack> ctx, int radius, int height) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        var level = (net.minecraft.server.level.ServerLevel) p.level();
        var center = p.blockPosition();
        int y1 = Math.max(level.getMinY(), center.getY() - height / 2), y2 = Math.min(level.getMaxY(), center.getY() + height / 2);
        var src = ctx.getSource();
        com.godmod.system.BlockJobs.submit(level, "Nettoyage de zone", com.godmod.system.BlockJobs.clearColumns(level, center.getX() - radius, center.getZ() - radius,
                        center.getX() + radius, center.getZ() + radius, y1, y2, radius),
                () -> src.sendSuccess(() -> Msg.prefix().append(Msg.text("Zone nettoyée.", ChatFormatting.GREEN)), true),
                msg -> src.sendSuccess(() -> Msg.text(msg, ChatFormatting.GRAY), false));
        GodData.log("ADMIN " + src.getTextName() + " : clearzone r=" + radius + " h=" + height + " en " + center.getX() + " " + center.getY() + " " + center.getZ()
                + " (" + level.dimension().identifier() + ")");
        reply(ctx, "Nettoyage lancé : rayon " + radius + ", de y=" + y1 + " à y=" + y2 + ". Le travail est étalé pour ne pas faire laguer.");
        return 1;
    }

    private static net.minecraft.core.BlockPos lookedBlock(ServerPlayer p) {
        var eye = p.getEyePosition();
        var end = eye.add(p.getLookAngle().scale(6));
        var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return null;
        return ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    /** Modifie un profil. Si la fonction renvoie un texte commençant par « ! », c'est une erreur. */
    private static int adminEdit(CommandContext<CommandSourceStack> ctx, Function<PlayerProfile, String> edit) {
        PlayerProfile t = profileArg(ctx, "joueur");
        if (t == null) return fail(ctx, "Joueur inconnu (il doit s'être connecté au moins une fois).");
        String res = edit.apply(t);
        if (res.startsWith("!")) return fail(ctx, res.substring(1));
        GodData.dirty();
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : " + res);
        ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
        if (sp != null) PowerSystem.applyAttributes(sp);
        reply(ctx, res);
        return 1;
    }

    private static int setPay(CommandContext<CommandSourceStack> ctx, boolean on) {
        GodData.settings().payEnabled = on;
        GodData.dirty();
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : pay " + (on ? "enable" : "disable"));
        reply(ctx, "Fonctionnalités payantes " + (on ? "activées." : "désactivées."));
        return 1;
    }

    private static int setNetherEnd(CommandContext<CommandSourceStack> ctx, boolean block) {
        GodData.settings().disableNetherEnd = block;
        GodData.dirty();
        reply(ctx, "Nether et End " + (block ? "bloqués (items disponibles au shop)." : "accessibles."));
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx, double threshold, double essence, int cooldown, int inactive) {
        Settings s = GodData.settings();
        s.thresholdMultiplier = threshold;
        s.essenceMultiplier = essence;
        s.rebellionCooldownHours = cooldown;
        s.inactiveGodDays = inactive;
        GodData.dirty();
        reply(ctx, "Mode appliqué : seuils ×" + threshold + ", Essence ×" + essence + ", rébellion tous les " + cooldown
                + " h, inactivité " + inactive + " j.");
        return 1;
    }

    private static int showLog(CommandContext<CommandSourceStack> ctx, int n) {
        List<String> log = GodData.state().log;
        int from = Math.max(0, log.size() - n);
        reply(ctx, "Journal (" + (log.size() - from) + " dernières entrées) :");
        for (int i = from; i < log.size(); i++) {
            String line = log.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}
