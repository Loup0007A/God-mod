package com.godmod.command;

import com.godmod.data.GodData;
import com.godmod.data.Job;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.data.Settings;
import com.godmod.data.Specialization;
import com.godmod.data.Stat;
import com.godmod.data.Tier;
import com.godmod.data.Warp;
import com.godmod.menu.Menus;
import com.godmod.progress.CrateSystem;
import com.godmod.progress.DailyReward;
import com.godmod.progress.EventSystem;
import com.godmod.progress.JobSystem;
import com.godmod.system.DivineWorld;
import com.godmod.system.FaithSystem;
import com.godmod.system.Hub;
import com.godmod.system.Moderation;
import com.godmod.system.PowerActions;
import com.godmod.system.PowerSystem;
import com.godmod.system.RebellionSystem;
import com.godmod.system.Shop;
import com.godmod.system.TournamentSystem;
import com.godmod.system.TpaSystem;
import com.godmod.system.Travel;
import com.godmod.system.WarSystem;
import com.godmod.system.WarpSystem;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/** Toutes les commandes du mod. Les joueurs n'ont besoin que de /dieu : le reste est accessible depuis l'interface. */
public final class GodCommands {

    private GodCommands() {
    }

    // ------------------------------------------------------------------ Suggestions

    static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) names.add(p.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    static final SuggestionProvider<CommandSourceStack> ONLINE = (ctx, b) ->
            SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b);

    /** Joueurs en ligne sauf soi-même. */
    static final SuggestionProvider<CommandSourceStack> OTHERS = (ctx, b) -> {
        List<String> names = new ArrayList<>(Arrays.asList(ctx.getSource().getServer().getPlayerNames()));
        names.remove(ctx.getSource().getTextName());
        return SharedSuggestionProvider.suggest(names, b);
    };

    /** Fidèles directs du joueur qui tape la commande. */
    private static final SuggestionProvider<CommandSourceStack> MY_FOLLOWERS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready() && ctx.getSource().getEntity() instanceof ServerPlayer sp)
            for (PlayerProfile f : GodData.followersOf(sp.getUUID().toString())) names.add(f.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    /** Dieux (et demi-dieux à la tête d'un panthéon). */
    private static final SuggestionProvider<CommandSourceStack> GODS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) if (p.tier.isGod()) names.add(p.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    /** Joueurs qui possèdent un monde divin. */
    private static final SuggestionProvider<CommandSourceStack> WORLD_OWNERS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) if (DivineWorld.hasWorld(p)) names.add(p.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> WARPS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (Warp w : GodData.state().warps.values()) names.add(w.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    /** Warps que le joueur peut supprimer (les siens ; tous pour l'équipe). */
    private static final SuggestionProvider<CommandSourceStack> MY_WARPS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) {
            boolean staff = Moderation.roleOf(ctx.getSource()).atLeast(Role.MODERATEUR);
            String me = ctx.getSource().getEntity() == null ? "" : ctx.getSource().getEntity().getUUID().toString();
            for (Warp w : GodData.state().warps.values()) if (staff || w.owner.equals(me)) names.add(w.name);
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> ISLAND_MEMBERS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready() && ctx.getSource().getEntity() instanceof ServerPlayer sp) {
            for (String id : GodData.get(sp).islandMembers.keySet()) {
                PlayerProfile m = GodData.find(id);
                if (m != null) names.add(m.name);
            }
        }
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> TPA_PENDING = (ctx, b) -> {
        List<String> names = ctx.getSource().getEntity() instanceof ServerPlayer sp ? TpaSystem.pendingNames(sp) : List.of();
        return SharedSuggestionProvider.suggest(names, b);
    };

    static SuggestionProvider<CommandSourceStack> words(String... w) {
        return (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.asList(w), b);
    }

    // ------------------------------------------------------------------ Aides d'exécution

    /** Exécute une action joueur ; la fonction renvoie un message d'erreur ou null. */
    static int run(CommandContext<CommandSourceStack> ctx, Function<ServerPlayer, String> action) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!GodData.ready()) {
            Msg.err(player, "Le Panthéon démarre encore, réessayez dans un instant.");
            return 0;
        }
        String err = action.apply(player);
        if (err != null && !err.isEmpty()) {
            Msg.err(player, err);
            return 0;
        }
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<ServerPlayer> menu) throws CommandSyntaxException {
        return run(ctx, p -> {
            menu.accept(p);
            return null;
        });
    }

    private static PlayerProfile profileArg(CommandContext<CommandSourceStack> ctx, String arg) {
        return GodData.byName(StringArgumentType.getString(ctx, arg));
    }

    private static String unknown(CommandContext<CommandSourceStack> ctx, String arg) {
        return "Joueur « " + StringArgumentType.getString(ctx, arg) + " » inconnu (il doit s'être connecté au moins une fois).";
    }

    static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text(text, ChatFormatting.WHITE)), false);
    }

    static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Msg.prefix().append(Msg.text(msg, ChatFormatting.RED)));
        return 0;
    }

    private static boolean on(String s) {
        return s.equalsIgnoreCase("on") || s.equalsIgnoreCase("oui") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("activer");
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerHelp(d);
        registerGod(d);
        registerWorld(d);
        registerTravel(d);
        registerIsland(d);
        registerProgress(d);
        registerEvents(d);
        registerShop(d);
        registerConflicts(d);
        registerAdmin(d);
        StaffCommands.register(d);
    }

    // ------------------------------------------------------------------ /aide

    private static void registerHelp(CommandDispatcher<CommandSourceStack> d) {
        for (String name : new String[]{"aide", "commandes"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> {
                        Help.summary(ctx.getSource());
                        return 1;
                    })
                    .then(Commands.argument("theme", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Help.categoryIds(), b))
                            .executes(ctx -> {
                                if (!Help.show(ctx.getSource(), StringArgumentType.getString(ctx, "theme"))) {
                                    Help.summary(ctx.getSource());
                                    return 0;
                                }
                                return 1;
                            })));
        }
    }

    // ------------------------------------------------------------------ /dieu et /pouvoir

    private static void registerGod(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> dieu = Commands.literal("dieu")
                .executes(ctx -> open(ctx, Menus::openMain))
                .then(Commands.literal("aide").executes(ctx -> {
                    Help.summary(ctx.getSource());
                    return 1;
                }))
                .then(Commands.literal("profil")
                        .executes(ctx -> run(ctx, p -> {
                            sendProfile(p, GodData.get(p), true);
                            return null;
                        }))
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return unknown(ctx, "joueur");
                                    PlayerProfile me = GodData.get(p);
                                    boolean details = me.uuid.equals(t.uuid) || GodData.isAbove(me.uuid, t)
                                            || Moderation.roleOf(p).atLeast(Role.MODERATEUR);
                                    sendProfile(p, t, details);
                                    return null;
                                }))))
                .then(Commands.literal("stats")
                        .executes(ctx -> open(ctx, Menus::openStats))
                        .then(Commands.argument("caracteristique", StringArgumentType.word()).suggests(words("force", "vitalite", "agilite", "esprit"))
                                .executes(ctx -> upgradeStat(ctx, 1))
                                .then(Commands.argument("points", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> upgradeStat(ctx, IntegerArgumentType.getInteger(ctx, "points"))))))
                .then(Commands.literal("specialisation")
                        .executes(ctx -> open(ctx, Menus::openSpecialization))
                        .then(Commands.argument("domaine", StringArgumentType.word()).suggests(words("ocean", "ciel", "cosmos"))
                                .executes(ctx -> run(ctx, p -> {
                                    Specialization s = Specialization.parse(StringArgumentType.getString(ctx, "domaine"));
                                    if (s == null || s == Specialization.AUCUNE) return "Domaine inconnu : ocean, ciel ou cosmos.";
                                    return Menus.chooseSpecialization(p, s);
                                }))))
                .then(Commands.literal("choix")
                        .then(Commands.literal("trone").executes(ctx -> run(ctx, p -> FaithSystem.resolveThrone(p, true))))
                        .then(Commands.literal("refuser").executes(ctx -> run(ctx, p -> FaithSystem.resolveThrone(p, false)))))
                .then(Commands.literal("codex").executes(ctx -> run(ctx, p -> {
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        if (com.godmod.util.DivineItems.CODEX.equals(com.godmod.util.DivineItems.kind(p.getInventory().getItem(i))))
                            return "Vous avez déjà un Codex divin (case 9 de la barre d'outils).";
                    }
                    Shop.give(p, com.godmod.util.DivineItems.codex());
                    Msg.ok(p, "Un nouveau Codex divin vous a été remis.");
                    return null;
                })))
                .then(Commands.literal("fideles").executes(ctx -> open(ctx, p -> Menus.openFollowers(p, 0))))
                .then(Commands.literal("liberer")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(MY_FOLLOWERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return unknown(ctx, "joueur");
                                    String err = FaithSystem.releaseFollower(p, t);
                                    if (err == null) Msg.ok(p, t.name + " est libéré.");
                                    return err;
                                }))))
                .then(powerTree(Commands.literal("pouvoir")));
        d.register(dieu);
        d.register(powerTree(Commands.literal("pouvoir")));
        d.register(powerTree(Commands.literal("pouvoirs")));
        d.register(Commands.literal("pantheon").executes(ctx -> open(ctx, Menus::openMain)));
        d.register(Commands.literal("profil")
                .executes(ctx -> run(ctx, p -> {
                    sendProfile(p, GodData.get(p), true);
                    return null;
                }))
                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> run(ctx, p -> {
                            PlayerProfile t = profileArg(ctx, "joueur");
                            if (t == null) return unknown(ctx, "joueur");
                            PlayerProfile me = GodData.get(p);
                            sendProfile(p, t, me.uuid.equals(t.uuid) || GodData.isAbove(me.uuid, t) || Moderation.roleOf(p).atLeast(Role.MODERATEUR));
                            return null;
                        }))));
    }

    private static int upgradeStat(CommandContext<CommandSourceStack> ctx, int points) throws CommandSyntaxException {
        return run(ctx, p -> {
            Stat s = Stat.parse(StringArgumentType.getString(ctx, "caracteristique"));
            if (s == null) return "Caractéristique inconnue : force, vitalite, agilite, esprit.";
            int done = 0;
            String err = null;
            for (int i = 0; i < points; i++) {
                err = PowerSystem.upgradeStat(p, s);
                if (err != null) break;
                done++;
            }
            if (done > 0) {
                Msg.ok(p, s.label + " +" + done + " → niveau " + GodData.get(p).stat(s) + " (" + PowerSystem.freeStatPoints(GodData.get(p)) + " point(s) restant(s)).");
                return null;
            }
            return err;
        });
    }

    /** Arbre commun à /pouvoir et /dieu pouvoir : un sous-mot par pouvoir, avec la bonne cible. */
    private static LiteralArgumentBuilder<CommandSourceStack> powerTree(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.executes(ctx -> open(ctx, Menus::openPowers));
        for (PowerActions.Power pw : PowerActions.Power.values()) {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(pw.name().toLowerCase());
            switch (pw) {
                case CHATIER -> node.executes(ctx -> open(ctx, p -> Menus.openFollowers(p, 0)))
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(MY_FOLLOWERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return unknown(ctx, "joueur");
                                    PowerActions.smite(p, t);
                                    return null;
                                })));
                case TEMPS -> node.executes(ctx -> open(ctx, Menus::openTime))
                        .then(Commands.argument("mode", StringArgumentType.word()).suggests(words("jour", "nuit", "pluie", "orage", "clair"))
                                .executes(ctx -> run(ctx, p -> {
                                    String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
                                    if (!List.of("jour", "nuit", "pluie", "orage", "clair").contains(mode)) return "Mode inconnu : jour, nuit, pluie, orage ou clair.";
                                    PowerActions.time(p, mode);
                                    return null;
                                })));
                case ESPACE -> node.executes(ctx -> open(ctx, Menus::openSpace))
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(OTHERS)
                                .executes(ctx -> run(ctx, p -> {
                                    ServerPlayer t = p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur"));
                                    if (t == null) return "Joueur introuvable ou hors ligne.";
                                    if (t == p) return "Vous êtes déjà ici.";
                                    PowerActions.space(p, t);
                                    return null;
                                })));
                default -> node.executes(ctx -> run(ctx, p -> {
                    PowerActions.cast(p, pw);
                    return null;
                }));
            }
            root.then(node);
        }
        return root;
    }

    private static void sendProfile(ServerPlayer viewer, PlayerProfile t, boolean details) {
        viewer.sendSystemMessage(Msg.header(t.name));
        Tier next = t.tier.next();
        viewer.sendSystemMessage(Msg.text("Palier : ", ChatFormatting.GRAY).append(Component.literal(t.tier.label).withStyle(t.tier.color, ChatFormatting.BOLD))
                .append(Msg.text(next == null ? "  (maximum)" : "  → " + next.label + " à " + Msg.num(PowerSystem.threshold(next)) + " Essence", ChatFormatting.DARK_GRAY)));
        viewer.sendSystemMessage(Msg.text("Puissance " + Msg.num(PowerSystem.powerScore(t)) + " · Fidèles " + GodData.totalFollowers(t.uuid)
                + " · Dieu : " + (t.master == null ? "aucun (libre)" : name(t.master)), ChatFormatting.GRAY));
        Job best = JobSystem.best(t);
        viewer.sendSystemMessage(Msg.text("Métier principal : " + best.icon + " " + best.label + " " + JobSystem.level(t, best)
                + " · " + JobSystem.totalLevels(t) + " niveaux au total · " + t.questsDone + " quête(s) accomplie(s)", ChatFormatting.GRAY));
        if (details) {
            viewer.sendSystemMessage(Msg.text("Essence " + Msg.num(t.essence) + " · Foi " + (int) t.faith + "/" + (int) t.maxFaith()
                    + " · " + Msg.money(t.money) + (GodData.settings().payEnabled ? " · " + t.gems + " gemmes" : "")
                    + " · " + CrateSystem.totalKeys(t) + " clé(s)", ChatFormatting.GRAY));
            viewer.sendSystemMessage(Msg.text("Force " + t.stat(Stat.FORCE) + " · Vitalité " + t.stat(Stat.VITALITE)
                    + " · Agilité " + t.stat(Stat.AGILITE) + " · Esprit " + t.stat(Stat.ESPRIT)
                    + " (" + PowerSystem.freeStatPoints(t) + " libre(s)) · Domaine " + t.specialization.label, ChatFormatting.GRAY));
        }
        viewer.sendSystemMessage(Msg.text("Victoires " + t.kills + " · Morts " + t.deaths + " · Déicides " + t.godsSlain
                + " · Tournois " + t.tournamentsWon + " · Événements " + t.eventsWon + " · Série quotidienne " + t.dailyStreak, ChatFormatting.DARK_GRAY));
        if (DivineWorld.hasWorld(t) && !t.uuid.equals(viewer.getUUID().toString())) {
            viewer.sendSystemMessage(Msg.text("", ChatFormatting.GRAY).append(Msg.button("Visiter son monde", "/monde visiter " + t.name, "Si vous y êtes autorisé", ChatFormatting.GREEN)));
        }
    }

    private static String name(String uuid) {
        PlayerProfile p = GodData.find(uuid);
        return p == null ? "?" : p.name;
    }

    // ------------------------------------------------------------------ /monde

    private static void registerWorld(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("monde")
                .executes(ctx -> run(ctx, p -> {
                    PlayerProfile pr = GodData.get(p);
                    if (!DivineWorld.hasWorld(pr)) return DivineWorld.sendHome(p);
                    return Travel.start(p, "Vers votre monde divin", () -> DivineWorld.sendHome(p));
                }))
                .then(Commands.literal("quitter").executes(ctx -> run(ctx, p -> {
                    if (!DivineWorld.isDivine(p.level())) return "Vous n'êtes pas dans un monde divin.";
                    return Travel.startRun(p, "Vers le Sanctuaire", () -> DivineWorld.leave(p));
                })))
                .then(Commands.literal("visiter")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(WORLD_OWNERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return unknown(ctx, "joueur");
                                    if (!DivineWorld.hasWorld(t)) return t.name + " ne possède pas de monde divin.";
                                    if (!DivineWorld.canEnter(GodData.get(p), t)) return "Vous n'avez pas accès au monde de " + t.name + ".";
                                    return Travel.start(p, "Vers le monde de " + t.name, () -> DivineWorld.visit(p, t));
                                })))));
    }

    // ------------------------------------------------------------------ Spawn, monde ressource, warps, tpa

    private static void registerTravel(CommandDispatcher<CommandSourceStack> d) {
        for (String name : new String[]{"spawn", "sanctuaire", "hub"}) {
            d.register(Commands.literal(name).executes(ctx -> run(ctx, p -> Travel.startRun(p, "Vers le Sanctuaire", () -> {
                Hub.teleport(p);
                Msg.ok(p, "Bienvenue au Sanctuaire.");
            }))));
        }
        for (String name : new String[]{"ressources", "minage"}) {
            d.register(Commands.literal(name).executes(ctx -> run(ctx, p -> Travel.start(p, "Vers le monde ressource", () -> Hub.toMine(p)))));
        }

        d.register(Commands.literal("setwarp").then(Commands.argument("nom", StringArgumentType.word())
                .executes(ctx -> run(ctx, p -> WarpSystem.set(p, StringArgumentType.getString(ctx, "nom"))))));
        d.register(Commands.literal("warp")
                .executes(ctx -> open(ctx, p -> Menus.openWarps(p, 0)))
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(WARPS)
                        .executes(ctx -> run(ctx, p -> goWarp(p, StringArgumentType.getString(ctx, "nom"))))));
        d.register(Commands.literal("warps").executes(ctx -> open(ctx, p -> Menus.openWarps(p, 0))));
        d.register(Commands.literal("delwarp").then(Commands.argument("nom", StringArgumentType.word()).suggests(MY_WARPS)
                .executes(ctx -> run(ctx, p -> WarpSystem.delete(p, StringArgumentType.getString(ctx, "nom"))))));

        d.register(Commands.literal("tpa").then(Commands.argument("joueur", StringArgumentType.word()).suggests(OTHERS)
                .executes(ctx -> run(ctx, p -> tpa(ctx, p, false)))));
        d.register(Commands.literal("tpahere").then(Commands.argument("joueur", StringArgumentType.word()).suggests(OTHERS)
                .executes(ctx -> run(ctx, p -> tpa(ctx, p, true)))));
        d.register(Commands.literal("tpaccept")
                .executes(ctx -> run(ctx, p -> TpaSystem.accept(p, null)))
                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(TPA_PENDING)
                        .executes(ctx -> run(ctx, p -> TpaSystem.accept(p, StringArgumentType.getString(ctx, "joueur"))))));
        d.register(Commands.literal("tpdeny")
                .executes(ctx -> run(ctx, p -> TpaSystem.deny(p, null)))
                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(TPA_PENDING)
                        .executes(ctx -> run(ctx, p -> TpaSystem.deny(p, StringArgumentType.getString(ctx, "joueur"))))));
        d.register(Commands.literal("tpatoggle").executes(ctx -> run(ctx, TpaSystem::toggle)));
    }

    /** Warp avec délai : on vérifie d'abord qu'il existe, pour ne pas faire attendre pour rien. */
    public static String goWarp(ServerPlayer p, String name) {
        Warp w = GodData.state().warps.get(name.toLowerCase());
        if (w == null) return "Warp « " + name + " » inconnu. Liste : /warps";
        return Travel.start(p, "Vers le warp " + w.name, () -> WarpSystem.go(p, w.name));
    }

    private static String tpa(CommandContext<CommandSourceStack> ctx, ServerPlayer p, boolean here) {
        ServerPlayer t = p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur"));
        if (t == null) return "Joueur introuvable ou hors ligne.";
        return TpaSystem.request(p, t, here);
    }

    // ------------------------------------------------------------------ Progression : métiers, quêtes, caisses, HUD, récompense

    private static void registerProgress(CommandDispatcher<CommandSourceStack> d) {
        for (String name : new String[]{"metiers", "metier", "jobs"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> open(ctx, Menus::openJobs))
                    .then(Commands.argument("metier", StringArgumentType.word()).suggests(words("mineur", "bucheron", "fermier", "chasseur", "pecheur"))
                            .executes(ctx -> run(ctx, p -> {
                                Job job = Job.parse(StringArgumentType.getString(ctx, "metier"));
                                if (job == null) job = Job.parse(com.godmod.progress.EventSystem.normalize(StringArgumentType.getString(ctx, "metier")).toUpperCase());
                                if (job == null) return "Métier inconnu : mineur, bucheron, fermier, chasseur, pecheur.";
                                Menus.openJobDetail(p, job);
                                return null;
                            }))));
        }
        for (String name : new String[]{"quetes", "quete", "quests"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> open(ctx, Menus::openQuests))
                    .then(Commands.literal("renouveler").executes(ctx -> run(ctx, com.godmod.progress.QuestSystem::reroll)))
                    .then(Commands.literal("semaine").executes(ctx -> open(ctx, Menus::openWeekly)))
                    .then(Commands.literal("ascension").executes(ctx -> open(ctx, com.godmod.progress.AscensionQuests::open)))
                    .then(Commands.literal("liste").executes(ctx -> run(ctx, p -> {
                        PlayerProfile pr = GodData.get(p);
                        com.godmod.progress.QuestSystem.refreshOffers(pr);
                        p.sendSystemMessage(Msg.header("Vos quêtes"));
                        if (pr.quests.isEmpty()) p.sendSystemMessage(Msg.text("Aucune quête en cours. ", ChatFormatting.GRAY)
                                .append(Msg.button("Choisir", "/quetes", "Ouvrir les quêtes", ChatFormatting.YELLOW)));
                        for (var qp : pr.quests) {
                            var q = com.godmod.progress.QuestSystem.QUESTS.get(qp.id);
                            if (q != null) p.sendSystemMessage(Msg.text(q.difficulty().stars + " " + q.title(), q.difficulty().color)
                                    .append(Msg.text(" — " + qp.progress + "/" + q.amount() + " · " + q.description(), ChatFormatting.GRAY)));
                        }
                        for (var qp : pr.weekly) {
                            var q = com.godmod.progress.QuestSystem.WEEKLIES.get(qp.id);
                            if (q != null) p.sendSystemMessage(Msg.text("✦ " + q.title(), qp.done ? ChatFormatting.GREEN : ChatFormatting.AQUA)
                                    .append(Msg.text(" — " + (qp.done ? "relevé" : qp.progress + "/" + q.amount()) + " · " + q.description(), ChatFormatting.GRAY)));
                        }
                        var step = com.godmod.progress.AscensionQuests.current(pr);
                        if (step != null) p.sendSystemMessage(Msg.text("Ascension : " + step.title(), ChatFormatting.LIGHT_PURPLE)
                                .append(Msg.text(" — " + step.goal() + " (" + com.godmod.progress.AscensionQuests.progress(pr, step) + "/" + step.target() + ")", ChatFormatting.GRAY)));
                        return null;
                    }))));
        }
        d.register(Commands.literal("ascension").executes(ctx -> open(ctx, com.godmod.progress.AscensionQuests::open)));
        for (String name : new String[]{"caisses", "cles", "keys"}) d.register(Commands.literal(name).executes(ctx -> open(ctx, Menus::openKeys)));
        for (String name : new String[]{"progression", "guide"}) d.register(Commands.literal(name).executes(ctx -> open(ctx, Menus::openGuide)));
        for (String name : new String[]{"recompense", "daily", "quotidien"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> run(ctx, p -> {
                        if (!DailyReward.available(GodData.get(p))) {
                            DailyReward.open(p);
                            return null;
                        }
                        return DailyReward.claim(p);
                    }))
                    .then(Commands.literal("calendrier").executes(ctx -> open(ctx, DailyReward::open))));
        }
        d.register(Commands.literal("hud")
                .executes(ctx -> run(ctx, p -> setHud(p, !GodData.get(p).hud)))
                .then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off"))
                        .executes(ctx -> run(ctx, p -> setHud(p, on(StringArgumentType.getString(ctx, "etat")))))));

        // Clés de caisse (admins)
        d.register(Commands.literal("cle").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("donner").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(words("commune", "rare", "epique", "divine"))
                                .executes(ctx -> giveKey(ctx, 1))
                                .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 1000))
                                        .executes(ctx -> giveKey(ctx, IntegerArgumentType.getInteger(ctx, "nombre")))))))
                .then(Commands.literal("tous").then(Commands.argument("type", StringArgumentType.word()).suggests(words("commune", "rare", "epique", "divine"))
                        .executes(ctx -> giveKeyAll(ctx, 1))
                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> giveKeyAll(ctx, IntegerArgumentType.getInteger(ctx, "nombre")))))));

        // Monde ressource
        d.register(Commands.literal("mine")
                .executes(ctx -> run(ctx, p -> Travel.start(p, "Vers le monde ressource", () -> Hub.toMine(p))))
                .then(Commands.literal("reset").requires(Moderation.require(Role.ADMIN))
                        .executes(ctx -> mineReset(ctx, false))
                        .then(Commands.literal("maintenant").executes(ctx -> mineReset(ctx, true)))
                        .then(Commands.literal("annuler").executes(ctx -> {
                            if (!com.godmod.system.MineReset.cancel(ctx.getSource().getServer())) return fail(ctx, "Aucune réinitialisation programmée.");
                            reply(ctx, "Réinitialisation du monde ressource annulée.");
                            return 1;
                        }))));

        // Équipe : /modo ajouter|admin|retirer|liste
        d.register(Commands.literal("modo").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> open(ctx, Menus::openTeam))
                .then(Commands.literal("ajouter").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> setRole(ctx, Role.MODERATEUR))))
                .then(Commands.literal("admin").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> setRole(ctx, Role.ADMIN))))
                .then(Commands.literal("retirer").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> setRole(ctx, Role.JOUEUR))))
                .then(Commands.literal("liste").executes(ctx -> {
                    StaffCommands.listTeam(ctx.getSource());
                    return 1;
                })));
    }

    private static String setHud(ServerPlayer p, boolean show) {
        PlayerProfile pr = GodData.get(p);
        pr.hud = show;
        GodData.dirty();
        if (!show) com.godmod.progress.Hud.hide(p);
        Msg.ok(p, "Affichage de la progression " + (show ? "activé." : "masqué. (/hud pour le réafficher)"));
        return null;
    }

    private static int setRole(CommandContext<CommandSourceStack> ctx, Role role) {
        PlayerProfile t = profileArg(ctx, "joueur");
        if (t == null) return fail(ctx, unknown(ctx, "joueur"));
        Role current = t.role == null ? Role.JOUEUR : t.role;
        if (current == role) return fail(ctx, t.name + " est déjà " + role.label + ".");
        if (ctx.getSource().getEntity() instanceof ServerPlayer sp && sp.getUUID().toString().equals(t.uuid) && role != Role.ADMIN)
            return fail(ctx, "Vous ne pouvez pas vous rétrograder vous-même.");
        reply(ctx, Moderation.setRole(ctx.getSource().getServer(), t, role, ctx.getSource().getTextName()));
        return 1;
    }

    private static int giveKey(CommandContext<CommandSourceStack> ctx, int n) {
        PlayerProfile t = profileArg(ctx, "joueur");
        if (t == null) return fail(ctx, unknown(ctx, "joueur"));
        CrateSystem.Crate c = CrateSystem.Crate.parse(StringArgumentType.getString(ctx, "type"));
        if (c == null) return fail(ctx, "Type inconnu : commune, rare, epique, divine.");
        CrateSystem.addKey(t, c, n);
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : " + n + " clé(s) " + c.label + " -> " + t.name);
        ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
        if (sp != null) Msg.ok(sp, "Vous recevez " + n + " clé(s) " + c.label + " ! Les caisses sont au Sanctuaire.");
        reply(ctx, n + " clé(s) " + c.label + " données à " + t.name + ".");
        return 1;
    }

    private static int giveKeyAll(CommandContext<CommandSourceStack> ctx, int n) {
        CrateSystem.Crate c = CrateSystem.Crate.parse(StringArgumentType.getString(ctx, "type"));
        if (c == null) return fail(ctx, "Type inconnu : commune, rare, epique, divine.");
        int count = 0;
        for (ServerPlayer sp : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            CrateSystem.addKey(GodData.get(sp), c, n);
            Msg.ok(sp, "Cadeau de l'équipe : " + n + " clé(s) " + c.label + " !");
            count++;
        }
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : " + n + " clé(s) " + c.label + " à tous (" + count + " joueurs)");
        reply(ctx, n + " clé(s) " + c.label + " données aux " + count + " joueur(s) connecté(s).");
        return 1;
    }

    private static int mineReset(CommandContext<CommandSourceStack> ctx, boolean now) {
        String res = com.godmod.system.MineReset.schedule(ctx.getSource().getServer(), ctx.getSource().getTextName(), now);
        if (res.startsWith("!")) return fail(ctx, res.substring(1));
        reply(ctx, res);
        return 1;
    }

    // ------------------------------------------------------------------ /event

    private static void registerEvents(CommandDispatcher<CommandSourceStack> d) {
        SuggestionProvider<CommandSourceStack> types = (c, b) -> SharedSuggestionProvider.suggest(EventSystem.typeNames(), b);
        for (String name : new String[]{"event", "events", "evenement"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> run(ctx, p -> {
                        EventSystem.info(p);
                        return null;
                    }))
                    .then(Commands.literal("rejoindre").executes(ctx -> run(ctx, EventSystem::join)))
                    .then(Commands.literal("liste").executes(ctx -> {
                        ctx.getSource().sendSystemMessage(Msg.header("Événements possibles"));
                        for (var e : EventSystem.catalog().entrySet())
                            ctx.getSource().sendSystemMessage(Msg.text(e.getKey(), ChatFormatting.YELLOW).append(Msg.text(" — " + e.getValue(), ChatFormatting.GRAY)));
                        return 1;
                    }))
                    .then(Commands.literal("lancer").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("type", StringArgumentType.word()).suggests(types).executes(ctx -> {
                                EventSystem.Type t = EventSystem.Type.parse(StringArgumentType.getString(ctx, "type"));
                                if (t == null) return fail(ctx, "Type inconnu. /event liste");
                                String err = EventSystem.start(ctx.getSource().getServer(), t, ctx.getSource().getTextName());
                                if (err != null) return fail(ctx, err);
                                reply(ctx, "Événement « " + t.label + " » lancé.");
                                return 1;
                            })))
                    .then(Commands.literal("stop").requires(Moderation.require(Role.ADMIN)).executes(ctx -> {
                        if (EventSystem.current() == null) return fail(ctx, "Aucun événement en cours.");
                        EventSystem.end(ctx.getSource().getServer(), "arrêté par " + ctx.getSource().getTextName() + ".");
                        return 1;
                    }))
                    .then(Commands.literal("auto").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> {
                                GodData.settings().eventsAuto = on(StringArgumentType.getString(ctx, "etat"));
                                GodData.dirty();
                                reply(ctx, "Événements automatiques " + (GodData.settings().eventsAuto ? "activés (toutes les "
                                        + GodData.settings().eventIntervalMinutes + " min)." : "désactivés."));
                                return 1;
                            })))
                    .then(Commands.literal("intervalle").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("minutes", IntegerArgumentType.integer(5, 24 * 60)).executes(ctx -> {
                                GodData.settings().eventIntervalMinutes = IntegerArgumentType.getInteger(ctx, "minutes");
                                GodData.dirty();
                                reply(ctx, "Un événement automatique toutes les " + GodData.settings().eventIntervalMinutes + " minutes (à partir du prochain).");
                                return 1;
                            })))
                    .then(Commands.literal("joueurs").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("minimum", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
                                GodData.settings().eventMinPlayers = IntegerArgumentType.getInteger(ctx, "minimum");
                                GodData.dirty();
                                reply(ctx, "Événements automatiques à partir de " + GodData.settings().eventMinPlayers + " joueur(s) connecté(s).");
                                return 1;
                            }))));
        }
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
                .then(Commands.literal("retirer").then(Commands.argument("joueur", StringArgumentType.word()).suggests(ISLAND_MEMBERS)
                        .executes(ctx -> run(ctx, p -> islandEdit(p, StringArgumentType.getString(ctx, "joueur"), null, false)))))
                .then(Commands.literal("membres").executes(ctx -> run(ctx, p -> {
                    PlayerProfile me = GodData.get(p);
                    if (!DivineWorld.hasWorld(me)) return "Vous n'avez pas de monde divin.";
                    p.sendSystemMessage(Msg.header("Membres de votre île"));
                    if (me.islandMembers.isEmpty()) p.sendSystemMessage(Msg.text("Aucun membre. /ile ajouter <joueur>", ChatFormatting.GRAY));
                    for (var e : me.islandMembers.entrySet()) {
                        PlayerProfile m = GodData.find(e.getKey());
                        p.sendSystemMessage(Msg.text((m == null ? "?" : m.name) + " : ", ChatFormatting.YELLOW)
                                .append(Msg.text(e.getValue().isEmpty() ? "aucune permission" : String.join(", ", e.getValue()).toLowerCase(), ChatFormatting.GRAY)));
                    }
                    p.sendSystemMessage(Msg.text("Fidèles : " + String.join(", ", me.islandFollowerPerms).toLowerCase()
                            + " · Visiteurs : " + (me.islandVisitorPerms.isEmpty() ? "aucune" : String.join(", ", me.islandVisitorPerms).toLowerCase())
                            + " · PvP : " + (me.islandPvp ? "oui" : "non"), ChatFormatting.DARK_GRAY));
                    return null;
                })))
                .then(Commands.literal("perm").then(Commands.argument("cible", StringArgumentType.word()).suggests(targets)
                        .then(Commands.argument("permission", StringArgumentType.word()).suggests(perms)
                                .then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off"))
                                        .executes(ctx -> run(ctx, p -> {
                                            com.godmod.data.IslandPerm perm = com.godmod.data.IslandPerm.parse(StringArgumentType.getString(ctx, "permission"));
                                            if (perm == null) return "Permission inconnue : entrer, casser, poser, interagir, coffres.";
                                            return islandEdit(p, StringArgumentType.getString(ctx, "cible"), perm, on(StringArgumentType.getString(ctx, "etat")));
                                        }))))))
                .then(Commands.literal("pvp").then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off"))
                        .executes(ctx -> run(ctx, p -> {
                            PlayerProfile me = GodData.get(p);
                            if (!DivineWorld.hasWorld(me)) return "Vous n'avez pas de monde divin.";
                            me.islandPvp = on(StringArgumentType.getString(ctx, "etat"));
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
            if (t == null) return "Joueur « " + target + " » inconnu.";
            if (t.uuid.equals(me.uuid)) return "Vous avez déjà tous les droits sur votre île.";
            key = t.uuid;
            label = t.name;
        }
        if (perm == null) {
            if (key.equals("fideles") || key.equals("visiteurs")) return "Utilisez /ile perm " + key + " <permission> <on|off>.";
            if (!on && !me.islandMembers.containsKey(key)) return label + " n'est pas membre de votre île.";
            if (on) for (com.godmod.data.IslandPerm pp : com.godmod.data.IslandPerm.values()) DivineWorld.setPerm(me, key, pp, true);
            else me.islandMembers.remove(key);
            GodData.dirty();
            Msg.ok(player, on ? label + " peut désormais construire et tout utiliser sur votre île." : label + " n'a plus aucune permission sur votre île.");
            ServerPlayer sp = PowerSystem.online(player.level().getServer(), key);
            if (sp != null) Msg.info(sp, on ? player.getName().getString() + " vous a ajouté à son île (/monde visiter " + player.getName().getString() + ")."
                    : player.getName().getString() + " vous a retiré de son île.");
            return null;
        }
        DivineWorld.setPerm(me, key, perm, on);
        Msg.ok(player, "Permission « " + perm.label + " » " + (on ? "accordée à " : "retirée à ") + label + ".");
        return null;
    }

    // ------------------------------------------------------------------ /shop, /argent, /payer, /baltop

    private static void registerShop(CommandDispatcher<CommandSourceStack> d) {
        for (String name : new String[]{"shop", "boutique"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> open(ctx, Menus::openShop))
                    .then(Commands.literal("vendre")
                            .executes(ctx -> run(ctx, p -> Shop.sell(p, p.getMainHandItem(), true)))
                            .then(Commands.literal("un").executes(ctx -> run(ctx, p -> Shop.sell(p, p.getMainHandItem(), false))))
                            .then(Commands.literal("tout").executes(ctx -> run(ctx, p -> Shop.sellAll(p, p.getMainHandItem()))))
                            .then(Commands.literal("menu").executes(ctx -> open(ctx, Menus::openSell))))
                    .then(Commands.literal("prix").executes(ctx -> run(ctx, p -> {
                        var stack = p.getMainHandItem();
                        if (stack.isEmpty()) return "Tenez un objet en main pour connaître son prix.";
                        long sell = Shop.sellPrice(stack.getItem()), buy = Shop.buyPrice(stack.getItem());
                        Msg.info(p, stack.getHoverName().getString() + " — achat : " + (buy > 0 ? Msg.money(buy) : "non vendu")
                                + " · revente : " + (sell > 0 ? Msg.money(sell) + " (la pile : " + Msg.money(sell * stack.getCount()) + ")" : "non racheté"));
                        return null;
                    }))));
        }
        for (String name : new String[]{"argent", "money", "solde"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> run(ctx, p -> {
                        PlayerProfile pr = GodData.get(p);
                        Msg.info(p, "Solde : " + Msg.money(pr.money) + (GodData.settings().payEnabled ? " · " + pr.gems + " gemmes" : "")
                                + " · " + CrateSystem.totalKeys(pr) + " clé(s) de caisse");
                        return null;
                    }))
                    .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS).requires(Moderation.require(Role.MODERATEUR))
                            .executes(ctx -> {
                                PlayerProfile t = profileArg(ctx, "joueur");
                                if (t == null) return fail(ctx, unknown(ctx, "joueur"));
                                reply(ctx, t.name + " : " + Msg.money(t.money) + " · " + t.gems + " gemmes · " + CrateSystem.totalKeys(t) + " clé(s)");
                                return 1;
                            })));
        }
        for (String name : new String[]{"payer", "virement"}) {
            d.register(Commands.literal(name).then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                    .then(Commands.argument("montant", LongArgumentType.longArg(1, 1_000_000_000L))
                            .executes(ctx -> run(ctx, p -> pay(ctx, p))))));
        }
        d.register(Commands.literal("baltop").executes(ctx -> {
            ranking(ctx.getSource(), "Les plus riches", t -> t.money, v -> Msg.money(v));
            return 1;
        }));
        d.register(Commands.literal("classement")
                .executes(ctx -> open(ctx, Menus::openRanking))
                .then(Commands.literal("puissance").executes(ctx -> {
                    ranking(ctx.getSource(), "Puissance", PowerSystem::powerScore, Msg::num);
                    return 1;
                }))
                .then(Commands.literal("argent").executes(ctx -> {
                    ranking(ctx.getSource(), "Les plus riches", t -> t.money, Msg::money);
                    return 1;
                }))
                .then(Commands.literal("metiers").executes(ctx -> {
                    ranking(ctx.getSource(), "Métiers (niveaux cumulés)", t -> JobSystem.totalLevels(t), v -> v + " niveaux");
                    return 1;
                }))
                .then(Commands.literal("quetes").executes(ctx -> {
                    ranking(ctx.getSource(), "Quêtes accomplies", t -> t.questsDone, v -> v + " quêtes");
                    return 1;
                }))
                .then(Commands.literal("evenements").executes(ctx -> {
                    ranking(ctx.getSource(), "Événements gagnés", t -> t.eventsWon, v -> v + " victoire(s)");
                    return 1;
                }))
                .then(Commands.literal("fideles").executes(ctx -> {
                    ranking(ctx.getSource(), "Fidèles", t -> GodData.totalFollowers(t.uuid), v -> v + " fidèle(s)");
                    return 1;
                })));
    }

    private static String pay(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        PlayerProfile me = GodData.get(p);
        PlayerProfile t = profileArg(ctx, "joueur");
        long amount = LongArgumentType.getLong(ctx, "montant");
        if (t == null) return unknown(ctx, "joueur");
        if (t.uuid.equals(me.uuid)) return "Vous ne pouvez pas vous payer vous-même.";
        if (me.money < amount) return "Solde insuffisant : vous avez " + Msg.money(me.money) + ".";
        me.money -= amount;
        t.money += amount;
        GodData.dirty();
        GodData.log("PAIEMENT : " + me.name + " -> " + t.name + " : " + amount);
        Msg.ok(p, "Vous avez donné " + Msg.money(amount) + " à " + t.name + ". Nouveau solde : " + Msg.money(me.money) + ".");
        ServerPlayer sp = PowerSystem.online(p.level().getServer(), t.uuid);
        if (sp != null) {
            Msg.ok(sp, me.name + " vous a donné " + Msg.money(amount) + " !");
            com.godmod.util.Fx.sound(sp, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 1.2F);
        }
        return null;
    }

    private static void ranking(CommandSourceStack src, String title, ToLongFunction<PlayerProfile> score, java.util.function.LongFunction<String> fmt) {
        List<PlayerProfile> all = new ArrayList<>(GodData.state().players.values());
        all.sort(Comparator.comparingLong(score).reversed());
        src.sendSystemMessage(Msg.header("Classement — " + title));
        String me = src.getEntity() == null ? "" : src.getEntity().getUUID().toString();
        ChatFormatting[] podium = {ChatFormatting.GOLD, ChatFormatting.WHITE, ChatFormatting.RED};
        boolean shownMe = false;
        for (int i = 0; i < Math.min(10, all.size()); i++) {
            PlayerProfile t = all.get(i);
            boolean mine = t.uuid.equals(me);
            shownMe |= mine;
            MutableComponent line = Msg.text((i + 1) + ". ", i < 3 ? podium[i] : ChatFormatting.GRAY)
                    .append(Component.literal(t.name).withStyle(mine ? ChatFormatting.GREEN : t.tier.color))
                    .append(Msg.text(" — " + fmt.apply(score.applyAsLong(t)), ChatFormatting.GRAY));
            src.sendSystemMessage(line);
        }
        if (!shownMe && !me.isEmpty()) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).uuid.equals(me)) {
                    src.sendSystemMessage(Msg.text("…  " + (i + 1) + ". vous — " + fmt.apply(score.applyAsLong(all.get(i))), ChatFormatting.GREEN));
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ Rébellion, tournoi, trône, guerre

    private static void registerConflicts(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rebellion")
                .executes(ctx -> open(ctx, Menus::openRebellion))
                .then(Commands.literal("declarer").executes(ctx -> run(ctx, RebellionSystem::declare)))
                .then(Commands.literal("rejoindre").executes(ctx -> run(ctx, RebellionSystem::join))));

        d.register(Commands.literal("tournoi")
                .executes(ctx -> open(ctx, Menus::openTournament))
                .then(Commands.literal("rejoindre").executes(ctx -> run(ctx, TournamentSystem::join)))
                .then(Commands.literal("quitter").executes(ctx -> run(ctx, TournamentSystem::leave)))
                .then(Commands.literal("creer").requires(Moderation.require(Role.MODERATEUR))
                        .executes(ctx -> createTournament(ctx, 1000))
                        .then(Commands.argument("prix", LongArgumentType.longArg(0))
                                .executes(ctx -> createTournament(ctx, LongArgumentType.getLong(ctx, "prix")))))
                .then(Commands.literal("annuler").requires(Moderation.require(Role.MODERATEUR))
                        .executes(ctx -> {
                            String err = TournamentSystem.cancel(ctx.getSource().getServer());
                            if (err != null) return fail(ctx, err);
                            reply(ctx, "Tournoi annulé.");
                            return 1;
                        })));

        d.register(Commands.literal("trone")
                .executes(ctx -> run(ctx, p -> {
                    Msg.info(p, "Un dieu absent depuis " + GodData.settings().inactiveGodDays + " jours peut être détrôné : /trone reclamer <dieu> lance un tournoi pour son trône.");
                    return null;
                }))
                .then(Commands.literal("reclamer")
                        .then(Commands.argument("dieu", StringArgumentType.word()).suggests(GODS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile god = profileArg(ctx, "dieu");
                                    if (god == null) return unknown(ctx, "dieu");
                                    return TournamentSystem.claimThrone(p, god);
                                })))));

        d.register(Commands.literal("guerre")
                .executes(ctx -> open(ctx, Menus::openWar))
                .then(Commands.literal("declarer")
                        .then(Commands.argument("dieu", StringArgumentType.word()).suggests(GODS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile god = profileArg(ctx, "dieu");
                                    if (god == null) return unknown(ctx, "dieu");
                                    return WarSystem.declare(p, god);
                                })))));
    }

    private static int createTournament(CommandContext<CommandSourceStack> ctx, long prize) {
        String err = TournamentSystem.create(ctx.getSource().getServer(), prize, null);
        if (err != null) return fail(ctx, err);
        reply(ctx, "Tournoi créé (prix : " + Msg.money(prize) + "). Les joueurs s'inscrivent avec /tournoi rejoindre.");
        return 1;
    }

    // ------------------------------------------------------------------ Administration

    private interface LongField {
        long get(PlayerProfile p);

        void set(PlayerProfile p, long v);
    }

    /** /godadmin argent|essence|gemmes <joueur> [definir|ajouter|retirer] <valeur> */
    private static LiteralArgumentBuilder<CommandSourceStack> valueEditor(String name, String label, LongField field) {
        var player = Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                .executes(ctx -> {
                    PlayerProfile t = profileArg(ctx, "joueur");
                    if (t == null) return fail(ctx, unknown(ctx, "joueur"));
                    reply(ctx, label + " de " + t.name + " : " + Msg.num(field.get(t)));
                    return 1;
                })
                .then(Commands.argument("valeur", LongArgumentType.longArg(0)).executes(ctx -> adminEdit(ctx, t -> {
                    field.set(t, LongArgumentType.getLong(ctx, "valeur"));
                    return label + " de " + t.name + " = " + Msg.num(field.get(t));
                })));
        for (String op : new String[]{"definir", "ajouter", "retirer"}) {
            player.then(Commands.literal(op).then(Commands.argument("valeur", LongArgumentType.longArg(0)).executes(ctx -> adminEdit(ctx, t -> {
                long v = LongArgumentType.getLong(ctx, "valeur");
                long before = field.get(t);
                long after = switch (op) {
                    case "ajouter" -> before + v;
                    case "retirer" -> Math.max(0, before - v);
                    default -> v;
                };
                field.set(t, after);
                return label + " de " + t.name + " : " + Msg.num(before) + " → " + Msg.num(after);
            }))));
        }
        return Commands.literal(name).then(player);
    }

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pay").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> {
                    reply(ctx, "Fonctionnalités payantes : " + (GodData.settings().payEnabled ? "activées" : "désactivées") + ". /pay enable|disable");
                    return 1;
                })
                .then(Commands.literal("enable").executes(ctx -> setPay(ctx, true)))
                .then(Commands.literal("disable").executes(ctx -> setPay(ctx, false)))
                .then(Commands.literal("status").executes(ctx -> {
                    reply(ctx, "Fonctionnalités payantes : " + (GodData.settings().payEnabled ? "activées" : "désactivées"));
                    return 1;
                })));

        d.register(Commands.literal("godadmin").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> {
                    Help.show(ctx.getSource(), "admin");
                    return 1;
                })
                .then(Commands.literal("voir")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return fail(ctx, unknown(ctx, "joueur"));
                                    showAdmin(ctx, t);
                                    return 1;
                                })))
                .then(valueEditor("essence", "Essence", new LongField() {
                    public long get(PlayerProfile p) {
                        return p.essence;
                    }

                    public void set(PlayerProfile p, long v) {
                        p.essence = v;
                    }
                }))
                .then(valueEditor("argent", "Argent", new LongField() {
                    public long get(PlayerProfile p) {
                        return p.money;
                    }

                    public void set(PlayerProfile p, long v) {
                        p.money = v;
                    }
                }))
                .then(valueEditor("gemmes", "Gemmes", new LongField() {
                    public long get(PlayerProfile p) {
                        return p.gems;
                    }

                    public void set(PlayerProfile p, long v) {
                        p.gems = v;
                    }
                }))
                .then(Commands.literal("metier")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("metier", StringArgumentType.word()).suggests(words("mineur", "bucheron", "fermier", "chasseur", "pecheur"))
                                        .then(Commands.argument("niveau", IntegerArgumentType.integer(0, Job.MAX_LEVEL))
                                                .executes(ctx -> adminEdit(ctx, t -> {
                                                    Job job = Job.parse(StringArgumentType.getString(ctx, "metier"));
                                                    if (job == null) return "!Métier inconnu : mineur, bucheron, fermier, chasseur, pecheur.";
                                                    int lvl = IntegerArgumentType.getInteger(ctx, "niveau");
                                                    t.jobLevel.put(job.name(), lvl);
                                                    t.jobXp.put(job.name(), 0.0);
                                                    return job.label + " de " + t.name + " = niveau " + lvl;
                                                }))))))
                .then(Commands.literal("palier")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("palier", StringArgumentType.word())
                                        .suggests(words("mortel", "eveille", "demi_dieu", "dieu", "dieu_supreme"))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            Tier tier = Tier.parse(StringArgumentType.getString(ctx, "palier"));
                                            if (tier == null) return "!Palier inconnu : mortel, eveille, demi_dieu, dieu, dieu_supreme.";
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
                                .executes(ctx -> fail(ctx, "Attention : tout le profil sera effacé. Confirmez avec /godadmin reset "
                                        + StringArgumentType.getString(ctx, "joueur") + " confirmer"))
                                .then(Commands.literal("confirmer").executes(ctx -> adminEdit(ctx, t -> {
                                    FaithSystem.release(ctx.getSource().getServer(), t);
                                    PlayerProfile fresh = new PlayerProfile(t.uuid, t.name);
                                    fresh.plotIndex = t.plotIndex;
                                    fresh.passwordHash = t.passwordHash;
                                    fresh.passwordSalt = t.passwordSalt;
                                    fresh.role = t.role;
                                    fresh.codexGiven = t.codexGiven;
                                    GodData.state().players.put(t.uuid, fresh);
                                    ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                    if (sp != null) PowerSystem.applyAttributes(sp);
                                    return "Profil de " + t.name + " réinitialisé (mot de passe et rang conservés).";
                                })))))
                .then(Commands.literal("teleport")
                        .executes(ctx -> {
                            reply(ctx, "Délai avant téléportation : " + GodData.settings().teleportWarmupSeconds + " s. /godadmin teleport <secondes>");
                            return 1;
                        })
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(0, 30)).executes(ctx -> {
                            GodData.settings().teleportWarmupSeconds = IntegerArgumentType.getInteger(ctx, "secondes");
                            GodData.dirty();
                            reply(ctx, "Délai avant téléportation : " + GodData.settings().teleportWarmupSeconds + " s (0 = immédiat).");
                            return 1;
                        })))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("reconstruire").executes(ctx -> {
                            com.godmod.system.WorldBuilder.build(ctx.getSource().getServer(), ctx.getSource());
                            reply(ctx, "Reconstruction du Sanctuaire lancée (quelques secondes).");
                            return 1;
                        }))
                        .then(Commands.literal("vider")
                                .executes(ctx -> fail(ctx, "Attention : tout le Sanctuaire sera effacé. Confirmez avec /godadmin spawn vider confirmer"))
                                .then(Commands.literal("confirmer").executes(ctx -> {
                                    com.godmod.system.WorldBuilder.clear(ctx.getSource().getServer(), ctx.getSource());
                                    reply(ctx, "Nettoyage complet du Sanctuaire lancé.");
                                    return 1;
                                })))
                        .then(Commands.literal("definir").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (!Hub.isSpawn(p.level())) return fail(ctx, "Placez-vous dans le Sanctuaire (/spawn).");
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
                            boolean on = Hub.toggleEditor(p);
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
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(WORLD_OWNERS)
                                .executes(ctx -> fail(ctx, "Attention : le monde divin sera effacé. Confirmez avec /godadmin clearile "
                                        + StringArgumentType.getString(ctx, "joueur") + " confirmer"))
                                .then(Commands.literal("confirmer").executes(ctx -> adminEdit(ctx, t -> {
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
                                })))))
                .then(Commands.literal("clearzone")
                        .then(Commands.argument("rayon", IntegerArgumentType.integer(1, 96))
                                .executes(ctx -> clearZone(ctx, IntegerArgumentType.getInteger(ctx, "rayon"), 64))
                                .then(Commands.argument("hauteur", IntegerArgumentType.integer(1, 384))
                                        .executes(ctx -> clearZone(ctx, IntegerArgumentType.getInteger(ctx, "rayon"),
                                                IntegerArgumentType.getInteger(ctx, "hauteur"))))))
                .then(Commands.literal("arene").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!Hub.isSpawn(p.level())) return fail(ctx, "L'Arène doit être dans le Sanctuaire (/spawn).");
                    Settings s = GodData.settings();
                    s.arenaSet = true;
                    s.arenaX = p.getX();
                    s.arenaY = p.getY();
                    s.arenaZ = p.getZ();
                    GodData.dirty();
                    reply(ctx, "Centre de l'Arène placé ici (tournois, rébellions et Titan).");
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
                .then(Commands.literal("reglages").executes(ctx -> {
                    showSettings(ctx);
                    return 1;
                }))
                .then(Commands.literal("journal")
                        .executes(ctx -> showLog(ctx, 15, null))
                        .then(Commands.argument("lignes", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> showLog(ctx, IntegerArgumentType.getInteger(ctx, "lignes"), null))
                                .then(Commands.argument("filtre", StringArgumentType.greedyString())
                                        .executes(ctx -> showLog(ctx, IntegerArgumentType.getInteger(ctx, "lignes"), StringArgumentType.getString(ctx, "filtre")))))));
    }

    private static void showAdmin(CommandContext<CommandSourceStack> ctx, PlayerProfile t) {
        CommandSourceStack src = ctx.getSource();
        boolean online = PowerSystem.online(src.getServer(), t.uuid) != null;
        src.sendSystemMessage(Msg.header(t.name + (online ? " (en ligne)" : " (hors ligne)")));
        reply(ctx, "UUID " + t.uuid + " · rang " + (t.role == null ? "Joueur" : t.role.label));
        reply(ctx, t.tier.label + " · Essence " + Msg.num(t.essence) + " · Foi " + (int) t.faith
                + " · Argent " + Msg.num(t.money) + " · Gemmes " + t.gems);
        reply(ctx, "Dieu : " + (t.master == null ? "aucun" : name(t.master)) + " · Fidèles "
                + GodData.followersOf(t.uuid).size() + "/" + GodData.totalFollowers(t.uuid)
                + " · Domaine " + t.specialization.label + " · Monde divin " + (DivineWorld.hasWorld(t) ? "oui (rayon " + DivineWorld.radius(t) + ")" : "non"));
        reply(ctx, "Stats F" + t.stat(Stat.FORCE) + " V" + t.stat(Stat.VITALITE) + " A" + t.stat(Stat.AGILITE)
                + " E" + t.stat(Stat.ESPRIT) + " (" + PowerSystem.spentStatPoints(t) + "/" + PowerSystem.totalStatPoints(t) + ")");
        StringBuilder jobs = new StringBuilder("Métiers :");
        for (Job j : Job.values()) jobs.append(' ').append(j.label).append(' ').append(JobSystem.level(t, j));
        reply(ctx, jobs.toString());
        StringBuilder keys = new StringBuilder("Clés :");
        for (CrateSystem.Crate c : CrateSystem.Crate.values()) keys.append(' ').append(c.label).append(' ').append(CrateSystem.keys(t, c));
        reply(ctx, keys + " · caisses ouvertes " + t.cratesOpened + " · quêtes " + t.questsDone + " (" + t.quests.size() + " en cours)");
        reply(ctx, "Tués " + t.kills + " · Morts " + t.deaths + " · Déicides " + t.godsSlain
                + " · Tournois " + t.tournamentsWon + " · Événements " + t.eventsWon + " · Série " + t.dailyStreak + " (record " + t.dailyBest + ")");
        reply(ctx, "Jeu " + Msg.time(t.playTicks * 50) + " · Vu il y a " + Msg.time(System.currentTimeMillis() - t.lastSeen));
    }

    private static void showSettings(CommandContext<CommandSourceStack> ctx) {
        Settings s = GodData.settings();
        ctx.getSource().sendSystemMessage(Msg.header("Réglages du Panthéon"));
        reply(ctx, "Seuils ×" + s.thresholdMultiplier + " · Essence ×" + s.essenceMultiplier + " · rébellion tous les " + s.rebellionCooldownHours
                + " h · dieu inactif après " + s.inactiveGodDays + " j");
        reply(ctx, "Mots de passe " + (s.authEnabled ? "oui" : "non") + " (session " + s.authSessionHours + " h) · premium " + (s.payEnabled ? "oui" : "non")
                + " · Nether/End " + (s.disableNetherEnd ? "scellés" : "ouverts"));
        reply(ctx, "Événements auto " + (s.eventsAuto ? "oui, toutes les " + s.eventIntervalMinutes + " min dès " + s.eventMinPlayers + " joueur(s)" : "non")
                + " · délai de téléportation " + s.teleportWarmupSeconds + " s");
        reply(ctx, "Monde divin : rayon " + s.worldBaseRadius + " + " + s.worldRadiusPerFollower + "/fidèle (max " + s.worldMaxRadius + ")");
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

    /** Modifie un profil. Si la fonction renvoie un texte commençant par « ! », c'est une erreur. */
    private static int adminEdit(CommandContext<CommandSourceStack> ctx, Function<PlayerProfile, String> edit) {
        PlayerProfile t = profileArg(ctx, "joueur");
        if (t == null) return fail(ctx, unknown(ctx, "joueur"));
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

    private static int showLog(CommandContext<CommandSourceStack> ctx, int n, String filter) {
        List<String> log = new ArrayList<>();
        for (String l : GodData.state().log) if (filter == null || l.toLowerCase().contains(filter.toLowerCase())) log.add(l);
        int from = Math.max(0, log.size() - n);
        reply(ctx, "Journal (" + (log.size() - from) + " entrée(s)" + (filter == null ? "" : " contenant « " + filter + " »") + ") :");
        for (int i = from; i < log.size(); i++) {
            String line = log.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}
