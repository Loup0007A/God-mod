package com.godmod.command;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.system.AuthSystem;
import com.godmod.system.Moderation;
import com.godmod.system.PowerSystem;
import com.godmod.system.WorldBuilder;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Compte (mot de passe), modération (/mod), équipe (/staff), chat d'équipe (/sc). */
public final class StaffCommands {
    private StaffCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) names.add(p.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerAccount(d);
        registerMod(d);
        registerStaff(d);
    }

    // ------------------------------------------------------------------ Compte

    private static int player(CommandContext<CommandSourceStack> ctx, Function<ServerPlayer, String> action) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!GodData.ready()) return 0;
        String err = action.apply(p);
        if (err != null && !err.isEmpty()) {
            Msg.err(p, err);
            return 0;
        }
        return 1;
    }

    /**
     * Les mots de passe sont lus en texte libre (tous les caractères sont acceptés : !, @, é…),
     * puis découpés sur les espaces : /register &lt;mdp&gt; &lt;mdp&gt;.
     */
    private static String[] twoWords(String raw) {
        String[] parts = raw.trim().split("\\s+");
        return parts.length == 2 ? parts : null;
    }

    private static void registerAccount(CommandDispatcher<CommandSourceStack> d) {
        for (String name : new String[]{"register", "reg", "inscription"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> player(ctx, p -> "Choisissez un mot de passe et tapez-le deux fois : /register <motdepasse> <motdepasse>"))
                    .then(Commands.argument("motdepasses", StringArgumentType.greedyString())
                            .executes(ctx -> player(ctx, p -> {
                                String[] w = twoWords(StringArgumentType.getString(ctx, "motdepasses"));
                                if (w == null) return "Tapez le mot de passe deux fois, séparé par un espace (sans espace dedans) : /register <motdepasse> <motdepasse>";
                                return AuthSystem.register(p, w[0], w[1]);
                            }))));
        }
        for (String name : new String[]{"login", "l", "connexion"}) {
            d.register(Commands.literal(name)
                    .executes(ctx -> player(ctx, p -> "Tapez : /login <motdepasse>"))
                    .then(Commands.argument("motdepasse", StringArgumentType.greedyString())
                            .executes(ctx -> player(ctx, p -> AuthSystem.login(p, StringArgumentType.getString(ctx, "motdepasse").trim())))));
        }
        d.register(Commands.literal("motdepasse")
                .executes(ctx -> player(ctx, p -> "Tapez : /motdepasse <ancien> <nouveau>"))
                .then(Commands.argument("ancien_nouveau", StringArgumentType.greedyString())
                        .executes(ctx -> player(ctx, p -> {
                            String[] w = twoWords(StringArgumentType.getString(ctx, "ancien_nouveau"));
                            if (w == null) return "Tapez : /motdepasse <ancien> <nouveau> (sans espace dans les mots de passe).";
                            return AuthSystem.changePassword(p, w[0], w[1]);
                        }))));
    }

    // ------------------------------------------------------------------ /mod

    private static PlayerProfile target(CommandContext<CommandSourceStack> ctx) {
        return GodData.byName(StringArgumentType.getString(ctx, "joueur"));
    }

    private static String by(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getTextName();
    }

    /** Exécute une action de modération ; un résultat commençant par « ! » est une erreur. */
    private static int act(CommandContext<CommandSourceStack> ctx, Function<PlayerProfile, String> action) {
        if (!GodData.ready()) return 0;
        PlayerProfile t = target(ctx);
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("Joueur inconnu (il doit s'être connecté au moins une fois)."));
            return 0;
        }
        if (ctx.getSource().getEntity() != null && ctx.getSource().getEntity().getUUID().toString().equals(t.uuid)) {
            ctx.getSource().sendFailure(Component.literal("Vous ne pouvez pas vous viser vous-même."));
            return 0;
        }
        Role mine = Moderation.roleOf(ctx.getSource());
        Role theirs = t.role == null ? Role.JOUEUR : t.role;
        if (theirs.ordinal() > mine.ordinal()) {
            ctx.getSource().sendFailure(Component.literal("Vous ne pouvez pas sanctionner un membre de rang supérieur."));
            return 0;
        }
        String res = action.apply(t);
        if (res.startsWith("!")) {
            ctx.getSource().sendFailure(Component.literal(res.substring(1)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text(res, ChatFormatting.GREEN)), false);
        return 1;
    }

    /** Consultation (pas une sanction) : aucun contrôle de rang. */
    private static int look(CommandContext<CommandSourceStack> ctx, Function<PlayerProfile, String> action) {
        if (!GodData.ready()) return 0;
        PlayerProfile t = target(ctx);
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("Joueur inconnu (il doit s'être connecté au moins une fois)."));
            return 0;
        }
        String res = action.apply(t);
        if (res.startsWith("!")) {
            ctx.getSource().sendFailure(Component.literal(res.substring(1)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text(res, ChatFormatting.GREEN)), false);
        return 1;
    }

    private static final SuggestionProvider<CommandSourceStack> ONLINE = (ctx, b) ->
            SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b);

    /** Copie en lecture seule de l'inventaire (ou du coffre de l'Ender) d'un joueur. */
    private static void showInventory(ServerPlayer viewer, ServerPlayer target, boolean ender) {
        String name = target.getName().getString();
        com.godmod.menu.Page page = new com.godmod.menu.Page((ender ? "Coffre de l'Ender de " : "Inventaire de ") + name);
        if (ender) {
            var ec = target.getEnderChestInventory();
            for (int i = 0; i < Math.min(27, ec.getContainerSize()); i++) page.set(i, ec.getItem(i).copy());
        } else {
            var inv = target.getInventory();
            for (int i = 9; i < 36; i++) page.set(i - 9, inv.getItem(i).copy());
            for (int i = 0; i < 9; i++) page.set(27 + i, inv.getItem(i).copy());
            for (int i = 36; i < Math.min(41, inv.getContainerSize()); i++) page.set(i, inv.getItem(i).copy());
        }
        page.set(45, com.godmod.menu.Page.icon(net.minecraft.world.item.Items.PAPER, name, ChatFormatting.YELLOW,
                ender ? "§7Coffre de l'Ender" : "§7Rangées 1-3 : inventaire · rangée 4 : barre d'outils",
                ender ? "" : "§7Rangée 5 : armure et main secondaire",
                "§7Vie : " + Math.round(target.getHealth()) + " · Niveau d'XP : " + target.experienceLevel,
                "§8Lecture seule — cliquez pour actualiser"), (sp, c) -> sp.level().getServer().execute(() -> showInventory(sp, target, ender)));
        page.close();
        page.open(viewer);
    }

    /** Liste de l'équipe (en ligne / hors ligne). */
    public static void listTeam(CommandSourceStack src) {
        src.sendSystemMessage(Msg.header("Équipe du serveur"));
        int n = 0;
        for (PlayerProfile p : GodData.state().players.values()) {
            if (p.role == null || p.role == Role.JOUEUR) continue;
            boolean online = PowerSystem.online(src.getServer(), p.uuid) != null;
            src.sendSystemMessage(Msg.text(online ? "● " : "○ ", online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(Component.literal(p.name).withStyle(p.role.color))
                    .append(Msg.text(" — " + p.role.label + (online ? "" : " (vu il y a " + Msg.time(System.currentTimeMillis() - p.lastSeen) + ")"), ChatFormatting.GRAY)));
            n++;
        }
        if (n == 0) src.sendSystemMessage(Msg.text("Personne pour l'instant : /modo ajouter <joueur>", ChatFormatting.GRAY));
        src.sendSystemMessage(Msg.text("Les opérateurs du serveur (op) sont aussi admins.", ChatFormatting.DARK_GRAY));
    }

    private static String reason(CommandContext<CommandSourceStack> ctx) {
        try {
            return StringArgumentType.getString(ctx, "raison");
        } catch (IllegalArgumentException e) {
            return "Aucune raison indiquée";
        }
    }

    private static void registerMod(CommandDispatcher<CommandSourceStack> d) {
        var mod = Commands.literal("mod").requires(Moderation.require(Role.MODERATEUR))
                .executes(ctx -> help(ctx))
                .then(Commands.literal("kick").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.kick(ctx.getSource().getServer(), t, reason(ctx), by(ctx))))
                        .then(Commands.argument("raison", StringArgumentType.greedyString())
                                .executes(ctx -> act(ctx, t -> Moderation.kick(ctx.getSource().getServer(), t, reason(ctx), by(ctx)))))))
                .then(Commands.literal("mute").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 7))
                                .executes(ctx -> act(ctx, t -> Moderation.mute(ctx.getSource().getServer(), t,
                                        IntegerArgumentType.getInteger(ctx, "minutes"), reason(ctx), by(ctx))))
                                .then(Commands.argument("raison", StringArgumentType.greedyString())
                                        .executes(ctx -> act(ctx, t -> Moderation.mute(ctx.getSource().getServer(), t,
                                                IntegerArgumentType.getInteger(ctx, "minutes"), reason(ctx), by(ctx))))))))
                .then(Commands.literal("unmute").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.unmute(ctx.getSource().getServer(), t, by(ctx))))))
                .then(Commands.literal("ban").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("heures", IntegerArgumentType.integer(0, 24 * 365))
                                .executes(ctx -> act(ctx, t -> Moderation.ban(ctx.getSource().getServer(), t,
                                        IntegerArgumentType.getInteger(ctx, "heures"), reason(ctx), by(ctx), Moderation.roleOf(ctx.getSource()))))
                                .then(Commands.argument("raison", StringArgumentType.greedyString())
                                        .executes(ctx -> act(ctx, t -> Moderation.ban(ctx.getSource().getServer(), t,
                                                IntegerArgumentType.getInteger(ctx, "heures"), reason(ctx), by(ctx), Moderation.roleOf(ctx.getSource()))))))))
                .then(Commands.literal("unban").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.unban(t, by(ctx))))))
                .then(Commands.literal("warn").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("raison", StringArgumentType.greedyString())
                                .executes(ctx -> act(ctx, t -> Moderation.warn(ctx.getSource().getServer(), t, reason(ctx), by(ctx)))))))
                .then(Commands.literal("freeze").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.toggleFreeze(ctx.getSource().getServer(), t, by(ctx))))))
                .then(Commands.literal("tp").then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> {
                            ServerPlayer me = ctx.getSource().getPlayerOrException();
                            return look(ctx, t -> {
                                ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                if (sp == null) return "!" + t.name + " n'est pas connecté.";
                                Teleport.to(me, (ServerLevel) sp.level(), sp.getX(), sp.getY(), sp.getZ());
                                return "Téléporté auprès de " + t.name + ".";
                            });
                        })))
                .then(Commands.literal("tphere").then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> {
                            ServerPlayer me = ctx.getSource().getPlayerOrException();
                            return act(ctx, t -> {
                                ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                if (sp == null) return "!" + t.name + " n'est pas connecté.";
                                Teleport.to(sp, (ServerLevel) me.level(), me.getX(), me.getY(), me.getZ());
                                Msg.info(sp, "Vous avez été téléporté par l'équipe de modération.");
                                GodData.log("TP : " + by(ctx) + " fait venir " + t.name);
                                return t.name + " a été téléporté auprès de vous.";
                            });
                        })))
                .then(Commands.literal("invsee").then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> {
                            ServerPlayer me = ctx.getSource().getPlayerOrException();
                            return look(ctx, t -> {
                                ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                if (sp == null) return "!" + t.name + " n'est pas connecté.";
                                me.level().getServer().execute(() -> showInventory(me, sp, false));
                                GodData.log("INVSEE : " + by(ctx) + " consulte l'inventaire de " + t.name);
                                return "Inventaire de " + t.name + " (lecture seule).";
                            });
                        })))
                .then(Commands.literal("ecsee").then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE)
                        .executes(ctx -> {
                            ServerPlayer me = ctx.getSource().getPlayerOrException();
                            return look(ctx, t -> {
                                ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                if (sp == null) return "!" + t.name + " n'est pas connecté.";
                                me.level().getServer().execute(() -> showInventory(me, sp, true));
                                GodData.log("ECSEE : " + by(ctx) + " consulte le coffre de l'Ender de " + t.name);
                                return "Coffre de l'Ender de " + t.name + " (lecture seule).";
                            });
                        })))
                .then(Commands.literal("info").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> look(ctx, t -> info(ctx, t)))))
                .then(Commands.literal("journal")
                        .executes(ctx -> log(ctx, 15))
                        .then(Commands.argument("lignes", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> log(ctx, IntegerArgumentType.getInteger(ctx, "lignes")))));
        d.register(mod);

        d.register(Commands.literal("sc").requires(Moderation.require(Role.MODERATEUR))
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                    Moderation.staffChat(ctx.getSource().getServer(), by(ctx), Moderation.roleOf(ctx.getSource()),
                            StringArgumentType.getString(ctx, "message"));
                    return 1;
                })));

        // Signalement accessible à tous les joueurs.
        d.register(Commands.literal("signaler").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                .then(Commands.argument("raison", StringArgumentType.greedyString()).executes(ctx -> player(ctx, me -> {
                    PlayerProfile t = target(ctx);
                    if (t == null) return "Joueur inconnu : vérifiez l'orthographe du pseudo.";
                    if (t.uuid.equals(me.getUUID().toString())) return "Vous ne pouvez pas vous signaler vous-même.";
                    PlayerProfile mine = GodData.get(me);
                    long wait = mine.lastReport + 60_000 - System.currentTimeMillis();
                    if (wait > 0) return "Patientez " + Msg.time(wait) + " avant un nouveau signalement.";
                    mine.lastReport = System.currentTimeMillis();
                    GodData.dirty();
                    int staff = 0;
                    for (ServerPlayer sp : ctx.getSource().getServer().getPlayerList().getPlayers())
                        if (Moderation.roleOf(sp).atLeast(Role.MODERATEUR)) staff++;
                    Moderation.staffAlert(ctx.getSource().getServer(), "Signalement de " + by(ctx) + " contre " + t.name + " : " + reason(ctx));
                    GodData.log("SIGNALEMENT : " + by(ctx) + " -> " + t.name + " (" + reason(ctx) + ")");
                    Msg.ok(me, "Signalement transmis à l'équipe" + (staff == 0 ? " (aucun membre connecté : il est enregistré dans le journal)" : "") + ". Merci !");
                    return null;
                })))));
    }

    private static String info(CommandContext<CommandSourceStack> ctx, PlayerProfile t) {
        CommandSourceStack src = ctx.getSource();
        boolean online = PowerSystem.online(src.getServer(), t.uuid) != null;
        line(src, t.name + (online ? " (en ligne)" : " (hors ligne, vu il y a " + Msg.time(System.currentTimeMillis() - t.lastSeen) + ")"));
        line(src, "Rang " + (t.role == null ? "Joueur" : t.role.label) + " · " + t.tier.label + " · compte "
                + (t.passwordHash == null ? "non protégé" : "protégé") + " · IP " + (t.lastIp == null ? "?" : t.lastIp));
        if (Moderation.isBanned(t)) line(src, "BANNI : " + t.banReason + " (par " + t.bannedBy + ")");
        if (Moderation.isMuted(t)) line(src, "Muet encore " + Msg.time(t.mutedUntil - System.currentTimeMillis()));
        line(src, "Avertissements : " + t.warnings.size());
        for (int i = Math.max(0, t.warnings.size() - 5); i < t.warnings.size(); i++) line(src, "  - " + t.warnings.get(i));
        line(src, "Tués " + t.kills + " · Morts " + t.deaths + " · Argent " + t.money + " · Essence " + t.essence);
        return "Fin de la fiche de " + t.name + ".";
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Msg.text(text, ChatFormatting.GRAY), false);
    }

    private static int log(CommandContext<CommandSourceStack> ctx, int n) {
        List<String> log = GodData.state().log;
        if (log.isEmpty()) line(ctx.getSource(), "Le journal est vide.");
        int from = Math.max(0, log.size() - n);
        for (int i = from; i < log.size(); i++) line(ctx.getSource(), log.get(i));
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        Help.show(ctx.getSource(), "moderation");
        if (Moderation.roleOf(ctx.getSource()).atLeast(Role.ADMIN))
            ctx.getSource().sendSystemMessage(Msg.text("Administration : ", ChatFormatting.GRAY).append(Msg.button("/aide admin", "/aide admin", "Commandes d'administration", ChatFormatting.RED)));
        return 1;
    }

    // ------------------------------------------------------------------ /staff et /auth (admins)

    private static void registerStaff(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("staff").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("ajouter").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("rang", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("moderateur", "admin"), b))
                                .executes(ctx -> act(ctx, t -> {
                                    Role r = Role.parse(StringArgumentType.getString(ctx, "rang"));
                                    if (r == null || r == Role.JOUEUR) return "!Rang inconnu : moderateur ou admin.";
                                    return Moderation.setRole(ctx.getSource().getServer(), t, r, by(ctx));
                                })))))
                .then(Commands.literal("retirer").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.setRole(ctx.getSource().getServer(), t, Role.JOUEUR, by(ctx))))))
                .then(Commands.literal("liste").executes(ctx -> {
                    listTeam(ctx.getSource());
                    return 1;
                })));

        d.register(Commands.literal("auth").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("on").executes(ctx -> setAuth(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setAuth(ctx, false)))
                .then(Commands.literal("session").then(Commands.argument("heures", IntegerArgumentType.integer(0, 24 * 30)).executes(ctx -> {
                    GodData.settings().authSessionHours = IntegerArgumentType.getInteger(ctx, "heures");
                    GodData.dirty();
                    line(ctx.getSource(), "Reconnexion automatique depuis la même IP : " + GodData.settings().authSessionHours + " h (0 = désactivée).");
                    return 1;
                })))
                .then(Commands.literal("reset").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> {
                            AuthSystem.reset(ctx.getSource().getServer(), t);
                            GodData.log("COMPTE : " + by(ctx) + " réinitialise le mot de passe de " + t.name);
                            return "Mot de passe de " + t.name + " réinitialisé : il devra refaire /register.";
                        })))));

        d.register(Commands.literal("structurer").requires(Moderation.require(Role.ADMIN)).executes(ctx -> {
            WorldBuilder.build(ctx.getSource().getServer(), ctx.getSource());
            line(ctx.getSource(), "Reconstruction du Sanctuaire lancée (dimension godmod:spawn).");
            return 1;
        }));
    }

    private static int setAuth(CommandContext<CommandSourceStack> ctx, boolean on) {
        GodData.settings().authEnabled = on;
        GodData.dirty();
        GodData.log("ADMIN " + by(ctx) + " : authentification " + (on ? "activée" : "désactivée"));
        line(ctx.getSource(), "Authentification par mot de passe " + (on ? "activée" : "désactivée") + " (effet aux prochaines connexions).");
        return 1;
    }
}
