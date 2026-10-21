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
        registerEssentials(d);
        registerConflicts(d);
        registerAdmin(d);
        registerComfort(d);
        StaffCommands.register(d);
    }

    // ------------------------------------------------------------------ /stats, /afk, privilèges

    private static void registerComfort(CommandDispatcher<CommandSourceStack> d) {
        for (String name : new String[]{"stats", "statistiques"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> open(ctx, p -> com.godmod.progress.StatsSystem.open(p, GodData.get(p))))
                    .then(Commands.literal("serveur").executes(ctx -> open(ctx, com.godmod.progress.StatsSystem::openServer)))
                    .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                            .executes(ctx -> run(ctx, p -> {
                                PlayerProfile t = profileArg(ctx, "joueur");
                                if (t == null) return unknown(ctx, "joueur");
                                com.godmod.progress.StatsSystem.open(p, t);
                                return null;
                            }))));
        }
        for (String n : new String[]{"generateur", "prestige", "gen"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> open(ctx, com.godmod.system.GeneratorSystem::open))
                    .then(Commands.literal("ameliorer").executes(ctx -> run(ctx, com.godmod.system.GeneratorSystem::upgrade))));
        }
        d.register(Commands.literal("clearlag").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> {
                    reply(ctx, "❄ Nettoyage immédiat : " + com.godmod.system.LagControl.clearNow(ctx.getSource().getServer()) + " objet(s) supprimé(s).");
                    return 1;
                })
                .then(Commands.literal("intervalle").then(Commands.argument("secondes", IntegerArgumentType.integer(0, 3600)).executes(ctx -> {
                    GodData.settings().clearlagSeconds = IntegerArgumentType.getInteger(ctx, "secondes");
                    GodData.dirty();
                    int s = GodData.settings().clearlagSeconds;
                    reply(ctx, s == 0 ? "Nettoyage automatique désactivé." : "Nettoyage des objets au sol toutes les " + s + " secondes.");
                    return 1;
                })))
                .then(Commands.literal("entites").then(Commands.argument("max", IntegerArgumentType.integer(0, 500)).executes(ctx -> {
                    GodData.settings().entityChunkLimit = IntegerArgumentType.getInteger(ctx, "max");
                    GodData.dirty();
                    int m = GodData.settings().entityChunkLimit;
                    reply(ctx, m == 0 ? "Limite de créatures par chunk désactivée." : "Au plus " + m + " créatures d'un même type par chunk.");
                    return 1;
                }))));
        for (String n : new String[]{"transcender", "transcendance"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> open(ctx, com.godmod.system.Transcendence::open))
                    .then(Commands.literal("confirmer").executes(ctx -> run(ctx, com.godmod.system.Transcendence::transcend))));
        }
        d.register(Commands.literal("trailer_lancer").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> run(ctx, p -> com.godmod.system.Trailer.start(p, null)))
                .then(Commands.argument("adresse", StringArgumentType.greedyString())
                        .executes(ctx -> run(ctx, p -> com.godmod.system.Trailer.start(p, StringArgumentType.getString(ctx, "adresse"))))));
        d.register(Commands.literal("trailer_stop").requires(Moderation.require(Role.ADMIN)).executes(ctx -> {
            if (!com.godmod.system.Trailer.active()) return fail(ctx, "Aucun tournage en cours.");
            com.godmod.system.Trailer.stop(ctx.getSource().getServer(), "arrêté");
            return 1;
        }));
        registerGive(d);
        registerZones(d);
        d.register(Commands.literal("tuto").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> run(ctx, p -> {
                    p.sendSystemMessage(Msg.header("Tableaux du tutoriel"));
                    var placed = com.godmod.system.TutoBoards.placed();
                    for (int i = 0; i < com.godmod.system.TutoBoards.PAGES.size(); i++) {
                        int n = i + 1;
                        String title = com.godmod.system.TutoBoards.PAGES.get(i)[0].replaceAll("§.", "");
                        p.sendSystemMessage(Msg.text(placed.contains(n) ? "✔ " : "✖ ", placed.contains(n) ? ChatFormatting.GREEN : ChatFormatting.RED)
                                .append(Msg.suggest(title, "/tuto tableau " + n, "Visez le centre du mur puis validez", ChatFormatting.YELLOW)));
                    }
                    p.sendSystemMessage(Msg.text("/tuto tableau <n> : sur le mur visé · /tuto tableau <n> ici : devant vous · /tuto retirer <n|tous>"
                            + " · /tuto actualiser : redessiner les tableaux posés", ChatFormatting.GRAY));
                    return null;
                }))
                .then(Commands.literal("tableau").then(Commands.argument("numero", IntegerArgumentType.integer(1, 99))
                        .executes(ctx -> run(ctx, p -> {
                            String err = com.godmod.system.TutoBoards.place(p, IntegerArgumentType.getInteger(ctx, "numero"), false);
                            if (err == null) placedNext(p, IntegerArgumentType.getInteger(ctx, "numero"), "Tableau " + IntegerArgumentType.getInteger(ctx, "numero") + " posé.");
                            return err;
                        }))
                        .then(Commands.literal("ici").executes(ctx -> run(ctx, p -> {
                            String err = com.godmod.system.TutoBoards.place(p, IntegerArgumentType.getInteger(ctx, "numero"), true);
                            if (err == null) placedNext(p, IntegerArgumentType.getInteger(ctx, "numero"), "Tableau " + IntegerArgumentType.getInteger(ctx, "numero") + " posé devant vous.");
                            return err;
                        })))))
                .then(Commands.literal("actualiser").executes(ctx -> {
                    int n = com.godmod.system.TutoBoards.refresh(ctx.getSource().getServer());
                    reply(ctx, n == 0 ? "Aucun tableau enregistré : posez-les avec /tuto tableau <n>." : n + " tableau(x) redessiné(s) avec les textes et le style actuels.");
                    return 1;
                }))
                .then(Commands.literal("fond").then(Commands.argument("bloc", StringArgumentType.greedyString())
                        .suggests(words("minecraft:gray_concrete", "minecraft:black_concrete", "minecraft:white_concrete", "minecraft:blue_terracotta",
                                "minecraft:dark_oak_planks", "minecraft:deepslate_tiles", "minecraft:light_blue_concrete"))
                        .executes(ctx -> {
                            String b = StringArgumentType.getString(ctx, "bloc").trim();
                            if (!b.contains(":")) b = "minecraft:" + b;
                            GodData.settings().tutoBackground = b;
                            GodData.dirty();
                            reply(ctx, "Fond des tableaux : " + b + ". " + com.godmod.system.TutoBoards.refresh(ctx.getSource().getServer()) + " tableau(x) mis à jour.");
                            return 1;
                        })))
                .then(Commands.literal("cadre").then(Commands.argument("bloc", StringArgumentType.greedyString())
                        .suggests(words("minecraft:gold_block", "minecraft:quartz_block", "minecraft:dark_oak_log", "aucun"))
                        .executes(ctx -> {
                            String b = StringArgumentType.getString(ctx, "bloc").trim();
                            if (b.equalsIgnoreCase("aucun")) b = "";
                            else if (!b.contains(":")) b = "minecraft:" + b;
                            GodData.settings().tutoFrame = b;
                            GodData.dirty();
                            int r = com.godmod.system.TutoBoards.refresh(ctx.getSource().getServer());
                            reply(ctx, (b.isEmpty() ? "Tableaux sans cadre." : "Cadre des tableaux : " + b + ".") + " " + r + " tableau(x) mis à jour.");
                            return 1;
                        })))
                .then(Commands.literal("retirer")
                        .then(Commands.literal("tous").executes(ctx -> {
                            com.godmod.system.TutoBoards.deleteAll(ctx.getSource().getServer());
                            reply(ctx, "Tous les tableaux du tutoriel ont été retirés.");
                            return 1;
                        }))
                        .then(Commands.argument("numero", IntegerArgumentType.integer(1, 99)).executes(ctx -> {
                            com.godmod.system.TutoBoards.delete(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "numero"));
                            reply(ctx, "Tableau " + IntegerArgumentType.getInteger(ctx, "numero") + " retiré.");
                            return 1;
                        }))));
        d.register(Commands.literal("afk").executes(ctx -> run(ctx, com.godmod.system.AfkSystem::toggle)));
        for (String name : new String[]{"afkshop", "boutiqueafk"}) d.register(Commands.literal(name).executes(ctx -> open(ctx, com.godmod.progress.AfkShop::open)));
        d.register(Commands.literal("privileges").executes(ctx -> open(ctx, Menus::openPerkShop)));

        for (String n : new String[]{"craft", "atelier", "etabli"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::craft)));
        for (String n : new String[]{"ec", "enderchest"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::enderChest)));
        for (String n : new String[]{"enclume", "anvil"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::anvil)));
        for (String n : new String[]{"manger", "feed"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::feed)));
        for (String n : new String[]{"soin", "heal"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::heal)));
        for (String n : new String[]{"chapeau", "hat"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::hat)));
        for (String n : new String[]{"reparer", "repair"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::repair)));
        for (String n : new String[]{"vol", "fly"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Perks::fly)));
        for (String n : new String[]{"tete", "head"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> run(ctx, p -> com.godmod.system.Perks.head(p, null)))
                    .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Perks.head(p, StringArgumentType.getString(ctx, "joueur"))))));
        }
        d.register(Commands.literal("skin").executes(ctx -> run(ctx, p ->
                "Changer de skin n'est pas possible avec ce mod : le serveur ne peut pas modifier votre apparence sans un mod dédié. "
                        + "Changez votre skin sur minecraft.net puis reconnectez-vous.")));
    }

    // ------------------------------------------------------------------ /zone (admins)

    private static void registerZones(CommandDispatcher<CommandSourceStack> d) {
        SuggestionProvider<CommandSourceStack> zones = (c, b) -> {
            List<String> n = new ArrayList<>();
            if (GodData.ready()) for (com.godmod.data.Zone z : GodData.state().zones.values()) n.add(z.name);
            return SharedSuggestionProvider.suggest(n, b);
        };
        var creer = Commands.literal("creer");
        var commandeNode = Commands.argument("nom", StringArgumentType.word());
        for (String mode : new String[]{"console", "joueur"}) {
            boolean console = mode.equals("console");
            commandeNode.then(Commands.literal(mode).then(Commands.argument("commande", StringArgumentType.greedyString())
                    .executes(ctx -> run(ctx, p -> com.godmod.system.ZoneSystem.createCommand(p, StringArgumentType.getString(ctx, "nom"),
                            console, StringArgumentType.getString(ctx, "commande"), false)))));
        }
        creer.then(Commands.literal("commande").then(commandeNode));
        for (com.godmod.data.Zone.Type t : com.godmod.data.Zone.Type.values()) {
            if (t == com.godmod.data.Zone.Type.COMMANDE) continue;
            creer.then(Commands.literal(t.name().toLowerCase()).then(Commands.argument("nom", StringArgumentType.word())
                    .executes(ctx -> run(ctx, p -> com.godmod.system.ZoneSystem.create(p, t, StringArgumentType.getString(ctx, "nom"), false)))
                    .then(Commands.literal("toutehauteur")
                            .executes(ctx -> run(ctx, p -> com.godmod.system.ZoneSystem.create(p, t, StringArgumentType.getString(ctx, "nom"), true))))));
        }
        d.register(Commands.literal("zone").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> run(ctx, p -> {
                    com.godmod.system.ZoneSystem.list(p);
                    return null;
                }))
                .then(Commands.literal("baton").executes(ctx -> run(ctx, p -> {
                    Shop.give(p, com.godmod.system.ZoneSystem.wand());
                    Msg.ok(p, "Bâton de zone : clic gauche = coin 1, clic droit = coin 2, puis /zone creer <afk|spawn|libre> <nom>.");
                    return null;
                })))
                .then(Commands.literal("pos1").executes(ctx -> run(ctx, p -> com.godmod.system.ZoneSystem.setCorner(p, true))))
                .then(Commands.literal("pos2").executes(ctx -> run(ctx, p -> com.godmod.system.ZoneSystem.setCorner(p, false))))
                .then(creer)
                .then(Commands.literal("supprimer")
                        .executes(ctx -> run(ctx, p -> com.godmod.system.ZoneSystem.delete(p, null)))
                        .then(Commands.argument("nom", StringArgumentType.word()).suggests(zones)
                                .executes(ctx -> run(ctx, p -> com.godmod.system.ZoneSystem.delete(p, StringArgumentType.getString(ctx, "nom"))))))
                .then(Commands.literal("liste").executes(ctx -> run(ctx, p -> {
                    com.godmod.system.ZoneSystem.list(p);
                    return null;
                }))));
    }

    // ------------------------------------------------------------------ /donner (admins)

    private static List<String> giveables() {
        List<String> l = new ArrayList<>(List.of("cle_commune", "cle_rare", "cle_epique", "cle_divine",
                "artefact_ocean", "artefact_ciel", "artefact_cosmos", "codex", "jetons_afk"));
        for (String mob : com.godmod.progress.SpawnerSystem.MOBS.keySet()) l.add("generateur_" + mob.substring(mob.indexOf(':') + 1));
        return l;
    }

    private static void registerGive(CommandDispatcher<CommandSourceStack> d) {
        SuggestionProvider<CommandSourceStack> items = (c, b) -> SharedSuggestionProvider.suggest(giveables(), b);
        d.register(Commands.literal("donner").requires(Moderation.require(Role.ADMIN))
                .then(Commands.argument("objet", StringArgumentType.word()).suggests(items)
                        .executes(ctx -> give(ctx, 1, null))
                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 10000))
                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "nombre"), null))
                                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE)
                                        .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "nombre"), StringArgumentType.getString(ctx, "joueur")))))));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int n, String who) throws CommandSyntaxException {
        ServerPlayer t = who == null ? ctx.getSource().getPlayerOrException() : ctx.getSource().getServer().getPlayerList().getPlayerByName(who);
        if (t == null) return fail(ctx, "Joueur introuvable ou hors ligne.");
        String obj = StringArgumentType.getString(ctx, "objet").toLowerCase();
        PlayerProfile p = GodData.get(t);
        String label;
        if (obj.startsWith("cle_")) {
            CrateSystem.Crate c = CrateSystem.Crate.parse(obj.substring(4));
            if (c == null) return fail(ctx, "Clé inconnue : cle_commune, cle_rare, cle_epique, cle_divine.");
            CrateSystem.addKey(p, c, n);
            label = n + " clé(s) " + c.label;
        } else if (obj.startsWith("artefact_")) {
            Specialization s = Specialization.parse(obj.substring(9));
            if (s == null || s == Specialization.AUCUNE) return fail(ctx, "Artefact inconnu : artefact_ocean, artefact_ciel, artefact_cosmos.");
            for (int i = 0; i < Math.min(n, 64); i++) Shop.give(t, com.godmod.util.DivineItems.artifact(s));
            label = Math.min(n, 64) + " artefact(s) " + s.label;
        } else if (obj.equals("codex")) {
            Shop.give(t, com.godmod.util.DivineItems.codex());
            label = "un Codex divin";
        } else if (obj.equals("jetons_afk")) {
            p.afkTokens += n;
            label = n + " jeton(s) AFK";
        } else if (obj.startsWith("generateur_")) {
            String mob = "minecraft:" + obj.substring(11);
            if (!com.godmod.progress.SpawnerSystem.MOBS.containsKey(mob)) return fail(ctx, "Générateur inconnu. Tapez /donner generateur_ puis Tab.");
            for (int i = 0; i < Math.min(n, 64); i++) Shop.give(t, com.godmod.progress.SpawnerSystem.item(mob));
            label = Math.min(n, 64) + " générateur(s) de " + com.godmod.progress.SpawnerSystem.mobName(mob).toLowerCase();
        } else {
            return fail(ctx, "Objet inconnu. Tapez /donner puis Tab pour la liste.");
        }
        GodData.dirty();
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : /donner " + label + " -> " + p.name);
        if (!t.getName().getString().equals(ctx.getSource().getTextName())) Msg.ok(t, "Vous recevez " + label + " de l'équipe.");
        reply(ctx, label + " donné(s) à " + p.name + ".");
        return 1;
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
        for (String name : new String[]{"cles", "keys"}) d.register(Commands.literal(name).executes(ctx -> open(ctx, Menus::openKeys)));
        d.register(Commands.literal("caisses").executes(ctx -> open(ctx, Menus::openKeys))
                .then(Commands.literal("decor").requires(Moderation.require(Role.ADMIN))
                        .executes(ctx -> reply1(ctx, "Coffres 3D posés sur " + com.godmod.progress.CrateDecor.place(ctx.getSource().getServer())
                                + " caisse(s), au-dessus des blocs existants (aucun bloc modifié). /caisses decor hauteur <blocs> pour les monter, /caisses decor retirer pour les enlever."))
                        .then(Commands.literal("hauteur").then(Commands.argument("blocs", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-2, 5))
                                .executes(ctx -> {
                                    GodData.settings().crateModelOffset = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "blocs");
                                    GodData.dirty();
                                    int n = com.godmod.progress.CrateDecor.place(ctx.getSource().getServer());
                                    return reply1(ctx, "Coffres 3D replacés (" + n + ") à " + GodData.settings().crateModelOffset + " bloc(s) au-dessus de leur socle.");
                                })))
                        .then(Commands.literal("retirer").executes(ctx -> {
                            com.godmod.progress.CrateDecor.remove(ctx.getSource().getServer());
                            return reply1(ctx, "Décor des caisses retiré.");
                        }))
                        .then(Commands.literal("anciens").executes(ctx -> reply1(ctx, "Anciens panneaux « Caisse … / clic droit » retirés autour de "
                                + com.godmod.progress.CrateDecor.removeOldLabels(ctx.getSource().getServer()) + " caisse(s)."))))
                .then(Commands.literal("ouvrir").then(Commands.argument("caisse", StringArgumentType.word())
                        .suggests(words("commune", "rare", "epique", "divine"))
                        .then(Commands.argument("nombre", IntegerArgumentType.integer(1, 64)).executes(ctx -> run(ctx, p -> {
                            var c = CrateSystem.Crate.parse(StringArgumentType.getString(ctx, "caisse"));
                            if (c == null) return "Caisse inconnue : commune, rare, epique, divine.";
                            return CrateSystem.openMany(p, c, IntegerArgumentType.getInteger(ctx, "nombre"));
                        }))))));
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

    // ------------------------------------------------------------------ Résidences, /back, kits, échanges, tombes

    private static void registerUseful(CommandDispatcher<CommandSourceStack> d) {
        // Courrier
        for (String n : new String[]{"courrier", "mail", "lettres"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> open(ctx, com.godmod.system.MailSystem::open))
                    .then(Commands.literal("envoyer").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.MailSystem.send(p, StringArgumentType.getString(ctx, "joueur"),
                                            StringArgumentType.getString(ctx, "message"), false, 0))))))
                    .then(Commands.literal("objet").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.MailSystem.send(p, StringArgumentType.getString(ctx, "joueur"), "", true, 0)))
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.MailSystem.send(p, StringArgumentType.getString(ctx, "joueur"),
                                            StringArgumentType.getString(ctx, "message"), true, 0))))))
                    .then(Commands.literal("argent").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                            .then(Commands.argument("montant", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.MailSystem.send(p, StringArgumentType.getString(ctx, "joueur"), "", false,
                                            com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "montant"))))
                                    .then(Commands.argument("message", StringArgumentType.greedyString())
                                            .executes(ctx -> run(ctx, p -> com.godmod.system.MailSystem.send(p, StringArgumentType.getString(ctx, "joueur"),
                                                    StringArgumentType.getString(ctx, "message"), false,
                                                    com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "montant")))))))));
        }
        // Primes
        for (String n : new String[]{"prime", "primes", "bounty"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> run(ctx, com.godmod.system.BountySystem::list))
                    .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                            .then(Commands.argument("montant", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.BountySystem.place(p, StringArgumentType.getString(ctx, "joueur"),
                                            com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "montant")))))));
        }
        // Annonces automatiques (admins)
        d.register(Commands.literal("annonces").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> run(ctx, p -> {
                    var st = GodData.settings();
                    p.sendSystemMessage(Msg.header("Annonces automatiques"));
                    p.sendSystemMessage(Msg.text((st.announcements ? "Activées" : "Désactivées") + " · toutes les " + st.announceMinutes + " min", ChatFormatting.GRAY));
                    for (int i = 0; i < st.announceMessages.size(); i++) {
                        p.sendSystemMessage(Msg.text((i + 1) + ". ", ChatFormatting.GRAY).append(com.godmod.system.Announcer.format(st.announceMessages.get(i)))
                                .append(Msg.button("✖", "/annonces retirer " + (i + 1), "Retirer cette annonce", ChatFormatting.RED)));
                    }
                    p.sendSystemMessage(Msg.text("/annonces ajouter <texte> · intervalle <min> · on|off · maintenant", ChatFormatting.DARK_GRAY));
                    return null;
                }))
                .then(Commands.literal("ajouter").then(Commands.argument("texte", StringArgumentType.greedyString()).executes(ctx -> {
                    GodData.settings().announceMessages.add(StringArgumentType.getString(ctx, "texte"));
                    GodData.dirty();
                    return reply1(ctx, "Annonce ajoutée (" + GodData.settings().announceMessages.size() + ").");
                })))
                .then(Commands.literal("retirer").then(Commands.argument("numero", IntegerArgumentType.integer(1)).executes(ctx -> {
                    var list = GodData.settings().announceMessages;
                    int i = IntegerArgumentType.getInteger(ctx, "numero") - 1;
                    if (i >= list.size()) return fail(ctx, "Il n'y a que " + list.size() + " annonce(s).");
                    list.remove(i);
                    GodData.dirty();
                    return reply1(ctx, "Annonce retirée.");
                })))
                .then(Commands.literal("intervalle").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240)).executes(ctx -> {
                    GodData.settings().announceMinutes = IntegerArgumentType.getInteger(ctx, "minutes");
                    GodData.dirty();
                    return reply1(ctx, "Une annonce toutes les " + GodData.settings().announceMinutes + " minutes.");
                })))
                .then(Commands.literal("on").executes(ctx -> {
                    GodData.settings().announcements = true;
                    GodData.dirty();
                    return reply1(ctx, "Annonces activées.");
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    GodData.settings().announcements = false;
                    GodData.dirty();
                    return reply1(ctx, "Annonces désactivées.");
                }))
                .then(Commands.literal("maintenant").executes(ctx -> {
                    String err = com.godmod.system.Announcer.next(ctx.getSource().getServer());
                    return err == null ? 1 : fail(ctx, err);
                })));
        // Infos : /vu, /ping, /tps
        for (String n : new String[]{"vu", "seen"}) {
            d.register(Commands.literal(n).then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                    .executes(ctx -> run(ctx, p -> {
                        PlayerProfile t = profileArg(ctx, "joueur");
                        if (t == null) return "Joueur inconnu.";
                        ServerPlayer on = p.level().getServer().getPlayerList().getPlayer(java.util.UUID.fromString(t.uuid));
                        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm").withZone(java.time.ZoneId.of("Europe/Paris"));
                        p.sendSystemMessage(Msg.header(t.name));
                        p.sendSystemMessage(Msg.text(on != null ? "● En ligne" : "○ Hors ligne · vu il y a " + Msg.time(System.currentTimeMillis() - t.lastSeen)
                                + " (" + f.format(java.time.Instant.ofEpochMilli(t.lastSeen)) + ")", on != null ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                        if (t.firstSeen > 0) p.sendSystemMessage(Msg.text("Arrivé le " + f.format(java.time.Instant.ofEpochMilli(t.firstSeen)), ChatFormatting.GRAY));
                        p.sendSystemMessage(Msg.text("Palier : ", ChatFormatting.GRAY).append(net.minecraft.network.chat.Component.literal(t.tier.label).withStyle(t.tier.color))
                                .append(Msg.text(" · " + Msg.num(t.essence) + " Essence", ChatFormatting.LIGHT_PURPLE)));
                        long b = com.godmod.system.BountySystem.amount(t.uuid);
                        if (b > 0) p.sendSystemMessage(Msg.text("☠ Prime sur sa tête : " + Msg.money(b), ChatFormatting.RED));
                        return null;
                    }))));
        }
        d.register(Commands.literal("ping")
                .executes(ctx -> run(ctx, p -> {
                    Msg.info(p, "Votre ping : " + p.connection.latency() + " ms.");
                    return null;
                }))
                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE).executes(ctx -> run(ctx, p -> {
                    ServerPlayer t = p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur"));
                    if (t == null) return "Joueur hors ligne.";
                    Msg.info(p, "Ping de " + t.getName().getString() + " : " + t.connection.latency() + " ms.");
                    return null;
                }))));
        d.register(Commands.literal("tps").executes(ctx -> {
            var server = ctx.getSource().getServer();
            double mspt = server.getAverageTickTimeNanos() / 1_000_000.0;
            double tps = Math.min(20.0, 1000.0 / Math.max(1, mspt));
            ChatFormatting col = tps >= 19 ? ChatFormatting.GREEN : tps >= 15 ? ChatFormatting.YELLOW : ChatFormatting.RED;
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1_048_576, max = rt.maxMemory() / 1_048_576;
            ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text(String.format(java.util.Locale.FRANCE, "TPS %.1f", tps), col, ChatFormatting.BOLD))
                    .append(Msg.text(String.format(java.util.Locale.FRANCE, " · %.1f ms par tick · %d joueur(s) · mémoire %d / %d Mo",
                            mspt, server.getPlayerCount(), used, max), ChatFormatting.GRAY)), false);
            return 1;
        }));
    }

    private static void registerClient(CommandDispatcher<CommandSourceStack> d) {
        registerUseful(d);
        // Envoyée automatiquement par le mod client.
        d.register(Commands.literal("godclient").then(Commands.argument("version", StringArgumentType.greedyString())
                .executes(ctx -> run(ctx, p -> com.godmod.system.ClientMod.hello(p, StringArgumentType.getString(ctx, "version"))))));
        d.register(Commands.literal("client").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> reply1(ctx, com.godmod.system.ClientMod.status(ctx.getSource().getServer())))
                .then(Commands.literal("demande").then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> {
                    GodData.settings().clientPrompt = on(StringArgumentType.getString(ctx, "etat"));
                    GodData.dirty();
                    return reply1(ctx, com.godmod.system.ClientMod.status(ctx.getSource().getServer()));
                })))
                .then(Commands.literal("obligatoire").then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> {
                    GodData.settings().clientRequired = on(StringArgumentType.getString(ctx, "etat"));
                    GodData.dirty();
                    return reply1(ctx, com.godmod.system.ClientMod.status(ctx.getSource().getServer())
                            + (GodData.settings().clientRequired ? " — les joueurs sans le mod sont expulsés 45 s après leur connexion." : ""));
                })))
                .then(Commands.literal("url").then(Commands.argument("adresse", StringArgumentType.greedyString()).executes(ctx -> {
                    GodData.settings().clientUrl = StringArgumentType.getString(ctx, "adresse").trim();
                    GodData.dirty();
                    return reply1(ctx, com.godmod.system.ClientMod.status(ctx.getSource().getServer()));
                }))));
    }

    private static void registerPack(CommandDispatcher<CommandSourceStack> d) {
        registerClient(d);
        d.register(Commands.literal("pack")
                .executes(ctx -> run(ctx, com.godmod.system.PackSystem::resend))
                .then(Commands.literal("etat").requires(Moderation.require(Role.ADMIN)).executes(ctx -> reply1(ctx, com.godmod.system.PackSystem.status())))
                .then(Commands.literal("recharger").requires(Moderation.require(Role.ADMIN)).executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    com.godmod.system.PackSystem.refresh(server, () -> {
                        for (ServerPlayer sp : server.getPlayerList().getPlayers()) com.godmod.system.PackSystem.send(sp);
                        ctx.getSource().sendSuccess(() -> Msg.text(com.godmod.system.PackSystem.status() + " — renvoyé à tous les joueurs.", ChatFormatting.GREEN), false);
                    });
                    return reply1(ctx, "Téléchargement du pack…");
                }))
                .then(Commands.literal("activer").requires(Moderation.require(Role.ADMIN))
                        .then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> {
                            GodData.settings().packEnabled = on(StringArgumentType.getString(ctx, "etat"));
                            GodData.dirty();
                            if (GodData.settings().packEnabled) com.godmod.system.PackSystem.refresh(ctx.getSource().getServer(), null);
                            return reply1(ctx, com.godmod.system.PackSystem.status());
                        })))
                .then(Commands.literal("obligatoire").requires(Moderation.require(Role.ADMIN))
                        .then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> {
                            GodData.settings().packRequired = on(StringArgumentType.getString(ctx, "etat"));
                            GodData.dirty();
                            for (ServerPlayer sp : ctx.getSource().getServer().getPlayerList().getPlayers()) com.godmod.system.Ranks.refresh(sp);
                            return reply1(ctx, com.godmod.system.PackSystem.status());
                        })))
                .then(Commands.literal("url").requires(Moderation.require(Role.ADMIN))
                        .then(Commands.argument("adresse", StringArgumentType.greedyString()).executes(ctx -> {
                            GodData.settings().packUrl = StringArgumentType.getString(ctx, "adresse").trim();
                            GodData.dirty();
                            com.godmod.system.PackSystem.refresh(ctx.getSource().getServer(), null);
                            return reply1(ctx, com.godmod.system.PackSystem.status());
                        }))));
    }

    private static void registerChat(CommandDispatcher<CommandSourceStack> d) {
        registerPack(d);
        String[] chans = {"global", "local", "commerce", "groupe"};
        String[] tabs = {"tout", "global", "local", "commerce", "groupe", "mp"};
        d.register(Commands.literal("chat")
                .executes(ctx -> run(ctx, p -> {
                    p.sendSystemMessage(com.godmod.system.ChatSystem.tabBar(p));
                    Msg.send(p, Msg.text("Cliquez sur un onglet pour n'afficher que ce canal. /canal pour choisir où vous écrivez, "
                            + "/chat masquer <canal> pour cacher un canal de l'onglet Tout.", ChatFormatting.GRAY));
                    return null;
                }))
                .then(Commands.literal("onglet").then(Commands.argument("onglet", StringArgumentType.word()).suggests(words(tabs))
                        .executes(ctx -> run(ctx, p -> {
                            String t = StringArgumentType.getString(ctx, "onglet");
                            if (t.equalsIgnoreCase("tout")) {
                                com.godmod.system.ChatSystem.showTab(p, null);
                                return null;
                            }
                            var c = com.godmod.system.ChatSystem.Channel.parse(t);
                            if (c == null || c == com.godmod.system.ChatSystem.Channel.SYSTEME) return "Onglets : " + String.join(", ", tabs) + ".";
                            com.godmod.system.ChatSystem.showTab(p, c);
                            return null;
                        }))))
                .then(Commands.literal("masquer").then(Commands.argument("canal", StringArgumentType.word()).suggests(words(chans))
                        .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.toggleHidden(p,
                                com.godmod.system.ChatSystem.Channel.parse(StringArgumentType.getString(ctx, "canal")))))))
                .then(Commands.literal("effacer").executes(ctx -> run(ctx, p -> {
                    for (int i = 0; i < 100; i++) p.sendSystemMessage(net.minecraft.network.chat.Component.empty());
                    p.sendSystemMessage(com.godmod.system.ChatSystem.tabBar(p));
                    return null;
                }))));
        d.register(Commands.literal("canal")
                .executes(ctx -> run(ctx, p -> {
                    com.godmod.system.ChatSystem.channelPicker(p);
                    return null;
                }))
                .then(Commands.argument("canal", StringArgumentType.word()).suggests(words(chans))
                        .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.setChannel(p,
                                com.godmod.system.ChatSystem.Channel.parse(StringArgumentType.getString(ctx, "canal")))))
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, p -> {
                                    var c = com.godmod.system.ChatSystem.Channel.parse(StringArgumentType.getString(ctx, "canal"));
                                    if (c == null || !c.writable()) return "Canaux : global, local, commerce, groupe.";
                                    return com.godmod.system.ChatSystem.say(p, c, StringArgumentType.getString(ctx, "message"));
                                })))));
        String[][] shortcuts = {{"g", "global"}, {"local", "local"}, {"commerce", "commerce"}, {"p", "groupe"}};
        for (String[] sc : shortcuts) {
            var c = com.godmod.system.ChatSystem.Channel.parse(sc[1]);
            d.register(Commands.literal(sc[0])
                    .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.setChannel(p, c)))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.say(p, c, StringArgumentType.getString(ctx, "message"))))));
        }
        d.register(Commands.literal("mp")
                .then(Commands.literal("off").executes(ctx -> run(ctx, p -> {
                    GodData.get(p).mpBlocked = true;
                    GodData.dirty();
                    Msg.info(p, "Messages privés désactivés (l'équipe peut toujours vous écrire). /mp on pour les réactiver.");
                    return null;
                })))
                .then(Commands.literal("on").executes(ctx -> run(ctx, p -> {
                    GodData.get(p).mpBlocked = false;
                    GodData.dirty();
                    Msg.info(p, "Messages privés activés.");
                    return null;
                })))
                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(OTHERS)
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.privateMessage(p,
                                        p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur")),
                                        StringArgumentType.getString(ctx, "message")))))));
        for (String n : new String[]{"r", "reponse"}) {
            d.register(Commands.literal(n).then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.reply(p, StringArgumentType.getString(ctx, "message"))))));
        }
        d.register(Commands.literal("ignorer")
                .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.toggleIgnore(p, null)))
                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                        .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.toggleIgnore(p, StringArgumentType.getString(ctx, "joueur"))))));
        d.register(Commands.literal("espion").requires(Moderation.require(Role.MODERATEUR)).executes(ctx -> run(ctx, p -> {
            PlayerProfile pr = GodData.get(p);
            pr.chatSpy = !pr.chatSpy;
            GodData.dirty();
            Msg.info(p, pr.chatSpy ? "Espion activé : vous voyez les messages privés des joueurs." : "Espion désactivé.");
            return null;
        })));
        SuggestionProvider<CommandSourceStack> members = (ctx, b) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            return SharedSuggestionProvider.suggest(p == null ? java.util.List.of() : com.godmod.system.ChatSystem.groupMembersNames(p), b);
        };
        for (String n : new String[]{"groupe", "party"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> run(ctx, com.godmod.system.ChatSystem::groupList))
                    .then(Commands.literal("creer").executes(ctx -> run(ctx, com.godmod.system.ChatSystem::groupCreate)))
                    .then(Commands.literal("inviter").then(Commands.argument("joueur", StringArgumentType.word()).suggests(OTHERS)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.groupInvite(p,
                                    p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur")))))))
                    .then(Commands.literal("accepter")
                            .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.groupAccept(p, null)))
                            .then(Commands.argument("chef", StringArgumentType.word())
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.groupAccept(p, StringArgumentType.getString(ctx, "chef"))))))
                    .then(Commands.literal("refuser")
                            .executes(ctx -> run(ctx, com.godmod.system.ChatSystem::groupDeny))
                            .then(Commands.argument("chef", StringArgumentType.word()).executes(ctx -> run(ctx, com.godmod.system.ChatSystem::groupDeny))))
                    .then(Commands.literal("quitter").executes(ctx -> run(ctx, com.godmod.system.ChatSystem::groupLeave)))
                    .then(Commands.literal("expulser").then(Commands.argument("joueur", StringArgumentType.word()).suggests(members)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.groupKick(p, StringArgumentType.getString(ctx, "joueur"))))))
                    .then(Commands.literal("chef").then(Commands.argument("joueur", StringArgumentType.word()).suggests(members)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.ChatSystem.groupLeader(p,
                                    p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur"))))))));
        }
    }

    private static void registerVote(CommandDispatcher<CommandSourceStack> d) {
        registerChat(d);
        SuggestionProvider<CommandSourceStack> sites = (ctx, b) -> SharedSuggestionProvider.suggest(
                com.godmod.system.VoteSystem.SITES.stream().map(com.godmod.system.VoteSystem.Site::id).toList(), b);
        for (String n : new String[]{"vote", "voter"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> run(ctx, com.godmod.system.VoteSystem::show))
                    .then(Commands.literal("recuperer").executes(ctx -> run(ctx, com.godmod.system.VoteSystem::claim)))
                    .then(Commands.literal("top").executes(ctx -> run(ctx, com.godmod.system.VoteSystem::top)))
                    .then(Commands.literal("config").requires(Moderation.require(Role.ADMIN))
                            .executes(ctx -> reply1(ctx, com.godmod.system.VoteSystem.status()))
                            .then(Commands.argument("site", StringArgumentType.word()).suggests(sites)
                                    .then(Commands.argument("cle", StringArgumentType.greedyString()).executes(ctx -> {
                                        String site = StringArgumentType.getString(ctx, "site");
                                        if (com.godmod.system.VoteSystem.site(site) == null) return fail(ctx, "Site inconnu : top-serveurs, serveur-prive ou liste-serveurs.");
                                        com.godmod.system.VoteSystem.setKey(site.toLowerCase(), StringArgumentType.getString(ctx, "cle"));
                                        return reply1(ctx, com.godmod.system.VoteSystem.status());
                                    }))))
                    .then(Commands.literal("recompense").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("pieces", IntegerArgumentType.integer(0, 1_000_000))
                                    .then(Commands.argument("essence", IntegerArgumentType.integer(0, 1_000_000)).executes(ctx -> {
                                        GodData.settings().voteMoney = IntegerArgumentType.getInteger(ctx, "pieces");
                                        GodData.settings().voteEssence = IntegerArgumentType.getInteger(ctx, "essence");
                                        GodData.dirty();
                                        return reply1(ctx, "Récompense par vote : " + Msg.money(GodData.settings().voteMoney) + ", "
                                                + GodData.settings().voteEssence + " Essence et une clé Commune.");
                                    })))));
        }
        for (String n : new String[]{"site", "wiki"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> run(ctx, p -> {
                        String url = GodData.settings().siteUrl;
                        p.sendSystemMessage(Msg.prefix().append(Msg.text("Site et wiki du serveur : ", ChatFormatting.AQUA))
                                .append(net.minecraft.network.chat.Component.literal(url).withStyle(st -> st.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(url))))));
                        return null;
                    }))
                    .then(Commands.literal("definir").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("adresse", StringArgumentType.greedyString()).executes(ctx -> {
                                String url = StringArgumentType.getString(ctx, "adresse").trim();
                                if (!url.startsWith("http")) url = "https://" + url;
                                try {
                                    java.net.URI.create(url);
                                } catch (Exception e) {
                                    return fail(ctx, "Adresse invalide.");
                                }
                                GodData.settings().siteUrl = url;
                                GodData.dirty();
                                return reply1(ctx, "Adresse du site : " + url);
                            }))));
        }
    }

    private static void registerEssentials(CommandDispatcher<CommandSourceStack> d) {
        registerVote(d);
        SuggestionProvider<CommandSourceStack> homes = (ctx, b) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            return SharedSuggestionProvider.suggest(p == null || !GodData.ready() ? java.util.List.of() : com.godmod.system.Homes.names(GodData.get(p)), b);
        };
        d.register(Commands.literal("sethome")
                .executes(ctx -> run(ctx, p -> com.godmod.system.Homes.set(p, null)))
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(homes)
                        .executes(ctx -> run(ctx, p -> com.godmod.system.Homes.set(p, StringArgumentType.getString(ctx, "nom"))))));
        for (String n : new String[]{"home", "maison"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> run(ctx, p -> com.godmod.system.Homes.go(p, null)))
                    .then(Commands.argument("nom", StringArgumentType.word()).suggests(homes)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Homes.go(p, StringArgumentType.getString(ctx, "nom"))))));
        }
        d.register(Commands.literal("delhome")
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(homes)
                        .executes(ctx -> run(ctx, p -> com.godmod.system.Homes.delete(p, StringArgumentType.getString(ctx, "nom"))))));
        for (String n : new String[]{"homes", "maisons"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Homes::list)));
        for (String n : new String[]{"back", "retour"}) d.register(Commands.literal(n).executes(ctx -> run(ctx, com.godmod.system.Homes::back)));

        for (String n : new String[]{"kit", "kits"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> open(ctx, com.godmod.system.Kits::open))
                    .then(Commands.argument("nom", StringArgumentType.word()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(com.godmod.system.Kits.KITS.keySet(), b))
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Kits.claim(p, StringArgumentType.getString(ctx, "nom"))))));
        }

        SuggestionProvider<CommandSourceStack> askers = (ctx, b) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            return SharedSuggestionProvider.suggest(p == null ? java.util.List.of() : com.godmod.system.Trade.pendingFrom(p), b);
        };
        for (String n : new String[]{"echange", "trade"}) {
            d.register(Commands.literal(n)
                    .then(Commands.literal("accepter")
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Trade.accept(p, null)))
                            .then(Commands.argument("joueur", StringArgumentType.word()).suggests(askers)
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.Trade.accept(p, StringArgumentType.getString(ctx, "joueur"))))))
                    .then(Commands.literal("refuser")
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Trade.deny(p, null)))
                            .then(Commands.argument("joueur", StringArgumentType.word()).suggests(askers)
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.Trade.deny(p, StringArgumentType.getString(ctx, "joueur"))))))
                    .then(Commands.argument("joueur", StringArgumentType.word()).suggests(OTHERS)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Trade.request(p,
                                    p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur")))))));
        }

        for (String n : new String[]{"tombe", "tombes"}) {
            d.register(Commands.literal(n)
                    .executes(ctx -> run(ctx, com.godmod.system.Graves::list))
                    .then(Commands.literal("recuperer").then(Commands.argument("tombe", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Graves.remote(p, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "tombe"))))))
                    .then(Commands.literal("activer").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> {
                                GodData.settings().graves = on(StringArgumentType.getString(ctx, "etat"));
                                GodData.dirty();
                                reply(ctx, GodData.settings().graves ? "Tombes activées : les objets des morts y sont rangés."
                                        : "Tombes désactivées : les objets tombent au sol (règles vanilla).");
                                return 1;
                            })))
                    .then(Commands.literal("prix").requires(Moderation.require(Role.ADMIN))
                            .then(Commands.argument("pieces", IntegerArgumentType.integer(0, 1_000_000)).executes(ctx -> {
                                GodData.settings().graveRemoteCost = IntegerArgumentType.getInteger(ctx, "pieces");
                                GodData.dirty();
                                reply(ctx, "Récupération d'une tombe à distance : " + Msg.money(GodData.settings().graveRemoteCost) + ".");
                                return 1;
                            }))));
        }
    }

    /** Après la pose d'un tableau : propose le suivant. */
    private static void placedNext(ServerPlayer p, int n, String msg) {
        int total = com.godmod.system.TutoBoards.PAGES.size();
        var line = Msg.prefix().append(Msg.text(msg + " ", ChatFormatting.GREEN));
        if (n < total) line.append(Msg.suggest("[Poser le tableau " + (n + 1) + "]", "/tuto tableau " + (n + 1), "Visez le mur suivant puis validez", ChatFormatting.YELLOW));
        else line.append(Msg.text("C'était le dernier (" + total + ").", ChatFormatting.GRAY));
        p.sendSystemMessage(line);
    }

    private static void registerMarket(CommandDispatcher<CommandSourceStack> d) {
        SuggestionProvider<CommandSourceStack> listed = (ctx, b) -> SharedSuggestionProvider.suggest(com.godmod.system.Market.listedItems(), b);
        SuggestionProvider<CommandSourceStack> mine = (ctx, b) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            return SharedSuggestionProvider.suggest(p == null ? java.util.List.of() : com.godmod.system.Market.myIds(p), b);
        };
        String[] sorts = {"recent", "prix", "prix_desc", "affaires", "quantite", "nom"};
        for (String name : new String[]{"market", "marche", "hdv"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> open(ctx, com.godmod.system.Market::open))
                    .then(Commands.literal("vendre").then(Commands.argument("prix", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Market.sell(p, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "prix"), 0)))
                            .then(Commands.argument("quantite", IntegerArgumentType.integer(1, 64))
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.Market.sell(p, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "prix"),
                                            IntegerArgumentType.getInteger(ctx, "quantite")))))))
                    .then(Commands.literal("recherche")
                            .executes(ctx -> open(ctx, p -> com.godmod.system.Market.search(p, "")))
                            .then(Commands.argument("texte", StringArgumentType.greedyString()).suggests(listed)
                                    .executes(ctx -> open(ctx, p -> com.godmod.system.Market.search(p, StringArgumentType.getString(ctx, "texte"))))))
                    .then(Commands.literal("tri").then(Commands.argument("ordre", StringArgumentType.word()).suggests(words(sorts))
                            .executes(ctx -> run(ctx, p -> {
                                var s = com.godmod.system.Market.Sort.parse(StringArgumentType.getString(ctx, "ordre"));
                                if (s == null) return "Tri inconnu. Choix : " + String.join(", ", sorts) + ".";
                                com.godmod.system.Market.setSort(p, s);
                                return null;
                            }))))
                    .then(Commands.literal("mesventes").executes(ctx -> open(ctx, com.godmod.system.Market::openMine)))
                    .then(Commands.literal("retirer").then(Commands.argument("offre", com.mojang.brigadier.arguments.LongArgumentType.longArg(1)).suggests(mine)
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Market.cancel(p,
                                    com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "offre"), Moderation.roleOf(p).atLeast(Role.MODERATEUR))))))
                    .then(Commands.literal("acheter").then(Commands.argument("offre", com.mojang.brigadier.arguments.LongArgumentType.longArg(1))
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Market.buy(p, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "offre"), 0)))
                            .then(Commands.argument("quantite", IntegerArgumentType.integer(1))
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.Market.buy(p, com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "offre"),
                                            IntegerArgumentType.getInteger(ctx, "quantite")))))))
                    .then(Commands.literal("recuperer").executes(ctx -> run(ctx, p -> {
                        if (GodData.get(p).marketReturns.isEmpty()) return "Rien à récupérer.";
                        if (com.godmod.system.Market.deliver(p, true) == 0) return "Libérez de la place dans votre inventaire.";
                        return null;
                    })))
                    .then(Commands.literal("prix").executes(ctx -> run(ctx, com.godmod.system.Market::priceInfo)))
                    .then(Commands.literal("courbe")
                            .executes(ctx -> run(ctx, p -> com.godmod.system.Market.chart(p, null)))
                            .then(Commands.argument("objet", StringArgumentType.greedyString()).suggests(listed)
                                    .executes(ctx -> run(ctx, p -> com.godmod.system.Market.chart(p, StringArgumentType.getString(ctx, "objet")))))));
        }
    }

    private static void registerShop(CommandDispatcher<CommandSourceStack> d) {
        registerMarket(d);
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

    private static int antibotToggle(CommandContext<CommandSourceStack> ctx, String what) {
        boolean v = on(StringArgumentType.getString(ctx, "etat"));
        Settings s = GodData.settings();
        switch (what) {
            case "noms" -> s.antibotNames = v;
            case "ip" -> s.antibotIpCheck = v;
            default -> s.antibotFlood = v;
        }
        GodData.dirty();
        reply(ctx, "Anti-bot (" + what + ") : " + (v ? "activé." : "désactivé."));
        return 1;
    }

    private static void registerAntiBot(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("antibot").requires(Moderation.require(Role.ADMIN))
                .executes(ctx -> {
                    Settings s = GodData.settings();
                    ctx.getSource().sendSystemMessage(Msg.header("Anti-bot"));
                    reply(ctx, "Pseudos générés (ex. QuietWhale5404) : " + (s.antibotNames ? "refusés" : "acceptés"));
                    reply(ctx, "Datacenters / VPN / proxys (nouveaux comptes) : " + (s.antibotIpCheck ? "refusés" : "acceptés"));
                    reply(ctx, "Vague de nouveaux comptes : " + (s.antibotFlood ? "fermeture 10 min" : "ignorée")
                            + (com.godmod.system.AntiBot.lockdownLeft() > 0 ? " · FERMÉ encore " + Msg.time(com.godmod.system.AntiBot.lockdownLeft()) : ""));
                    reply(ctx, "Mot de passe obligatoire : " + (s.authEnabled ? "oui (expulsion après " + s.authTimeoutSeconds + " s sans /register)" : "NON (/auth on conseillé)"));
                    reply(ctx, "Pseudos autorisés : " + (s.antibotAllowed.isEmpty() ? "aucun" : String.join(", ", s.antibotAllowed))
                            + " · IP bannies : " + s.bannedIps.size());
                    reply(ctx, "Les joueurs déjà inscrits ne sont jamais concernés.");
                    return 1;
                })
                .then(Commands.literal("noms").then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> antibotToggle(ctx, "noms"))))
                .then(Commands.literal("ip").then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> antibotToggle(ctx, "ip"))))
                .then(Commands.literal("vague").then(Commands.argument("etat", StringArgumentType.word()).suggests(words("on", "off")).executes(ctx -> antibotToggle(ctx, "vague"))))
                .then(Commands.literal("autoriser").then(Commands.argument("pseudo", StringArgumentType.word()).executes(ctx -> {
                    String n = StringArgumentType.getString(ctx, "pseudo");
                    if (GodData.settings().antibotAllowed.stream().noneMatch(x -> x.equalsIgnoreCase(n))) GodData.settings().antibotAllowed.add(n);
                    GodData.dirty();
                    reply(ctx, n + " peut se connecter malgré l'anti-bot.");
                    return 1;
                })))
                .then(Commands.literal("retirer").then(Commands.argument("pseudo", StringArgumentType.word()).executes(ctx -> {
                    String n = StringArgumentType.getString(ctx, "pseudo");
                    GodData.settings().antibotAllowed.removeIf(x -> x.equalsIgnoreCase(n));
                    GodData.dirty();
                    reply(ctx, n + " n'est plus autorisé d'office.");
                    return 1;
                })))
                .then(Commands.literal("scan").executes(ctx -> {
                    int n = com.godmod.system.AntiBot.scan(ctx.getSource().getServer());
                    reply(ctx, n + " bot(s) expulsé(s) ; les adresses des autres nouveaux comptes sont en cours de vérification.");
                    return 1;
                }))
                .then(Commands.literal("expulser").executes(ctx -> {
                    int n = com.godmod.system.AntiBot.kickUnregistered(ctx.getSource().getServer(), "Expulsé : compte non inscrit.");
                    reply(ctx, n + " joueur(s) non inscrit(s) expulsé(s).");
                    return 1;
                }))
                .then(Commands.literal("ouvrir").executes(ctx -> {
                    com.godmod.system.AntiBot.endLockdown();
                    reply(ctx, "Inscriptions rouvertes.");
                    return 1;
                }))
                .then(Commands.literal("purge").executes(ctx -> {
                    reply(ctx, com.godmod.system.AntiBot.purge(ctx.getSource().getServer()) + " profil(s) de comptes jamais inscrits supprimé(s).");
                    return 1;
                }))
                .then(Commands.literal("bannirip").then(Commands.argument("cible", StringArgumentType.word()).suggests(ONLINE).executes(ctx -> {
                    String c = StringArgumentType.getString(ctx, "cible");
                    ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(c);
                    String ip = t != null ? com.godmod.system.AuthSystem.ip(t) : c;
                    if (ip == null || !ip.matches("[0-9a-fA-F.:]+")) {
                        PlayerProfile pp = GodData.byName(c);
                        ip = pp == null ? null : pp.lastIp;
                    }
                    if (ip == null) return fail(ctx, "Adresse introuvable : donnez un joueur connecté, un joueur connu ou une IP.");
                    if (!GodData.settings().bannedIps.contains(ip)) GodData.settings().bannedIps.add(ip);
                    GodData.dirty();
                    GodData.log("ANTIBOT : IP " + ip + " bannie par " + ctx.getSource().getTextName());
                    int n = 0;
                    for (ServerPlayer sp : new ArrayList<>(ctx.getSource().getServer().getPlayerList().getPlayers()))
                        if (ip.equals(com.godmod.system.AuthSystem.ip(sp))) {
                            sp.connection.disconnect(Component.literal("Cette adresse est bannie du serveur."));
                            n++;
                        }
                    reply(ctx, "Adresse " + ip + " bannie (" + n + " joueur(s) expulsé(s)).");
                    return 1;
                })))
                .then(Commands.literal("debannirip").then(Commands.argument("ip", StringArgumentType.word()).executes(ctx -> {
                    String ip = StringArgumentType.getString(ctx, "ip");
                    boolean r = GodData.settings().bannedIps.remove(ip);
                    GodData.dirty();
                    return r ? reply1(ctx, "Adresse " + ip + " débannie.") : fail(ctx, "Cette adresse n'est pas bannie.");
                }))));
    }

    private static int reply1(CommandContext<CommandSourceStack> ctx, String s) {
        reply(ctx, s);
        return 1;
    }

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        registerAntiBot(d);
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
                                        .suggests(words("mortel", "eveille", "demi_dieu", "dieu", "dieu_supreme", "titan", "primordial", "astral", "eternel", "createur"))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            Tier tier = Tier.parse(StringArgumentType.getString(ctx, "palier"));
                                            if (tier == null) return "!Palier inconnu : mortel, eveille, demi_dieu, dieu, dieu_supreme, titan, primordial, astral, eternel, createur.";
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
